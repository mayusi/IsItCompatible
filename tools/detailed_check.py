#!/usr/bin/env python3
"""Detailed check: incomplete guides, authored guides by game/emulator."""

import json
from pathlib import Path

BASE_PATH = Path("C:/Users/Naxte/Downloads/ClaudeS/iSiTCompatible/app/src/main/assets/seed")

def load_json_utf8(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

# Load guides
guides_files = {
    'base': load_json_utf8(BASE_PATH / "guides/base-guides.json"),
    'authored-top': load_json_utf8(BASE_PATH / "guides/authored-top.json"),
    'authored-top-2': load_json_utf8(BASE_PATH / "guides/authored-top-2.json"),
}

# Load games
game_files = {
    'consoles': load_json_utf8(BASE_PATH / "games/consoles.json"),
    'ps2': load_json_utf8(BASE_PATH / "games/ps2.json"),
    'bulk-catalog': load_json_utf8(BASE_PATH / "games/bulk-catalog.json"),
    'expansion-consoles': load_json_utf8(BASE_PATH / "games/expansion-consoles.json"),
    'expansion-pc': load_json_utf8(BASE_PATH / "games/expansion-pc.json"),
    'windows': load_json_utf8(BASE_PATH / "games/windows.json"),
}

# Flatten
all_guides = {}
for source, guides_data in guides_files.items():
    guides_list = guides_data.get('guides', []) if isinstance(guides_data, dict) else guides_data
    for g in guides_list:
        guide_id = g.get('id')
        if guide_id:
            all_guides[guide_id] = (g, source)

all_games = {}
for source, games_data in game_files.items():
    games_list = games_data.get('games', []) if isinstance(games_data, dict) else games_data
    for game in games_list:
        game_id = game.get('id')
        if game_id:
            all_games[game_id] = (game, source)

print("\n" + "="*80)
print("INCOMPLETE GUIDES DETAIL")
print("="*80)

incomplete_guides = []
for guide_id, (guide_obj, source) in all_guides.items():
    steps = guide_obj.get('steps', [])
    kinds = [s.get('kind') for s in steps]

    has_action = 'ACTION' in kinds
    has_container = 'CONTAINER' in kinds
    has_settings = any('settings' in str(s).lower() for s in steps)

    if not has_action or (not has_container and not has_settings):
        incomplete_guides.append((guide_id, has_action, has_container, has_settings, guide_obj))

for guide_id, has_action, has_container, has_settings, guide_obj in sorted(incomplete_guides):
    emu_id = guide_obj.get('emulatorId')
    game_id = guide_obj.get('gameId')
    tier = guide_obj.get('tier')
    print(f"\n{guide_id} (tier {tier}, emulator: {emu_id}, game: {game_id})")
    print(f"  Has ACTION: {has_action}")
    print(f"  Has CONTAINER: {has_container}")
    print(f"  Has SETTINGS in steps: {has_settings}")
    steps = guide_obj.get('steps', [])
    print(f"  Step kinds: {[s.get('kind') for s in steps]}")

print("\n" + "="*80)
print("AUTHORED GUIDES (TIER 1-2)")
print("="*80)

authored_guides = {}
for guide_id, (guide_obj, source) in all_guides.items():
    tier = guide_obj.get('tier')
    if tier in [1, 2]:
        authored_guides[guide_id] = guide_obj

print(f"\nTotal authored guides: {len(authored_guides)}")
print("\nByemulator:")
by_emulator = {}
for guide_id, guide_obj in authored_guides.items():
    emu_id = guide_obj.get('emulatorId')
    if emu_id not in by_emulator:
        by_emulator[emu_id] = []
    by_emulator[emu_id].append((guide_id, guide_obj.get('gameId')))

for emu_id in sorted(by_emulator.keys()):
    print(f"\n{emu_id}: {len(by_emulator[emu_id])} games")
    for guide_id, game_id in sorted(by_emulator[emu_id]):
        if game_id and game_id in all_games:
            game = all_games[game_id][0]
            print(f"  - {game.get('title', game_id)}")
        else:
            print(f"  - {game_id}")
