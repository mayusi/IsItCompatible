import json, glob
wanted = [
    "skyrim", "elden-ring", "gta-v", "grand-theft-auto-v", "god-of-war",
    "dark-souls", "witcher-3", "cyberpunk", "persona-5", "zelda-tears",
    "zelda-breath", "mario-odyssey", "metroid-dread", "final-fantasy-vii-remake",
    "red-dead", "hollow-knight", "celeste", "stardew", "hades",
    "shadow-of-the-colossus", "kingdom-hearts", "nier-automata", "sekiro",
    "monster-hunter", "resident-evil-4", "spider-man", "smash",
]
found = {}
for f in glob.glob('app/src/main/assets/seed/**/*.json', recursive=True):
    try:
        d = json.load(open(f, encoding='utf-8'))
    except Exception:
        continue
    for g in d.get('games', []):
        gid = g['id']
        title = g.get('title', '')
        slug = g.get('titleSlug', '')
        for w in wanted:
            if w in gid.lower() or w in slug.lower() or w.replace('-', ' ') in title.lower():
                found[gid] = (title, g.get('platform'))
for gid, (title, plat) in sorted(found.items()):
    print(f"{gid:40} | {plat:8} | {title}")
