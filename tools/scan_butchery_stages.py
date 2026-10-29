"""Стадии туш Butchery из декомпиляции (_mcref/mods/butchery-5.1): блок → стадия → таблица разреза, следующая стадия, инструмент.
Результат — tools/butchery_stages.json (его читает gen_food_pack.py). Перезапускать при обновлении Butchery."""
import json, os, re, sys
sys.stdout.reconfigure(encoding='utf-8')
SRC = os.path.join(os.path.dirname(__file__), '..', '..', '_mcref', 'mods', 'butchery-5.1', 'net', 'mcreator', 'butchery')
names = {}
for m in re.finditer(r'RegistryObject<Block> (\w+) = REGISTRY\.register\("(\w+)"', open(os.path.join(SRC, 'init', 'ButcheryModBlocks.java'), encoding='utf-8').read()):
    names[m.group(1)] = m.group(2)
res = {}
pdir = os.path.join(SRC, 'procedures')
CHECK = re.compile(r'm_60734_\(\) == ButcheryModBlocks\.(\w+)\.get\(\)\s*&&\s*\(.{0,400}?\)\s*==\s*(\d+)\)', re.S)
for fn in sorted(os.listdir(pdir)):
    if 'cutup' not in fn.lower():
        continue
    s = open(os.path.join(pdir, fn), encoding='utf-8').read()
    hits = list(CHECK.finditer(s))
    for k, m in enumerate(hits):
        end = hits[k + 1].start() if k + 1 < len(hits) else len(s)
        body = s[m.end():end]
        # инструмент: ближайшие теги перед проверкой
        before = s[max(0, m.start() - 3000):m.start()]
        tools = re.findall(r'parse\("forge:(\w+)"\)', before)
        tool = tools[-1] if tools else '?'
        tab = re.search(r'parse\("(?:butchery:blocks/)?(?!organs)(\w+?)_drop"\)', body)
        nxt = re.search(r'int _value = (\d+);', body)
        air = 'Blocks.f_50016_' in body[:3000]
        blk = names.get(m.group(1), m.group(1).lower())
        res.setdefault(blk, {})[m.group(2)] = {'table': tab.group(1) if tab else None,
                                               'next': 'air' if air and not (nxt and body.find(nxt.group(0)) < body.find('Blocks.f_50016_')) else (int(nxt.group(1)) if nxt else None),
                                               'tool': tool, 'proc': fn[:-5]}
out = os.path.join(os.path.dirname(__file__), 'butchery_stages.json')
carcass = sorted(n for n in names.values() if re.search(r'carcass|corpse|villager|^pufferfish$', n) and 'head' not in n and 'skull' not in n)
json.dump({'stages': res, 'blocks': carcass}, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1, sort_keys=True)
for b in ('drained_cow_carcass', 'drained_pig_carcass', 'hanging_cow_carcass', 'hanging_pig_carcass'):
    print(b, json.dumps(res.get(b), ensure_ascii=False))
print(len(res), 'blocks;', sorted(k for k in res if 'cow' in k or 'pig' in k or 'mooshroom' in k))
