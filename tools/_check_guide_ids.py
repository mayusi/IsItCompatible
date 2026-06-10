import json, glob
ids = {}
dupes = []
total = 0
for f in glob.glob('app/src/main/assets/seed/guides/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    for g in d.get('guides', []):
        total += 1
        gid = g['id']
        if gid in ids:
            dupes.append((gid, ids[gid], f))
        ids[gid] = f
print(f"total guide entries across files: {total}")
print(f"distinct ids: {len(ids)}")
if dupes:
    print("DUPLICATE IDS (later overwrites earlier in Room upsert):")
    for gid, first, second in dupes:
        print(f"  {gid}  in {first}  AND  {second}")
else:
    print("no duplicate ids")
