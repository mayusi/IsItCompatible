#!/usr/bin/env python3
"""
v0.4 Chunk 4.4 — bulk catalog + report generator.

Inputs:
  - app/src/main/assets/seed/games/*.json   (existing games we already shipped)
  - app/src/main/assets/seed/catalog.json   (canonical emulator + preset ids)
  - the BULK_GAMES table inline below       (1500+ new entries to add)
  - the RULES tables inline below           (platform/genre/year -> preset & fps)

Outputs (committed alongside the rest of the seed):
  - app/src/main/assets/seed/games/bulk-catalog.json
        ~1500 new game stubs across every platform we care about.
  - app/src/main/assets/seed/reports/generated-sd8.json
        Two reports per game (SD8 Elite + SD8 Gen 2) generated from the rules.

Reports carry source="GENERATED_HEURISTIC" so the UI labels them honestly:
"estimated by rules engine — help by submitting a real test".

Run:
  python tools/generate-reports/generate.py

No external dependencies (stdlib json only).
"""

from __future__ import annotations
import json
import os
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "seed"
GAMES_DIR = ASSETS / "games"
REPORTS_DIR = ASSETS / "reports"

# ---------------------------------------------------------------------------
# Device fingerprints we generate reports for (matches the plan).
# ---------------------------------------------------------------------------
DEV_SD8_ELITE = {
    "socFamily": "Snapdragon 8 Elite",
    "gpuVendor": "ADRENO",
    "gpuModel": "Adreno 830",
    "ramMb": 16384,
    "androidApi": 35,
}
DEV_SD8_GEN2 = {
    "socFamily": "Snapdragon 8 Gen 2",
    "gpuVendor": "ADRENO",
    "gpuModel": "Adreno 740",
    "ramMb": 12288,
    "androidApi": 33,
}

# ---------------------------------------------------------------------------
# Rule table — (platform, optional engine/year/genre keys) -> emulator + preset
# + (sd8e_fps, sd8e_stab, sd8gen2_fps, sd8gen2_stab).
# Order matters: first match wins, so put the most-specific rules first.
# Preset ids must exist in catalog.json.
# ---------------------------------------------------------------------------
# Tier: "perfect" 60 / "high" ~55 / "mid" ~40 / "low" ~25 / "very-low" ~15
def _rule(emu, preset, sd8e_fps, sd8e_stab, sd8g2_fps, sd8g2_stab, alternates=None):
    return {"emulatorId": emu, "presetId": preset,
            "sd8e": (sd8e_fps, sd8e_stab), "sd8g2": (sd8g2_fps, sd8g2_stab),
            "alternates": alternates or []}

def _alt(emu, preset, sd8e_fps_delta, sd8g2_fps_delta, stab_drop=0):
    """An alternate emulator for the same game. Deltas applied to the primary's fps;
    `stab_drop` shifts stability one tier worse per step (PERFECT->PLAYABLE->GLITCHY->CRASHES).
    Lets a rule emit N reports per game instead of one."""
    return {"emulatorId": emu, "presetId": preset,
            "sd8e_delta": sd8e_fps_delta, "sd8g2_delta": sd8g2_fps_delta,
            "stab_drop": stab_drop}

# v0.7 Chunk 7.2: cross-emulator alternate sets. Most Windows games can run on
# more than one Cmod/GameNative/GameHub/Mobox; the recommender should be able
# to choose between them based on the user's preferences (we just have to feed
# it the data). Deltas reflect community wisdom on each engine family.
ALT_MODERN_AAA = [
    # GameNative is better than Cmod for modern DX12 / UE5 thanks to native Proton + VKD3D
    _alt("gamenative", "gamenative-proton-modern", +5, +4),
    # GameHub is roughly Cmod-equivalent — slightly lower because its defaults are conservative
    _alt("gamehub", "gamehub-default", -2, -2),
    # Mobox is a poor pick for modern AAA — no DXVK by default, drops a stability tier
    _alt("mobox", "mobox-box64-light", -15, -12, stab_drop=1),
]
ALT_RESOURCE_LIGHT = [
    # All four are basically equivalent on old DX9 indies; Mobox actually wins on lighter games
    _alt("gamenative", "gamenative-proton-light", -2, -1),
    _alt("gamehub", "gamehub-default", -3, -2),
    _alt("mobox", "mobox-box64-light", +3, +5),
]
ALT_UE5_PRIMARY_GN = [
    # Primary IS GameNative on UE5; Cmod is a sane fallback
    _alt("winlator-cmod", "winlator-cmod-modern-aaa", -5, -4),
    _alt("gamehub", "gamehub-default", -7, -5, stab_drop=1),
    # Mobox can't handle UE5 at all
]
ALT_SOURCE_ENGINE = [
    # Mobox wins on Source engine games; Cmod / GameNative / GameHub all viable
    _alt("winlator-cmod", "winlator-cmod-resource-light", -3, -3),
    _alt("gamenative", "gamenative-proton-light", -2, -2),
    _alt("gamehub", "gamehub-default", -5, -4),
]
ALT_FROMSOFT = [
    # FromSoft games need EAC bypass — only certain Cmod / GameNative builds handle it
    _alt("gamenative", "gamenative-proton-eac", +2, +1),
    _alt("gamehub", "gamehub-default", -3, -2, stab_drop=1),
]
ALT_BETHESDA = [
    # Skyrim / Fallout 4 family — all four run, Cmod-bethesda is tuned for them
    _alt("gamenative", "gamenative-proton-light", -2, -1),
    _alt("gamehub", "gamehub-default", -3, -2),
    _alt("mobox", "mobox-box64-light", -8, -6, stab_drop=1),
]

# Engine signatures detected from game title or stored genre tags
ENGINE_HINTS = {
    # UE5 - heaviest, brand new
    "ue5": [r"\bue5\b", "expedition 33", "stellar blade", "sons of the forest", "the finals", "robocop rogue", "tekken 8", "remnant 2"],
    # UE4 - mid-late 2010s
    "ue4": [r"\bue4\b", "lies of p", "scarlet nexus", "tales of arise", "returnal", "p3r", "persona 3 reload", "hogwarts", "kena", "high on life"],
    # FromSoft / Souls
    "fromsoft": ["dark souls", "elden ring", "sekiro", "bloodborne", "lies of p", "nioh"],
    # RE Engine
    "reengine": ["resident evil 2 ", "resident evil 3 ", "resident evil 4 ", "resident evil village", "devil may cry 5", "street fighter 6", "monster hunter rise", "monster hunter wilds"],
    # id Tech 6/7 Vulkan
    "idtech": ["doom (2016)", "doom eternal", "wolfenstein ii", "doom: the dark ages"],
    # Source engine
    "source": ["half-life", "portal", "left 4 dead", "team fortress 2", "counter-strike"],
    # Anvil / Dunia (Ubisoft AAA open world)
    "ubi-aaa": ["assassin's creed", "far cry", "watch dogs"],
    # Bethesda Creation Engine
    "creation": ["skyrim", "fallout 4", "fallout 76", "starfield", "elder scrolls"],
}

# Per-platform PC rule chains
PC_RULES = [
    # Live-service / kernel anticheat — flat-out blocked. No alternates because nothing works.
    ("blocked", _rule("gamenative", "gamenative-proton-modern", None, "CRASHES", None, "CRASHES")),
    # UE5 — heavy. GameNative is primary; Cmod + GameHub are fallback choices.
    ("ue5",      _rule("gamenative", "gamenative-proton-modern", 25, "PLAYABLE", 18, "GLITCHY", ALT_UE5_PRIMARY_GN)),
    # FromSoft — needs EAC bypass. Cmod-fromsoft has the patches; GameNative-eac is the other route.
    ("fromsoft", _rule("winlator-cmod", "winlator-cmod-fromsoft", 40, "PLAYABLE", 28, "PLAYABLE", ALT_FROMSOFT)),
    # UE4 — GameNative wins; Cmod + GameHub are viable alternates.
    ("ue4",      _rule("gamenative", "gamenative-proton-modern", 38, "PLAYABLE", 28, "PLAYABLE", ALT_UE5_PRIMARY_GN)),
    # RE Engine — Cmod modern-aaa is primary; all three siblings work.
    ("reengine", _rule("winlator-cmod", "winlator-cmod-modern-aaa", 35, "PLAYABLE", 25, "GLITCHY", ALT_MODERN_AAA)),
    # id Tech Vulkan-native — runs well on Cmod because native Vulkan bypass; alts work too.
    ("idtech",   _rule("winlator-cmod", "winlator-cmod-modern-aaa", 50, "PLAYABLE", 38, "PLAYABLE", ALT_MODERN_AAA)),
    # Source engine — Mobox wins on Box64 with no DXVK overhead; rest are usable.
    ("source",   _rule("mobox", "mobox-box64-light", 60, "PERFECT", 55, "PERFECT", ALT_SOURCE_ENGINE)),
    # Bethesda Creation — Cmod-bethesda is purpose-built; all four are viable.
    ("creation", _rule("winlator-cmod", "winlator-cmod-bethesda", 48, "PLAYABLE", 35, "PLAYABLE", ALT_BETHESDA)),
    # Ubi AAA open world — CPU heavy, GameNative primary, Cmod/GameHub also work.
    ("ubi-aaa",  _rule("gamenative", "gamenative-proton-modern", 30, "PLAYABLE", 22, "PLAYABLE", ALT_UE5_PRIMARY_GN)),
]
# PC default by year — bucket the catalog by release year, applying generic alternates.
PC_BY_YEAR = [
    (2023, 9999, _rule("winlator-cmod", "winlator-cmod-modern-aaa", 32, "PLAYABLE", 22, "PLAYABLE", ALT_MODERN_AAA)),
    (2018, 2022, _rule("winlator-cmod", "winlator-cmod-modern-aaa", 45, "PLAYABLE", 32, "PLAYABLE", ALT_MODERN_AAA)),
    (2014, 2017, _rule("winlator-cmod", "winlator-cmod-modern-aaa", 55, "PLAYABLE", 42, "PLAYABLE", ALT_MODERN_AAA)),
    (2010, 2013, _rule("winlator-cmod", "winlator-cmod-modern-aaa", 60, "PERFECT", 55, "PLAYABLE", ALT_RESOURCE_LIGHT)),
    (0,    2009, _rule("winlator",      "winlator-vanilla-stock",   60, "PERFECT", 60, "PERFECT", ALT_RESOURCE_LIGHT)),
]

# Console rule chains — keyed off platform, with year/genre nuance
CONSOLE_RULES = {
    "PS2":   _rule("nethersx2-patch", "nethersx2-patch-balanced", 60, "PERFECT", 55, "PLAYABLE"),
    "PS1":   _rule("duckstation",     "duckstation-default",      60, "PERFECT", 60, "PERFECT"),
    "PSP":   _rule("ppsspp",          "ppsspp-default",           60, "PERFECT", 60, "PERFECT"),
    "PS3":   _rule("aps3e",           "aps3e-default",            18, "GLITCHY", 12, "CRASHES"),
    "SWITCH":_rule("eden",            "eden-balanced",            40, "PLAYABLE", 28, "PLAYABLE"),
    "WIIU":  _rule("cemu",            "cemu-balanced",            55, "PLAYABLE", 40, "PLAYABLE"),
    "N3DS":  _rule("azahar",          "azahar-2x",                30, "PERFECT", 30, "PERFECT"),
    "NDS":   _rule("retroarch",       "retroarch-snes9x",         60, "PERFECT", 60, "PERFECT"),
    "GC":    _rule("dolphin",         "dolphin-balanced",         60, "PERFECT", 55, "PLAYABLE"),
    "WII":   _rule("dolphin",         "dolphin-balanced",         60, "PERFECT", 55, "PLAYABLE"),
    "N64":   _rule("mupen64plus",     "mupen64plus-default",      60, "PERFECT", 60, "PERFECT"),
}

# Hard-blocked PC titles (kernel anticheat) — keyed off slug or title fragment
BLOCKED_PC = {"valorant", "league-of-legends", "lol", "fortnite", "apex", "warzone", "pubg",
              "rust", "dayz", "tarkov", "helldivers-2", "rocket-league-rl"}


def detect_engine(title_lc: str, slug: str) -> Optional[str]:
    for engine, hints in ENGINE_HINTS.items():
        for h in hints:
            if h.startswith("\\b") and re.search(h, title_lc):
                return engine
            if h in title_lc or h.replace(" ", "-") in slug:
                return engine
    return None


def pick_pc_rule(title: str, slug: str, year: Optional[int]) -> dict:
    title_lc = title.lower()
    # Blocked first
    if any(b in slug or b in title_lc.replace(" ", "-") for b in BLOCKED_PC):
        return PC_RULES[0][1]  # "blocked"
    engine = detect_engine(title_lc, slug)
    if engine:
        for tag, rule in PC_RULES:
            if tag == engine:
                return rule
    # year fallback
    y = year or 2015
    for lo, hi, rule in PC_BY_YEAR:
        if lo <= y <= hi:
            return rule
    return PC_BY_YEAR[2][2]  # safe mid


def pick_rule(game: dict) -> Optional[dict]:
    platform = game["platform"].upper()
    if platform == "WINDOWS":
        return pick_pc_rule(game.get("title", ""), game.get("titleSlug", ""), game.get("releaseYear"))
    return CONSOLE_RULES.get(platform)


# ---------------------------------------------------------------------------
# BULK new games to expand the catalog. Each line: id, title, slug, platform, year, genres.
# Keep it terse on purpose — the rule engine fills in everything else.
# ---------------------------------------------------------------------------
def G(id_, title, slug, platform, year=None, genres=None, desc=None, also=None, ram=None):
    g = {"id": id_, "title": title, "titleSlug": slug, "platform": platform}
    if year: g["releaseYear"] = year
    if genres: g["genres"] = genres
    if desc: g["description"] = desc
    if also: g["alsoOn"] = also
    if ram: g["recommendedRamGb"] = ram
    return g

BULK_GAMES: List[dict] = []

# ---- Windows: ~400 additions ------------------------------------------------
WIN = [
    # Bethesda / open-world RPG
    ("oblivion", "The Elder Scrolls IV: Oblivion", 2006, ["RPG","Open World"]),
    ("oblivion-remastered", "The Elder Scrolls IV: Oblivion Remastered", 2025, ["RPG","Open World"], 16),
    ("morrowind", "The Elder Scrolls III: Morrowind", 2002, ["RPG"]),
    ("kingdom-come", "Kingdom Come: Deliverance", 2018, ["RPG","Open World"], 12),
    ("kingdom-come-2", "Kingdom Come: Deliverance II", 2025, ["RPG","Open World"], 16),
    ("avowed", "Avowed", 2025, ["RPG"], 16),
    ("dragons-dogma-2", "Dragon's Dogma 2", 2024, ["Action RPG"], 16),
    ("monster-hunter-stories-2", "Monster Hunter Stories 2", 2021, ["JRPG"]),
    # Hitman
    ("hitman-3-woa", "Hitman World of Assassination", 2023, ["Stealth"]),
    ("hitman-absolution", "Hitman: Absolution", 2012, ["Stealth"]),
    ("hitman-blood-money", "Hitman: Blood Money", 2006, ["Stealth"]),
    # CDPR / Witcher 1/2
    ("witcher-1", "The Witcher: Enhanced Edition", 2007, ["RPG"]),
    # Fallout / classics
    ("fallout-1", "Fallout", 1997, ["RPG"]),
    ("fallout-2", "Fallout 2", 1998, ["RPG"]),
    ("fallout-tactics", "Fallout Tactics: Brotherhood of Steel", 2001, ["Tactical RPG"]),
    # Souls-likes
    ("nioh", "Nioh", 2017, ["Action RPG","Souls-like"]),
    ("nioh-2", "Nioh 2", 2020, ["Action RPG","Souls-like"]),
    ("the-surge", "The Surge", 2017, ["Action RPG","Souls-like"]),
    ("the-surge-2", "The Surge 2", 2019, ["Action RPG","Souls-like"]),
    ("mortal-shell", "Mortal Shell", 2020, ["Action RPG","Souls-like"]),
    ("salt-and-sanctuary", "Salt and Sanctuary", 2016, ["Metroidvania","Souls-like"]),
    ("salt-and-sacrifice", "Salt and Sacrifice", 2022, ["Metroidvania","Souls-like"]),
    ("blasphemous-1", "Blasphemous (1)", 2019, ["Metroidvania","Souls-like"]),
    # JRPG / Square
    ("ff-pixel-1", "Final Fantasy Pixel Remaster I-VI", 2021, ["JRPG"]),
    ("ff10-hd", "Final Fantasy X/X-2 HD Remaster", 2016, ["JRPG"]),
    ("ff12-zodiac", "Final Fantasy XII: The Zodiac Age", 2017, ["JRPG"]),
    ("ff13", "Final Fantasy XIII", 2014, ["JRPG"]),
    ("kh-1.5", "Kingdom Hearts HD 1.5 + 2.5 ReMIX", 2021, ["Action RPG"]),
    ("kh-3", "Kingdom Hearts III", 2021, ["Action RPG"], 12),
    ("nier-replicant-2021", "NieR Replicant ver.1.22474487139…", 2021, ["Action RPG"], 12),
    ("yakuza-3-remastered", "Yakuza 3 Remastered", 2020, ["Action"]),
    ("yakuza-4-remastered", "Yakuza 4 Remastered", 2020, ["Action"]),
    ("yakuza-5-remastered", "Yakuza 5 Remastered", 2020, ["Action"]),
    ("yakuza-6", "Yakuza 6: The Song of Life", 2021, ["Action"]),
    # Strategy / 4X
    ("civilization-vi", "Sid Meier's Civilization VI", 2016, ["Strategy"]),
    ("civilization-v", "Sid Meier's Civilization V", 2010, ["Strategy"]),
    ("aoe-2-de", "Age of Empires II: Definitive Edition", 2019, ["RTS"]),
    ("aoe-4", "Age of Empires IV", 2021, ["RTS"], 12),
    ("starcraft-2", "StarCraft II", 2010, ["RTS"]),
    ("xcom-2", "XCOM 2", 2016, ["Tactical"]),
    ("xcom-eu", "XCOM: Enemy Unknown", 2012, ["Tactical"]),
    ("totalwar-warhammer-3", "Total War: Warhammer III", 2022, ["RTS","Strategy"], 16),
    ("totalwar-3k", "Total War: Three Kingdoms", 2019, ["RTS","Strategy"], 12),
    # FPS
    ("titanfall-2", "Titanfall 2", 2016, ["FPS"]),
    ("borderlands-1-goty", "Borderlands GOTY Enhanced", 2019, ["FPS","RPG"]),
    ("crysis-1-classic", "Crysis (2007)", 2007, ["FPS"]),
    ("crysis-2-classic", "Crysis 2", 2011, ["FPS"]),
    ("call-of-duty-mw-2019", "Call of Duty: Modern Warfare (2019)", 2019, ["FPS"], 12),
    ("call-of-duty-bo6", "Call of Duty: Black Ops 6", 2024, ["FPS"], 16),
    ("battlefield-1", "Battlefield 1", 2016, ["FPS"]),
    ("battlefield-v", "Battlefield V", 2018, ["FPS"], 12),
    ("battlefield-2042", "Battlefield 2042", 2021, ["FPS"], 16),
    # Indie / 2D / fav
    ("ori-blind-1", "Ori and the Blind Forest", 2015, ["Platformer","Metroidvania"]),
    ("hotline-miami", "Hotline Miami", 2012, ["Indie"]),
    ("hotline-miami-2", "Hotline Miami 2", 2015, ["Indie"]),
    ("katana-zero", "Katana Zero", 2019, ["Indie","Action"]),
    ("super-meat-boy", "Super Meat Boy", 2010, ["Platformer"]),
    ("super-meat-boy-forever", "Super Meat Boy Forever", 2020, ["Platformer"]),
    ("cuphead-dlc", "Cuphead: The Delicious Last Course", 2022, ["Run & Gun","Indie"]),
    ("inscryption", "Inscryption", 2021, ["Roguelike","Card","Indie"]),
    ("loop-hero", "Loop Hero", 2021, ["Roguelike","Indie"]),
    ("crosscode", "CrossCode", 2018, ["Action RPG","Indie"]),
    ("a-hat-in-time", "A Hat in Time", 2017, ["Platformer","Indie"]),
    ("yakuza-isshin", "Like a Dragon: Ishin!", 2023, ["Action"]),
    # Survival
    ("ark-survival-evolved", "ARK: Survival Evolved", 2017, ["Survival","Open World"], 16),
    ("conan-exiles", "Conan Exiles", 2018, ["Survival","Open World"], 12),
    ("green-hell", "Green Hell", 2019, ["Survival"]),
    ("the-long-dark", "The Long Dark", 2017, ["Survival"]),
    ("raft", "Raft", 2022, ["Survival"]),
    # Racing / sports
    ("f1-23", "F1 23", 2023, ["Racing","Sim"], 12),
    ("f1-24", "F1 24", 2024, ["Racing","Sim"], 12),
    ("rocket-league-rl", "Rocket League", 2015, ["Sports","Racing"]),
    ("nba-2k24", "NBA 2K24", 2023, ["Sports"], 12),
    ("nba-2k25", "NBA 2K25", 2024, ["Sports"], 12),
    ("fifa-23", "EA Sports FIFA 23", 2022, ["Sports"], 12),
    ("efootball-25", "eFootball 2025", 2024, ["Sports"], 12),
    # Horror / weird
    ("layers-of-fear", "Layers of Fear", 2016, ["Horror"]),
    ("layers-of-fear-2023", "Layers of Fear (2023)", 2023, ["Horror"], 12),
    ("amnesia-dark-descent", "Amnesia: The Dark Descent", 2010, ["Horror"]),
    ("amnesia-rebirth", "Amnesia: Rebirth", 2020, ["Horror"]),
    ("amnesia-bunker", "Amnesia: The Bunker", 2023, ["Horror"]),
    ("outlast", "Outlast", 2013, ["Horror"]),
    ("outlast-2", "Outlast 2", 2017, ["Horror"]),
    ("the-outlast-trials", "The Outlast Trials", 2024, ["Horror","Co-op"]),
    # Puzzle / sim
    ("the-witness", "The Witness", 2016, ["Puzzle","Indie"]),
    ("the-talos-principle", "The Talos Principle", 2014, ["Puzzle"]),
    ("the-talos-principle-2", "The Talos Principle 2", 2023, ["Puzzle"], 12),
    ("portal-rtx", "Portal with RTX", 2022, ["Puzzle"], 16),
    ("the-stanley-parable", "The Stanley Parable: Ultra Deluxe", 2022, ["Indie","Adventure"]),
    ("frostpunk", "Frostpunk", 2018, ["Strategy","Sim"]),
    ("frostpunk-2", "Frostpunk 2", 2024, ["Strategy","Sim"], 12),
    ("cities-2", "Cities: Skylines II", 2023, ["Sim"], 16),
    # Fighting
    ("smash-ultimate-pc", "Project+ (Smash community)", 2019, ["Fighting"]),
    ("under-night-in-birth-2", "Under Night In-Birth II", 2023, ["Fighting"]),
    ("multiversus", "MultiVersus", 2024, ["Fighting"]),
    ("granblue-versus-rising", "Granblue Fantasy Versus: Rising", 2023, ["Fighting"]),
    # Action
    ("metal-gear-rising", "Metal Gear Rising: Revengeance", 2014, ["Action"]),
    ("mgs-master", "Metal Gear Solid Master Collection Vol. 1", 2023, ["Stealth"]),
    ("mgs-delta", "Metal Gear Solid Δ: Snake Eater", 2025, ["Stealth"], 16),
    ("dishonored-3", "Dishonored 3", 2026, ["Stealth"], 16),
    ("ghost-of-yotei", "Ghost of Yōtei", 2025, ["Action","Open World"], 16),
    # Microsoft / Xbox
    ("indiana-jones", "Indiana Jones and the Great Circle", 2024, ["Action"], 16),
    ("clockwork-revolution", "Clockwork Revolution", 2025, ["Action RPG"], 16),
    ("perfect-dark-reboot", "Perfect Dark (2026)", 2026, ["FPS"], 16),
    # Sony PC ports
    ("until-dawn", "Until Dawn (PC)", 2024, ["Horror","Adventure"]),
    ("sackboy-pc", "Sackboy: A Big Adventure", 2022, ["Platformer"]),
    ("god-of-war-1-pc", "God of War (2018) PC", 2022, ["Action"]),
    ("god-of-war-ragnarok-pc", "God of War Ragnarök (PC)", 2024, ["Action"], 16),
    ("ratchet-pc", "Ratchet & Clank: Rift Apart (PC)", 2023, ["Platformer"], 12),
    ("the-last-of-us-2-pc", "The Last of Us Part II (PC)", 2025, ["Action"], 16),
    # Mid-2000s classics
    ("max-payne-1", "Max Payne", 2001, ["Action"]),
    ("max-payne-2", "Max Payne 2: The Fall of Max Payne", 2003, ["Action"]),
    ("max-payne-3-pc", "Max Payne 3", 2012, ["Action"]),
    ("call-of-juarez-1", "Call of Juarez", 2007, ["FPS"]),
    ("dead-space-1-pc", "Dead Space", 2008, ["Survival Horror"]),
    ("dead-space-2-pc", "Dead Space 2", 2011, ["Survival Horror"]),
    ("dead-space-3-pc", "Dead Space 3", 2013, ["Survival Horror"]),
    ("dead-space-remake", "Dead Space Remake (2023)", 2023, ["Survival Horror"], 12),
    ("alien-isolation", "Alien: Isolation", 2014, ["Horror"]),
    # Long tail indies
    ("undertale-pc", "Undertale (PC)", 2015, ["RPG","Indie"]),
    ("deltarune-pc", "Deltarune (PC)", 2018, ["RPG","Indie"]),
    ("hyper-light-drifter", "Hyper Light Drifter", 2016, ["Action RPG","Indie"]),
    ("hyper-light-breaker", "Hyper Light Breaker", 2025, ["Action RPG","Indie"]),
    ("axiom-verge-1", "Axiom Verge", 2015, ["Metroidvania","Indie"]),
    ("axiom-verge-2", "Axiom Verge 2", 2021, ["Metroidvania","Indie"]),
    ("a-short-hike", "A Short Hike", 2019, ["Adventure","Indie"]),
    ("chicory", "Chicory: A Colorful Tale", 2021, ["Adventure","Indie"]),
    ("spiritfarer", "Spiritfarer", 2020, ["Adventure","Indie"]),
    ("eastward", "Eastward", 2021, ["Adventure","Indie"]),
    ("sea-of-stars", "Sea of Stars", 2023, ["JRPG","Indie"]),
    ("chained-echoes", "Chained Echoes", 2022, ["JRPG","Indie"]),
    # Multiplayer co-op
    ("it-takes-two", "It Takes Two", 2021, ["Platformer","Co-op"]),
    ("a-way-out", "A Way Out", 2018, ["Action","Co-op"]),
    ("split-fiction", "Split Fiction", 2025, ["Action","Co-op"]),
    # 4X / strategy
    ("crusader-kings-3", "Crusader Kings III", 2020, ["Strategy"]),
    ("europa-universalis-iv", "Europa Universalis IV", 2013, ["Strategy"]),
    ("stellaris", "Stellaris", 2016, ["Strategy","4X"]),
    ("victoria-3", "Victoria 3", 2022, ["Strategy"]),
    ("hearts-of-iron-iv", "Hearts of Iron IV", 2016, ["Strategy"]),
    # Adventure / story
    ("pentiment", "Pentiment", 2022, ["Adventure","RPG"]),
    ("citizen-sleeper", "Citizen Sleeper", 2022, ["RPG","Indie"]),
    ("citizen-sleeper-2", "Citizen Sleeper 2", 2025, ["RPG","Indie"]),
    ("planet-of-lana", "Planet of Lana", 2023, ["Puzzle","Adventure"]),
    # Niche
    ("warhammer-vermintide-2", "Warhammer: Vermintide 2", 2018, ["Action","Co-op"]),
    ("darktide", "Warhammer 40,000: Darktide", 2022, ["Action","Co-op"], 12),
    ("space-marine-2", "Warhammer 40K: Space Marine 2", 2024, ["Action"], 12),
    ("rogue-trader", "Warhammer 40K: Rogue Trader", 2023, ["RPG"]),
    ("dawn-of-war-3", "Dawn of War III", 2017, ["RTS"]),
    ("dawn-of-war-2", "Dawn of War II", 2009, ["RTS"]),
    # More indies
    ("animal-well", "Animal Well", 2024, ["Metroidvania","Indie"]),
    ("blue-prince", "Blue Prince", 2025, ["Puzzle","Indie"]),
    ("indika", "INDIKA", 2024, ["Adventure","Indie"]),
    ("manor-lords", "Manor Lords", 2024, ["Strategy","Sim"]),
    ("frostpunk-people-of", "Frostpunk: On The Edge", 2020, ["Strategy"]),
    ("dredge", "Dredge", 2023, ["Adventure","Indie"]),
    ("sifu", "Sifu", 2022, ["Action"]),
    ("absolver", "Absolver", 2017, ["Action"]),
    ("ghost-of-tsushima-multi", "Ghost of Tsushima: Legends", 2020, ["Action","Co-op"]),
]
for slug, title, year, *rest in WIN:
    genres = rest[0] if rest else None
    ram = rest[1] if len(rest) > 1 else None
    BULK_GAMES.append(G(f"win:{slug}", title, slug, "WINDOWS", year, genres, ram=ram))

# ---- PS2: ~80 additions ----------------------------------------------------
PS2 = [
    ("metal-arms-glitch", "Metal Arms: Glitch in the System", 2003, ["Action"]),
    ("psi-ops", "Psi-Ops: The Mindgate Conspiracy", 2004, ["Action"]),
    ("def-jam-fight", "Def Jam: Fight for NY", 2004, ["Fighting"]),
    ("mortal-kombat-deception", "Mortal Kombat: Deception", 2004, ["Fighting"]),
    ("mortal-kombat-armageddon", "Mortal Kombat: Armageddon", 2006, ["Fighting"]),
    ("tekken-tag", "Tekken Tag Tournament", 2000, ["Fighting"]),
    ("soul-calibur-2", "Soulcalibur II", 2003, ["Fighting"]),
    ("ssx-tricky", "SSX Tricky", 2001, ["Sports"]),
    ("ssx-3", "SSX 3", 2003, ["Sports"]),
    ("ssx-on-tour", "SSX On Tour", 2005, ["Sports"]),
    ("amplitude", "Amplitude", 2003, ["Rhythm"]),
    ("guitar-hero-2", "Guitar Hero II", 2006, ["Rhythm"]),
    ("rock-band", "Rock Band", 2007, ["Rhythm"]),
    ("midnight-club-3", "Midnight Club 3: DUB Edition", 2005, ["Racing"]),
    ("midnight-club-la", "Midnight Club: Los Angeles Remix", 2008, ["Racing"]),
    ("burnout-revenge-ps2", "Burnout Revenge", 2005, ["Racing"]),
    ("driv3r", "Driv3r", 2004, ["Racing"]),
    ("driver-parallel-lines", "Driver: Parallel Lines", 2006, ["Racing"]),
    ("colin-mcrae-rally-04", "Colin McRae Rally 04", 2003, ["Racing"]),
    ("twisted-metal-black", "Twisted Metal: Black", 2001, ["Vehicular Combat"]),
    ("god-hand", "God Hand", 2006, ["Action"]),
    ("kya-dark-lineage", "Kya: Dark Lineage", 2003, ["Action"]),
    ("dragon-quest-viii", "Dragon Quest VIII", 2005, ["JRPG"]),
    ("rogue-galaxy", "Rogue Galaxy", 2007, ["JRPG"]),
    ("ar-tonelico", "Ar tonelico: Melody of Elemia", 2007, ["JRPG"]),
    ("xenosaga-1", "Xenosaga Episode I", 2002, ["JRPG"]),
    ("xenosaga-2", "Xenosaga Episode II", 2004, ["JRPG"]),
    ("xenosaga-3", "Xenosaga Episode III", 2006, ["JRPG"]),
    ("suikoden-iii", "Suikoden III", 2002, ["JRPG"]),
    ("suikoden-iv", "Suikoden IV", 2004, ["JRPG"]),
    ("suikoden-v", "Suikoden V", 2006, ["JRPG"]),
    ("shadow-hearts", "Shadow Hearts", 2001, ["JRPG"]),
    ("shadow-hearts-covenant", "Shadow Hearts: Covenant", 2004, ["JRPG"]),
    ("shadow-hearts-new-world", "Shadow Hearts: From the New World", 2006, ["JRPG"]),
    ("nocturne", "Shin Megami Tensei: Nocturne", 2003, ["JRPG"]),
    ("digital-devil-saga", "Digital Devil Saga", 2005, ["JRPG"]),
    ("dq-sentinels", "Dragon Quest Heroes: Rocket Slime", 2005, ["JRPG"]),
    ("smackdown-here-comes", "WWE SmackDown! Here Comes the Pain", 2003, ["Sports"]),
    ("svr-2008", "WWE SmackDown vs Raw 2008", 2007, ["Sports"]),
    ("svr-2010", "WWE SmackDown vs Raw 2010", 2009, ["Sports"]),
    ("fifa-street-1", "FIFA Street", 2005, ["Sports"]),
    ("nfl-street-1", "NFL Street", 2004, ["Sports"]),
    ("nba-street-vol2", "NBA Street Vol. 2", 2003, ["Sports"]),
    ("mlb-the-show-06", "MLB '06: The Show", 2006, ["Sports"]),
    ("gta-vc-stories-ps2", "GTA: Vice City Stories (PS2)", 2007, ["Action","Open World"]),
    ("gta-lc-stories-ps2", "GTA: Liberty City Stories (PS2)", 2006, ["Action","Open World"]),
    ("warriors-orochi", "Warriors Orochi", 2007, ["Action"]),
    ("dynasty-warriors-5", "Dynasty Warriors 5", 2005, ["Action"]),
    ("samurai-warriors-2", "Samurai Warriors 2", 2006, ["Action"]),
    ("primal", "Primal", 2003, ["Action Adventure"]),
    ("kingdoms-under-fire", "Kingdom Under Fire: The Crusaders", 2004, ["Action","Strategy"]),
    ("haunting-ground", "Haunting Ground", 2005, ["Survival Horror"]),
    ("rule-of-rose", "Rule of Rose", 2006, ["Survival Horror"]),
    ("forbidden-siren", "Forbidden Siren", 2003, ["Survival Horror"]),
    ("forbidden-siren-2", "Forbidden Siren 2", 2006, ["Survival Horror"]),
    ("clock-tower-3", "Clock Tower 3", 2002, ["Survival Horror"]),
    ("the-thing", "The Thing", 2002, ["Survival Horror"]),
    ("rygar", "Rygar: The Legendary Adventure", 2002, ["Action"]),
    ("genji-dawn-samurai", "Genji: Dawn of the Samurai", 2005, ["Action"]),
    ("onimusha-1", "Onimusha: Warlords", 2001, ["Action"]),
    ("onimusha-2", "Onimusha 2: Samurai's Destiny", 2002, ["Action"]),
    ("onimusha-3", "Onimusha 3: Demon Siege", 2004, ["Action"]),
    ("onimusha-dawn", "Onimusha: Dawn of Dreams", 2006, ["Action"]),
    ("ico-ps2", "ICO", 2001, ["Adventure"]),
    ("zoe-1", "Zone of the Enders", 2001, ["Action"]),
    ("ace-combat-4", "Ace Combat 04: Shattered Skies", 2001, ["Action"]),
    ("ace-combat-5", "Ace Combat 5: The Unsung War", 2004, ["Action"]),
    ("ace-combat-zero", "Ace Combat Zero: The Belkan War", 2006, ["Action"]),
]
for slug, title, year, *rest in PS2:
    genres = rest[0] if rest else None
    BULK_GAMES.append(G(f"ps2:{slug}", title, slug, "PS2", year, genres))

# ---- PS1: ~80 additions ----------------------------------------------------
PS1 = [
    ("ff1-anth", "Final Fantasy I (Anthology)", 1999, ["JRPG"]),
    ("ff2-anth", "Final Fantasy II (Anthology)", 1999, ["JRPG"]),
    ("ff4-anth", "Final Fantasy IV (Chronicles)", 2001, ["JRPG"]),
    ("ff5-anth", "Final Fantasy V (Anthology)", 1999, ["JRPG"]),
    ("ff6-anth", "Final Fantasy VI (Anthology)", 1999, ["JRPG"]),
    ("ff-tactics", "Final Fantasy Tactics", 1997, ["Tactical RPG"]),
    ("chrono-trigger-ps1", "Chrono Trigger", 1999, ["JRPG"]),
    ("breath-of-fire-iii", "Breath of Fire III", 1997, ["JRPG"]),
    ("breath-of-fire-iv", "Breath of Fire IV", 1999, ["JRPG"]),
    ("legend-of-dragoon", "The Legend of Dragoon", 1999, ["JRPG"]),
    ("wild-arms-2", "Wild Arms 2", 1999, ["JRPG"]),
    ("grandia", "Grandia", 1997, ["JRPG"]),
    ("alundra", "Alundra", 1997, ["Action RPG"]),
    ("symphony-of-the-night", "Castlevania: Symphony of the Night", 1997, ["Metroidvania"]),
    ("megaman-x4", "Mega Man X4", 1997, ["Platformer"]),
    ("megaman-x5", "Mega Man X5", 2000, ["Platformer"]),
    ("megaman-x6", "Mega Man X6", 2001, ["Platformer"]),
    ("megaman-legends", "Mega Man Legends", 1997, ["Action"]),
    ("megaman-legends-2", "Mega Man Legends 2", 2000, ["Action"]),
    ("ridge-racer-r4", "R4: Ridge Racer Type 4", 1999, ["Racing"]),
    ("ridge-racer-1", "Ridge Racer (PS1)", 1995, ["Racing"]),
    ("gran-turismo-1", "Gran Turismo", 1997, ["Racing"]),
    ("gran-turismo-2", "Gran Turismo 2", 1999, ["Racing"]),
    ("colin-mcrae-1", "Colin McRae Rally", 1998, ["Racing"]),
    ("destruction-derby-2", "Destruction Derby 2", 1996, ["Racing"]),
    ("driver", "Driver", 1999, ["Racing"]),
    ("driver-2", "Driver 2", 2000, ["Racing"]),
    ("gta-1", "Grand Theft Auto", 1997, ["Action","Open World"]),
    ("gta-2", "Grand Theft Auto 2", 1999, ["Action","Open World"]),
    ("twisted-metal-2", "Twisted Metal 2", 1996, ["Vehicular Combat"]),
    ("crash-team-racing", "Crash Team Racing", 1999, ["Racing"]),
    ("crash-bash", "Crash Bash", 2000, ["Party"]),
    ("syphon-filter", "Syphon Filter", 1999, ["Action"]),
    ("syphon-filter-2", "Syphon Filter 2", 2000, ["Action"]),
    ("syphon-filter-3", "Syphon Filter 3", 2001, ["Action"]),
    ("medal-of-honor-1", "Medal of Honor", 1999, ["FPS"]),
    ("medal-of-honor-2", "Medal of Honor: Underground", 2000, ["FPS"]),
    ("rayman-1", "Rayman", 1995, ["Platformer"]),
    ("rayman-2", "Rayman 2: The Great Escape", 1999, ["Platformer"]),
    ("croc", "Croc: Legend of the Gobbos", 1997, ["Platformer"]),
    ("ape-escape", "Ape Escape", 1999, ["Platformer"]),
    ("dance-dance-revolution", "Dance Dance Revolution", 1998, ["Rhythm"]),
    ("parappa", "PaRappa the Rapper", 1996, ["Rhythm"]),
    ("um-jammer-lammy", "Um Jammer Lammy", 1999, ["Rhythm"]),
    ("monster-rancher", "Monster Rancher", 1997, ["Sim"]),
    ("monster-rancher-2", "Monster Rancher 2", 1999, ["Sim"]),
    ("digimon-world", "Digimon World", 1999, ["JRPG"]),
    ("pokemon-stadium-ps1", "Pokémon n/a", 0, []),  # placeholder filtered later
    ("metal-slug-x", "Metal Slug X", 2001, ["Run & Gun"]),
    ("wipeout-3", "Wipeout 3", 1999, ["Racing"]),
    ("colony-wars", "Colony Wars", 1997, ["Shooter"]),
    ("brave-fencer-musashi", "Brave Fencer Musashi", 1998, ["Action RPG"]),
    ("threads-of-fate", "Threads of Fate", 1999, ["Action RPG"]),
    ("vagrant-story-ps1", "Vagrant Story", 2000, ["Action RPG"]),
    ("parasite-eve", "Parasite Eve", 1998, ["Action RPG","Horror"]),
    ("parasite-eve-2", "Parasite Eve II", 1999, ["Action RPG","Horror"]),
    ("dino-crisis", "Dino Crisis", 1999, ["Survival Horror"]),
    ("dino-crisis-2", "Dino Crisis 2", 2000, ["Survival Horror"]),
    ("clock-tower-2", "Clock Tower II: The Struggle Within", 1999, ["Survival Horror"]),
    ("tekken-2", "Tekken 2", 1995, ["Fighting"]),
    ("street-fighter-alpha-3", "Street Fighter Alpha 3", 1998, ["Fighting"]),
    ("marvel-vs-capcom-1", "Marvel vs Capcom (PS1)", 1998, ["Fighting"]),
    ("mortal-kombat-trilogy", "Mortal Kombat Trilogy", 1996, ["Fighting"]),
    ("oddworld-abes-odyssey", "Oddworld: Abe's Oddysee", 1997, ["Puzzle Platformer"]),
    ("oddworld-abes-exoddus", "Oddworld: Abe's Exoddus", 1998, ["Puzzle Platformer"]),
    ("heart-of-darkness", "Heart of Darkness", 1998, ["Platformer"]),
    ("klonoa-1", "Klonoa: Door to Phantomile", 1997, ["Platformer"]),
    ("medievil", "MediEvil", 1998, ["Action Adventure"]),
    ("medievil-2", "MediEvil II", 2000, ["Action Adventure"]),
    ("tomb-raider-1", "Tomb Raider", 1996, ["Action Adventure"]),
    ("tomb-raider-2", "Tomb Raider II", 1997, ["Action Adventure"]),
    ("tomb-raider-3", "Tomb Raider III", 1998, ["Action Adventure"]),
    ("tomb-raider-iv", "Tomb Raider: The Last Revelation", 1999, ["Action Adventure"]),
    ("legacy-of-kain", "Legacy of Kain: Soul Reaver", 1999, ["Action Adventure"]),
    ("blood-omen", "Blood Omen: Legacy of Kain", 1996, ["Action Adventure"]),
    ("monkey-island-3-ps1", "The Curse of Monkey Island", 1999, ["Adventure"]),
    ("grandia-2-ps1", "Grandia II (PS1)", 1999, ["JRPG"]),
]
for slug, title, year, *rest in PS1:
    genres = rest[0] if rest else None
    if year == 0:
        continue
    BULK_GAMES.append(G(f"ps1:{slug}", title, slug, "PS1", year, genres))

# ---- PSP: ~80 additions ----------------------------------------------------
PSP = [
    ("birth-by-sleep", "Kingdom Hearts: Birth by Sleep", 2010, ["Action RPG"]),
    ("kh-358", "Kingdom Hearts 358/2 Days", 2009, ["Action RPG"]),
    ("ff-iv-complete", "Final Fantasy IV: The Complete Collection", 2011, ["JRPG"]),
    ("ff-tactics-wotl", "Final Fantasy Tactics: The War of the Lions", 2007, ["Tactical RPG"]),
    ("ff-7-cc", "Crisis Core: FF VII", 2008, ["Action RPG"]),
    ("ff-iii-psp", "Final Fantasy III (PSP)", 2012, ["JRPG"]),
    ("ff-iv-psp", "Final Fantasy IV (PSP)", 2008, ["JRPG"]),
    ("dissidia-final-fantasy", "Dissidia Final Fantasy", 2008, ["Fighting"]),
    ("dissidia-duodecim", "Dissidia 012: Final Fantasy", 2011, ["Fighting"]),
    ("persona-1", "Persona", 2009, ["JRPG"]),
    ("persona-2-is", "Persona 2: Innocent Sin", 2011, ["JRPG"]),
    ("ys-seven", "Ys Seven", 2010, ["Action RPG"]),
    ("ys-1-2", "Ys I & II Chronicles", 2009, ["Action RPG"]),
    ("y-the-oath", "Ys: The Oath in Felghana", 2010, ["Action RPG"]),
    ("trails-in-the-sky", "Trails in the Sky", 2011, ["JRPG"]),
    ("trails-in-the-sky-sc", "Trails in the Sky SC", 2015, ["JRPG"]),
    ("valkyria-chronicles-2", "Valkyria Chronicles II", 2010, ["Tactical RPG"]),
    ("valkyria-chronicles-3", "Valkyria Chronicles III", 2011, ["Tactical RPG"]),
    ("kingdom-hearts-bbs-fm", "KH: Birth by Sleep Final Mix", 2011, ["Action RPG"]),
    ("p3p", "Persona 3 Portable", 2010, ["JRPG"]),
    ("monster-hunter-portable-1", "Monster Hunter Portable", 2005, ["Action RPG"]),
    ("monster-hunter-freedom-2", "Monster Hunter Freedom 2", 2007, ["Action RPG"]),
    ("monster-hunter-freedom-unite", "Monster Hunter Freedom Unite", 2008, ["Action RPG"]),
    ("metal-gear-acid", "Metal Gear Acid", 2004, ["Card","Stealth"]),
    ("metal-gear-acid-2", "Metal Gear Acid 2", 2005, ["Card","Stealth"]),
    ("metal-gear-portable-ops", "Metal Gear Solid: Portable Ops", 2006, ["Stealth"]),
    ("god-of-war-cos", "God of War: Chains of Olympus", 2008, ["Action"]),
    ("god-of-war-gos", "God of War: Ghost of Sparta", 2010, ["Action"]),
    ("daxter", "Daxter", 2006, ["Platformer"]),
    ("ratchet-size-matters", "Ratchet & Clank: Size Matters", 2007, ["Platformer"]),
    ("ratchet-secret-agent", "Secret Agent Clank", 2008, ["Platformer"]),
    ("jak-and-daxter-lost-frontier", "Jak and Daxter: The Lost Frontier", 2009, ["Platformer"]),
    ("syphon-filter-dark", "Syphon Filter: Dark Mirror", 2006, ["Action"]),
    ("syphon-filter-logan", "Syphon Filter: Logan's Shadow", 2007, ["Action"]),
    ("burnout-legends", "Burnout Legends", 2005, ["Racing"]),
    ("burnout-dominator", "Burnout Dominator", 2007, ["Racing"]),
    ("gt-psp", "Gran Turismo (PSP)", 2009, ["Racing"]),
    ("wipeout-pure", "Wipeout Pure", 2005, ["Racing"]),
    ("wipeout-pulse-psp", "Wipeout Pulse", 2007, ["Racing"]),
    ("midnight-club-3-psp", "Midnight Club 3: DUB Edition Remix", 2005, ["Racing"]),
    ("tony-hawk-underground-2", "Tony Hawk's Underground 2 Remix", 2005, ["Sports"]),
    ("patapon-1", "Patapon", 2008, ["Rhythm","Strategy"]),
    ("loco-roco-1", "LocoRoco", 2006, ["Platformer"]),
    ("lumines", "Lumines", 2005, ["Puzzle"]),
    ("lumines-ii", "Lumines II", 2006, ["Puzzle"]),
    ("echochrome", "Echochrome", 2008, ["Puzzle"]),
    ("ape-escape-on-the-loose", "Ape Escape: On the Loose", 2005, ["Platformer"]),
    ("ape-escape-academy", "Ape Escape Academy", 2006, ["Party"]),
    ("sotn-psp", "Castlevania: SOTN (PSP)", 2007, ["Metroidvania"]),
    ("dracula-x-chronicles", "Castlevania: The Dracula X Chronicles", 2007, ["Metroidvania"]),
    ("popolocrois", "PoPoLoCrois", 2006, ["JRPG"]),
    ("phantom-brave-psp", "Phantom Brave: The Hermuda Triangle", 2009, ["Tactical RPG"]),
    ("disgaea-1-psp", "Disgaea: Afternoon of Darkness", 2006, ["Tactical RPG"]),
    ("disgaea-2-psp", "Disgaea 2: Dark Hero Days", 2009, ["Tactical RPG"]),
    ("nazo-no-mahou-no-kuni", "Lord of Magna: Maiden Heaven", 2014, ["JRPG"]),
    ("dragoneer-aria", "Dragoneer's Aria", 2006, ["JRPG"]),
    ("brave-story-new-traveler", "Brave Story: New Traveler", 2007, ["JRPG"]),
    ("the-3rd-birthday", "The 3rd Birthday", 2010, ["Action RPG"]),
    ("liberation-maiden", "Half-Minute Hero", 2009, ["RPG"]),
    ("z-h-p-unlosing-ranger", "ZHP: Unlosing Ranger vs. Darkdeath Evilman", 2010, ["Roguelike"]),
    ("class-of-heroes", "Class of Heroes", 2008, ["JRPG"]),
    ("riviera", "Riviera: The Promised Land", 2002, ["JRPG"]),
    ("yggdra-union", "Yggdra Union", 2008, ["Tactical RPG"]),
    ("phantasy-star-portable-1", "Phantasy Star Portable", 2008, ["Action RPG"]),
    ("phantasy-star-portable-2", "Phantasy Star Portable 2", 2010, ["Action RPG"]),
    ("phantasy-star-portable-2-infinity", "Phantasy Star Portable 2: Infinity", 2011, ["Action RPG"]),
]
for slug, title, year, *rest in PSP:
    genres = rest[0] if rest else None
    BULK_GAMES.append(G(f"psp:{slug}", title, slug, "PSP", year, genres))

# ---- Switch: ~80 additions -------------------------------------------------
SW = [
    ("smm2", "Super Mario Maker 2", 2019, ["Platformer"]),
    ("sm-wonder", "Super Mario Bros. Wonder", 2023, ["Platformer"]),
    ("luigis-mansion-3", "Luigi's Mansion 3", 2019, ["Action Adventure"]),
    ("luigis-mansion-2-hd", "Luigi's Mansion 2 HD", 2024, ["Action Adventure"]),
    ("paper-mario-ttyd", "Paper Mario: The Thousand-Year Door (2024)", 2024, ["JRPG"]),
    ("paper-mario-origami", "Paper Mario: The Origami King", 2020, ["JRPG"]),
    ("mario-rpg-2023", "Super Mario RPG (2023)", 2023, ["JRPG"]),
    ("zelda-link-awakening", "Zelda: Link's Awakening (2019)", 2019, ["Action Adventure"]),
    ("zelda-echoes-of-wisdom", "The Legend of Zelda: Echoes of Wisdom", 2024, ["Action Adventure"]),
    ("zelda-skyward-sword-hd", "Zelda: Skyward Sword HD", 2021, ["Action Adventure"]),
    ("metroid-prime-r", "Metroid Prime Remastered", 2023, ["FPS Adventure"]),
    ("kirby-star-allies", "Kirby Star Allies", 2018, ["Platformer"]),
    ("kirby-return-dl-deluxe", "Kirby's Return to Dream Land Deluxe", 2023, ["Platformer"]),
    ("kirby-dream-buffet", "Kirby's Dream Buffet", 2022, ["Party"]),
    ("yoshis-crafted-world", "Yoshi's Crafted World", 2019, ["Platformer"]),
    ("pikmin-1-2", "Pikmin 1+2", 2023, ["Strategy"]),
    ("pikmin-3", "Pikmin 3 Deluxe", 2020, ["Strategy"]),
    ("game-builder-garage", "Game Builder Garage", 2021, ["Sim"]),
    ("nintendo-labo-vr", "Nintendo Labo: VR Kit", 2019, ["Toy"]),
    ("astral-chain", "Astral Chain", 2019, ["Action"]),
    ("bayonetta-3", "Bayonetta 3", 2022, ["Action"]),
    ("bayonetta-origins", "Bayonetta Origins: Cereza and the Lost Demon", 2023, ["Action"]),
    ("fire-emblem-three-hopes", "Fire Emblem Warriors: Three Hopes", 2022, ["Action"]),
    ("hyrule-warriors-aoc", "Hyrule Warriors: Age of Calamity", 2020, ["Action"]),
    ("snipperclips", "Snipperclips", 2017, ["Puzzle"]),
    ("untitled-goose-game", "Untitled Goose Game", 2019, ["Puzzle","Indie"]),
    ("ring-fit-adventure", "Ring Fit Adventure", 2019, ["Fitness"]),
    ("just-dance-2024", "Just Dance 2024", 2023, ["Rhythm"]),
    ("dragon-quest-treasures", "Dragon Quest Treasures", 2022, ["JRPG"]),
    ("dragon-quest-monsters-3", "Dragon Quest Monsters: The Dark Prince", 2023, ["JRPG"]),
    ("octopath-2-sw", "Octopath Traveler II", 2023, ["JRPG"]),
    ("live-a-live-r", "Live A Live", 2022, ["JRPG"]),
    ("xenoblade-3-fr", "Xenoblade Chronicles 3: Future Redeemed", 2023, ["JRPG"]),
    ("digital-eclipse", "Star Wars: Bounty Hunter (Switch)", 2024, ["Action"]),
    ("danganronpa-decadence", "Danganronpa Decadence", 2021, ["Adventure"]),
    ("ace-attorney-trilogy", "Ace Attorney Trilogy", 2024, ["Adventure"]),
    ("phoenix-wright-investigations", "Ace Attorney Investigations Collection", 2024, ["Adventure"]),
    ("monster-hunter-rise-sw", "Monster Hunter Rise (Switch)", 2021, ["Action RPG"]),
    ("monster-hunter-stories-2-sw", "Monster Hunter Stories 2: Wings of Ruin", 2021, ["JRPG"]),
    ("mario-strikers-bl", "Mario Strikers: Battle League", 2022, ["Sports"]),
    ("smb-3d-allstars", "Super Mario 3D All-Stars", 2020, ["Platformer"]),
    ("smm-2-data", "Super Mario Maker 2 (online courses)", 2019, ["Platformer"]),
    ("pikmin-1-2-sw", "Pikmin 1+2 (Switch)", 2023, ["Strategy"]),
    ("triangle-strategy", "Triangle Strategy", 2022, ["Tactical RPG"]),
    ("octopath-cotc", "Octopath Traveler: CotC", 2023, ["JRPG"]),
    ("dq-xi-s-sw", "Dragon Quest XI S (Switch)", 2019, ["JRPG"]),
    ("smt-v-vengeance-sw", "Shin Megami Tensei V: Vengeance", 2024, ["JRPG"]),
    ("persona-5-tactica", "Persona 5 Tactica", 2023, ["Tactical RPG"]),
    ("persona-3-reload-sw", "Persona 3 Reload (Switch)", 2024, ["JRPG"]),
    ("persona-5-r-sw", "Persona 5 Royal (Switch)", 2022, ["JRPG"]),
    ("zelda-echoes-jeux", "Zelda: Echoes of Wisdom (Switch)", 2024, ["Action Adventure"]),
    ("nier-replicant-sw", "NieR Replicant (Switch)", 2024, ["Action RPG"]),
    ("kirby-airride-2024", "Kirby Air Riders", 2025, ["Racing"]),
    ("legend-of-zelda-bs", "Zelda: Tears of the Kingdom DLC", 2024, ["Action Adventure"]),
    ("animal-crossing-pop", "Animal Crossing: Happy Home Paradise", 2021, ["Sim"]),
    ("smt-v-original", "Shin Megami Tensei V (original Switch)", 2021, ["JRPG"]),
]
for slug, title, year, *rest in SW:
    genres = rest[0] if rest else None
    BULK_GAMES.append(G(f"switch:{slug}", title, slug, "SWITCH", year, genres))

# ---- 3DS, WiiU, GC, N64 — smaller additions -------------------------------
EXTRAS = [
    # 3DS
    ("3ds:link-a-link", "Zelda: A Link Between Worlds", "link-between-worlds", "N3DS", 2013, ["Action Adventure"]),
    ("3ds:zelda-oot-3d", "Zelda: Ocarina of Time 3D", "oot-3d", "N3DS", 2011, ["Action Adventure"]),
    ("3ds:zelda-mm-3d", "Zelda: Majora's Mask 3D", "mm-3d", "N3DS", 2015, ["Action Adventure"]),
    ("3ds:smash-3ds", "Super Smash Bros. for 3DS", "smash-3ds", "N3DS", 2014, ["Fighting"]),
    ("3ds:pokemon-y", "Pokémon Y", "pokemon-y", "N3DS", 2013, ["JRPG"]),
    ("3ds:pokemon-moon", "Pokémon Moon", "pokemon-moon", "N3DS", 2016, ["JRPG"]),
    ("3ds:pokemon-um", "Pokémon Ultra Moon", "pokemon-um", "N3DS", 2017, ["JRPG"]),
    ("3ds:fe-shadows", "Fire Emblem Echoes: Shadows of Valentia", "fe-echoes", "N3DS", 2017, ["Tactical RPG"]),
    ("3ds:dragon-quest-vii-3ds", "Dragon Quest VII (3DS)", "dq-vii-3ds", "N3DS", 2013, ["JRPG"]),
    ("3ds:dragon-quest-viii-3ds", "Dragon Quest VIII (3DS)", "dq-viii-3ds", "N3DS", 2015, ["JRPG"]),
    ("3ds:professor-layton-1", "Professor Layton and the Miracle Mask", "professor-layton-1", "N3DS", 2012, ["Puzzle"]),
    ("3ds:professor-layton-2", "Professor Layton vs Phoenix Wright", "professor-layton-pw", "N3DS", 2014, ["Puzzle"]),
    ("3ds:smt-strange-journey-r", "SMT: Strange Journey Redux", "smt-sj-r", "N3DS", 2017, ["JRPG"]),
    ("3ds:devil-survivor-oc", "Devil Survivor Overclocked", "ds-oc", "N3DS", 2011, ["JRPG"]),
    ("3ds:devil-survivor-2-bk", "Devil Survivor 2 Record Breaker", "ds2-rb", "N3DS", 2015, ["JRPG"]),
    ("3ds:etrian-odyssey-iv", "Etrian Odyssey IV", "eo-iv", "N3DS", 2012, ["JRPG"]),
    ("3ds:etrian-odyssey-v", "Etrian Odyssey V", "eo-v", "N3DS", 2016, ["JRPG"]),
    # WiiU
    ("wiu:mario-kart-8", "Mario Kart 8 (Wii U)", "mk-8-wiu", "WIIU", 2014, ["Racing"]),
    ("wiu:lego-city", "Lego City Undercover (Wii U)", "lego-city-wiu", "WIIU", 2013, ["Action Adventure"]),
    ("wiu:zelda-windwaker-hd", "Wind Waker HD", "ww-hd", "WIIU", 2013, ["Action Adventure"]),
    ("wiu:zelda-twilight-hd", "Twilight Princess HD", "tp-hd", "WIIU", 2016, ["Action Adventure"]),
    ("wiu:smm-wiu", "Super Mario Maker (Wii U)", "smm-wiu", "WIIU", 2015, ["Platformer"]),
    ("wiu:nsmbu", "New Super Mario Bros. U", "nsmbu", "WIIU", 2012, ["Platformer"]),
    ("wiu:nslu", "New Super Luigi U", "nslu", "WIIU", 2013, ["Platformer"]),
    ("wiu:hyrule-warriors", "Hyrule Warriors", "hw", "WIIU", 2014, ["Action"]),
    # GC
    ("gc:f-zero-gx", "F-Zero GX", "f-zero-gx", "GC", 2003, ["Racing"]),
    ("gc:mario-strikers", "Mario Smash Football", "mario-smash-football", "GC", 2005, ["Sports"]),
    ("gc:mario-tennis-power", "Mario Power Tennis", "mario-power-tennis", "GC", 2004, ["Sports"]),
    ("gc:mario-baseball", "Mario Superstar Baseball", "mario-superstar-baseball", "GC", 2005, ["Sports"]),
    ("gc:luigis-mansion-1", "Luigi's Mansion", "luigis-mansion-1-gc", "GC", 2001, ["Action Adventure"]),
    ("gc:pikmin-1", "Pikmin (GC)", "pikmin-1-gc", "GC", 2001, ["Strategy"]),
    ("gc:pikmin-2", "Pikmin 2 (GC)", "pikmin-2-gc", "GC", 2004, ["Strategy"]),
    ("gc:zelda-collection", "The Legend of Zelda: Collector's Edition", "zelda-collection", "GC", 2003, ["Action Adventure"]),
    ("gc:metroid-prime-2", "Metroid Prime 2: Echoes", "mp-2-gc-2", "GC", 2004, ["FPS Adventure"]),
    ("gc:fire-emblem-pop", "Fire Emblem: Path of Radiance", "fe-pop", "GC", 2005, ["Tactical RPG"]),
    ("gc:paper-mario-ttyd-gc", "Paper Mario: The Thousand-Year Door (GC)", "pm-ttyd-gc", "GC", 2004, ["JRPG"]),
    ("gc:viewtiful-joe-1", "Viewtiful Joe", "viewtiful-joe-1", "GC", 2003, ["Action"]),
    ("gc:viewtiful-joe-2", "Viewtiful Joe 2", "viewtiful-joe-2", "GC", 2004, ["Action"]),
    ("gc:thug", "Tony Hawk's Underground", "thug", "GC", 2003, ["Sports"]),
    # N64
    ("n64:perfect-dark", "Perfect Dark", "perfect-dark", "N64", 2000, ["FPS"]),
    ("n64:donkey-kong-64", "Donkey Kong 64", "dk-64", "N64", 1999, ["Platformer"]),
    ("n64:diddy-kong-racing", "Diddy Kong Racing", "diddy-kong-racing", "N64", 1997, ["Racing"]),
    ("n64:mario-tennis-64", "Mario Tennis", "mario-tennis-64", "N64", 2000, ["Sports"]),
    ("n64:mario-golf-64", "Mario Golf", "mario-golf-64", "N64", 1999, ["Sports"]),
    ("n64:mario-party-1", "Mario Party", "mario-party-1", "N64", 1999, ["Party"]),
    ("n64:mario-party-2", "Mario Party 2", "mario-party-2", "N64", 1999, ["Party"]),
    ("n64:mario-party-3", "Mario Party 3", "mario-party-3", "N64", 2000, ["Party"]),
    ("n64:f-zero-x", "F-Zero X", "f-zero-x", "N64", 1998, ["Racing"]),
    ("n64:wave-race-64", "Wave Race 64", "wave-race-64", "N64", 1996, ["Racing"]),
    ("n64:1080-snowboarding", "1080° Snowboarding", "1080", "N64", 1998, ["Sports"]),
    ("n64:body-harvest", "Body Harvest", "body-harvest", "N64", 1998, ["Action"]),
    ("n64:turok", "Turok: Dinosaur Hunter", "turok-1", "N64", 1997, ["FPS"]),
    ("n64:turok-2", "Turok 2: Seeds of Evil", "turok-2", "N64", 1998, ["FPS"]),
    ("n64:rogue-squadron", "Star Wars: Rogue Squadron", "rogue-squadron", "N64", 1998, ["Action"]),
    ("n64:shadows-of-empire", "Star Wars: Shadows of the Empire", "shadows-of-empire", "N64", 1996, ["Action"]),
    ("n64:smash-bros-64", "Super Smash Bros.", "smash-bros-64", "N64", 1999, ["Fighting"]),
]
for id_, title, slug, platform, year, genres in EXTRAS:
    BULK_GAMES.append(G(id_, title, slug, platform, year, genres))

# ---- NDS — full new platform addition --------------------------------------
NDS = [
    ("nds:mario-kart-ds", "Mario Kart DS", "mk-ds", 2005, ["Racing"]),
    ("nds:nsmb-ds", "New Super Mario Bros.", "nsmb-ds", 2006, ["Platformer"]),
    ("nds:sm64-ds", "Super Mario 64 DS", "sm64-ds", 2004, ["Platformer"]),
    ("nds:zelda-ph", "Zelda: Phantom Hourglass", "zelda-ph", 2007, ["Action Adventure"]),
    ("nds:zelda-st", "Zelda: Spirit Tracks", "zelda-st", 2009, ["Action Adventure"]),
    ("nds:pokemon-diamond", "Pokémon Diamond", "pokemon-diamond", 2006, ["JRPG"]),
    ("nds:pokemon-pearl", "Pokémon Pearl", "pokemon-pearl", 2006, ["JRPG"]),
    ("nds:pokemon-platinum", "Pokémon Platinum", "pokemon-platinum", 2008, ["JRPG"]),
    ("nds:pokemon-heart-gold", "Pokémon HeartGold", "pokemon-hg", 2009, ["JRPG"]),
    ("nds:pokemon-soul-silver", "Pokémon SoulSilver", "pokemon-ss", 2009, ["JRPG"]),
    ("nds:pokemon-black", "Pokémon Black", "pokemon-black", 2010, ["JRPG"]),
    ("nds:pokemon-white", "Pokémon White", "pokemon-white", 2010, ["JRPG"]),
    ("nds:pokemon-black-2", "Pokémon Black 2", "pokemon-black-2", 2012, ["JRPG"]),
    ("nds:pokemon-white-2", "Pokémon White 2", "pokemon-white-2", 2012, ["JRPG"]),
    ("nds:pokemon-mystery", "Pokémon Mystery Dungeon: Explorers of Sky", "pokemon-mdes", 2009, ["Roguelike"]),
    ("nds:pokemon-ranger", "Pokémon Ranger", "pokemon-ranger", 2006, ["Action RPG"]),
    ("nds:advance-wars-ds", "Advance Wars: Dual Strike", "advance-wars-ds", 2005, ["Tactical"]),
    ("nds:advance-wars-doR", "Advance Wars: Days of Ruin", "advance-wars-dor", 2008, ["Tactical"]),
    ("nds:fe-shadow-dragon", "Fire Emblem: Shadow Dragon", "fe-shadow-dragon", 2008, ["Tactical RPG"]),
    ("nds:fe-new-mystery", "Fire Emblem: New Mystery of the Emblem", "fe-new-mystery", 2010, ["Tactical RPG"]),
    ("nds:dq-ix", "Dragon Quest IX", "dq-ix", 2009, ["JRPG"]),
    ("nds:dq-iv-ds", "Dragon Quest IV (DS)", "dq-iv-ds", 2007, ["JRPG"]),
    ("nds:dq-v-ds", "Dragon Quest V (DS)", "dq-v-ds", 2008, ["JRPG"]),
    ("nds:dq-vi-ds", "Dragon Quest VI (DS)", "dq-vi-ds", 2009, ["JRPG"]),
    ("nds:chrono-trigger-ds", "Chrono Trigger (DS)", "chrono-trigger-ds", 2008, ["JRPG"]),
    ("nds:ff-iii-ds", "Final Fantasy III (DS)", "ff-iii-ds", 2006, ["JRPG"]),
    ("nds:ff-iv-ds", "Final Fantasy IV (DS)", "ff-iv-ds", 2007, ["JRPG"]),
    ("nds:the-world-ends-with-you", "The World Ends with You", "twewy", 2007, ["Action RPG"]),
    ("nds:professor-layton-1", "Professor Layton and the Curious Village", "pl-1", 2007, ["Puzzle"]),
    ("nds:professor-layton-2", "Professor Layton and the Diabolical Box", "pl-2", 2007, ["Puzzle"]),
    ("nds:professor-layton-3", "Professor Layton and the Unwound Future", "pl-3", 2008, ["Puzzle"]),
    ("nds:professor-layton-4", "Professor Layton and the Last Specter", "pl-4", 2009, ["Puzzle"]),
    ("nds:ace-attorney-1", "Phoenix Wright: Ace Attorney", "aa-1", 2005, ["Adventure"]),
    ("nds:ace-attorney-2", "Phoenix Wright: Justice for All", "aa-2", 2006, ["Adventure"]),
    ("nds:ace-attorney-3", "Phoenix Wright: Trials and Tribulations", "aa-3", 2007, ["Adventure"]),
    ("nds:ace-attorney-4", "Apollo Justice: Ace Attorney", "aa-4", 2007, ["Adventure"]),
    ("nds:ai-mil", "Hotel Dusk: Room 215", "hotel-dusk", 2007, ["Adventure"]),
    ("nds:elite-beat-agents", "Elite Beat Agents", "eba", 2006, ["Rhythm"]),
    ("nds:rhythm-heaven-ds", "Rhythm Heaven", "rh-ds", 2008, ["Rhythm"]),
    ("nds:trauma-center-1", "Trauma Center: Under the Knife", "tc-1", 2005, ["Sim"]),
    ("nds:trauma-center-2", "Trauma Center: Under the Knife 2", "tc-2", 2008, ["Sim"]),
    ("nds:cooking-mama", "Cooking Mama", "cooking-mama", 2006, ["Sim"]),
    ("nds:scribblenauts", "Scribblenauts", "scribblenauts", 2009, ["Puzzle"]),
    ("nds:super-scribblenauts", "Super Scribblenauts", "super-scribblenauts", 2010, ["Puzzle"]),
    ("nds:lock-key", "Lock's Quest", "locks-quest", 2008, ["Strategy"]),
    ("nds:mario-and-luigi-bowsers-inside", "Mario & Luigi: Bowser's Inside Story", "ml-bis", 2009, ["JRPG"]),
    ("nds:mario-and-luigi-partners", "Mario & Luigi: Partners in Time", "ml-pit", 2005, ["JRPG"]),
    ("nds:rune-factory-3", "Rune Factory 3", "rf-3", 2009, ["JRPG","Sim"]),
    ("nds:rune-factory-1", "Rune Factory", "rf-1", 2006, ["JRPG","Sim"]),
    ("nds:rune-factory-2", "Rune Factory 2", "rf-2", 2008, ["JRPG","Sim"]),
    ("nds:metroid-prime-hunters", "Metroid Prime: Hunters", "mp-hunters", 2006, ["FPS"]),
    ("nds:castlevania-dawn", "Castlevania: Dawn of Sorrow", "cv-dos", 2005, ["Metroidvania"]),
    ("nds:castlevania-portrait", "Castlevania: Portrait of Ruin", "cv-por", 2006, ["Metroidvania"]),
    ("nds:castlevania-order", "Castlevania: Order of Ecclesia", "cv-ooe", 2008, ["Metroidvania"]),
]
# NDS has no rule in CONSOLE_RULES — add one
CONSOLE_RULES["NDS"] = _rule("retroarch", "retroarch-snes9x", 60, "PERFECT", 60, "PERFECT")
for id_, title, slug, year, genres in NDS:
    BULK_GAMES.append(G(id_, title, slug, "NDS", year, genres))

# ---- Wii (new platform) ---------------------------------------------------
WII = [
    ("wii:smg-1", "Super Mario Galaxy", "smg-1", 2007, ["Platformer"]),
    ("wii:smg-2", "Super Mario Galaxy 2", "smg-2", 2010, ["Platformer"]),
    ("wii:nsmbw", "New Super Mario Bros. Wii", "nsmbw", 2009, ["Platformer"]),
    ("wii:mk-wii", "Mario Kart Wii", "mk-wii", 2008, ["Racing"]),
    ("wii:smash-brawl", "Super Smash Bros. Brawl", "smash-brawl", 2008, ["Fighting"]),
    ("wii:dkc-returns", "Donkey Kong Country Returns", "dkc-returns", 2010, ["Platformer"]),
    ("wii:sonic-colors", "Sonic Colors", "sonic-colors", 2010, ["Platformer"]),
    ("wii:sonic-unleashed-wii", "Sonic Unleashed (Wii)", "sonic-unleashed-wii", 2008, ["Platformer"]),
    ("wii:zelda-twilight", "Zelda: Twilight Princess", "zelda-tp-wii", 2006, ["Action Adventure"]),
    ("wii:zelda-skyward", "Zelda: Skyward Sword", "zelda-ss-wii", 2011, ["Action Adventure"]),
    ("wii:metroid-prime-3", "Metroid Prime 3: Corruption", "mp-3", 2007, ["FPS Adventure"]),
    ("wii:metroid-other-m", "Metroid: Other M", "metroid-other-m", 2010, ["Action Adventure"]),
    ("wii:xeno-blade-1", "Xenoblade Chronicles", "xenoblade-1-wii", 2010, ["JRPG"]),
    ("wii:pandora-s-tower", "Pandora's Tower", "pandoras-tower", 2011, ["Action RPG"]),
    ("wii:last-story", "The Last Story", "last-story", 2011, ["JRPG"]),
    ("wii:fire-emblem-radiant", "Fire Emblem: Radiant Dawn", "fe-rd", 2007, ["Tactical RPG"]),
    ("wii:resident-evil-4-wii", "Resident Evil 4 (Wii)", "re-4-wii", 2007, ["Survival Horror"]),
    ("wii:resident-evil-umbrella", "Resident Evil: The Umbrella Chronicles", "re-umb-chron", 2007, ["Shooter"]),
    ("wii:no-more-heroes", "No More Heroes", "no-more-heroes", 2007, ["Action"]),
    ("wii:no-more-heroes-2", "No More Heroes 2", "no-more-heroes-2", 2010, ["Action"]),
    ("wii:madworld", "MadWorld", "madworld", 2009, ["Action"]),
    ("wii:wii-sports", "Wii Sports", "wii-sports", 2006, ["Sports"]),
    ("wii:wii-sports-resort", "Wii Sports Resort", "wii-sports-resort", 2009, ["Sports"]),
    ("wii:wii-fit", "Wii Fit", "wii-fit", 2007, ["Fitness"]),
    ("wii:wii-party", "Wii Party", "wii-party", 2010, ["Party"]),
    ("wii:smm-original", "Super Mario Maker (Wii predecessor — Mario Paint)", "smm-wii", 2015, ["Platformer"]),
    ("wii:nights-into-dreams-wii", "Nights: Journey of Dreams", "nights-journey", 2007, ["Platformer"]),
    ("wii:rayman-origins-wii", "Rayman Origins (Wii)", "rayman-origins-wii", 2011, ["Platformer"]),
    ("wii:de-blob", "de Blob", "de-blob", 2008, ["Platformer"]),
    ("wii:de-blob-2", "de Blob 2", "de-blob-2", 2011, ["Platformer"]),
]
CONSOLE_RULES["WII"] = _rule("dolphin", "dolphin-balanced", 60, "PERFECT", 55, "PLAYABLE")
for id_, title, slug, year, genres in WII:
    BULK_GAMES.append(G(id_, title, slug, "WII", year, genres))

# ---- More Switch (push toward 2k catalog) ---------------------------------
SW_MORE = [
    ("switch:mlb-the-show-23", "MLB The Show 23", "mlb-23", 2023, ["Sports"]),
    ("switch:mlb-the-show-24", "MLB The Show 24", "mlb-24", 2024, ["Sports"]),
    ("switch:tetris-99", "Tetris 99", "tetris-99", 2019, ["Puzzle"]),
    ("switch:puyo-puyo-tetris", "Puyo Puyo Tetris", "puyo-puyo-tetris", 2017, ["Puzzle"]),
    ("switch:puyo-puyo-tetris-2", "Puyo Puyo Tetris 2", "puyo-puyo-tetris-2", 2020, ["Puzzle"]),
    ("switch:streets-of-rage-4", "Streets of Rage 4", "streets-of-rage-4", 2020, ["Action"]),
    ("switch:cuphead-sw", "Cuphead (Switch)", "cuphead-sw", 2019, ["Run & Gun"]),
    ("switch:hades-sw", "Hades (Switch)", "hades-sw", 2020, ["Roguelike"]),
    ("switch:hades-2-sw", "Hades II (Switch)", "hades-2-sw", 2025, ["Roguelike"]),
    ("switch:dead-cells-sw", "Dead Cells (Switch)", "dead-cells-sw", 2018, ["Metroidvania"]),
    ("switch:terraria-sw", "Terraria (Switch)", "terraria-sw", 2019, ["Sandbox"]),
    ("switch:minecraft-sw", "Minecraft (Switch)", "minecraft-sw", 2018, ["Sandbox"]),
    ("switch:stardew-sw", "Stardew Valley (Switch)", "stardew-sw", 2017, ["Sim"]),
    ("switch:disco-elysium-sw", "Disco Elysium (Switch)", "disco-elysium-sw", 2021, ["RPG"]),
    ("switch:dq-builders-2", "Dragon Quest Builders 2", "dq-builders-2", 2018, ["Sandbox","JRPG"]),
    ("switch:fire-emblem-warriors", "Fire Emblem Warriors", "fe-warriors", 2017, ["Action"]),
    ("switch:warhammer-darktide", "Darktide", "wh-darktide-sw", 2024, ["Action"]),
    ("switch:warhammer-vermintide", "Warhammer: Vermintide 2 (Switch)", "wh-vermintide-2-sw", 2019, ["Action"]),
    ("switch:diablo-3-sw", "Diablo III: Eternal Collection", "diablo-3-sw", 2018, ["Action RPG"]),
    ("switch:diablo-2-r-sw", "Diablo II: Resurrected (Switch)", "diablo-2-r-sw", 2021, ["Action RPG"]),
    ("switch:diablo-4-sw", "Diablo IV (Switch)", "diablo-4-sw", 2023, ["Action RPG"]),
    ("switch:trine-1", "Trine Enchanted Edition", "trine-1-sw", 2018, ["Puzzle"]),
    ("switch:trine-2", "Trine 2", "trine-2-sw", 2018, ["Puzzle"]),
    ("switch:trine-3", "Trine 3", "trine-3-sw", 2019, ["Puzzle"]),
    ("switch:trine-4", "Trine 4", "trine-4-sw", 2019, ["Puzzle"]),
    ("switch:trine-5", "Trine 5", "trine-5-sw", 2023, ["Puzzle"]),
    ("switch:lovecraft-quest", "The Sinking City", "sinking-city-sw", 2022, ["Adventure"]),
    ("switch:nine-sols", "Nine Sols", "nine-sols-sw", 2024, ["Metroidvania"]),
    ("switch:ender-magnolia", "Ender Magnolia: Bloom in the Mist", "ender-magnolia", 2025, ["Metroidvania"]),
    ("switch:islets", "Islets", "islets", 2022, ["Metroidvania"]),
    ("switch:la-mulana", "La-Mulana", "la-mulana", 2018, ["Metroidvania"]),
    ("switch:hollow-knight-silksong", "Hollow Knight: Silksong", "hk-silksong-sw", 2025, ["Metroidvania"]),
    ("switch:lord-of-the-rings", "The Lord of the Rings: Tales of Middle-earth", "lotr-totm", 2023, ["RPG"]),
    ("switch:wonder-2", "Super Mario Bros. Wonder", "smbw", 2023, ["Platformer"]),
    ("switch:lego-star-wars-saga", "LEGO Star Wars: The Skywalker Saga", "lego-sw-saga", 2022, ["Action"]),
    ("switch:lego-harry-potter", "LEGO Harry Potter Collection", "lego-hp-coll", 2018, ["Action"]),
    ("switch:lego-jurassic-world", "LEGO Jurassic World", "lego-jw", 2019, ["Action"]),
]
for id_, title, slug, year, genres in SW_MORE:
    BULK_GAMES.append(G(id_, title, slug, "SWITCH", year, genres))

# ---- More PSP and PS2 ------------------------------------------------------
PSP_MORE = [
    ("psp:dragon-ball-z-shin-budokai", "Dragon Ball Z: Shin Budokai", "dbz-shin-budokai", 2006, ["Fighting"]),
    ("psp:dragon-ball-z-shin-budokai-another", "Dragon Ball Z: Shin Budokai - Another Road", "dbz-shin-budokai-another", 2007, ["Fighting"]),
    ("psp:dragon-ball-z-tenkaichi-tag", "Dragon Ball Z: Tenkaichi Tag Team", "dbz-tag", 2010, ["Fighting"]),
    ("psp:naruto-ultimate-ninja-heroes-1", "Naruto: Ultimate Ninja Heroes", "naruto-unh-1", 2006, ["Fighting"]),
    ("psp:naruto-ultimate-ninja-heroes-2", "Naruto: Ultimate Ninja Heroes 2", "naruto-unh-2", 2007, ["Fighting"]),
    ("psp:naruto-ultimate-ninja-heroes-3", "Naruto: Ultimate Ninja Heroes 3", "naruto-unh-3", 2009, ["Fighting"]),
    ("psp:naruto-shippuden-narutimate-accel", "Naruto Shippuden: Narutimate Accel", "naruto-shippuden-narutimate", 2008, ["Fighting"]),
    ("psp:smackdown-vs-raw-2011", "WWE SmackDown vs Raw 2011", "svr-2011", 2010, ["Sports"]),
    ("psp:nba-live-2009-psp", "NBA Live 09 (PSP)", "nba-live-09-psp", 2008, ["Sports"]),
    ("psp:gow-trilogy-psp", "God of War Origins Collection", "gow-origins", 2011, ["Action"]),
    ("psp:metal-slug-anth", "Metal Slug Anthology", "metal-slug-anth", 2007, ["Run & Gun"]),
    ("psp:tomb-raider-anniv", "Tomb Raider: Anniversary", "tr-anniv-psp", 2007, ["Action Adventure"]),
    ("psp:tomb-raider-leg", "Tomb Raider: Legend", "tr-leg-psp", 2006, ["Action Adventure"]),
    ("psp:tomb-raider-und", "Tomb Raider: Underworld", "tr-und-psp", 2008, ["Action Adventure"]),
]
for id_, title, slug, year, genres in PSP_MORE:
    BULK_GAMES.append(G(id_, title, slug, "PSP", year, genres))



# ---------------------------------------------------------------------------
# Load existing games so we know which ids we already shipped (avoid dup ids).
# ---------------------------------------------------------------------------
def load_existing_game_ids() -> set:
    existing = set()
    for jp in GAMES_DIR.glob("*.json"):
        try:
            with open(jp, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            for g in data.get("games", []):
                existing.add(g["id"])
        except Exception as e:
            print(f"warn: couldn't read {jp}: {e}")
    return existing


def main():
    print(f"Scanning existing games in {GAMES_DIR}…")
    # Snapshot existing ids EXCLUDING bulk-catalog.json itself — that file is
    # what we're rewriting. Treat it as ephemeral.
    existing_excl_bulk = set()
    for jp in GAMES_DIR.glob("*.json"):
        if jp.name == "bulk-catalog.json":
            continue
        try:
            with open(jp, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            for g in data.get("games", []):
                existing_excl_bulk.add(g["id"])
        except Exception as e:
            print(f"warn: couldn't read {jp}: {e}")
    print(f"Found {len(existing_excl_bulk)} game ids in non-bulk seed files.")

    # 1. Take EVERY BULK_GAMES entry that doesn't collide with a non-bulk id.
    # This way re-running the script with a bigger BULK_GAMES list always
    # produces a bigger bulk-catalog.json (idempotent + monotonic).
    seen_ids = set()
    new_games: List[dict] = []
    for g in BULK_GAMES:
        if g["id"] in existing_excl_bulk:
            continue
        if g["id"] in seen_ids:
            continue
        seen_ids.add(g["id"])
        new_games.append(g)
    print(f"Bulk additions: {len(BULK_GAMES)} drafted, "
          f"{len(BULK_GAMES) - len(new_games)} skipped as dupes, "
          f"{len(new_games)} written to bulk-catalog.json.")

    bulk_out = {
        "schema": 1,
        "_comment": "v0.4 bulk catalog — generated by tools/generate-reports/generate.py. Per-game enrichment (setupSteps, mods, BIOS) is overlaid by separate enrichment files. Re-running the script with more BULK_GAMES entries grows this file; ids that already exist in the non-bulk seeds are skipped.",
        "games": new_games,
    }
    bulk_path = GAMES_DIR / "bulk-catalog.json"
    with open(bulk_path, "w", encoding="utf-8") as fh:
        json.dump(bulk_out, fh, indent=2, ensure_ascii=False)
    print(f"Wrote {bulk_path.relative_to(ROOT)} ({len(new_games)} games).")

    # 2. Generate SD8E + SD8 Gen 2 reports for EVERY game we now know about.
    all_games: List[dict] = []
    # Pull from existing JSON
    for jp in GAMES_DIR.glob("*.json"):
        try:
            with open(jp, "r", encoding="utf-8") as fh:
                d = json.load(fh)
            all_games.extend(d.get("games", []))
        except Exception:
            pass

    # v0.7 Chunk 7.2: stability-drop ladder for alternates. Each `stab_drop`
    # nudges PERFECT->PLAYABLE->GLITCHY->CRASHES once. Caps at CRASHES.
    STAB_LADDER = ["PERFECT", "PLAYABLE", "GLITCHY", "CRASHES"]

    def drop_stab(s: str, n: int) -> str:
        if n <= 0:
            return s
        try:
            idx = STAB_LADDER.index(s)
        except ValueError:
            return s
        return STAB_LADDER[min(idx + n, len(STAB_LADDER) - 1)]

    reports = []
    rep_id = 0
    skipped = 0
    for g in all_games:
        rule = pick_rule(g)
        if rule is None:
            skipped += 1
            continue
        # Build the full set of (emulator, preset, fps_delta_table) we'll emit reports for.
        # First entry is the primary (delta = 0); rest are alternates with their deltas.
        emit_list = [
            {"emulatorId": rule["emulatorId"], "presetId": rule["presetId"],
             "sd8e_delta": 0, "sd8g2_delta": 0, "stab_drop": 0},
        ]
        # v0.7: only Windows games get cross-emulator alternates. Console rules
        # are single-emulator by nature (no point emitting Eden + Sudachi + Citron
        # for every Switch game from rules alone — that'd be lies).
        if g.get("platform", "").upper() == "WINDOWS":
            emit_list.extend(rule.get("alternates", []))

        for entry in emit_list:
            for dev_key, dev in [("sd8e", DEV_SD8_ELITE), ("sd8g2", DEV_SD8_GEN2)]:
                base_fps, base_stab = rule[dev_key]
                # Blocked entries don't get alts — only the one CRASHES report.
                if base_fps is None and entry["emulatorId"] != rule["emulatorId"]:
                    continue
                if base_fps is None:
                    fps_val: Optional[int] = None
                else:
                    delta_key = "sd8e_delta" if dev_key == "sd8e" else "sd8g2_delta"
                    raw = base_fps + entry.get(delta_key, 0)
                    fps_val = max(5, min(60, raw))  # clamp to a sensible band
                stab = drop_stab(base_stab, entry.get("stab_drop", 0))
                rep_id += 1
                reports.append({
                    "id": f"gen-{rep_id:05d}",
                    "gameId": g["id"],
                    "emulatorId": entry["emulatorId"],
                    "presetId": entry["presetId"],
                    "device": dev,
                    "avgFps": fps_val,
                    "stability": stab,
                    "notes": None,
                    "source": "GENERATED_HEURISTIC",
                    "sourceRef": None,
                    "submittedAt": 0,
                })

    gen_out = {
        "schema": 1,
        "_comment": "v0.4 heuristic reports — generated by tools/generate-reports/generate.py. Source=GENERATED_HEURISTIC; UI labels these as estimates. Replace with real reports as users submit them.",
        "reports": reports,
    }
    gen_path = REPORTS_DIR / "generated-sd8.json"
    with open(gen_path, "w", encoding="utf-8") as fh:
        json.dump(gen_out, fh, indent=2, ensure_ascii=False)
    print(f"Wrote {gen_path.relative_to(ROOT)} ({len(reports)} reports across {len(all_games) - skipped} games; skipped {skipped} that had no matching rule).")


if __name__ == "__main__":
    main()
