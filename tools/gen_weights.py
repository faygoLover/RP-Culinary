# Таблица весов и стаков ингредиентов (блок 2 плана): src/main/resources/assets/rpculinary/weights.json.
# Мясо — по ролям из галереи (gallery/review.json, решения Alex), остальное — по группам «всё по весу».
# Формат: {"items": {"id": [граммы, стак]}}. Стак 0 — не менять (у предмета свой меньший). Запуск: python -I tools/gen_weights.py
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, 'gallery'))
import content as C

review = json.load(open(os.path.join(ROOT, 'gallery', 'review.json'), encoding='utf-8'))['items']
ROLE = {'por': (250, 16), 'small': (100, 32), 'steak': (400, 8), 'whole': (1000, 4), 'big': (250, 16)}  # крупный кусок с весом в NBT — стак 1 (код), без NBT — порция
W = {}
for grp, lst in C.MEAT_ROLES:
    for i, rec, note in lst:
        role = review.get('role:' + i, {}).get('mark') or rec
        if role in ROLE:
            W[i] = list(ROLE[role])
# Точные веса цельных и крупных (номинал = то, на что рассчитано питание за штуку; крупные режутся по NBT)
EXACT = {'minecraft:chicken': 800, 'minecraft:rabbit': 1300, 'minecraft:cod': 1500, 'minecraft:salmon': 2000,
         'butchery:raw_ham': 2000, 'farm_and_charm:lamb_ham': 700, 'butchery:raw_pork_leg': 500, 'butchery:raw_chicken_leg': 250,
         'butchery:raw_sausage': 1000, 'butchery:raw_blood_sausage': 1000, 'butchery:raw_cod_fillet': 600, 'butchery:raw_salmon': 600}
for i, g in EXACT.items():
    if i in W:
        W[i][0] = g
# Готовые версии — за сырыми (масса после жарки ~0.7, стак тот же)
COOKED = {'minecraft:cooked_beef': 'minecraft:beef', 'minecraft:cooked_porkchop': 'minecraft:porkchop', 'minecraft:cooked_mutton': 'minecraft:mutton',
          'minecraft:cooked_chicken': 'minecraft:chicken', 'minecraft:cooked_rabbit': 'minecraft:rabbit', 'minecraft:cooked_cod': 'minecraft:cod',
          'minecraft:cooked_salmon': 'minecraft:salmon', 'farmersdelight:cooked_bacon': 'farmersdelight:bacon',
          'farmersdelight:cooked_chicken_cuts': 'farmersdelight:chicken_cuts', 'farmersdelight:cooked_cod_slice': 'farmersdelight:cod_slice',
          'farmersdelight:cooked_salmon_slice': 'farmersdelight:salmon_slice', 'farmersdelight:cooked_mutton_chops': 'farmersdelight:mutton_chops',
          'farmersdelight:beef_patty': 'farmersdelight:minced_beef', 'butchery:cooked_ribeye_steak': 'butchery:raw_ribeye_steak',
          'butchery:cooked_tbone_steak': 'butchery:raw_tbone_steak', 'butchery:cooked_sirloin_steak': 'butchery:raw_sirloin_steak',
          'butchery:cooked_pork_loin': 'butchery:raw_pork_loin', 'butchery:cooked_pork_leg': 'butchery:raw_pork_leg', 'butchery:cooked_ham': 'butchery:raw_ham',
          'butchery:cooked_lamb_rib': 'butchery:raw_lamb_rib', 'butchery:cooked_lamb_loin': 'butchery:raw_lamb_loin',
          'butchery:cooked_lamb_sirloin': 'butchery:raw_lamb_sirloin', 'butchery:cooked_chicken_leg': 'butchery:raw_chicken_leg',
          'butchery:cooked_chicken_wing': 'butchery:raw_chicken_wing', 'butchery:cooked_sausage': 'butchery:raw_sausage',
          'butchery:cooked_blood_sausage': 'butchery:raw_blood_sausage', 'butchery:cooked_cod_fillet': 'butchery:raw_cod_fillet',
          'butchery:cooked_salmon': 'butchery:raw_salmon', 'butchery:cooked_beef_mince': 'butchery:raw_beef_mince',
          'butchery:cooked_lamb_mince': 'butchery:raw_lamb_mince', 'farmersdelight:smoked_ham': 'butchery:raw_ham'}
for c, r in COOKED.items():
    if r in W:
        W[c] = [round(W[r][0] * 0.7), W[r][1] if W[r][1] > 1 else 8]
# Экзотика: готовые версии крупных кусков — порции
for grp, lst in C.MEAT_ROLES:
    if grp.startswith('Дичь'):
        for i, rec, note in lst:
            c = i.replace(':raw_', ':cooked_')
            if c != i:
                W.setdefault(c, [175, 16])
# Остальные ингредиенты «по весу»
GROUPS = {
    (150, 32): ['minecraft:potato', 'minecraft:baked_potato', 'minecraft:carrot', 'minecraft:beetroot', 'farmersdelight:onion', 'farm_and_charm:onion',
                'farmersdelight:tomato', 'farm_and_charm:tomato', 'minecraft:apple', 'zombie_extreme:green_apple', 'farm_and_charm:corn',
                'alexsmobs:banana', 'survival_instinct:orange', 'minecraft:golden_carrot', 'minecraft:golden_apple', 'farmersdelight:pumpkin_slice',
                'minecraft:melon_slice', 'minecraft:brown_mushroom', 'minecraft:red_mushroom', 'biomesoplenty:glowshroom', 'biomesoplenty:toadstool'],
    (50, 64): ['minecraft:sweet_berries', 'minecraft:glow_berries', 'farm_and_charm:strawberry', 'vinery:cherry', 'minecraft:cocoa_beans',
               'herbalbrews:coffee_beans', 'alexscaves:pine_nuts', 'brewery:hops'],
    (60, 16): ['minecraft:egg', 'minecraft:turtle_egg'],
    (1000, 8): ['farmersdelight:cabbage', 'farm_and_charm:lettuce', 'farm_and_charm:flour', 'refurbished_furniture:wheat_flour'],
    (100, 32): ['farmersdelight:cabbage_leaf', 'minecraft:wheat', 'farm_and_charm:barley', 'farm_and_charm:oat', 'farmersdelight:rice',
                'minecraft:sugar', 'butchery:salt', 'mekanism:salt', 'refurbished_furniture:sea_salt', 'farm_and_charm:butter', 'sa_combat:butter',
                'farm_and_charm:yeast', 'minecraft:dried_kelp'],
    (500, 16): ['farmersdelight:wheat_dough', 'farm_and_charm:dough', 'refurbished_furniture:dough', 'candlelight:dough', 'farmersdelight:raw_pasta',
                'farm_and_charm:raw_pasta', 'refurbished_furniture:cheese', 'sa_combat:cheese', 'candlelight:mozzarella', 'minecraft:bread',
                'farm_and_charm:farmers_bread'],
    (3000, 4): ['minecraft:pumpkin', 'minecraft:melon'],
    (500, 16): ['farmersdelight:milk_bottle'],
}
for (g, st), ids in GROUPS.items():
    for i in ids:
        W.setdefault(i, [g, st])
# Мешки и ящики с продуктами — стак 1 (содержимое — блок 6)
BAGS = ['farm_and_charm:beetroot_bag', 'farm_and_charm:carrot_bag', 'farm_and_charm:corn_bag', 'farm_and_charm:flour_bag', 'farm_and_charm:lettuce_bag',
        'farm_and_charm:oat_bag', 'farm_and_charm:onion_bag', 'farm_and_charm:potato_bag', 'farm_and_charm:strawberry_bag', 'farm_and_charm:tomato_bag',
        'farmersdelight:beetroot_crate', 'farmersdelight:cabbage_crate', 'farmersdelight:carrot_crate', 'farmersdelight:onion_crate',
        'farmersdelight:potato_crate', 'farmersdelight:tomato_crate', 'farmersdelight:rice_bag',
        'quark:potato_crate', 'quark:beetroot_crate', 'quark:carrot_crate', 'quark:apple_crate', 'quark:berry_sack', 'quark:glowberry_sack',
        'quark:cocoa_beans_sack', 'quark:nether_wart_sack', 'quark:golden_apple_crate', 'quark:golden_carrot_crate',
        'vinery:apple_bag', 'vinery:cherry_bag', 'vinery:red_grape_bag', 'vinery:white_grape_bag', 'vinery:red_jungle_grape_bag',
        'vinery:white_jungle_grape_bag', 'vinery:red_savanna_grape_bag', 'vinery:white_savanna_grape_bag', 'vinery:red_taiga_grape_bag',
        'vinery:white_taiga_grape_bag']
for b in BAGS:
    W[b] = [0, 1]
out = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'rpculinary', 'weights.json')
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    json.dump({'_': 'генерируется tools/gen_weights.py; [граммы, стак]', 'items': dict(sorted(W.items()))}, f, ensure_ascii=False, indent=1)
print(len(W), 'items')
