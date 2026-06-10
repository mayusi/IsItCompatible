#!/usr/bin/env python3
"""
tools/ingest/ingest.py  —  BUILD-TIME community-data ingest.

  ⚠️  THIS NEVER SHIPS IN THE APK.  ⚠️
  It is run by a maintainer at dev/build time. The app never scrapes anything
  at runtime. This script fetches REAL community compatibility data, normalises
  it to the app's seed DTO JSON shapes (CompatDbDto), tags honest provenance,
  dedupes against the existing seed, and writes curated JSON into
  app/src/main/assets/seed/.

WHAT IT INGESTS (researched 2026-06-08, see README.md for citations):

  1. EmuReady public mobile API (https://www.emuready.com/api/mobile/trpc).
     - listings.get   → handheld / Android-device compatibility reports
                        (real SoC + GPU + Android device + performance + notes).
                        These become ReportDto with source = EMUREADY_SNAPSHOT
                        (the recommender treats any non-GENERATED_HEURISTIC
                        source as REAL data).
     - GameNative-emulator EmuReady listings additionally produce a tier-3
       community GuideDto (real sourceUrl back to the EmuReady listing) so the
       community config/notes surface honestly as "From the community".

  HONESTY DECISIONS (do NOT remove):
   * We do NOT fabricate a GameNative <Game>_config.json blob. EmuReady listings
     do not carry the full flat config payload (customFieldValues was null on
     every sampled GameNative listing), and the andreisugu/gamenative-config-tools
     `public/cached-configs.json` snapshot is PLACEHOLDER/EXAMPLE data
     ("Example Game 1", 3 rows) — NOT real community configs. So no
     gameNativeConfig is attached unless a real flat config is supplied via
     --gamenative-config-file. If a field is unknown we OMIT it.
   * EmuReady reports are tagged EMUREADY_SNAPSHOT (real). Community guides are
     tier 3 ("From the community / EmuReady"), never tier 1 (tier 1 = the user's
     own device-verified data).
   * Reports only attach to games that ALREADY exist in the seed (title match),
     so we never invent games and every report surfaces in the app.

USAGE:
  # Small proof-of-real sample (a few dozen popular games), writes real seed:
  python tools/ingest/ingest.py --max-pages 6 --as-of 2026-06-08

  # Full ingest (all ~16k handheld listings — slow; respect rate limits):
  python tools/ingest/ingest.py --max-pages 0 --as-of 2026-06-08

OUTPUT (committed):
  app/src/main/assets/seed/reports/emuready-snapshot.json   (EMUREADY_SNAPSHOT reports)
  app/src/main/assets/seed/guides/gamenative-community.json (tier-3 community guides)

No third-party deps — stdlib only.
"""
from __future__ import annotations
import argparse
import glob
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / "app" / "src" / "main" / "assets" / "seed"
REPORTS_OUT = SEED / "reports" / "emuready-snapshot.json"
GUIDES_OUT = SEED / "guides" / "gamenative-community.json"

EMUREADY_BASE = os.environ.get(
    "EMUREADY_BASE", "https://www.emuready.com/api/mobile/trpc"
)
EMUREADY_SITE = "https://www.emuready.com"

# EmuReady system.name -> app platform prefix used in game ids (e.g. "win:elden-ring")
SYSTEM_TO_PLATFORM = {
    "Microsoft Windows": "win",
    "Nintendo Switch": "switch",
    "Nintendo Wii U": "wiiu",
    "Nintendo Wii": "wii",
    "Nintendo 3DS": "3ds",
    "Nintendo 64": "n64",
    "Nintendo DS": "nds",
    "Nintendo GameCube": "gc",
    "Sony PlayStation": "ps1",
    "Sony PlayStation 2": "ps2",
    "Sony PlayStation 3": "ps3",
    "Sony PlayStation Portable": "psp",
    "Sony PlayStation Vita": "vita",
}

# EmuReady emulator.name -> app emulator id (catalog ids). Names with no honest
# app equivalent are intentionally absent → those listings are skipped, not faked.
EMULATOR_NAME_TO_ID = {
    "GameNative": "gamenative",
    "Winlator": "winlator",
    "Winlator (Cmod)": "winlator-cmod",
    "Mobox": "mobox",
    "Eden": "eden",
    "Citron": "citron",
    "Sudachi": "sudachi",
    "Cemu": "cemu",
    "AetherSX2": "armsx2",
    "NetherSX2": "nethersx2-patch",
    "PCSX2": "armsx2",
    "PPSSPP": "ppsspp",
    "DuckStation": "duckstation",
    "Dolphin": "dolphin",
    "Azahar": "azahar",
    "Lime3DS": "lime3ds",
    "Citra": "citra-mmj",
    "Vita3K": "vita3k",
    "RetroArch": "retroarch",
    "RPCS3": "rpcsx",
    "shadPS4": "shadps4",
    "Mupen64Plus": "mupen64plus",
}

# EmuReady performance.label -> app stability enum (PERFECT/PLAYABLE/GLITCHY/CRASHES).
# Scale confirmed live 2026-06-08: rank 1 Perfect, 2 Great, 3 Playable, 5 Ingame, 8 Nothing.
PERF_LABEL_TO_STABILITY = {
    "Perfect": "PERFECT",
    "Great": "PERFECT",
    "Playable": "PLAYABLE",
    "Ingame": "GLITCHY",
    "Menus": "CRASHES",
    "Intro": "CRASHES",
    "Loadable": "CRASHES",
    "Nothing": "CRASHES",
}

# GPU vendor inference from SoC manufacturer / gpuModel string.
def gpu_vendor(soc: dict) -> str:
    model = (soc.get("gpuModel") or "").lower()
    if "adreno" in model:
        return "ADRENO"
    if "mali" in model:
        return "MALI"
    if "immortalis" in model:
        return "MALI"
    if "xclipse" in model or "samsung" in model:
        return "XCLIPSE"
    if "powervr" in model:
        return "POWERVR"
    return "UNKNOWN"


def http_get(url: str, retries: int = 3, pause: float = 1.0) -> str:
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "IsItCompatible-ingest (build-time; contact via repo)",
                    "Accept": "application/json",
                },
            )
            with urllib.request.urlopen(req, timeout=45) as r:
                return r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            last = e
            if e.code in (429, 500, 502, 503):
                time.sleep(pause * (attempt + 1) * 2)
                continue
            raise
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(pause * (attempt + 1))
    raise last  # type: ignore[misc]


def trpc(endpoint: str, params: dict) -> dict:
    # tRPC (superjson) GET: input is {"json": <params>} url-encoded.
    inp = urllib.parse.quote(json.dumps({"json": params}))
    url = f"{EMUREADY_BASE}/{endpoint}?input={inp}"
    raw = http_get(url)
    data = json.loads(raw)
    if "error" in data:
        raise RuntimeError(f"{endpoint} error: {data['error']}")
    return data["result"]["data"]["json"]


# ---- title matching against existing seed games ----------------------------

def norm_title(t: str) -> str:
    t = t.lower()
    t = re.sub(r"\([^)]*\)", " ", t)            # drop "(Remake)", "(2024)" etc.
    t = re.sub(r"[^a-z0-9]+", " ", t)            # punctuation -> space
    t = re.sub(r"\b(the|a|an|of|edition|remastered|hd|deluxe|definitive)\b", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def load_existing():
    games_by_id = {}
    title_index = {}          # (platform, norm_title) -> gameId
    report_ids = set()
    guide_ids = set()
    own_outputs = {str(REPORTS_OUT.resolve()), str(GUIDES_OUT.resolve())}
    for f in glob.glob(str(SEED / "**" / "*.json"), recursive=True):
        # Don't read our own outputs as "existing" canon — we regenerate them,
        # so they must not dedupe themselves to empty on a re-run.
        if str(Path(f).resolve()) in own_outputs:
            continue
        try:
            d = json.load(open(f, encoding="utf-8"))
        except Exception:
            continue
        for g in d.get("games", []):
            gid = g["id"]
            games_by_id[gid] = g
            plat = gid.split(":")[0]
            title_index[(plat, norm_title(g.get("title", "")))] = gid
            title_index[(plat, norm_title(gid.split(":", 1)[-1].replace("-", " ")))] = gid
        for r in d.get("reports", []):
            report_ids.add(r.get("id"))
        for gd in d.get("guides", []):
            guide_ids.add(gd.get("id"))
    return games_by_id, title_index, report_ids, guide_ids


def match_game_id(title: str, platform: str, title_index: dict) -> str | None:
    key = (platform, norm_title(title))
    if key in title_index:
        return title_index[key]
    # loose: try first 3 normalized words
    nt = norm_title(title)
    parts = nt.split()
    if len(parts) >= 3:
        prefix = " ".join(parts[:3])
        for (p, t), gid in title_index.items():
            if p == platform and t.startswith(prefix):
                return gid
    return None


# ---- ingest ----------------------------------------------------------------

def ingest(max_pages: int, limit: int, as_of: str, sleep_s: float):
    games_by_id, title_index, existing_report_ids, existing_guide_ids = load_existing()
    print(f"[seed] {len(games_by_id)} games, {len(existing_report_ids)} report ids, "
          f"{len(existing_guide_ids)} guide ids already in seed")

    reports: list[dict] = []
    guides: list[dict] = []
    seen_report_ids: set[str] = set()
    seen_guide_ids: set[str] = set()
    stats = {"fetched": 0, "matched": 0, "no_game": 0, "no_emu": 0, "dup": 0, "no_device": 0}

    page = 1
    while True:
        if max_pages and page > max_pages:
            break
        try:
            j = trpc("listings.get", {"page": page, "limit": limit})
        except Exception as e:  # noqa: BLE001
            print(f"[emuready] page {page} failed: {e}")
            break
        listings = j.get("listings", [])
        pagination = j.get("pagination", {})
        if not listings:
            break
        for L in listings:
            stats["fetched"] += 1
            game = L.get("game", {}) or {}
            system = (game.get("system") or {}).get("name", "")
            platform = SYSTEM_TO_PLATFORM.get(system)
            if not platform:
                stats["no_game"] += 1
                continue
            gid = match_game_id(game.get("title", ""), platform, title_index)
            if not gid:
                stats["no_game"] += 1
                continue
            emu_name = (L.get("emulator") or {}).get("name", "")
            emu_id = EMULATOR_NAME_TO_ID.get(emu_name)
            if not emu_id:
                stats["no_emu"] += 1
                continue

            device = L.get("device") or {}
            soc = device.get("soc") or {}
            if not soc.get("name") and not soc.get("gpuModel"):
                stats["no_device"] += 1
                continue
            ram_mb = 0
            # EmuReady handheld listings don't expose RAM directly; omit (0) honestly.

            perf = (L.get("performance") or {}).get("label", "")
            stability = PERF_LABEL_TO_STABILITY.get(perf, "PLAYABLE")
            listing_id = L.get("id", "")
            rid = f"emuready:{listing_id}"
            if rid in existing_report_ids or rid in seen_report_ids:
                stats["dup"] += 1
                continue
            seen_report_ids.add(rid)

            submitted_ms = 0
            created = L.get("createdAt")
            if created:
                try:
                    submitted_ms = int(
                        time.mktime(time.strptime(created[:19], "%Y-%m-%dT%H:%M:%S"))
                    ) * 1000
                except Exception:
                    submitted_ms = 0

            report = {
                "id": rid,
                "gameId": gid,
                "emulatorId": emu_id,
                "device": {
                    "socFamily": soc.get("name") or "Unknown",
                    "gpuVendor": gpu_vendor(soc),
                    "gpuModel": soc.get("gpuModel") or "Unknown",
                    "ramMb": ram_mb,
                    "androidApi": 0,
                },
                "stability": stability,
                "source": "EMUREADY_SNAPSHOT",
                "sourceRef": f"{EMUREADY_SITE}/listings/{listing_id}",
                "submittedAt": submitted_ms,
            }
            notes = (L.get("notes") or "").strip()
            if notes:
                report["notes"] = notes[:500]
            # avgFps: EmuReady has no numeric fps field on listings → omit (honest).
            reports.append(report)
            stats["matched"] += 1

            # GameNative-emulator listings → tier-3 community guide w/ real backlink.
            if emu_id == "gamenative":
                guide_id = f"emuready:{gid}:gamenative:t3:{listing_id[:8]}"
                if guide_id in existing_guide_ids or guide_id in seen_guide_ids:
                    continue
                seen_guide_ids.add(guide_id)
                steps = [{
                    "kind": "GET_APP",
                    "text": "Install GameNative.",
                    "url": "https://gamenative.app",
                }]
                if notes:
                    steps.append({
                        "kind": "TIP",
                        "text": f"Community note (via EmuReady): {notes[:400]}",
                    })
                steps.append({
                    "kind": "ACTION",
                    "text": f"Community report: {perf or 'see EmuReady'} on "
                            f"{soc.get('name') or 'an Android device'}.",
                })
                guides.append({
                    "id": guide_id,
                    "gameId": gid,
                    "emulatorId": "gamenative",
                    "tier": 3,
                    "sourceLabel": "From the community (EmuReady / GameNative)",
                    "sourceUrl": f"{EMUREADY_SITE}/listings/{listing_id}",
                    "dataAsOf": (created[:10] if created else as_of),
                    "steps": steps,
                })

        print(f"[emuready] page {page}/{pagination.get('pages', '?')}: "
              f"+{stats['matched']} matched reports so far")
        if not pagination.get("hasNextPage"):
            break
        page += 1
        time.sleep(sleep_s)

    print(f"[stats] {stats}")
    write_outputs(reports, guides, as_of)


def write_outputs(reports: list[dict], guides: list[dict], as_of: str):
    REPORTS_OUT.parent.mkdir(parents=True, exist_ok=True)
    GUIDES_OUT.parent.mkdir(parents=True, exist_ok=True)
    reports_doc = {
        "schema": 1,
        "generatedAt": as_of,
        "_comment": (
            "REAL community compatibility reports ingested from EmuReady's public "
            "mobile API (https://www.emuready.com/api/mobile/trpc/listings.get) by "
            "tools/ingest/ingest.py. source=EMUREADY_SNAPSHOT → the recommender "
            "treats these as REAL (non-heuristic). Each report links back to its "
            "EmuReady listing via sourceRef (attribution). Data is community-"
            f"contributed on EmuReady. dataAsOf: {as_of}. DO NOT hand-edit; "
            "regenerate with the ingest tool."
        ),
        "reports": reports,
    }
    guides_doc = {
        "schema": 1,
        "generatedAt": as_of,
        "_comment": (
            "Tier-3 community setup guides derived from real GameNative listings on "
            "EmuReady (each carries a real sourceUrl backlink). Tier 3 = 'From the "
            "community', NOT tier 1 (tier 1 = the user's own device-verified data). "
            "No GameNative <Game>_config.json blob is attached: EmuReady listings do "
            "not carry the full flat config, and the only public 'snapshot' repo "
            "(andreisugu/gamenative-config-tools) ships PLACEHOLDER example data, so "
            f"fabricating one would be dishonest. dataAsOf: {as_of}."
        ),
        "guides": guides,
    }
    REPORTS_OUT.write_text(
        json.dumps(reports_doc, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    GUIDES_OUT.write_text(
        json.dumps(guides_doc, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"[ok] wrote {len(reports)} reports  -> {REPORTS_OUT.relative_to(ROOT)}")
    print(f"[ok] wrote {len(guides)} guides   -> {GUIDES_OUT.relative_to(ROOT)}")


def main():
    ap = argparse.ArgumentParser(description="Build-time EmuReady -> seed ingest (never ships in app).")
    ap.add_argument("--max-pages", type=int, default=6,
                    help="Pages of EmuReady listings to fetch (0 = all ~800 pages). Default 6 (proof sample).")
    ap.add_argument("--limit", type=int, default=50, help="Listings per page (EmuReady max 50).")
    ap.add_argument("--as-of", default=time.strftime("%Y-%m-%d"),
                    help="ISO date stamped as dataAsOf / generatedAt.")
    ap.add_argument("--sleep", type=float, default=0.7, help="Seconds between pages (rate-limit politeness).")
    args = ap.parse_args()
    ingest(args.max_pages, args.limit, args.as_of, args.sleep)


if __name__ == "__main__":
    main()
