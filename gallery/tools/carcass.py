import zipfile, json, re, collections, sys
z = zipfile.ZipFile(r'C:/Users/Alex/AppData/Roaming/ElyPrismLauncher/instances/DEP_S5_rev/minecraft/mods/butchery-5.1-forge-1.20.1.jar')
P = 'data/butchery/loot_tables/blocks/'
names = [x[len(P):-5] for x in z.namelist() if x.startswith(P) and x.endswith('.json')]
STAGE = re.compile(r'^(.*?)_(cut(?:_\d)?_drop(?:_fd_compat)?|skin_drop|head_drop|leg(?:_\d)?_drop|organ_drop_\d|body_drop|arm_drop|fur_drop|wing_drop|heart_drop|eyes_drop|spike_drop|head_breakdown)$')

def cnt(f):
    c = f.get('count', 1)
    if isinstance(c, dict):
        return c.get('min', 1), c.get('max', 1)
    return c, c

def parse(name):
    d = json.loads(z.read(P + name + '.json'))
    res = []
    for pool in d.get('pools', []):
        r = pool.get('rolls', 1)
        if isinstance(r, dict):
            r = (r.get('min', 1) + r.get('max', 1)) / 2
        chance = 1.0
        for c in pool.get('conditions', []):
            if 'chance' in c:
                chance *= c['chance']
        ents = pool.get('entries', [])
        tw = sum(e.get('weight', 1) for e in ents) or 1
        for e in ents:
            if e.get('type') != 'minecraft:item':
                continue
            lo, hi = 1, 1
            fort = False
            ech = 1.0
            for f in e.get('functions', []):
                if f.get('function', '').endswith('set_count'):
                    lo, hi = cnt(f)
                if 'apply_bonus' in f.get('function', ''):
                    fort = True
            for c in e.get('conditions', []):
                if 'chance' in c:
                    ech *= c['chance']
            p = r * chance * ech * e.get('weight', 1) / tw
            res.append((e['name'], lo * p, hi * p, fort))
    return res

out = collections.OrderedDict()
for n in sorted(names):
    m = STAGE.match(n)
    if not m:
        continue
    animal, stage = m.group(1), m.group(2)
    out.setdefault(animal, []).append((stage, parse(n)))
json.dump(out, open('carcass.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
for a, st in out.items():
    tot = collections.defaultdict(lambda: [0, 0])
    for s, items in st:
        if s.endswith('fd_compat'):
            continue
        for i, lo, hi, f in items:
            tot[i][0] += lo; tot[i][1] += hi
    print(a, '|', ', '.join(s for s, _ in st), '|', '; '.join('%s %s' % (i.split(':')[1], ('%g' % v[0]) if v[0] == v[1] else '%g-%g' % tuple(v)) for i, v in tot.items()))
