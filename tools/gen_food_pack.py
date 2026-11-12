# Генератор встроенного датапака RP Culinary «поверх всех» (resourcepacks/rpculinary_food) — блок 1 плана
# docs/02_plan_rework_2026-10-29.md: теги, разделка туш Butchery (K = 0.25, вес крупных кусков в NBT), дроп мобов,
# отключение ванильного мяса и окорока FD, рецепты (tools/recipes_spec.py).
# Данные сборки — gallery/cache/db.json (gallery/tools/scan.py). Запуск: python -I tools/gen_food_pack.py
import json, os, re, sys, glob, zipfile, shutil
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACK = os.path.join(ROOT, 'src', 'main', 'resources', 'resourcepacks', 'rpculinary_food')
DATA = os.path.join(PACK, 'data')
MODS = os.path.expandvars(r"D:\ElyPrismLauncher\instances\DEP_S5_rev\minecraft\mods")
VAN = os.path.expandvars(r"%APPDATA%\ElyPrismLauncher\libraries\com\mojang\minecraft\1.20.1\minecraft-1.20.1-client.jar")
D = json.load(open(os.path.join(ROOT, 'gallery', 'cache', 'db.json'), encoding='utf-8'))
K = 0.25
written = []
if os.path.isdir(DATA):
    shutil.rmtree(DATA)  # пак целиком из генератора — старое не остаётся

def read_pack(rel):
    return json.load(open(os.path.join(DATA, *rel.split('/')), encoding='utf-8'))

def out(rel, obj):
    # Записи тегов — необязательные: нет мода/предмета — тег не ломается целиком
    if '/tags/' in rel and isinstance(obj.get('values'), list):
        obj = dict(obj, values=[v if isinstance(v, dict) else {'id': v, 'required': False} for v in obj['values']])
    p = os.path.join(DATA, *rel.split('/'))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')
    written.append(rel)

JARS = [VAN] + sorted(glob.glob(os.path.join(MODS, '*.jar')))
def read_json(path):
    for j in JARS:
        try:
            return json.loads(zipfile.ZipFile(j).read(path).decode('utf-8-sig'))
        except KeyError:
            continue
    return None

# ------------------------------------------------------------------ теги
BIG = {'butchery:raw_chuck_steak', 'butchery:raw_rump_steak', 'butchery:raw_pork_belly', 'butchery:raw_pork_shoulder',
       'butchery:raw_lamb_shoulder', 'butchery:raw_leg_of_lamb', 'candlelight:beef_tartare', 'farmersdelight:ham',
       'butchery:liver', 'butchery:heart', 'butchery:lungs', 'butchery:stomach', 'butchery:brain'}
MEAT_TAGS = ['forge:raw_beef', 'c:raw_beef', 'candlelight:raw_beef', 'butchery:raw_beef', 'forge:raw_pork', 'c:raw_pork',
             'butchery:raw_pork', 'forge:raw_mutton', 'c:raw_mutton', 'butchery:raw_mutton', 'forge:raw_meat', 'c:raw_meat',
             'butchery:raw_meat', 'butchery:raw_meats', 'butchery:meat', 'butchery:meats']
for t in MEAT_TAGS:
    vals = D['tags'].get(t)
    if vals is None:
        continue
    keep = [v for v in vals if v not in BIG]
    ns, path = t.split(':', 1)
    out(f'{ns}/tags/items/{path}.json', {'replace': True, 'values': keep})

def add_tag(t, values, replace=False):
    ns, path = t.split(':', 1)
    out(f'{ns}/tags/items/{path}.json', {'replace': replace, 'values': values})

add_tag('forge:vegetables/tomato', ['farm_and_charm:tomato'])
add_tag('forge:vegetables/onion', ['farm_and_charm:onion'])
add_tag('forge:seeds/tomato', ['farm_and_charm:tomato_seeds'])
# Соль: каменная соль (кристалл) — не соль, её толкут в ступке; блок соли CS — не соль
add_tag('forge:salt', ['#forge:dusts/salt', 'butchery:salt', 'refurbished_furniture:sea_salt'], replace=True)
add_tag('forge:cheeses', ['refurbished_furniture:cheese', 'sa_combat:cheese'])
add_tag('rpculinary:mince', ['farm_and_charm:minced_beef', 'butchery:raw_beef_mince', 'butchery:raw_lamb_mince'])
add_tag('rpculinary:sausage', ['butchery:raw_sausage', 'butchery:raw_blood_sausage'])
add_tag('rpculinary:big_piece', sorted(BIG - {'candlelight:beef_tartare', 'farmersdelight:ham'}))
# Скрытые дубли (JEI/креатив — код RP Culinary)
add_tag('rpcore:jei_hidden', ['farmersdelight:ham', 'butchery:raw_leg_of_lamb', 'sa_combat:burger', 'butchery:cooked_leg_of_lamb',
                              'butchery:cooked_chuck_steak', 'butchery:cooked_rump_steak', 'butchery:cooked_pork_belly',
                              'butchery:cooked_pork_shoulder', 'butchery:cooked_lamb_shoulder'])

# ------------------------------------------------------------------ разделка туш Butchery
def entry(item, n=1, weight=None, source=None, chance=None):
    """Пул с одним предметом: n штук (число или (min,max)), вес в NBT (г) — у крупных кусков и органов."""
    e = {'type': 'minecraft:item', 'name': item, 'functions': []}
    lo, hi = (n, n) if isinstance(n, int) else n
    e['functions'].append({'function': 'minecraft:set_count', 'count': lo if lo == hi else {'min': lo, 'max': hi}})
    if weight:
        tag = {'rpculinary:weight': int(weight)}
        if source:
            tag['rpculinary:source'] = source
        e['functions'].append({'function': 'minecraft:set_nbt', 'tag': json.dumps(tag, separators=(',', ':'))})
    pool = {'rolls': 1, 'entries': [e]}
    if chance is not None and chance < 1:
        pool['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': round(chance, 3)}]
    return pool

# Потери по навыку (блок 3): функция rpculinary:skill_yield на мясе и органах. Не теряются кости, жир, шкура, копыта, обрезки.
NO_LOSS = {'minecraft:bone', 'butchery:animal_fat', 'butchery:hoof', 'butchery:meat_scraps', 'butchery:bird_foot', 'butchery:wishbone',
           'minecraft:brown_mushroom', 'minecraft:red_mushroom', 'minecraft:bone_meal'}
WEIGHTS = json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'weights.json'), encoding='utf-8'))['items']
def with_yield(pools):
    for p in pools:
        for e in p.get('entries', []):
            n = e.get('name')
            if e.get('type') == 'minecraft:item' and n not in NO_LOSS and n in WEIGHTS and                     not any(f.get('function') == 'rpculinary:skill_yield' for f in e.get('functions', [])):
                e.setdefault('functions', []).append({'function': 'rpculinary:skill_yield'})
    return pools

# Доп. разрезы между «родными» стадиями туши (решение 28.10 §5.6): таблица разреза → список своих таблиц, которые
# выпадают ДО неё (каждая — отдельный клик колуном; «родной» разрез — последним и двигает стадию).
EXTRA = {}
TABLE_ITEMS = {}  # таблица (имя без _drop) → предметы: для порога нарезки на доске по виду
def note_items(name, pools):
    TABLE_ITEMS.setdefault(name, set()).update(e.get('name') for p in pools for e in p.get('entries', []) if e.get('type') == 'minecraft:item')

def extra(native, name, pools):
    note_items(native, pools)
    EXTRA.setdefault(native, []).append(f'rpculinary:carcass/{name}')
    out(f'rpculinary/loot_tables/carcass/{name}.json', {'type': 'minecraft:block', 'pools': with_yield(pools),
                                                        'random_sequence': f'rpculinary:carcass/{name}'})

FD_COMPAT = {'pig_cut_1_drop', 'pig_cut_3_drop', 'goat_cut_1_drop', 'goat_cut_3_drop'}
def table(name, pools):
    pools = with_yield(pools)
    note_items(name[:-len('_drop')] if name.endswith('_drop') else name, pools)
    if name in FD_COMPAT:  # вариант Butchery при «Farmer's Delight» в конфиге — то же самое
        out(f'butchery/loot_tables/blocks/{name}_fd_compat.json', {'type': 'minecraft:block', 'pools': pools,
                                                                    'random_sequence': f'butchery:blocks/{name}_fd_compat'})
    out(f'butchery/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': pools,
                                                     'random_sequence': f'butchery:blocks/{name}'})

def organs(src, live):
    """Органы по весу туши × K: крупные — один кусок с весом в NBT, мелкие (<60 г) — с шансом."""
    w = lambda frac: live * 1000 * frac * K
    res = []
    for item, frac in (('butchery:liver', 0.010), ('butchery:heart', 0.004), ('butchery:lungs', 0.007), ('butchery:stomach', 0.02)):
        g = w(frac)
        res.append(entry(item, 1, weight=g, source=src) if g >= 60 else entry(item, 1, weight=60, source=src, chance=g / 60))
    kid = w(0.0012)
    res.append(entry('butchery:kidney', 2) if kid >= 60 else entry('butchery:kidney', 1, chance=kid * 2 / 60))
    res.append(entry('butchery:intestines', max(1, min(8, round(live / 60)))))
    return res

def pieces(item, kg, src, piece_kg=5.0):
    """Крупное мясо: куски по ~piece_kg (вес в NBT). Мало мяса — порции по 250 г без NBT."""
    if kg < 1.0:
        return [entry(item, max(1, round(kg / 0.25)))]
    n = max(1, round(kg / piece_kg))
    return [entry(item, 1, weight=kg / n * 1000, source=src) for _ in range(n)]

def scraps(kg):
    return [entry('butchery:meat_scraps', max(1, round(kg / 0.25)))] if kg > 0.1 else []

def fat(kg):
    return [entry('butchery:animal_fat', max(1, round(kg / 0.25)))] if kg > 0.1 else []

def bones(n):
    return [entry('minecraft:bone', n)] if n else []

def hooves(n):
    return [entry('butchery:hoof', n)]

# Подробные планы (галерея «Разделка по тушам»)
def split(pools, items):
    """Пулы с этими предметами — отдельно (первый список), остальные — второй."""
    a = [p for p in pools if any(e.get('name') in items for e in p['entries'])]
    return a, [p for p in pools if p not in a]

def cow(name, more=()):
    # брюшина → грудь (родной 1) | передняя четверть → середина (родной 2) | задняя часть → голяшки, обрезки (родной 3)
    belly, chest = split(organs('cow', 500), {'butchery:stomach', 'butchery:intestines'})
    extra(f'{name}_cut_1', f'{name}_belly', belly)
    table(f'{name}_cut_1_drop', chest + hooves(2))
    extra(f'{name}_cut_2', f'{name}_forequarter', [entry('butchery:raw_chuck_steak', 1, weight=6000, source='cow') for _ in range(2)])
    table(f'{name}_cut_2_drop', [entry('butchery:raw_ribeye_steak', 4), entry('butchery:raw_sirloin_steak', 4)] + list(more))
    extra(f'{name}_cut_3', f'{name}_hindquarter', [entry('butchery:raw_rump_steak', 1, weight=5000, source='cow') for _ in range(2)]
          + [entry('butchery:raw_tbone_steak', 4)])
    table(f'{name}_cut_3_drop', scraps(8) + fat(5) + bones(8) + hooves(2))
cow('cow')
cow('brownmooshroom', [entry('minecraft:brown_mushroom', 2)])
cow('redmooshroom', [entry('minecraft:red_mushroom', 2)])

def pig(name, hanging=None):
    # органы (родной 1) | лопатка, рулька → корейка, грудинка, шпик (родной 2) | окорок → шпик/жир, обрезки, кости (родной 3)
    cut2 = [entry('butchery:raw_pork_loin', 4), entry('butchery:raw_pork_belly', 1, weight=2000, source='pig'), entry('farm_and_charm:bacon', 8)]
    cut3 = fat(2.5) + scraps(1.5) + bones(4) + hooves(4)
    table(f'{name}_cut_1_drop', organs('pig', 110))
    extra(f'{name}_cut_2', f'{name}_shoulders', [entry('butchery:raw_pork_shoulder', 1, weight=1200, source='pig') for _ in range(2)]
          + [entry('butchery:raw_pork_leg', 4)])
    table(f'{name}_cut_2_drop', cut2)
    extra(f'{name}_cut_3', f'{name}_hams', [entry('butchery:raw_ham', 2)])
    table(f'{name}_cut_3_drop', cut3)
    if hanging:  # у подвешенных пиглинов свои таблицы 2 и 3 — те же разрезы
        h2, h3 = hanging
        table(f'{h2}_drop', json.loads(json.dumps(cut2)))
        table(f'{h3}_drop', json.loads(json.dumps(cut3)))
        EXTRA[h2], EXTRA[h3] = EXTRA[f'{name}_cut_2'], EXTRA[f'{name}_cut_3']
pig('pig')
pig('piglin', ('piglin_hanging_cut_2', 'piglin_hanging_cut_3'))
pig('piglin_brute', ('hanging_piglin_brute_cut_2', 'hanging_piglin_brute_cut_3'))
# Butchery: подвешенная корова берёт «cow_cut_1_drop» без пространства имён (= minecraft:) — их же ошибка; кладём копию
out('minecraft/loot_tables/cow_cut_1_drop.json', read_pack('butchery/loot_tables/blocks/cow_cut_1_drop.json'))

def sheep(name, src):
    table(f'{name}_cut_1_drop', organs(src, 50) + [entry('butchery:raw_lamb_shoulder', 1, weight=500, source=src) for _ in range(2)] + hooves(2))
    table(f'{name}_cut_2_drop', [entry('butchery:raw_lamb_rib', 1), entry('butchery:raw_lamb_loin', 1), entry('butchery:raw_lamb_sirloin', 1)])
    table(f'{name}_cut_3_drop', [entry('farm_and_charm:lamb_ham', 2)] + scraps(0.5) + fat(0.75) + bones(2) + hooves(2))
sheep('sheep', 'sheep')
sheep('goat', 'goat')

# Курица и кролик — мелочь, без уменьшения
table('chicken_cut_1_drop', [entry('butchery:raw_chicken_leg', 2), entry('butchery:bird_foot', 2)])
table('chicken_cut_2_drop', [entry('butchery:raw_chicken_wing', 2), entry('butchery:heart', 1, weight=60, source='chicken', chance=0.3),
                             entry('butchery:liver', 1, weight=60, source='chicken', chance=0.5), entry('butchery:wishbone', 1, chance=0.25)])
table('chicken_cut_3_drop', [entry('minecraft:chicken', 1), entry('butchery:animal_fat', 1, chance=0.6), entry('minecraft:bone', 1)])
table('rabbit_cut_1_drop', [entry('butchery:liver', 1, weight=60, source='rabbit', chance=0.4)])
table('rabbit_cut_2_drop', [entry('minecraft:rabbit', 1)])
table('rabbit_cut_3_drop', [entry('butchery:animal_fat', 1, chance=0.4), entry('minecraft:bone', 1)])

# Дичь и экзотика: (имя таблицы, мясо, источник, живой вес кг, хвосты: доп. пулы для разреза 3)
GAME = [
    ('horse', 'butchery:raw_horse_meat', 'horse', 450), ('donkey', 'butchery:raw_donkey_steak', 'donkey', 200),
    ('mule', 'butchery:raw_mule_steak', 'mule', 350), ('llama', 'butchery:raw_llama_steak', 'llama', 130),
    ('camel', 'butchery:raw_camel_meat', 'camel', 500), ('wolf', 'butchery:raw_wolf_meat', 'wolf', 40),
    ('fox', 'butchery:raw_fox_meat', 'fox', 6), ('polar_bear_carcass', 'butchery:raw_polar_bear_meat', 'polar_bear', 400),
    ('panda', 'butchery:raw_panda_steak', 'panda', 100), ('ocelot', 'butchery:raw_ocelot_meat', 'ocelot', 12),
    ('skinned_cat', 'butchery:raw_cat_meat', 'cat', 4), ('hoglin', 'butchery:raw_hoglin_chunk', 'hoglin', 150),
    ('strider', 'butchery:raw_strider_meat', 'strider', 100), ('sniffer', 'butchery:raw_sniffer_steak', 'sniffer', 800),
    ('dolphin', 'butchery:raw_dolphin_meat', 'dolphin', 150), ('creeper', 'butchery:raw_creeper_steak', 'creeper', 60),
    ('guardian', 'butchery:raw_guardian_meat', 'guardian', 50), ('villager', 'butchery:raw_villager_steak', 'villager', 70),
    ('pillager', 'butchery:raw_pillager_meat', 'pillager', 70), ('vindicator', 'butchery:raw_vindicator_meat', 'vindicator', 70),
    ('evoker', 'butchery:raw_evoker_meat', 'evoker', 70), ('witch', 'butchery:raw_witch_meat', 'witch', 70),
    ('warden', 'butchery:raw_warden_meat', 'warden', 1000),
]
for name, meat, src, live in GAME:
    old = {i: read_json(f'data/butchery/loot_tables/blocks/{name}_cut_{i}_drop.json') for i in (1, 2, 3)}
    kg = live * 0.38 * K
    # не-мясное из старых таблиц (костная мука, шипы, скелет и т. п.) сохраняем
    keep = {i: [p for p in (old[i] or {}).get('pools', []) if not any(
        e.get('name') in (meat, 'butchery:animal_fat', 'minecraft:bone') for e in p.get('entries', []))] for i in (1, 2, 3)}
    table(f'{name}_cut_1_drop', organs(src, live) + keep[1])
    table(f'{name}_cut_2_drop', pieces(meat, kg * 0.85, src) + keep[2])
    table(f'{name}_cut_3_drop', scraps(kg * 0.15) + fat(live * 0.04 * K) + bones(max(1, round(live / 60))) + keep[3])

# Органы — теперь в таблицах видов; общие таблицы органов пусты (иначе у коровы и курицы одинаковые)
for i in (1, 2, 3):
    table(f'organs_drop_{i}', [])

# ------------------------------------------------------------------ дроп мобов
VANILLA_MEAT = {'minecraft:beef', 'minecraft:porkchop', 'minecraft:mutton', 'minecraft:chicken', 'minecraft:rabbit',
                'minecraft:cooked_beef', 'minecraft:cooked_porkchop', 'minecraft:cooked_mutton', 'minecraft:cooked_chicken', 'minecraft:cooked_rabbit'}
for ent in ('cow', 'mooshroom', 'pig', 'sheep', 'chicken', 'rabbit', 'hoglin'):
    lt = read_json(f'data/minecraft/loot_tables/entities/{ent}.json')
    if lt is None:
        continue
    lt['pools'] = [p for p in lt.get('pools', []) if not any(e.get('name') in VANILLA_MEAT for e in p.get('entries', []))]
    out(f'minecraft/loot_tables/entities/{ent}.json', lt)
# у овцы шерсть — подтаблицы; у курицы перья — остаются

# Окорок FD с ножа — выключить (условие, которое никогда не выполнится)
for gm in ('scavenging_ham_from_pig', 'scavenging_ham_from_hoglin', 'scavenging_smoked_ham_from_pig', 'scavenging_smoked_ham_from_hoglin'):
    j = read_json(f'data/farmersdelight/loot_modifiers/{gm}.json')
    if j:
        j['conditions'] = list(j.get('conditions', [])) + [{'condition': 'minecraft:random_chance', 'chance': 0.0}]
        out(f'farmersdelight/loot_modifiers/{gm}.json', j)

# Alex's Mobs / Alex's Caves — без туш: сразу крупный кусок с весом (живой вес × 0.38 × K)
def big_drop(path, meat, src, live, piece_kg=5.0, cap=20):
    lt = read_json(path)
    if not lt:
        return
    kg = live * 0.38 * K
    n = max(1, min(cap, round(kg / piece_kg)))
    w = int(kg / n * 1000)
    for p in lt.get('pools', []):
        for e in p.get('entries', []):
            if e.get('name') == meat:
                e['functions'] = [f for f in e.get('functions', []) if 'count' not in f.get('function', '') and 'looting' not in f.get('function', '')]
                e['functions'] = [{'function': 'minecraft:set_count', 'count': n},
                                  {'function': 'minecraft:set_nbt', 'tag': json.dumps({'rpculinary:weight': w, 'rpculinary:source': src}, separators=(',', ':'))}]
    out(path[len('data/'):], lt)
big_drop('data/alexsmobs/loot_tables/entities/kangaroo.json', 'alexsmobs:kangaroo_meat', 'kangaroo', 60)
big_drop('data/alexsmobs/loot_tables/entities/moose.json', 'alexsmobs:moose_ribs', 'moose', 500)
for ent, live in (('grottoceratops', 1000), ('relicheirus', 1500), ('tremorsaurus', 3000), ('atlatitan', 20000)):
    big_drop(f'data/alexscaves/loot_tables/entities/{ent}.json', 'alexscaves:dinosaur_chop', 'dinosaur', live)

# ------------------------------------------------------------------ туши для кода (блок 3): уровень, вес, доп. разрезы
# Уровни разделки 1–10 — решения Alex 29.10 (§6). Живой вес, кг; в переноске и в XP — × K (мелочь до 5 кг — без K).
SPECIES = [  # (ключ в id блока, вид, уровень, живой вес) — первый подходящий
    ('elder_guardian', 'elder_guardian', 8, 200), ('ender_dragon', 'ender_dragon', 10, 5000), ('cave_spider', 'cave_spider', 6, 20),
    ('polar_bear', 'polar_bear', 5, 400), ('zombie_horse', 'horse', 5, 450), ('skeleton_horse', 'skeleton_horse', 3, 30),
    ('zombie_villager', 'villager', 7, 70), ('zombified_piglin', 'piglin', 7, 80), ('piglin_brute', 'piglin', 7, 100),
    ('wither_skeleton', 'skeleton', 3, 15), ('glow_squid', 'squid', 3, 20), ('mooshroom', 'cow', 5, 500), ('snowfox', 'fox', 3, 6),
    ('snow_fox', 'fox', 3, 6), ('playercorpse', 'human', 7, 70), ('sniffer', 'sniffer', 8, 800), ('warden', 'warden', 9, 1000),
    ('wither', 'wither', 10, 300), ('chicken', 'chicken', 1, 2), ('rabbit', 'rabbit', 1, 2), ('cod', 'fish', 1, 2), ('salmon', 'fish', 1, 3),
    ('pufferfish', 'fish', 1, 1), ('squid', 'squid', 3, 20), ('sheep', 'sheep', 3, 50), ('goat', 'goat', 3, 50), ('pig', 'pig', 3, 110),
    ('fox', 'fox', 3, 6), ('cat', 'cat', 3, 4), ('ocelot', 'ocelot', 3, 12), ('frog', 'frog', 3, 1), ('bat', 'bat', 3, 1),
    ('axolotl', 'axolotl', 3, 1), ('cow', 'cow', 5, 500), ('horse', 'horse', 5, 450), ('donkey', 'donkey', 5, 200), ('mule', 'mule', 5, 350),
    ('llama', 'llama', 5, 130), ('camel', 'camel', 5, 500), ('wolf', 'wolf', 5, 40), ('panda', 'panda', 5, 100), ('turtle', 'turtle', 5, 150),
    ('dolphin', 'dolphin', 5, 150), ('hoglin', 'hoglin', 5, 150), ('zoglin', 'hoglin', 5, 150), ('creeper', 'creeper', 6, 60),
    ('spider', 'spider', 6, 40), ('silverfish', 'silverfish', 6, 1), ('endermite', 'endermite', 6, 1), ('bee', 'bee', 6, 1),
    ('strider', 'strider', 6, 100), ('phantom', 'phantom', 6, 20), ('slime', 'slime', 6, 20), ('magma', 'slime', 6, 20),
    ('enderman', 'enderman', 7, 80), ('shulker', 'shulker', 7, 30), ('guardian', 'guardian', 7, 50), ('villager', 'villager', 7, 70),
    ('pillager', 'pillager', 7, 70), ('vindicator', 'vindicator', 7, 70), ('evoker', 'evoker', 7, 70), ('witch', 'witch', 7, 70),
    ('piglin', 'piglin', 7, 80), ('drowned', 'zombie', 7, 70), ('husk', 'zombie', 7, 70), ('zombie', 'zombie', 7, 70),
    ('stray', 'skeleton', 3, 15), ('skeleton', 'skeleton', 3, 15),
]
BS = json.load(open(os.path.join(HERE, 'butchery_stages.json'), encoding='utf-8'))
carc = {}
for b in sorted(set(BS['blocks']) | set(BS['stages'])):
    sp = next((x for x in SPECIES if x[0] in b), None)
    if sp is None:
        continue
    kg = sp[3] if sp[3] <= 5 else sp[3] * K
    ex = {st: EXTRA[v['table']] for st, v in BS['stages'].get(b, {}).items() if v['table'] in EXTRA and EXTRA[v['table']]}
    carc['butchery:' + b] = {'species': sp[1], 'level': sp[2], 'kg': round(kg, 2), **({'extra': ex} if ex else {})}
# Порог нарезки на доске (уровень вида; доска — на 1 ниже): предмет → самый низкий уровень среди видов, с чьих туш он падает
items_lvl = {'minecraft:chicken': 1, 'minecraft:rabbit': 1, 'minecraft:cod': 1, 'minecraft:salmon': 1, 'butchery:raw_cod_fillet': 1,
             'butchery:raw_salmon': 1, 'alexsmobs:kangaroo_meat': 5, 'alexsmobs:moose_ribs': 5, 'alexscaves:dinosaur_chop': 10}
for tname, its in TABLE_ITEMS.items():
    sp = next((x for x in SPECIES if x[0] in tname), None)
    if sp is None:
        continue
    for it in its:
        if it in WEIGHTS and it not in NO_LOSS:
            items_lvl[it] = min(items_lvl.get(it, 99), sp[2])
with open(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'carcasses.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump({'_': 'tools/gen_food_pack.py — не править руками', 'blocks': carc, 'items': items_lvl,
               'species': {**{x[1]: x[2] for x in SPECIES}, 'kangaroo': 5, 'moose': 5, 'dinosaur': 10}}, f, ensure_ascii=False, indent=1, sort_keys=True)
    f.write('\n')
# Свой вес туш вместо замедления Butchery (эффект по тегам) — теги пустые
for t in ('weighted_carcass_l', 'weighted_carcass_ll'):
    out(f'butchery/tags/items/{t}.json', {'replace': True, 'values': []})
print('carcasses', len(carc), 'with extra cuts', sum(1 for v in carc.values() if 'extra' in v))

# ------------------------------------------------------------------ рецепты
sys.path.insert(0, HERE)
try:
    import recipes_spec
    recipes_spec.generate(out, read_json, D)
except ImportError:
    pass

# ------------------------------------------------------------------ планы доски (по одной части за удар) — для кода
try:
    with open(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'board.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'_': 'tools/gen_food_pack.py из recipes_spec.cut — не править руками', 'plans': recipes_spec.BOARD}, f, ensure_ascii=False, indent=1)
        f.write('\n')
except NameError:
    pass

# ------------------------------------------------------------------ мешки и ящики (блок 6) — таблица для кода
import containers_spec as CS
with open(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'containers.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump({'_': 'tools/gen_food_pack.py из tools/containers_spec.py — не править руками',
               'containers': {c: {'accept': a, 'kg': kg} for c, (a, kg, m) in CS.CONTAINERS.items()}}, f, ensure_ascii=False, indent=1)
    f.write('\n')

# ------------------------------------------------------------------ pack.mcmeta + отчёт
with open(os.path.join(PACK, 'pack.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump({'pack': {'pack_format': 15, 'description': 'RP Culinary: разделка, теги, рецепты (поверх модов)'}}, f, ensure_ascii=False, indent=1)
print('files', len(written))
