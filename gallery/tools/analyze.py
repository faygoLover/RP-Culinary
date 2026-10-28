import json, re, os, glob, collections, sys
D = json.load(open('db.json', encoding='utf-8'))
ru, en, tags, models = D['ru'], D['en'], D['tags'], D['models']
NUT = r"C:\Users\Alex\Desktop\Claude Code\RP Culinary\src\main\resources\data\rpculinary\rpculinary\nutrition"

memo = {}
def tag_items(t, seen=None):
    if t in memo:
        return memo[t]
    seen = seen or set()
    if t in seen:
        return set()
    seen.add(t)
    out = set()
    for v in tags.get(t, []):
        v = v.rstrip('?')
        if v.startswith('#'):
            out |= tag_items(v[1:], seen)
        else:
            out.add(v)
    memo[t] = out
    return out

def name(i):
    ns, p = i.split(':', 1)
    for k in ('item.%s.%s' % (ns, p.replace('/', '.')), 'block.%s.%s' % (ns, p.replace('/', '.'))):
        if k in ru:
            return ru[k]
    for k in ('item.%s.%s' % (ns, p.replace('/', '.')), 'block.%s.%s' % (ns, p.replace('/', '.'))):
        if k in en:
            return en[k] + ' (en)'
    return ''

FOODTAG = re.compile(r'^(forge|c|minecraft|butchery|farm_and_charm|candlelight|diet|sa_decor|vinery|alexscaves|csaugmentations|origins|balm|refurbished_furniture|brewery):'
                     r'(raw_|cooked_|crops|vegetable|fruit|berr|grain|milk|eggs|dough|flour|pasta|salad|foods|fishes|mushrooms|meat|cheese|butter|strawberr|canned_foods|fresh_foods|herbs|food$|cooking_oil|minced|raw_mince|salt|.*grapejuice|special_food|sweet_berries)')
BAD_TAG = re.compile(r'raw_materials|raw_blocks|storage_blocks|fishing_rod|rotten_foods|crops/(cotton|flax|tobacco|cactus)|seeds')
F = set()
for t in tags:
    if FOODTAG.match(t) and not BAD_TAG.search(t):
        F |= tag_items(t)
for f in glob.glob(os.path.join(NUT, '*.json')):
    F |= set(json.load(open(f, encoding='utf-8')).get('items', []))
# food classes (MCreator-like)
for jar, names in D['foodclass'].items():
    nss = [ns for ns, j in D['nsjar'].items() if j == jar]
    for n in names:
        for ns in nss:
            for cand in (n, n.replace('_', '')):
                if ns + ':' + cand in models:
                    F.add(ns + ':' + cand)
VAN = ('apple golden_apple enchanted_golden_apple melon_slice sweet_berries glow_berries chorus_fruit carrot golden_carrot potato baked_potato '
       'poisonous_potato beetroot dried_kelp beef cooked_beef porkchop cooked_porkchop mutton cooked_mutton chicken cooked_chicken rabbit cooked_rabbit '
       'cod cooked_cod salmon cooked_salmon tropical_fish pufferfish bread cookie cake pumpkin_pie rotten_flesh spider_eye mushroom_stew beetroot_soup '
       'rabbit_stew suspicious_stew honey_bottle milk_bucket egg sugar wheat cocoa_beans brown_mushroom red_mushroom pumpkin melon sugar_cane honeycomb '
       'kelp sea_pickle pumpkin_seeds melon_seeds beetroot_seeds wheat_seeds nether_wart crimson_fungus warped_fungus fermented_spider_eye glistering_melon_slice').split()
F |= {'minecraft:' + v for v in VAN}
FOODTYPES = ('minecraft:smoking', 'minecraft:campfire_cooking', 'farmersdelight:cooking', 'farm_and_charm:', 'brewery:', 'vinery:', 'herbalbrews:',
             'refurbished_furniture:oven', 'refurbished_furniture:frying', 'refurbished_furniture:toaster', 'refurbished_furniture:microwave',
             'refurbished_furniture:cutting_board', 'butchery:meatgrinder', 'farmersdelight:dough', 'candlelight:', 'refurbished_furniture:freezer')
NOTFOOD = {'minecraft:bowl', 'minecraft:glass_bottle', 'minecraft:bucket', 'minecraft:stick', 'minecraft:paper', 'minecraft:string', 'minecraft:water_bucket'}

def expand(lst):
    out = set()
    for x in lst:
        if x.startswith('#'):
            out |= tag_items(x[1:])
        else:
            out.add(x)
    return out
R = []
for r in D['recipes']:
    R.append((r, expand(r['in']), expand(r['out'])))
EXTRA = '''alexscaves:caramel alexscaves:caramel_apple alexscaves:gelatin_blue alexscaves:gelatin_green alexscaves:gelatin_pink alexscaves:gelatin_red alexscaves:gelatin_yellow
alexscaves:gummy_ring_blue alexscaves:gummy_ring_green alexscaves:gummy_ring_pink alexscaves:gummy_ring_red alexscaves:gummy_ring_yellow alexscaves:candy_cane alexscaves:chocolate_ice_cream_scoop
alexscaves:cooked_mussel alexscaves:pine_nuts alexscaves:licoroot alexscaves:small_peppermint alexscaves:large_peppermint alexscaves:peppermint_powder alexscaves:gingerbread_crumbs alexscaves:primordial_soup alexscaves:seething_stew
alexscaves:purple_soda_bottle alexscaves:sea_pig alexscaves:block_of_chocolate alexscaves:block_of_frosting alexscaves:block_of_vanilla_frosting alexscaves:block_of_chocolate_frosting alexscaves:cake_layer alexscaves:rock_candy_red alexscaves:gumball_pile alexscaves:stinky_fish
alexsmobs:kangaroo_meat alexsmobs:cooked_kangaroo_meat alexsmobs:moose_ribs alexsmobs:cooked_moose_ribs alexsmobs:lobster_tail alexsmobs:cooked_lobster_tail alexsmobs:maggot alexsmobs:fish_oil alexsmobs:shrimp_fried_rice alexsmobs:rainbow_jelly alexsmobs:blood_sac alexsmobs:mosquito_repellent_stew alexsmobs:kangaroo_burger alexsmobs:banana alexsmobs:blobfish alexsmobs:flying_fish alexsmobs:triops_eggs alexsmobs:leafcutter_ant_pupa alexsmobs:mimicream
butchery:cooked_sausages butchery:cooked_blood_sausages butchery:enderman_heart butchery:enderman_intestines butchery:enderman_kidney butchery:enderman_liver butchery:frog_heart butchery:honey_stomach butchery:meat_scraps butchery:rotten_heart butchery:rotten_intestines butchery:rotten_kidney butchery:rotten_liver butchery:withered_heart butchery:bat_wing butchery:bee_wings butchery:blood_bucket butchery:bottle_of_infected_blood butchery:pufferfish
candlelight:dough candlelight:tomato_sauce candlelight:toast candlelight:chocolate_box
csaugmentations:nutriogloop csaugmentations:cyclofungi csaugmentations:salt csaugmentations:crushed_acid_mushroom csfoundation:acid_mushroom csfoundation:radioactive_carrot csfoundation:radioactive_potato csfoundation:radioactive_flesh
farmersdelight:pumpkin_slice farmersdelight:rice_panicle farmersdelight:pie_crust farmersdelight:cake_slice farmersdelight:melon_juice farmersdelight:rotten_tomato farmersdelight:cabbage_seeds farmersdelight:tomato_seeds farmersdelight:honey_cookie farmersdelight:sweet_berry_cookie farmersdelight:roasted_mutton_chops farmersdelight:hamburger farmersdelight:bacon_sandwich farmersdelight:chicken_sandwich farmersdelight:egg_sandwich farmersdelight:kelp_roll farmersdelight:kelp_roll_slice farmersdelight:apple_pie farmersdelight:apple_pie_slice farmersdelight:fruit_salad farmersdelight:melon_popsicle farmersdelight:steak_and_potatoes farmersdelight:stuffed_potato farmersdelight:shepherds_pie farmersdelight:honey_glazed_ham farmersdelight:chocolate_pie farmersdelight:sweet_berry_cheesecake
farm_and_charm:barley_seeds farm_and_charm:oat_seeds farm_and_charm:rotten_tomato farm_and_charm:nettle_tea farm_and_charm:ribwort_tea farm_and_charm:barley_ball farm_and_charm:oat_ball
fl_zone_arts:pukh_flesh herbalbrews:green_tea_leaf herbalbrews:tea_blossom
legendarysurvivaloverhaul:apple_juice legendarysurvivaloverhaul:beetroot_juice legendarysurvivaloverhaul:cactus_juice legendarysurvivaloverhaul:carrot_juice legendarysurvivaloverhaul:chorus_fruit_juice legendarysurvivaloverhaul:glistering_melon_juice legendarysurvivaloverhaul:glow_berries_juice legendarysurvivaloverhaul:golden_apple_juice legendarysurvivaloverhaul:golden_carrot_juice legendarysurvivaloverhaul:melon_juice legendarysurvivaloverhaul:pumpkin_juice legendarysurvivaloverhaul:sweet_berries_juice legendarysurvivaloverhaul:purified_water_bottle legendarysurvivaloverhaul:healing_herbs legendarysurvivaloverhaul:ice_fern_leaf legendarysurvivaloverhaul:sun_fern_leaf
sa_combat:butter sa_combat:peanut_butter sa_combat:fruit_jam sa_combat:coxinha_dough sa_combat:pastel_dough sa_combat:raw_cheese_pastel sa_combat:raw_coxinha sa_combat:raw_meat_pastel sa_combat:raw_wind_pastel sa_combat:saltine_cracker sa_combat:chips sa_combat:chocolate_bar sa_combat:candys sa_combat:cereal_fruit_loops sa_combat:milk_box sa_combat:milk_gallon sa_combat:coffee sa_combat:big_burger sa_combat:vodka sa_combat:wine sa_combat:beer
vinery:applesauce vinery:rotten_cherry zombie_extreme:infected_flesh zombie_extreme:rotten_apple brewery:beer_mug'''.split()
F |= set(EXTRA)
CRAFT = ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:smelting')
FOODKW = re.compile(r'meat|raw|cooked|egg|fish|fruit|berr|juice|milk|chees|bread|dough|flour|salt|sugar|honey|cand|chocol|nut|steak|chop|fillet|soup|stew|jam|cookie|cake|pie|tea|coffee|sauce|butter|apple|carrot|potato|tomato|onion|corn|rice|grape|cherry|mushroom|mince|sausage|bacon|ham|burger|pizza|sandwich|toast|crust|pasta|salad|roll|meal|treat|pancake|cider|wine|can|chili|beef|chicken|mutton|pork|rabbit|cod|salmon|kelp|melon|pumpkin|carmel|caramel|marshmallow|food|stick|glistering|golden|popped|gelatin|ice_cream|frosting|crumbs|layer|feed')
BADOUT = re.compile(r'dye|wool|carpet|awning|flag|bunting|wallpaper|rope|tape|thatch|sack|module|sleeping|tent|on_a_stick|fishing|crate|_bag$|block|_dye$|seeds|sign|bale|pile|carcass|banner|fertilizer|feed|soap|torch|candle|lantern|bucket$|^bottle$')
CONT = NOTFOOD | {'minecraft:water_bucket', 'minecraft:milk_bucket'}
for _ in range(4):
    for r, ins, outs in R:
        if r['type'].startswith(FOODTYPES) and ins & F:
            F |= {o for o in outs if o not in NOTFOOD and ':' in o}
        elif r['type'] in CRAFT and (ins - CONT) and (ins - CONT) <= F:
            F |= {o for o in outs if FOODKW.search(o.split(':')[1]) and not BADOUT.search(o.split(':')[1])}
F -= NOTFOOD
# food recipes and per-item input stats
useIn = collections.defaultdict(list)
madeBy = collections.defaultdict(list)
for r, ins, outs in R:
    fo = {o for o in outs & F if not re.search(r'dye|slab|stairs|seeds|fertilizer|compost|polished|_bag$|crate|_block$', o)}
    if not fo:
        continue
    for i in ins:
        if i in F:
            useIn[i].append((r['type'], sorted(fo)[0], r['jar']))
    for o in fo:
        madeBy[o].append((r['type'], sorted(ins & F)[:3]))
cands = sorted(set(useIn) | F)
rows = []
for i in cands:
    if i not in models and not i.startswith('minecraft:'):
        continue
    u = useIn.get(i, [])
    outs = collections.Counter(o for _, o, _ in u)
    types = collections.Counter(t.split(':')[-1] for t, _, _ in u)
    mb = madeBy.get(i, [])
    rows.append({'id': i, 'name': name(i), 'jar': models.get(i, 'minecraft'), 'nin': len(u), 'types': dict(types),
                 'outs': [o for o, _ in outs.most_common(6)], 'made': [(t.split(':')[-1], ins) for t, ins in mb[:3]], 'nmade': len(mb)})
json.dump(rows, open('cands.json', 'w', encoding='utf-8'), ensure_ascii=False)
with open('cands.txt', 'w', encoding='utf-8') as f:
    for x in rows:
        f.write('%s|%s|%s|in%d %s|<-%s\n' % (x['id'], x['name'], x['jar'][:14], x['nin'], ','.join(o.split(':')[1][:18] for o in x['outs'][:4]),
                                             ';'.join('%s:%s' % (t, '+'.join(a.split(':')[1][:14] for a in ins)) for t, ins in x['made'][:2])))
print(len(rows), sum(1 for x in rows if x['nin']))
