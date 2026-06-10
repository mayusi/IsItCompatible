import json
d = json.load(open('app/src/main/assets/seed/catalog.json', encoding='utf-8'))
for e in d.get('emulators', []):
    print(f"{e['id']:22} | pkg={e.get('packageId','')!s:34} | {e.get('platformTargets')} | {e.get('sourceUrl','')}")
print()
print("Drivers:")
for dr in d.get('drivers', []):
    print(f"  {dr['id']:18} -> {dr.get('name','')}")
