import json, os, glob, collections, re, sys
D = json.load(open('db.json', encoding='utf-8'))
ru, en, models = D['ru'], D['en'], D['models']
rows = {r['id']: r for r in json.load(open('cands.json', encoding='utf-8'))}
NUT = r"C:\Users\Alex\Desktop\Claude Code\RP Culinary\src\main\resources\data\rpculinary\rpculinary\nutrition"
rpc = {}
for f in glob.glob(os.path.join(NUT, '*.json')):
    for i in json.load(open(f, encoding='utf-8')).get('items', []):
        rpc[i] = os.path.basename(f)[:-5]

FIX = {'soviet:misc/sugar': 'Сахар', 'soviet:misc/sugar2': 'Сахар (2)', 'soviet:misc/condensed_milk': 'Сгущёнка'}
def name(i):
    if i in FIX:
        return FIX[i]
    ns, p = i.split(':', 1)
    p = p.replace('/', '.')
    for d, suf in ((ru, ''), (en, ' *(en)*')):
        for k in ('item.%s.%s' % (ns, p), 'block.%s.%s' % (ns, p)):
            if k in d:
                return d[k].replace('|', '/').replace('§c', '') + suf
    return '—'

MOD = {'minecraft': 'ваниль', 'farmersdelight': "Farmer's Delight", 'farm_and_charm': 'Farm & Charm', 'butchery': 'Butchery', 'candlelight': 'Candlelight',
       'brewery': 'Brewery', 'vinery': 'Vinery', 'herbalbrews': 'HerbalBrews', 'alexscaves': "Alex's Caves", 'alexsmobs': "Alex's Mobs",
       'refurbished_furniture': 'Refurbished Furniture', 'sa_combat': 'SA Essentials', 'survival_instinct': 'Survival Instinct', 'zombie_extreme': 'Zombie Extreme',
       'legendarysurvivaloverhaul': 'Legendary Survival Overhaul', 'csfoundation': 'CS Foundation', 'csaugmentations': 'CS Augmentations', 'scpo': 'SCP: Overtime',
       'fl_zone_arts': 'fl_zone_arts', 'stardew_fishing': 'Stardew Fishing', 'biomesoplenty': "Biomes O' Plenty", 'blocksyouneed_luna': 'Blocks You Need',
       'mekanism': 'Mekanism', 'quark': 'Quark', 'supplementaries': 'Supplementaries', 'camping': 'Camping', 'immersive_weathering': 'Immersive Weathering', 'soviet': 'SovietEra'}

SECT = [('meat', '1. Мясо (по видам животных)'), ('offal', '1а. Субпродукты и кровь'), ('flesh', '1б. Плоть и прочее «мясо»'),
        ('fish', '2. Рыба'), ('sea', '2а. Морепродукты'), ('bird', '3. Птица'), ('eggs', '4. Яйца'), ('dairy', '5. Молочное и сыр'),
        ('grain', '6. Зерно, мука и тесто'), ('veg', '7. Овощи'), ('fruit', '8. Фрукты и ягоды'), ('mush', '9. Грибы'), ('nuts', '10. Орехи, семена, бобы'),
        ('herbs', '11. Травы и специи'), ('sweet', '12. Сахар, мёд и сладкое'), ('fat', '13. Жиры и масла'), ('sauce', '14. Соусы'),
        ('drink', '15. Напитки как ингредиенты'), ('misc', '16. Прочее (дрожжи, полуфабрикаты)')]
MCOL = {'meat': ['сырое', 'отрубы/части', 'фарш', 'котлета', 'сосиска', 'бекон/ветчина', 'копчёное', 'жареное', 'бульон'],
        'fish': ['сырое', 'отрубы/части', 'фарш', 'котлета', 'копчёное', 'солёное', 'жареное', 'консервы', 'бульон']}
MCOL['bird'] = MCOL['meat']; MCOL['sea'] = MCOL['fish']
TIER = {'meat': {'A': ['отрубы/части', 'фарш', 'котлета', 'сосиска', 'копчёное', 'бульон'], 'B': ['фарш', 'копчёное', 'бульон'], 'C': ['фарш'], 'X': []},
        'fish': {'A': ['отрубы/части', 'фарш', 'котлета', 'копчёное', 'солёное', 'бульон'], 'B': ['отрубы/части', 'копчёное', 'солёное', 'жареное'], 'C': ['жареное']},
        'sea': {'B': ['жареное', 'консервы'], 'C': ['жареное']}}
TIER['bird'] = TIER['meat']

prod = collections.OrderedDict()   # (sec, product) -> {'origin', 'tier', 'forms': OrderedDict(form -> [ids]), 'props': [(form, text)]}
for line in open('data.txt', encoding='utf-8'):
    line = line.rstrip('\n')
    if not line or line.startswith('#'):
        continue
    if line.startswith('+'):
        sec, p, form, text = line[1:].split('|', 3)
        prod.setdefault((sec, p), {'origin': '', 'tier': '', 'forms': collections.OrderedDict(), 'props': []})['props'].append((form, text))
        continue
    sec, p, origin, tier, form, ids = line.split('|')
    e = prod.setdefault((sec, p), {'origin': '', 'tier': '', 'forms': collections.OrderedDict(), 'props': []})
    if origin:
        e['origin'] = origin
    if tier:
        e['tier'] = tier
    if form != '—':
        e['forms'].setdefault(form, []).extend(ids.split())
    else:
        e['empty'] = True

used = set()
warn = []
for (sec, p), e in prod.items():
    for f, ids in e['forms'].items():
        for i in ids:
            used.add(i)
            if i not in rows and i not in models:
                warn.append(i)
# matrix proposals
for (sec, p), e in prod.items():
    if sec in MCOL:
        t = TIER[sec].get(e['tier'], [])
        want = list(t)
        if e.get('empty'):
            want = ['сырое', 'жареное'] + want
        e['mprops'] = [c for c in want if c not in e['forms']]

def outs(i):
    r = rows.get(i)
    if not r:
        return ''
    ns = []
    for o in r['outs']:
        n = name(o).replace(' *(en)*', '')
        if n not in ns and n != '—':
            ns.append(n)
    return ', '.join(ns[:3])

def nin(i):
    r = rows.get(i)
    return r['nin'] if r else 0

out = []
W = out.append
stats = collections.OrderedDict()
for sec, title in SECT:
    items = [(k, e) for k, e in prod.items() if k[0] == sec]
    np = sum(1 for k, e in items if e['forms'])
    nf = sum(len(e['forms']) for k, e in items)
    ni = sum(len(ids) for k, e in items for ids in e['forms'].values())
    nprop = sum(len(e.get('mprops', [])) + len(e['props']) for k, e in items)
    stats[sec] = (title, np, nf, ni, nprop)

def section(sec, title):
    items = [(k[1], e) for k, e in prod.items() if k[0] == sec]
    W('## %s\n' % title)
    if sec in MCOL:
        cols = MCOL[sec]
        W('**Матрица форм.** ✓N — есть (N предметов), **🟩** — предлагаю добавить, «·» — не нужно/нет смысла.\n')
        W('| Продукт | Откуда / биом | ' + ' | '.join(cols) + ' |')
        W('|---|---|' + '---|' * len(cols))
        for p, e in items:
            cells = []
            for c in cols:
                if c in e['forms']:
                    cells.append('✓%d' % len(e['forms'][c]))
                elif c in e.get('mprops', []):
                    cells.append('**🟩**')
                else:
                    cells.append('·')
            W('| %s | %s | %s |' % (p, e['origin'], ' | '.join(cells)))
        W('')
        W('**Что есть, по предметам:**\n')
    W('| Продукт | Откуда / биом | Форма | ID | Название в игре | Мод | Рецептов | Идёт в (примеры) | Группа RPC |')
    W('|---|---|---|---|---|---|---|---|---|')
    for p, e in items:
        first = True
        for f, ids in e['forms'].items():
            for i in ids:
                W('| %s | %s | %s | `%s` | %s | %s | %s | %s | %s |' % (
                    ('**%s**' % p) if first else '', e['origin'] if first else '', f, i, name(i), MOD.get(i.split(':')[0], i.split(':')[0]),
                    nin(i) or '—', outs(i) or '—', rpc.get(i, '—')))
                first = False
        if e.get('empty') and first:
            W('| **%s** | %s | — | — | предметов нет | — | — | — | — |' % (p, e['origin']))
            first = False
        for f, text in e['props']:
            W('| %s | %s | **🟩 %s** | — | — | — | — | **%s** | — |' % (('**%s**' % p) if first else '', e['origin'] if first else '', f, text or 'предложение'))
            first = False
    W('')

# ---------- header & summary
tot_p = sum(s[1] for s in stats.values()); tot_f = sum(s[2] for s in stats.values()); tot_i = sum(s[3] for s in stats.values()); tot_x = sum(s[4] for s in stats.values())
H = []
H.append('# Ингредиенты еды в сборке S5 и их формы (28.10.2026)\n')
H.append('Справочник для обсуждения переделки RP Culinary «своим путём». Собран скриптом по модам инстанса `DEP_S5_rev` (257 jar + ваниль 1.20.1): '
         'названия из `lang`, рецепты из `data/*/recipes` всех модов (верстак, печь, коптильня, костёр, доска и кастрюля FD, мясорубка/плита/жаровня Let\'s Do, '
         'Butchery, Refurbished Furniture, Vinery, Brewery, HerbalBrews и др.), теги развёрнуты. Код модов не трогал.\n')
H.append('**Как читать.** «Рецептов» — во сколько рецептов с едой на выходе предмет входит как ингредиент (с учётом тегов: если рецепт просит `#forge:raw_beef`, '
         'засчитывается каждому сырому мясу из тега; в счёт входят и корма для животных FD/F&C). «Группа RPC» — в каком файле `nutrition/*.json` нашей кулинарии '
         'предмет задан явно; «—» значит, что питательность считается по рецепту (`NutritionTable`) или не задана. **🟩 жирным** — мои предложения, чего не хватает.\n')
H.append('## Сводка\n')
H.append('| Группа | Продуктов | Форм | Предметов (id) | 🟩 предложений |')
H.append('|---|---|---|---|---|')
for sec, (title, np, nf, ni, nprop) in stats.items():
    H.append('| %s | %d | %d | %d | %d |' % (title, np, nf, ni, nprop))
H.append('| **Всего** | **%d** | **%d** | **%d** | **%d** |' % (tot_p, tot_f, tot_i, tot_x))
H.append('')
H.append(open('summary.md', encoding='utf-8').read())
for sec, title in SECT:
    section(sec, title)
out = H + out + [open('tail.md', encoding='utf-8').read()]
# leftovers: food items not classified
left = [r for r in rows.values() if r['id'] not in used]
lin = sorted([r for r in left if r['nin']], key=lambda r: -r['nin'])
lz = collections.Counter(r['id'].split(':')[0] for r in left if not r['nin'])
txt = '\n'.join(out)
txt = txt.replace('%%READY%%', ', '.join('%s — %d' % (MOD.get(k, k), v) for k, v in lz.most_common()))
open(sys.argv[1], 'w', encoding='utf-8').write(txt)
print('products', tot_p, 'forms', tot_f, 'items', tot_i, 'props', tot_x)
print('WARN unknown ids:', warn)
print('UNCLASSIFIED with nin>0:', [(r['id'], r['nin']) for r in lin])
