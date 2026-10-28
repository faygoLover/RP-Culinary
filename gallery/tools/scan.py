import zipfile, json, os, re, sys, glob
MODS = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\instances\DEP_S5_rev\minecraft\mods")
VAN = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\libraries\com\mojang\minecraft\1.20.1\minecraft-1.20.1-client.jar")
ASSETS = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\assets")
OUT = sys.argv[1]

def lj(b):
    try:
        return json.loads(b.decode('utf-8-sig'))
    except Exception:
        try:
            # strip comments / trailing commas
            s = re.sub(r'//[^\n]*', '', b.decode('utf-8-sig', 'ignore'))
            s = re.sub(r',\s*([}\]])', r'\1', s)
            return json.loads(s)
        except Exception:
            return None

jars = [VAN] + sorted(glob.glob(os.path.join(MODS, '*.jar')))
loaded_mods = set(['minecraft', 'forge'])
# first pass: mod ids
for j in jars:
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    for n in z.namelist():
        if n == 'META-INF/mods.toml':
            t = z.read(n).decode('utf-8', 'ignore')
            for m in re.finditer(r'modId\s*=\s*"([^"]+)"', t):
                loaded_mods.add(m.group(1))

ru, en = {}, {}
rp_ru = {}
tags = {}  # tag -> set(entries)
recipes = []
models = {}  # item id -> jar
foodclass = {}
nsjar = {}
CAMEL = re.compile(r'(?<!^)(?=[A-Z])')

def cond_ok(c):
    if not isinstance(c, list):
        return True
    for x in c:
        if not isinstance(x, dict):
            continue
        t = x.get('type', '')
        if t == 'forge:mod_loaded' and x.get('modid') not in loaded_mods:
            return False
        if t == 'forge:not':
            v = x.get('value', {})
            if v.get('type') == 'forge:mod_loaded' and v.get('modid') in loaded_mods:
                return False
        if t == 'forge:and' and not cond_ok(x.get('values')):
            return False
    return True

OUTK = {'result', 'results', 'output', 'outputs', 'result_item', 'resultitem', 'byproduct', 'byproducts'}

def collect(o, acc):
    if isinstance(o, dict):
        for k in ('item', 'id'):
            v = o.get(k)
            if isinstance(v, str) and ':' in v:
                acc.add(v)
        v = o.get('tag')
        if isinstance(v, str):
            acc.add('#' + v)
        for k, v in o.items():
            if k in ('item', 'tag', 'id', 'type', 'conditions', 'nbt', 'count'):
                continue
            collect(v, acc)
    elif isinstance(o, list):
        for x in o:
            collect(x, acc)
    elif isinstance(o, str) and re.fullmatch(r'#?[a-z0-9_.\-]+:[a-z0-9_/.\-]+', o):
        acc.add(o)

for j in jars:
    jn = os.path.basename(j)
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    for n in z.namelist():
        p = n.split('/')
        if len(p) >= 6 and p[0] == 'resourcepacks' and p[2] == 'assets' and p[4] == 'lang' and p[5] == 'ru_ru.json':
            d = lj(z.read(n))
            if isinstance(d, dict):
                rp_ru.update({k: v for k, v in d.items() if isinstance(v, str)})
            continue
        if len(p) >= 4 and p[0] == 'assets' and p[2] == 'lang' and p[3] in ('ru_ru.json', 'en_us.json'):
            d = lj(z.read(n))
            if isinstance(d, dict):
                (ru if p[3] == 'ru_ru.json' else en).update({k: v for k, v in d.items() if isinstance(v, str)})
        elif len(p) >= 5 and p[0] == 'assets' and p[2] == 'models' and p[3] == 'item' and n.endswith('.json'):
            iid = p[1] + ':' + '/'.join(p[4:])[:-5]
            models.setdefault(iid, jn)
            nsjar.setdefault(p[1], jn)
        elif len(p) >= 5 and p[0] == 'data' and p[2] == 'tags' and p[3] == 'items' and n.endswith('.json'):
            d = lj(z.read(n))
            if not isinstance(d, dict):
                continue
            tg = p[1] + ':' + '/'.join(p[4:])[:-5]
            s = tags.setdefault(tg, set())
            if d.get('replace'):
                s.clear()
            for v in d.get('values', []):
                if isinstance(v, dict):
                    v = v.get('id')
                if isinstance(v, str):
                    s.add(v)
        elif len(p) >= 4 and p[0] == 'data' and p[2] == 'recipes' and n.endswith('.json'):
            d = lj(z.read(n))
            if not isinstance(d, dict):
                continue
            if not cond_ok(d.get('conditions')):
                continue
            if d.get('type') == 'forge:conditional':
                rs = d.get('recipes') or []
                d = None
                for r in rs:
                    if cond_ok(r.get('conditions')):
                        d = r.get('recipe')
                        break
                if not isinstance(d, dict):
                    continue
            ins, outs = set(), set()
            for k, v in d.items():
                if k in ('type', 'conditions', 'group', 'category'):
                    continue
                (outs if k.lower() in OUTK else ins).__class__  # noop
                collect(v, outs if k.lower() in OUTK else ins)
            recipes.append({'id': p[1] + ':' + '/'.join(p[3:])[:-5], 'type': d.get('type', '?'), 'in': sorted(ins), 'out': sorted(outs), 'jar': jn})
        elif n.endswith('Item.class') and '/item' in n.lower():
            b = z.read(n)
            if b'FoodProperties$Builder' in b or b'net/minecraft/world/food/FoodProperties' in b:
                cls = p[-1][:-10]
                foodclass.setdefault(jn, []).append(CAMEL.sub('_', cls).lower())

# vanilla ru
try:
    idx = json.load(open(os.path.join(ASSETS, 'indexes', '5.json'), encoding='utf-8'))
    h = idx['objects']['minecraft/lang/ru_ru.json']['hash']
    d = json.load(open(os.path.join(ASSETS, 'objects', h[:2], h), encoding='utf-8'))
    for k, v in d.items():
        ru.setdefault(k, v)
except Exception as e:
    print('no vanilla ru', e)

ru.update(rp_ru)
print('s5lang keys', len(rp_ru))
tags = {k: sorted(v) for k, v in tags.items()}
json.dump({'ru': ru, 'en': en, 'tags': tags, 'recipes': recipes, 'models': models, 'foodclass': foodclass, 'nsjar': nsjar, 'mods': sorted(loaded_mods)}, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False)
print(len(ru), len(en), len(tags), len(recipes), len(models), sum(len(v) for v in foodclass.values()))
