#!/usr/bin/env python3
"""
v0.8 Chunk 8.5 — OFFLINE guide-content research tool.

  ⚠️  THIS NEVER SHIPS IN THE APK.  ⚠️

It runs during build sessions only. It fetches candidate setup-guide content
from public sources, normalises it into our GuideDto JSON shape, and writes
DRAFTS to tools/scrape-guides/drafts/ for a human to review. Nothing it
produces reaches users until a person reads it and copies vetted entries into
app/src/main/assets/seed/guides/ (or the IsItCompatible-DB repo) by hand.

Why offline-only (the locked v0.8 decision):
  - In-app scraping is ToS-grey, legally risky, and breaks when sites change.
  - Scraped content has no reliable "as of" date — it could be years stale.
  - Both contradict the app's honesty + F-Droid-clean principles.
So scraping is a *research aid for the maintainer*, gated behind human review.

Sources:
  1. EmuReady API — the one legal, stable, dated source. Console games.
     (Needs an API base + token if EmuReady requires one; configured below.)
  2. "manual" sources (wikis, Discord pins) — this tool only RECORDS the URL
     and a TODO; it does NOT auto-scrape them. A human fetches + vets those.

Usage:
  python tools/scrape-guides/scrape_guides.py emuready --game "God of War" --platform PS2
  python tools/scrape-guides/scrape_guides.py manual   --emulator winlator-cmod --url https://...

Output:
  tools/scrape-guides/drafts/<timestamp>-<slug>.json   (a draft GuideDto)
  Every draft is tagged tier=3 (EmuReady) or tier=0 (UNVETTED manual) so it can
  NEVER be mistaken for an authored/verified guide if accidentally loaded.

No third-party deps beyond stdlib + (optional) requests for the EmuReady call.
"""

from __future__ import annotations
import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAFTS = Path(__file__).resolve().parent / "drafts"
DRAFTS.mkdir(exist_ok=True)

# EmuReady public API base. Adjust if their API surface differs; this tool is
# run by a human who can verify the endpoint before each session.
EMUREADY_API = os.environ.get("EMUREADY_API", "https://emuready.com/api")


def slugify(s: str) -> str:
    return "".join(c if c.isalnum() else "-" for c in s.lower()).strip("-")


def write_draft(name: str, payload: dict) -> Path:
    # Timestamp passed in by the human via env (we can't trust wall-clock for
    # reproducibility, and this is a manual tool anyway).
    stamp = os.environ.get("SCRAPE_STAMP", "draft")
    out = DRAFTS / f"{stamp}-{slugify(name)}.json"
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return out


def emuready_fetch(game: str, platform: str) -> dict:
    """
    Query EmuReady for compatibility notes on a game and shape them into a
    DRAFT GuideDto (tier=3, 'From EmuReady', with a backlink). Returns the draft
    dict. The human reviewer decides whether it's good enough to commit.
    """
    q = urllib.parse.urlencode({"q": game, "platform": platform})
    url = f"{EMUREADY_API}/search?{q}"
    print(f"[emuready] GET {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "IsItCompatible-research-tool"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[emuready] fetch failed: {e}")
        print("[emuready] (EmuReady's API surface may differ — verify the endpoint, "
              "or fall back to recording the page URL as a 'manual' source.)")
        return {}

    # We DON'T assume EmuReady's exact schema — we record the raw hit + a
    # skeleton guide for the human to flesh out. Honest: we never fabricate steps.
    draft = {
        "_draft": True,
        "_review_required": "Human must verify accuracy + currency before committing.",
        "_raw_emuready": data,
        "guide": {
            "id": f"{slugify(platform)}:{slugify(game)}:REVIEW:t3",
            "gameId": f"{platform.lower()}:{slugify(game)}",
            "emulatorId": "REVIEW_SET_ME",
            "tier": 3,
            "sourceLabel": "From EmuReady",
            "sourceUrl": f"https://emuready.com/?q={urllib.parse.quote(game)}",
            "dataAsOf": os.environ.get("SCRAPE_DATE", ""),
            "steps": [],
            "troubleshooting": [],
        },
    }
    return draft


def manual_record(emulator: str, url: str) -> dict:
    """
    For wiki/Discord/Reddit sources: this tool does NOT scrape them. It only
    records the URL + a tier=0 (UNVETTED) skeleton so a human can fetch the page
    themselves, read it, and hand-write a vetted guide. tier=0 guarantees the
    app would never surface it even if loaded by mistake (resolver wants tier>=1,
    and our loader can filter tier==0 out).
    """
    return {
        "_draft": True,
        "_review_required": "UNVETTED. A human must read the source URL and "
                            "hand-author the steps. This tool did NOT scrape it.",
        "guide": {
            "id": f"manual:{slugify(emulator)}:REVIEW:t0",
            "gameId": None,
            "emulatorId": emulator,
            "tier": 0,
            "sourceLabel": "UNVETTED — do not ship",
            "sourceUrl": url,
            "dataAsOf": "",
            "steps": [],
            "troubleshooting": [],
        },
    }


def main():
    ap = argparse.ArgumentParser(description="Offline guide-content research tool (never ships).")
    sub = ap.add_subparsers(dest="cmd", required=True)

    e = sub.add_parser("emuready", help="Draft a guide from EmuReady's API.")
    e.add_argument("--game", required=True)
    e.add_argument("--platform", required=True)

    m = sub.add_parser("manual", help="Record a manual source URL for human review (no scrape).")
    m.add_argument("--emulator", required=True)
    m.add_argument("--url", required=True)

    args = ap.parse_args()

    if args.cmd == "emuready":
        draft = emuready_fetch(args.game, args.platform)
        if not draft:
            sys.exit(1)
        out = write_draft(args.game, draft)
        print(f"[ok] wrote draft → {out.relative_to(ROOT)}")
        print("     REVIEW IT, set emulatorId + steps, then copy the `guide` block "
              "into app/src/main/assets/seed/guides/ if accurate.")
    elif args.cmd == "manual":
        draft = manual_record(args.emulator, args.url)
        out = write_draft(f"{args.emulator}-manual", draft)
        print(f"[ok] recorded manual source → {out.relative_to(ROOT)}")
        print("     This was NOT scraped. Open the URL, read it, hand-author the guide.")


if __name__ == "__main__":
    main()
