import json, glob, os

# Which game ids already have an authored (tier<=2) guide?
authored = set()
for f in glob.glob('app/src/main/assets/seed/guides/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    for g in d.get('guides', []):
        if g.get('tier', 4) <= 2 and g.get('gameId'):
            authored.add(g['gameId'])

# All games + their report count (proxy for popularity) + recommended emulator.
games = {}
reports_by_game = {}
for f in glob.glob('app/src/main/assets/seed/**/*.json', recursive=True):
    try:
        d = json.load(open(f, encoding='utf-8'))
    except Exception:
        continue
    for g in d.get('games', []):
        games[g['id']] = (g.get('title',''), g.get('platform',''))
    for r in d.get('reports', []):
        reports_by_game[r['gameId']] = reports_by_game.get(r['gameId'], 0) + 1

# Rank games WITHOUT an authored guide by report count.
rows = []
for gid, (title, plat) in games.items():
    if gid in authored:
        continue
    rows.append((reports_by_game.get(gid, 0), gid, title, plat))
rows.sort(reverse=True)

print(f"Authored guides exist for {len(authored)} games.")
print(f"Top 40 games WITHOUT an authored guide (by report count):")
print(f"{'reports':>7}  {'id':40} {'platform':8} title")
for cnt, gid, title, plat in rows[:40]:
    print(f"{cnt:>7}  {gid:40} {plat:8} {title}")
