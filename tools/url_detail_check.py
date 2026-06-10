#!/usr/bin/env python3
"""Check URL validity more thoroughly."""

import json
from pathlib import Path

BASE_PATH = Path("C:/Users/Naxte/Downloads/ClaudeS/iSiTCompatible/app/src/main/assets/seed")

def load_json_utf8(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return json.load(f)

guides_files = {
    'base': load_json_utf8(BASE_PATH / "guides/base-guides.json"),
    'authored-top': load_json_utf8(BASE_PATH / "guides/authored-top.json"),
    'authored-top-2': load_json_utf8(BASE_PATH / "guides/authored-top-2.json"),
}

all_guides = {}
for source, guides_data in guides_files.items():
    guides_list = guides_data.get('guides', []) if isinstance(guides_data, dict) else guides_data
    for g in guides_list:
        guide_id = g.get('id')
        if guide_id:
            all_guides[guide_id] = (g, source)

print("\n" + "="*80)
print("ALL GET_APP URLs")
print("="*80)

for guide_id, (guide_obj, source) in sorted(all_guides.items()):
    steps = guide_obj.get('steps', [])
    for step in steps:
        if step.get('kind') == 'GET_APP':
            url = step.get('url', '')
            text = step.get('text', '')
            print(f"\n{guide_id}")
            print(f"  {text}")
            print(f"  {url}")

print("\n" + "="*80)
print("ALL GET_DRIVER references")
print("="*80)

catalog = load_json_utf8(BASE_PATH / "catalog.json")
drivers = {d.get('id'): d for d in catalog.get('drivers', [])}

print("\nAvailable drivers in catalog:")
for driver_id in sorted(drivers.keys()):
    print(f"  {driver_id}")

print("\nGET_DRIVER steps in guides:")
for guide_id, (guide_obj, source) in sorted(all_guides.items()):
    steps = guide_obj.get('steps', [])
    for step in steps:
        if step.get('kind') == 'GET_DRIVER':
            driver_id = step.get('driverId', '')
            text = step.get('text', '')
            exists = "✓" if driver_id in drivers else "✗ MISSING"
            print(f"\n{guide_id}: {exists}")
            print(f"  Driver: {driver_id}")
            print(f"  {text}")
