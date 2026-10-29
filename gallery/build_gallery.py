# Сборка галереи «Кулинария — разделка и формы»: site/data/cards.json + site/img/*.png (спрайты из jar сборки).
# Данные — gallery/cache/{db,cands}.json (их делают tools/scan.py и tools/analyze.py). Запуск: python -I build_gallery.py
import json, os, re, sys, io, zipfile, collections, glob
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import content as C
import content2 as C2

MODS = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\instances\DEP_S5_rev\minecraft\mods")
VAN = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\libraries\com\mojang\minecraft\1.20.1\minecraft-1.20.1-client.jar")
D = json.load(open(os.path.join(HERE, 'cache', 'db.json'), encoding='utf-8'))
rows = {r['id']: r for r in json.load(open(os.path.join(HERE, 'cache', 'cands.json'), encoding='utf-8'))}
SITE = os.path.join(HERE, 'site')
os.makedirs(os.path.join(SITE, 'img'), exist_ok=True)
os.makedirs(os.path.join(SITE, 'data'), exist_ok=True)

jars = {}
def jar(name):
    if name not in jars:
        jars[name] = zipfile.ZipFile(VAN if name.startswith('minecraft') else os.path.join(MODS, name))
    return jars[name]
ALLJ = [os.path.basename(p) for p in glob.glob(os.path.join(MODS, '*.jar'))]
def read(path, prefer=None):
    for j in ([prefer] if prefer else []) + ['minecraft'] + ALLJ:
        try:
            return jar(j).read(path)
        except KeyError:
            continue
    return None

def name(i):
    ns, p = i.split(':', 1)
    for d in (D['ru'], D['en']):
        for k in ('item.%s.%s' % (ns, p.replace('/', '.')), 'block.%s.%s' % (ns, p.replace('/', '.'))):
            if k in d:
                return d[k].replace('§c', '')
    return ''

def tex_of(model_ref, prefer, depth=0):
    if depth > 6 or not model_ref:
        return None
    ns, p = (model_ref.split(':', 1) + [''])[:2] if ':' in model_ref else ('minecraft', model_ref)
    b = read('assets/%s/models/%s.json' % (ns, p), prefer)
    if not b:
        return None
    try:
        m = json.loads(b.decode('utf-8-sig'))
    except Exception:
        return None
    t = m.get('textures', {})
    for k in ('layer0', 'all', 'front', 'side', 'top', 'texture', 'particle'):
        if k in t and not str(t[k]).startswith('#'):
            return t[k]
    if t:
        v = [x for x in t.values() if not str(x).startswith('#')]
        if v:
            return v[0]
    return tex_of(m.get('parent'), prefer, depth + 1)

def sprite(i):
    out = os.path.join(SITE, 'img', i.replace(':', '__').replace('/', '_') + '.png')
    rel = 'img/' + os.path.basename(out)
    if os.path.exists(out):
        return rel
    ns, p = i.split(':', 1)
    prefer = D['models'].get(i)
    prefer = None if prefer in (None, 'minecraft') else prefer
    t = tex_of('%s:item/%s' % (ns, p), prefer)
    if not t:
        return ''
    tns, tp = t.split(':', 1) if ':' in t else ('minecraft', t)
    b = read('assets/%s/textures/%s.png' % (tns, tp), prefer)
    if not b:
        return ''
    try:
        from PIL import Image
        im = Image.open(io.BytesIO(b)).convert('RGBA')
        if im.height > im.width:
            im = im.crop((0, 0, im.width, im.width))
        im.save(out)
    except ImportError:
        open(out, 'wb').write(b)
    return rel

def item(i):
    r = rows.get(i)
    return {'id': i, 'name': name(i), 'img': sprite(i), 'nin': r['nin'] if r else 0}

cards = []
for x in C.Q:
    cards.append({'kind': 'q', 'group': x['group'], 'id': x['id'], 'title': x['title'], 'text': x['text'],
                  'items': [item(i) for i in x['items']], 'options': x['options'], 'rec': x['rec']})
for grp, lst in C.MEAT_ROLES:
    for i, rec, note in lst:
        it = item(i)
        cards.append({'kind': 'role', 'group': grp, 'id': 'role:' + i, 'title': i, 'text': [note] if note else [],
                      'items': [it], 'options': C.ROLES, 'rec': rec})

# блюда на верстаке
skip = set()
for line in open(os.path.join(HERE, 'tools', 'data.txt'), encoding='utf-8'):
    if line.startswith(('#', '+')) or '|' not in line:
        continue
    skip |= set(line.rstrip('\n').split('|')[-1].split())
KEEPV = {'minecraft:bread', 'minecraft:cake', 'minecraft:pumpkin_pie', 'minecraft:mushroom_stew', 'minecraft:beetroot_soup', 'minecraft:rabbit_stew',
         'minecraft:cookie', 'farmersdelight:pie_crust', 'farmersdelight:apple_pie', 'farmersdelight:kelp_roll', 'refurbished_furniture:cheese'}
NONFOOD = re.compile(r'_dye$|bandage|medkit|soap|flax|boots|helmet|pill|salt|shelf_mushroom|chicken_nest|beer_mug|ice_sickle|candy_cane_pole|package|herb|honeypuff|'
                     r'_can$|canned|tremorzilla_egg|rock_candy|nutriogloop|marshmallow_on|milk_box|milk_bottle|milk_bucket|coffee_beans|_leaf$|licoroot|peppermint_powder|cocoa_beans')
F = set(rows)
recs = collections.defaultdict(list)
for r in D['recipes']:
    if r['type'] not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
        continue
    for o in r['out']:
        if (o in C2.FEAST) or o in F and (o not in skip or o in KEEPV or re.search(r'can', o.split(':')[1])) and not NONFOOD.search(o.split(':')[1]):
            ins = [x for x in r['in'] if x != o]
            if ins:
                recs[o].append(ins)
MOVE = re.compile(r'soup|stew|pie|cake|bread|cookie|cheesecake|grilled|roasted|steak_and|barbecue|pancake|crust|cheese$|primordial|seething')
def inname(x):
    if x.startswith('#'):
        return '#' + x[1:].split(':')[1]
    return name(x) or x
for o in sorted(recs):
    ins = recs[o][0]
    cards.append({'kind': 'craft', 'group': (D['models'].get(o) or 'minecraft').split('-')[0].split('_')[0], 'id': 'craft:' + o, 'title': o,
                  'text': ['Сейчас на верстаке: ' + ' + '.join(sorted(set(inname(x) for x in ins))) + (' (рецептов: %d)' % len(recs[o]) if len(recs[o]) > 1 else '')],
                  'items': [item(o)], 'options': [['keep', 'оставить на верстаке'], ['move', 'в готовку'], ['drop', 'убрать']],
                  'rec': 'move' if MOVE.search(o.split(':')[1]) else 'keep'})

for grp, cid, title, text, ids, rec in C.ANALOG:
    cards.append({'kind': 'analog', 'group': grp, 'id': cid, 'title': title, 'text': text,
                  'items': [item(i) for i in ids], 'options': C.ANALOG_OPTS, 'rec': rec})
for grp, i, text, rec in C2.NORECIPE:
    cards.append({'kind': 'norecipe', 'group': grp, 'id': 'nr:' + i, 'title': i, 'text': [text],
                  'items': [item(i)], 'options': C2.NORECIPE_OPTS, 'rec': rec})
# ------------------------------------------------------------------ группы экзотики и техники (29.10)
sys.path.insert(0, os.path.join(HERE, '..', 'tools'))
import groups_spec as GS
plain = lambda t: t.replace('**', '')
def gitems(entries):
    out = []
    for e in entries:
        ids = [e] if ':' in e else [i for i in rows if i.split(':', 1)[1] == e]
        out += [i for i in ids if i in rows and i not in out]
    return out
for g in GS.GROUPS:
    ids = gitems(g['items'])
    ing, food = [i for i in ids if rows[i]['nin'] > 0], [i for i in ids if rows[i]['nin'] == 0]
    cards.append({'kind': 'group', 'group': 'Группы экзотики', 'id': 'g:' + g['key'], 'title': g['title'],
                  'text': [plain(g['why']), 'Эффект: ' + plain(g['effect']), 'Знание: ' + plain(g['knowledge'])], 'items': [],
                  'cols': [['Ингредиенты', [item(i) for i in ing]], ['Еда (не ингредиент)', [item(i) for i in food]]],
                  'options': [['ok', 'так'], ['edit', 'поправить']], 'rec': 'ok'})
for g in GS.GROUPS:
    for n, qq in enumerate(g.get('ask', [])):
        cards.append({'kind': 'group', 'group': 'Вопросы по группам', 'id': f"gq:{g['key']}:{n}", 'title': g['title'], 'text': [qq],
                      'items': [], 'options': [['yes', 'да'], ['no', 'нет'], ['edit', 'иначе']], 'rec': ''})
cards.append({'kind': 'group', 'group': 'Вопросы по группам', 'id': 'gq:knowledge', 'title': 'Как получать знание группы',
              'text': ['Как было у брата: книга «Записи исследований» (ПКМ — изучить, книга одноразовая, выдаёт ГМ). Без знания повар готовит на 3 уровня хуже, не выше «хорошего», и блюдо чаще травит.',
                       'Оставить так? Или знание — перком в окне перков (например «Охотник», «Грибник»)?'],
              'items': [], 'options': [['book', 'книга, как было'], ['perk', 'перки'], ['both', 'и то и другое'], ['edit', 'иначе']], 'rec': 'book'})
for n, t in enumerate(GS.RECIPES):
    rec = 'free' if t['perk'].startswith('без перка') else 'lock'
    cards.append({'kind': 'tech', 'group': 'Техники по перку', 'id': 'tech:' + str(n), 'title': t['title'],
                  'text': ['Перк: ' + plain(t['perk']), 'Что входит: ' + plain(t['what']), 'Без перка: ' + plain(t['without'])], 'items': [],
                  'options': [['lock', 'нельзя'], ['brak', 'брак'], ['free', 'без перка'], ['edit', 'иначе']], 'rec': rec})
cards.append({'kind': 'tech', 'group': 'Перки и сладости', 'id': 'tech:perks', 'title': 'Новые перки (умения)',
              'text': ['«Винокур» — самогон, пиво, вино, сидр, медовуха.', '«Заготовщик» — консервы, копчение, засолка, вяление.',
                       '«Сыровар» — сыр и масло (или отдать «Заготовщику»).', 'Колбасы и кондитерка — по уровню навыка «Кулинария», без перков.',
                       'Цену в очках предложу после ответа; ориентир — как «Полевой повар» (3).'],
              'items': [], 'options': [['ok', 'так'], ['two', 'сыр в «Заготовщик» (два перка)'], ['edit', 'иначе']], 'rec': 'ok'})
cards.append({'kind': 'tech', 'group': 'Перки и сладости', 'id': 'tech:candy', 'title': 'Сладости: что сейчас и что предлагаю',
              'text': ['Сейчас (сделал брат, работает): после конфеты 10 минут «сонливость» — выносливость восстанавливается на 10 % медленнее; '
                       'и с шансом 25 % за порцию «зуб заныл» — короткая боль (с телом Medicine) или 0,5 сердца урона.',
                       'Предлагаю реалистичнее:',
                       '• сахар сразу даёт прилив: +выносливость на пару минут, потом спад — та же сонливость;',
                       '• «зуб заныл» — только если за RP-сутки съедено много сладкого (например 5+ порций), а с перком «Плохие зубы» — с первой;',
                       '• голод сладкое почти не утоляет (быстро проголодаешься снова).',
                       'Выбери: оставить как есть, моё, или выключить совсем.'],
              'items': [], 'options': [['now', 'как сейчас'], ['mine', 'как предлагаю'], ['off', 'выключить'], ['edit', 'иначе']], 'rec': 'mine'})
json.dump({'decided': C.DECIDED, 'cards': cards}, open(os.path.join(SITE, 'data', 'cards.json'), 'w', encoding='utf-8'), ensure_ascii=False)
miss = sorted({it['id'] for c in cards for it in c['items'] if not it['img']})
unk = sorted({it['id'] for c in cards for it in c['items'] if it['id'] not in D['models'] and not it['id'].startswith('minecraft:')})
print('cards', collections.Counter(c['kind'] for c in cards), 'no sprite', miss, 'unknown', unk)
