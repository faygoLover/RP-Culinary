"""Теги групп экзотики из tools/groups_spec.py (решения Alex 29.10): data/rpculinary/tags/items/exotic/<группа>.json и
effect/<действие>.json. id сверяются по gallery/cache/cands.json (данные сборки). Старые теги удаляются. Запуск: python -I tools/gen_groups.py"""
import json, os, shutil, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import groups_spec as GS
ids = [c['id'] for c in json.load(open(os.path.join(ROOT, 'gallery', 'cache', 'cands.json'), encoding='utf-8'))]
TAGS = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'rpculinary', 'tags', 'items')

def resolve(entries):
    out = []
    for e in entries:
        for i in ([e] if ':' in e else [i for i in ids if i.split(':', 1)[1] == e]):
            if i not in out:
                out.append(i)
    return out

def write(rel, values):
    p = os.path.join(TAGS, *rel.split('/')) + '.json'
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'replace': False, 'values': [{'id': v, 'required': False} for v in values]}, f, ensure_ascii=False, indent=1)
        f.write('\n')

for d in ('exotic', 'effect'):
    shutil.rmtree(os.path.join(TAGS, d), ignore_errors=True)
n = 0
for g in GS.GROUPS:
    v = resolve(g['items'])
    write('exotic/' + g['key'], v)
    n += len(v)
for k, entries in GS.EFFECTS.items():
    write('effect/' + k, resolve(entries))
print('groups', len(GS.GROUPS), 'items', n, 'effects', len(GS.EFFECTS))
