#!/usr/bin/env python3
"""
Audit script for iSiTCompatible Android app seed data.
Checks for coverage gaps, stale/invalid data, and consistency issues.
"""

import json
import os
import re
from collections import defaultdict
from pathlib import Path

BASE_PATH = Path("C:/Users/Naxte/Downloads/ClaudeS/iSiTCompatible/app/src/main/assets/seed")

# ============================================================================
# Load all JSON data
# ============================================================================

def load_json(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

# Load guides
guides_files = {
    'base': load_json(BASE_PATH / "guides/base-guides.json"),
    'authored-top': load_json(BASE_PATH / "guides/authored-top.json"),
    'authored-top-2': load_json(BASE_PATH / "guides/authored-top-2.json"),
}

# Load catalog
catalog = load_json(BASE_PATH / "catalog.json")

# Load games
def load_json_utf8(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

game_files = {
    'consoles': load_json_utf8(BASE_PATH / "games/consoles.json"),
    'ps2': load_json_utf8(BASE_PATH / "games/ps2.json"),
    'bulk-catalog': load_json_utf8(BASE_PATH / "games/bulk-catalog.json"),
    'expansion-consoles': load_json_utf8(BASE_PATH / "games/expansion-consoles.json"),
    'expansion-pc': load_json_utf8(BASE_PATH / "games/expansion-pc.json"),
    'windows': load_json_utf8(BASE_PATH / "games/windows.json"),
}

enrichment = load_json(BASE_PATH / "enrichment/hand-written.json")

# ============================================================================
# Build comprehensive indexes
# ============================================================================

# Flatten all guides
all_guides = {}
guide_by_tier = defaultdict(list)
for source, guides_data in guides_files.items():
    guides_list = guides_data.get('guides', []) if isinstance(guides_data, dict) else guides_data
    for g in guides_list:
        guide_id = g.get('id')
        if guide_id:
            all_guides[guide_id] = (g, source)
            tier = g.get('tier')
            guide_by_tier[tier].append((guide_id, source))

# Flatten all games
all_games = {}
games_by_console = defaultdict(list)
for source, games_data in game_files.items():
    games_list = games_data.get('games', []) if isinstance(games_data, dict) else games_data
    for game in games_list:
        game_id = game.get('id')
        if game_id:
            all_games[game_id] = (game, source)
            console = game.get('platform')  # Note: it's 'platform' not 'console'
            if console:
                games_by_console[console].append(game_id)

# Flatten all emulators and drivers from catalog
emulators = {e.get('id'): e for e in catalog.get('emulators', [])}
drivers = {d.get('id'): d for d in catalog.get('drivers', [])}
presets = {p.get('id'): p for p in catalog.get('presets', [])}

# ============================================================================
# A. COVERAGE GAPS
# ============================================================================

print("\n" + "="*80)
print("A. COVERAGE GAPS")
print("="*80)

# A1: Authored guide coverage
authored_guide_count = len(guide_by_tier[1]) + len(guide_by_tier[2])
total_games = len(all_games)
print(f"\nA1. Authored guide coverage:")
print(f"    Games with authored guides (tier <= 2): {authored_guide_count}")
print(f"    Total games in database: {total_games}")
print(f"    Coverage: {authored_guide_count/total_games*100:.1f}%")

# A2: Platform coverage for major consoles (using actual platform codes)
major_consoles = ['PS2', 'PSP', 'SWITCH', 'GC', 'WII', 'N64', 'N3DS', 'PS1', 'NDS', 'WINDOWS']
major_console_labels = {'PS2': 'PS2', 'PSP': 'PSP', 'SWITCH': 'Switch', 'GC': 'GameCube', 'WII': 'Wii', 'N64': 'N64', 'N3DS': '3DS', 'PS1': 'PS1', 'NDS': 'DS', 'WINDOWS': 'PC'}
print(f"\nA2. Authored guides by platform:")
platform_guide_count = {}
for console in major_consoles:
    games_on_platform = [gid for gid in games_by_console.get(console, [])
                         if all_guides.get(gid, ({},))[0].get('tier') in [1, 2]]
    platform_guide_count[console] = len(games_on_platform)
    total_on_platform = len(games_by_console.get(console, []))
    label = major_console_labels.get(console, console)
    print(f"    {label:15} {len(games_on_platform):3d} authored guides / {total_on_platform:3d} total games")

low_coverage = {p: c for p, c in platform_guide_count.items() if c == 0}
if low_coverage:
    low_coverage_labels = [major_console_labels.get(p, p) for p in low_coverage.keys()]
    print(f"\n    PLATFORMS WITH ZERO AUTHORED GUIDES: {sorted(low_coverage_labels)}")

# A3: Base guides for emulators
print(f"\nA3. Base guides (tier 4) coverage for emulators:")
base_guides = [g for g in guide_by_tier[4]]
emulators_with_base = set()
for guide_id, source in base_guides:
    guide_obj = all_guides[guide_id][0]
    emu_id = guide_obj.get('emulatorId')
    if emu_id:
        emulators_with_base.add(emu_id)

emulators_missing_base = [eid for eid in emulators.keys() if eid not in emulators_with_base]
print(f"    Emulators with base guides: {len(emulators_with_base)} / {len(emulators)}")
if emulators_missing_base:
    print(f"    EMULATORS WITHOUT BASE GUIDES: {sorted(emulators_missing_base)}")

# ============================================================================
# B. LIKELY-WRONG / STALE DATA
# ============================================================================

print("\n" + "="*80)
print("B. LIKELY-WRONG / STALE DATA")
print("="*80)

issues = {
    'bad_urls': [],
    'missing_drivers': [],
    'missing_emulators': [],
    'orphan_guides': [],
    'contradictory_perf': [],
}

# B1: Check GET_APP URLs
print("\nB1. Checking GET_APP step URLs...")
suspicious_url_patterns = [
    r'github\.com/[^/]+/[^/]+/releases?/tag/latest',  # generic latest
    r'github\.com/[^/]+/[^/]+$',                       # repo root, not release
    r'github\.io',                                      # github pages (rarely correct)
]

for guide_id, (guide_obj, source) in all_guides.items():
    steps = guide_obj.get('steps', [])
    for step in steps:
        if step.get('kind') == 'GET_APP':
            url = step.get('url', '')
            # Check if URL looks wrong
            if not url.startswith('http'):
                issues['bad_urls'].append((guide_id, 'no http prefix', url))
            # Check for obvious dead patterns
            for pattern in suspicious_url_patterns:
                if re.search(pattern, url):
                    issues['bad_urls'].append((guide_id, 'suspicious pattern', url))

if issues['bad_urls']:
    print(f"    Found {len(issues['bad_urls'])} suspicious URLs:")
    for guide_id, reason, url in issues['bad_urls'][:10]:
        print(f"      {guide_id}: {reason}")
        print(f"        {url}")
else:
    print("    No obviously bad URLs found")

# B2: Check GET_DRIVER steps reference valid drivers
print("\nB2. Checking GET_DRIVER step references...")
for guide_id, (guide_obj, source) in all_guides.items():
    steps = guide_obj.get('steps', [])
    for step in steps:
        if step.get('kind') == 'GET_DRIVER':
            driver_id = step.get('driverId')
            if driver_id and driver_id not in drivers:
                issues['missing_drivers'].append((guide_id, driver_id))

if issues['missing_drivers']:
    print(f"    FOUND {len(issues['missing_drivers'])} missing drivers:")
    for guide_id, driver_id in issues['missing_drivers']:
        print(f"      Guide {guide_id} references missing driver: {driver_id}")
else:
    print("    All referenced drivers exist in catalog")

# B3: Check emulatorId references
print("\nB3. Checking emulatorId references...")
for guide_id, (guide_obj, source) in all_guides.items():
    emu_id = guide_obj.get('emulatorId')
    if emu_id and emu_id not in emulators:
        issues['missing_emulators'].append((guide_id, emu_id))

if issues['missing_emulators']:
    print(f"    FOUND {len(issues['missing_emulators'])} missing emulators:")
    for guide_id, emu_id in issues['missing_emulators']:
        print(f"      Guide {guide_id} references missing emulator: {emu_id}")
else:
    print("    All referenced emulators exist in catalog")

# B4: Check guide gameIds exist
print("\nB4. Checking gameId references...")
for guide_id, (guide_obj, source) in all_guides.items():
    game_id = guide_obj.get('gameId')
    if game_id and game_id not in all_games:
        issues['orphan_guides'].append((guide_id, game_id, source))

if issues['orphan_guides']:
    print(f"    FOUND {len(issues['orphan_guides'])} orphan guides (game not in any games file):")
    for guide_id, game_id, source in issues['orphan_guides']:
        print(f"      Guide {guide_id} (from {source}) references missing game: {game_id}")
else:
    print("    All guide gameIds exist in game files")

# B5: Check for obviously wrong perf claims
print("\nB5. Checking for suspicious performance claims...")
# Known CPU-heavy AAA games that should NOT claim locked 60fps everywhere
heavy_games_keywords = ['GTA', 'Red Dead', 'Cyberpunk', 'Kingdom Come', 'Witcher 3']
for guide_id, (guide_obj, source) in all_guides.items():
    steps = guide_obj.get('steps', [])
    for step in steps:
        if step.get('kind') == 'ACTION':
            desc = step.get('description', '').lower()
            if 'locked 60' in desc or '60 fps' in desc:
                game_id = guide_obj.get('gameId')
                if game_id and game_id in all_games:
                    game = all_games[game_id][0]
                    game_name = game.get('name', '').lower()
                    for keyword in heavy_games_keywords:
                        if keyword.lower() in game_name:
                            issues['contradictory_perf'].append((guide_id, game_name, desc))

if issues['contradictory_perf']:
    print(f"    Found {len(issues['contradictory_perf'])} possibly exaggerated perf claims:")
    for guide_id, game, claim in issues['contradictory_perf']:
        print(f"      {guide_id} ({game}): '{claim}'")
else:
    print("    No obviously contradictory perf claims found")

# ============================================================================
# C. CONSISTENCY
# ============================================================================

print("\n" + "="*80)
print("C. CONSISTENCY")
print("="*80)

# C1: Check step completeness
print("\nC1. Checking step completeness (ACTION + CONTAINER/settings)...")
incomplete_guides = []
for guide_id, (guide_obj, source) in all_guides.items():
    steps = guide_obj.get('steps', [])
    kinds = [s.get('kind') for s in steps]

    has_action = 'ACTION' in kinds
    has_container = 'CONTAINER' in kinds
    has_settings = any('settings' in str(s).lower() for s in steps)

    if not has_action or (not has_container and not has_settings):
        incomplete_guides.append((guide_id, 'has_action=' + str(has_action),
                                 'has_container=' + str(has_container)))

if incomplete_guides:
    print(f"    FOUND {len(incomplete_guides)} guides missing critical steps:")
    for guide_id, action_status, container_status in incomplete_guides[:10]:
        print(f"      {guide_id}: {action_status}, {container_status}")
else:
    print("    All guides appear to have required steps")

# C2: Check for duplicate guide IDs
print("\nC2. Checking for duplicate guide IDs...")
seen_ids = defaultdict(list)
for source, guides_data in guides_files.items():
    guides_list = guides_data.get('guides', []) if isinstance(guides_data, dict) else guides_data
    for g in guides_list:
        guide_id = g.get('id')
        if guide_id:
            seen_ids[guide_id].append(source)

duplicates = {gid: sources for gid, sources in seen_ids.items() if len(sources) > 1}
if duplicates:
    print(f"    FOUND {len(duplicates)} duplicate guide IDs:")
    for guide_id, sources in list(duplicates.items())[:10]:
        print(f"      {guide_id}: appears in {sources}")
else:
    print("    No duplicate guide IDs found")

# ============================================================================
# SUMMARY
# ============================================================================

print("\n" + "="*80)
print("SUMMARY")
print("="*80)
print(f"\nTotal guides loaded: {len(all_guides)}")
print(f"  Tier 1 (authored): {len(guide_by_tier[1])}")
print(f"  Tier 2 (authored): {len(guide_by_tier[2])}")
print(f"  Tier 3 (semi-auto): {len(guide_by_tier[3])}")
print(f"  Tier 4 (base): {len(guide_by_tier[4])}")

print(f"\nTotal games loaded: {len(all_games)}")
print(f"Total emulators in catalog: {len(emulators)}")
print(f"Total drivers in catalog: {len(drivers)}")

print(f"\nIssues found:")
print(f"  Bad URLs: {len(issues['bad_urls'])}")
print(f"  Missing drivers: {len(issues['missing_drivers'])}")
print(f"  Missing emulators: {len(issues['missing_emulators'])}")
print(f"  Orphan guides: {len(issues['orphan_guides'])}")
print(f"  Suspicious perf claims: {len(issues['contradictory_perf'])}")
print(f"  Incomplete guides: {len(incomplete_guides)}")
print(f"  Duplicate IDs: {len(duplicates)}")
