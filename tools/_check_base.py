import json, glob
cat = json.load(open(r'app\src\main\assets\seed\catalog.json', encoding='utf-8'))
emu_ids = {e['id'] for e in cat.get('emulators', [])}
base = set()
total_guides = 0
for f in glob.glob(r'app\src\main\assets\seed\guides\*.json'):
    d = json.load(open(f, encoding='utf-8'))
    for g in d.get('guides', []):
        total_guides += 1
        if g.get('gameId') is None and g.get('tier') == 4:
            base.add(g['emulatorId'])
missing = sorted(emu_ids - base)
print('catalog emulators:', len(emu_ids))
print('emulators WITH base guide:', len(base & emu_ids))
print('MISSING base guide:', missing if missing else 'NONE')
print('total guides across all files:', total_guides)
