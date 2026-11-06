"""Поваренные книги (D3, вариант А, решения Алекса 03.11): что в какой книге. → src/main/resources/assets/rpculinary/cookbook.json
- base — рецепты, которые повар знает сразу: нарезка, тесто, мука, рис (не блюда);
- books — книги по разделам (выдаёт ГМ, раскладывают как лут): рецепт + нужный навык (по числу разных ингредиентов);
- разделка мяса на доске — своя книга, навык от 3; пиво и вино — книга «Винокура», заготовки — «Заготовщика»;
- secret — сложные (6+ разных ингредиентов, праздничные блюда) ни в одну книгу не попадают: найдут сами или в описаниях.
Источник — данные сборки (gallery/cache/db.json) и наш встроенный датапак (рецепты rpculinary, отключённые чужие).
Запуск: python -I tools/gen_cookbook.py"""
import glob, json, os
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
D = json.load(open(os.path.join(ROOT, 'gallery', 'cache', 'db.json'), encoding='utf-8'))
CANDS = {c['id']: c for c in json.load(open(os.path.join(ROOT, 'gallery', 'cache', 'cands.json'), encoding='utf-8'))}
FOOD = set(CANDS)
PACK = os.path.join(ROOT, 'src', 'main', 'resources', 'resourcepacks', 'rpculinary_food', 'data')

# что отключено нашим паком (условие forge:false / not mod_loaded) и наши рецепты
off, ours = set(), []
for p in glob.glob(os.path.join(PACK, '*', 'recipes', '**', '*.json'), recursive=True):
    rel = os.path.relpath(p, PACK).replace('\\', '/')
    ns, _, path = rel.split('/', 2)
    rid = f'{ns}:{path[:-5]}'
    j = json.load(open(p, encoding='utf-8'))
    conds = json.dumps(j.get('conditions', []))
    if ns != 'rpculinary':
        if 'forge:false' in conds or ('"forge:not"' in conds and ns == 'minecraft'):
            off.add(rid)
        continue
    res = j.get('result') or j.get('output') or {}
    out = res if isinstance(res, str) else res.get('item') if isinstance(res, dict) else None
    if not out and isinstance(j.get('result'), list) and j['result']:
        out = j['result'][0].get('item')
    ings = j.get('ingredients') or ([j['ingredient']] if 'ingredient' in j else [])
    ins = [x.get('item') or ('#' + x['tag'] if 'tag' in x else None) for x in ings if isinstance(x, dict)]
    ours.append({'id': rid, 'type': j.get('type', ''), 'in': [x for x in ins if x], 'out': [out] if out else []})

recipes = [r for r in D['recipes'] if r['id'] not in off] + ours

BASE_OUT = {'farmersdelight:wheat_dough', 'farm_and_charm:dough', 'refurbished_furniture:dough', 'candlelight:dough', 'farm_and_charm:flour',
            'refurbished_furniture:wheat_flour', 'farmersdelight:pie_crust', 'farmersdelight:raw_pasta', 'farm_and_charm:raw_pasta',
            'farmersdelight:rice', 'farmersdelight:cooked_rice', 'farmersdelight:cabbage_leaf', 'farmersdelight:pumpkin_slice',
            'farmersdelight:tomato_sauce', 'sa_combat:pastel_dough', 'sa_combat:coxinha_dough', 'refurbished_furniture:bread_slice',
            'minecraft:melon_slice', 'butchery:salt', 'farmersdelight:minced_beef'}
SECRET_OUT = {'farmersdelight:honey_glazed_ham_block', 'farmersdelight:shepherds_pie_block', 'farmersdelight:roast_chicken_block',
              'farmersdelight:stuffed_pumpkin_block', 'farmersdelight:rice_roll_medley_block', 'farmersdelight:gleaming_salad_block',
              'alexscaves:primordial_soup', 'alexscaves:seething_stew', 'candlelight:beef_wellington', 'candlelight:lasagne'}
MEAT_WORDS = ('beef', 'pork', 'mutton', 'chicken', 'rabbit', 'lamb', 'ham', 'meat', 'steak', 'sausage', 'bacon', 'patty', 'kangaroo', 'moose',
              'meatball', 'goulash', 'bolognese', 'chop', 'mince', 'wellington')
FISH_WORDS = ('cod', 'salmon', 'fish', 'tuna', 'sardine', 'sushi', 'kelp_roll', 'squid', 'lobster', 'shrimp', 'calamari')
SOUP_WORDS = ('soup', 'stew', 'broth', 'borscht', 'chowder', 'goulash', 'grits', 'porridge')
BAKE_WORDS = ('bread', 'pie', 'cake', 'cookie', 'pretzel', 'gingerbread', 'pizza', 'cupcake', 'waffle', 'donut', 'pancake', 'biscuit',
              'toast', 'crust', 'cheesecake', 'muffin', 'strudel', 'tart', 'bun')
DRINK_TYPES = ('herbalbrews:kettle_brewing', 'vinery:apple_mashing')
VINTNER_TYPES = ('brewery:brewing', 'vinery:wine_fermentation', 'vinery:apple_fermenting')
JUNK = ('block', 'pole', 'boots', 'nest', 'trash', 'fish_oil', 'conversion', 'helmet', 'bandage', 'pill', 'syringe', 'soap', 'dye', 'feed',
        'fertilizer', 'seeds', 'sapling', 'beer_mug', 'medkit', 'package', 'stairs', 'slab', 'shelf_mushroom', 'sickle', 'healing_herbs',
        'honeypuff', 'polished_salt', 'flesh', 'nether_wart')
JUNK_ID = ('wild_', 'colony', 'from_block', 'from_leaves', 'from_slices', 'from_plant', 'bottle_from', 'bucket_from', '_ball', 'smelting_potion',
           'csaugmentations:salt', 'blocksyouneed_luna', 'dye', 'uncompress', 'uncrafting', 'thatch', 'conversion', 'golden_', 'honeycomb',
           'bottledhoney', 'sugar_from', 'minecraft:wheat', 'carrots_recipe', 'potato_recipe', 'herbs/', 'tremorzilla', 'fermented_spider',
           'flax', 'biome_treat', 'sack_of_sating', 'darkened_apple', 'crushed_acid', 'glistering', 'minecraft:melon', 'apple_mashing/honey',
           '_from_campfire_cooking', '_from_smoking', 'from_smelting')
SWEET_WORDS = ('candy', 'gummy', 'jelly', 'caramel', 'chocolate', 'frosting', 'peppermint', 'licoroot', 'marshmallow', 'ice_cream',
               'cupcake', 'donut', 'sugar', 'gumball', 'popsicle', 'jam', 'honey_cookie', 'mousse')
CANNED = lambda o: 'canned' in o or o.endswith('_can') or o in ('farmersdelight:smoked_ham',)


def section(r, out):
    t, path = r['type'], out.split(':')[1]
    if t in VINTNER_TYPES:
        return 'vintner'
    if CANNED(out):
        return 'preserves'
    if t == 'farmersdelight:cutting' and any(w in out for w in MEAT_WORDS + ('chicken', 'cod', 'salmon', 'half_chicken')):
        return 'butchery'
    if t in DRINK_TYPES or any(w in path for w in ('juice', 'tea', 'cocoa', 'hot_chocolate', 'coffee', 'cider', 'lemonade')):
        return 'drinks'
    if any(w in path for w in SWEET_WORDS):
        return 'baking'
    if any(w in path for w in FISH_WORDS):
        return 'fish'
    if any(w in path for w in SOUP_WORDS):
        return 'soups'
    if any(w in path for w in BAKE_WORDS) or t in ('farm_and_charm:stove', 'refurbished_furniture:oven_baking'):
        return 'baking'
    if any(w in path for w in MEAT_WORDS) or t in ('farm_and_charm:roaster',):
        return 'meat'
    return 'salads'


PREP_TYPES = {'farmersdelight:cutting', 'refurbished_furniture:cutting_board_slicing', 'farm_and_charm:mincer', 'farm_and_charm:crafting_bowl',
              'butchery:pestleandmortarrecipetype', 'vinery:apple_mashing', 'refurbished_furniture:cutting_board_combining'}
MIN_SKILL = {'base': 0, 'soups': 1, 'baking': 2, 'meat': 2, 'fish': 2, 'salads': 0, 'drinks': 0, 'butchery': 3, 'preserves': 2, 'vintner': 2}
PERK = {'preserves': 'rpculinary:preserver', 'vintner': 'rpculinary:vintner'}
# Второй рецепт того же блюда, который тоже нужен в книге (4 колбаски из жареной связки — рядом с колбаской из кастрюли F&C)
ALSO = {'rpculinary:board/sausage_plate'}
STATION_TYPES = {'farmersdelight:cooking', 'farmersdelight:cutting', 'farm_and_charm:pot_cooking', 'farm_and_charm:stove', 'farm_and_charm:roaster',
                 'farm_and_charm:crafting_bowl', 'farm_and_charm:mincer', 'minecraft:crafting_shaped', 'minecraft:crafting_shapeless',
                 'minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking', 'refurbished_furniture:oven_baking',
                 'refurbished_furniture:frying_pan_cooking', 'refurbished_furniture:cutting_board_combining',
                 'refurbished_furniture:cutting_board_slicing', 'refurbished_furniture:toaster_heating', 'herbalbrews:kettle_brewing',
                 'vinery:apple_mashing', 'vinery:apple_fermenting', 'vinery:wine_fermentation', 'brewery:brewing',
                 'butchery:pestleandmortarrecipetype'}

base, books, secret, seen, base_out = [], {}, [], set(), []
for r in recipes:
    if r['type'] not in STATION_TYPES or not r['out']:
        continue
    out = r['out'][0]
    if out not in FOOD and out not in BASE_OUT and not CANNED(out) or any(w in out.split(':')[1] for w in JUNK) or any(w in r['id'] for w in JUNK_ID):
        continue
    if r['type'] in ('minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking') and r['id'].startswith(('minecraft:', 'butchery:', 'alexsmobs:', 'alexscaves:')) \
            and not out.endswith(('bread', 'baked_potato', 'dried_kelp')):
        continue  # простое «пожарь сырое» — не книжный рецепт
    key = (r['type'], out, tuple(sorted(r['in'])))
    if key in seen:
        continue
    seen.add(key)
    distinct = len(set(r['in']))
    meaty = any(w in out.split(':')[1] for w in MEAT_WORDS + FISH_WORDS) or r['type'] == 'farm_and_charm:mincer' and out != 'farm_and_charm:flour'
    prep = r['type'] in PREP_TYPES and CANDS.get(out, {}).get('nin', 0) > 0 and distinct <= 2 and r['type'] not in VINTNER_TYPES and not CANNED(out) and not meaty
    if (out in BASE_OUT and not meaty or prep) and r['type'] not in VINTNER_TYPES:  # полуфабрикат из 1–2 продуктов, не мясо — база
        if any(b[1] == out for b in base_out):
            continue
        base_out.append((r['id'], out))
        base.append(r['id'])
        continue
    if out in SECRET_OUT or distinct >= 6:
        secret.append(r['id'])
        continue
    sec = section(r, out)
    if any(e['out'] == out for e in books.get(sec, [])) and r['id'] not in ALSO:
        continue  # одно блюдо — один рецепт в книге
    skill = min(8, max(MIN_SKILL[sec], distinct - 2))
    books.setdefault(sec, []).append({'id': r['id'], 'skill': skill, 'out': out})

data = {'_': 'tools/gen_cookbook.py — не править руками', 'base': sorted(base),
        'books': {k: {'skill': MIN_SKILL[k], **({'perk': PERK[k]} if k in PERK else {}), 'recipes': [{'id': e['id'], 'skill': e['skill']} for e in sorted(v, key=lambda e: (e['skill'], e['id']))]}
                  for k, v in sorted(books.items())},
        'secret': sorted(secret)}
with open(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'cookbook.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
    f.write('\n')
print('base', len(base), 'books', {k: len(v) for k, v in books.items()}, 'secret', len(secret))
