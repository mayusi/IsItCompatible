import json, glob
authored = set()
for f in glob.glob(r'app\src\main\assets\seed\guides\*.json'):
    d = json.load(open(f, encoding='utf-8'))
    for g in d.get('guides', []):
        if g.get('tier',4) <= 2 and g.get('gameId'): authored.add(g['gameId'])
games = {}
reports = {}
for f in glob.glob(r'app\src\main\assets\seed\**\*.json', recursive=True):
    if 'guides' in f: continue
    try: d = json.load(open(f, encoding='utf-8'))
    except: continue
    for g in d.get('games', []):
        games[g['id']] = (g.get('title',''), g.get('platform',''))
    for r in d.get('reports', []):
        reports[r['gameId']] = reports.get(r['gameId'],0)+1
# console platforms only, no authored guide yet, ranked by reports
consoles = {'PS1','PS2','PSP','SWITCH','GC','WII','N64','N3DS','NDS'}
rows = []
for gid,(t,p) in games.items():
    if p in consoles and gid not in authored:
        rows.append((p, reports.get(gid,0), gid, t))
# pick top ~3 per platform by report count
from collections import defaultdict
byp = defaultdict(list)
for p,c,gid,t in sorted(rows, key=lambda x:-x[1]): byp[p].append((c,gid,t))
for p in sorted(byp):
    print(f"=== {p} (need guides) ===")
    for c,gid,t in byp[p][:4]:
        print(f"  {gid}  | {t}  ({c} reports)")
