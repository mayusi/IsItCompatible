#!/usr/bin/env python3
"""Quick check of what platforms exist in the games data."""

import json
from collections import defaultdict
from pathlib import Path

BASE_PATH = Path("C:/Users/Naxte/Downloads/ClaudeS/iSiTCompatible/app/src/main/assets/seed")

game_files = {
    'consoles': json.load(open(BASE_PATH / "games/consoles.json", encoding='utf-8')),
    'ps2': json.load(open(BASE_PATH / "games/ps2.json", encoding='utf-8')),
    'bulk-catalog': json.load(open(BASE_PATH / "games/bulk-catalog.json", encoding='utf-8')),
    'expansion-consoles': json.load(open(BASE_PATH / "games/expansion-consoles.json", encoding='utf-8')),
    'expansion-pc': json.load(open(BASE_PATH / "games/expansion-pc.json", encoding='utf-8')),
    'windows': json.load(open(BASE_PATH / "games/windows.json", encoding='utf-8')),
}

platform_counts = defaultdict(int)
for source, games_data in game_files.items():
    games_list = games_data.get('games', [])
    for game in games_list:
        platform = game.get('platform')
        if platform:
            platform_counts[platform] += 1

print("Actual platforms in data:")
for platform in sorted(platform_counts.keys()):
    print(f"  {platform}: {platform_counts[platform]}")
