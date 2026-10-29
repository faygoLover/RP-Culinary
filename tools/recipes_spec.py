# Рецепты встроенного датапака (блок 1 плана): что убрать с верстака, что добавить на станции. Решения — галерея culinary
# (gallery/review.json) и docs/butchery_carcasses_2026-10-28.md §5–7. Вызывается из gen_food_pack.py.
import json

OFF = [{'type': 'forge:false'}]
CRAFT = ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless')


def I(x):
    """Ингредиент: '#тег' или 'id'."""
    return {'tag': x[1:]} if x.startswith('#') else {'item': x}


def generate(out, read_json, D):
    by_out = {}
    for r in D['recipes']:
        for o in r['out']:
            by_out.setdefault(o, []).append(r)

    STATION_MOD = {'refurbished_furniture:sweet_berry_jam_toast': 'refurbished_furniture'}

    def disable(rid, station='farmersdelight'):
        ns, path = rid.split(':', 1)
        # Свой рецепт мода убираем всегда; ванильный — только если стоит мод со станцией-заменой (иначе без еды останемся)
        cond = OFF if ns != 'minecraft' else [{'type': 'forge:not', 'value': {'type': 'forge:mod_loaded', 'modid': station}}]
        orig = read_json(f'data/{ns}/recipes/{path}.json') if ns == 'minecraft' else None
        if not isinstance(orig, dict):
            orig = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:barrier'}], 'result': {'item': 'minecraft:barrier'}}
        orig = dict(orig)
        orig['conditions'] = list(orig.get('conditions', [])) + cond
        out(f'{ns}/recipes/{path}.json', orig)

    def off_craft(item, types=CRAFT, station=None):
        # где заменяет станция: выпечка и пироги — F&C, супы/хлеб — FD
        if station is None:
            station = 'farm_and_charm' if item in ('minecraft:cake', 'minecraft:cookie', 'minecraft:pumpkin_pie') else 'farmersdelight'
        n = 0
        for r in by_out.get(item, []):
            if r['type'] in types:
                disable(r['id'], station); n += 1
        return n

    def mods_of(o, acc):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in ('item', 'type') and isinstance(v, str) and ':' in v:
                    acc.add(v.split(':')[0])
                mods_of(v, acc)
        elif isinstance(o, list):
            for v in o:
                mods_of(v, acc)
        return acc

    def rec(rid, obj):
        # нет нужного мода (тип станции или предмет) — рецепт молча не грузится
        need = sorted(mods_of(obj, set()) - {'minecraft', 'forge'})
        obj = dict(obj, conditions=[{'type': 'forge:mod_loaded', 'modid': m} for m in need])
        out(f'rpculinary/recipes/{rid}.json', obj)

    # -------------------------------------------------- 1. верстак → станции (аналог уже есть: просто убрать верстак)
    for it in ['minecraft:beetroot_soup', 'minecraft:mushroom_stew', 'minecraft:rabbit_stew', 'minecraft:bread',
               'refurbished_furniture:sweet_berry_jam_toast', 'refurbished_furniture:glow_berry_jam_toast']:
        off_craft(it)

    # -------------------------------------------------- 2. бургеры и сэндвичи — доска Refurbished (сборка)
    def board(rid, ings, result, count=1):
        rec(f'board/{rid}', {'type': 'refurbished_furniture:cutting_board_combining', 'count': count,
                             'ingredients': [I(x) for x in ings], 'result': result})
    SL = 'refurbished_furniture:bread_slice'
    for it in ['farmersdelight:hamburger', 'sa_combat:burger', 'sa_combat:cheese_burger', 'alexsmobs:kangaroo_burger',
               'farmersdelight:bacon_sandwich', 'farmersdelight:chicken_sandwich', 'farmersdelight:egg_sandwich',
               'farmersdelight:bacon_and_eggs']:
        off_craft(it)
    board('hamburger', [SL, 'farmersdelight:beef_patty', '#forge:salad_ingredients', '#forge:crops/tomato', SL], 'farmersdelight:hamburger')
    board('cheese_burger', [SL, 'farmersdelight:beef_patty', '#forge:cheeses', '#forge:salad_ingredients', SL], 'sa_combat:cheese_burger')
    board('big_burger', [SL, 'farmersdelight:beef_patty', '#forge:cheeses', 'farmersdelight:beef_patty', SL], 'sa_combat:big_burger')
    board('kangaroo_burger', [SL, 'alexsmobs:cooked_kangaroo_meat', '#forge:salad_ingredients', SL], 'alexsmobs:kangaroo_burger')
    board('bacon_sandwich', [SL, '#forge:cooked_bacon', '#forge:crops/tomato', '#forge:salad_ingredients', SL], 'farmersdelight:bacon_sandwich')
    board('chicken_sandwich', [SL, '#forge:cooked_chicken', 'minecraft:carrot', '#forge:salad_ingredients', SL], 'farmersdelight:chicken_sandwich')
    board('egg_sandwich', [SL, '#forge:cooked_eggs', SL], 'farmersdelight:egg_sandwich')
    # яичница с беконом: F&C — жаровня (как есть), FD — на сковороде жарятся части, на доске — подача
    board('bacon_and_eggs', ['farmersdelight:fried_egg', 'farmersdelight:cooked_bacon'], 'farmersdelight:bacon_and_eggs')
    board('hot_dog', [SL, 'butchery:cooked_sausage', '#forge:crops/onion'], 'sa_combat:hot_dog', 4)

    # -------------------------------------------------- 3. салаты — миска F&C
    def bowl(rid, ings, result, count=1):
        rec(f'bowl/{rid}', {'type': 'farm_and_charm:crafting_bowl', 'ingredients': [I(x) for x in ings],
                            'result': {'item': result, 'count': count}})
    for it in ['farmersdelight:mixed_salad', 'farmersdelight:fruit_salad', 'farmersdelight:nether_salad', 'farmersdelight:pie_crust',
               'refurbished_furniture:cheese']:
        off_craft(it)
    bowl('mixed_salad', ['#forge:salad_ingredients', '#forge:crops/tomato', 'minecraft:beetroot', 'minecraft:bowl'], 'farmersdelight:mixed_salad')
    bowl('fruit_salad', ['minecraft:apple', 'minecraft:melon_slice', '#forge:berries', 'minecraft:bowl'], 'farmersdelight:fruit_salad')
    bowl('nether_salad', ['minecraft:crimson_fungus', 'minecraft:warped_fungus', 'minecraft:bowl'], 'farmersdelight:nether_salad')
    bowl('pie_crust', ['#forge:flour', '#forge:milk', '#forge:butter'], 'farmersdelight:pie_crust')
    # сыр: молоко + соль + желудок (сычужный фермент) — без спецпредметов
    bowl('cheese', ['minecraft:milk_bucket', '#forge:salt', 'butchery:stomach'], 'refurbished_furniture:cheese', 2)
    bowl('cheese_sa', ['farmersdelight:milk_bottle', 'farmersdelight:milk_bottle', '#forge:salt', 'butchery:stomach'], 'sa_combat:cheese')

    def roast(rid, ings, result, count=1):
        rec(f'roaster/{rid}', {'type': 'farm_and_charm:roaster', 'ingredients': [I(x) for x in ings], 'container': {'item': 'minecraft:bowl'},
                               'result': {'item': result, 'count': count}, 'requiresLearning': False})
    # -------------------------------------------------- 4. выпечка — плита F&C (stove)
    def stove(rid, ings, result, count=1):
        if len(ings) > 3:  # у плиты F&C три слота — длинное запекаем на жаровне («противень», до 6)
            return roast(rid, ings, result, count)
        rec(f'stove/{rid}', {'type': 'farm_and_charm:stove', 'ingredients': [I(x) for x in ings],
                             'result': {'item': result, 'count': count}, 'experience': 0.5, 'requiresLearning': False})
    for it in ['minecraft:cake', 'minecraft:cookie', 'minecraft:pumpkin_pie', 'farmersdelight:apple_pie', 'farmersdelight:chocolate_pie',
               'farmersdelight:sweet_berry_cheesecake', 'farmersdelight:honey_cookie', 'farmersdelight:sweet_berry_cookie',
               'supplementaries:pancake', 'sa_combat:cheese_bread', 'farmersdelight:steak_and_potatoes', 'farmersdelight:barbecue_stick',
               'farmersdelight:roasted_mutton_chops']:
        off_craft(it)
    stove('cake', ['#forge:flour', '#forge:eggs', 'minecraft:sugar', '#forge:milk', '#forge:butter'], 'minecraft:cake')
    stove('cookie', ['#forge:flour', 'minecraft:cocoa_beans', 'minecraft:sugar'], 'minecraft:cookie', 8)
    stove('honey_cookie', ['#forge:flour', 'minecraft:honey_bottle'], 'farmersdelight:honey_cookie', 8)
    stove('sweet_berry_cookie', ['#forge:flour', 'minecraft:sweet_berries', 'minecraft:sugar'], 'farmersdelight:sweet_berry_cookie', 8)
    stove('pumpkin_pie', ['farmersdelight:pie_crust', 'farmersdelight:pumpkin_slice', '#forge:eggs', 'minecraft:sugar'], 'minecraft:pumpkin_pie')
    stove('apple_pie', ['farmersdelight:pie_crust', 'minecraft:apple', 'minecraft:apple', 'minecraft:sugar'], 'farmersdelight:apple_pie')
    stove('chocolate_pie', ['farmersdelight:pie_crust', 'minecraft:cocoa_beans', '#forge:milk', 'minecraft:sugar'], 'farmersdelight:chocolate_pie')
    stove('cheesecake', ['farmersdelight:pie_crust', 'minecraft:sweet_berries', '#forge:cheeses', 'minecraft:sugar'], 'farmersdelight:sweet_berry_cheesecake')
    stove('cheese_bread', ['#forge:dough', '#forge:cheeses', '#forge:eggs'], 'sa_combat:cheese_bread', 2)
    stove('waffles', ['#forge:dough', '#forge:eggs', '#forge:milk', 'minecraft:sugar'], 'sa_combat:waffles', 2)
    stove('biscuit', ['#forge:flour', '#forge:butter', '#forge:milk'], 'sa_combat:biscuit', 4)
    stove('rice_cookie', ['farmersdelight:rice', 'minecraft:sugar', '#forge:eggs'], 'survival_instinct:rice_cookie', 4)
    stove('steak_and_potatoes', ['#forge:cooked_beef', 'minecraft:baked_potato', '#forge:crops/onion', 'farmersdelight:cooked_rice'], 'farmersdelight:steak_and_potatoes')
    # пиццы SCP:O — запекаются на плите F&C (несколько ингредиентов; в духовку Refurbished влезает один)
    P = ['#forge:dough', 'farmersdelight:tomato_sauce']
    for rid, extra, res in [
            ('margherita', ['candlelight:mozzarella', '#forge:salad_ingredients'], 'scpo:pizza_margherita'),
            ('neopolitan', ['#forge:crops/tomato', 'candlelight:mozzarella', '#forge:salad_ingredients'], 'scpo:pizza_neopolitan'),
            ('cheese', ['#forge:cheeses', '#forge:cheeses'], 'scpo:pizza_cheese'),
            ('pepperoni', ['#forge:cheeses', 'butchery:cooked_sausage'], 'scpo:pizza_pepperoni'),
            ('hawaiian', ['#forge:cheeses', 'butchery:raw_ham', 'sa_combat:canned_pineapple'], 'scpo:pizza_hawaiian'),
            ('chicken', ['#forge:cheeses', 'farmersdelight:cooked_chicken_cuts', 'farm_and_charm:corn'], 'scpo:pizza_chicken'),
            ('taco', ['#rpculinary:mince', 'farm_and_charm:corn', '#forge:crops/tomato'], 'scpo:pizza_taco'),
            ('olive_onion', ['#forge:cheeses', '#forge:crops/onion'], 'scpo:pizza_olive_onion'),
            ('greenpepper', ['#forge:cheeses', 'farmersdelight:cabbage_leaf'], 'scpo:pizza_greenpepper'),
            ('swedish', ['#forge:cheeses', 'alexsmobs:banana', 'farmersdelight:cooked_chicken_cuts'], 'scpo:pizza_swedish'),
            ('deepdish', ['#forge:dough', '#forge:cheeses', '#forge:cheeses', '#rpculinary:mince'], 'scpo:pizza_deepdish'),
            ('square', ['#forge:dough', '#forge:cheeses'], 'scpo:pizza_square')]:
        stove(f'pizza_{rid}', P + extra, res)
    stove('pizza_bianca', ['#forge:dough', '#forge:cheeses', '#forge:milk'], 'scpo:pizza_bianca')
    stove('pizza_calzone', ['#forge:dough', '#forge:cheeses', 'butchery:raw_ham'], 'scpo:pizza_colzone')
    stove('pizza_biscuit', ['#forge:dough', 'minecraft:sugar', '#forge:berries'], 'scpo:pizza_biscuit')

    # -------------------------------------------------- 5. жаровня F&C (roaster): блины, фастфуд
    roast('pancake', ['#forge:dough', '#forge:eggs', '#forge:milk', 'minecraft:sugar'], 'supplementaries:pancake', 4)
    roast('taco', ['#forge:dough', '#rpculinary:mince', '#forge:salad_ingredients', '#forge:crops/tomato', '#forge:cheeses'], 'sa_combat:taco', 2)
    roast('burrito', ['#forge:dough', '#rpculinary:mince', 'farm_and_charm:corn', '#forge:cheeses'], 'sa_combat:burrito', 2)
    roast('donut', ['#forge:dough', 'minecraft:sugar', 'butchery:animal_fat'], 'sa_combat:donut', 4)
    roast('chips', ['minecraft:potato', 'minecraft:potato', 'butchery:animal_fat', '#forge:salt'], 'sa_combat:chips', 2)
    roast('barbecue_stick', ['#forge:cooked_beef', '#forge:crops/onion', '#forge:crops/tomato', 'minecraft:stick'], 'farmersdelight:barbecue_stick')
    roast('roasted_mutton_chops', ['farmersdelight:mutton_chops', '#forge:crops/onion', '#forge:crops/tomato', 'farmersdelight:cooked_rice'], 'farmersdelight:roasted_mutton_chops')

    # -------------------------------------------------- 6. кастрюля FD: горячий шоколад, рагу AC/AM, праздничные блоки, консервы
    def pot(rid, ings, result, container=None, count=1, time=200):
        o = {'type': 'farmersdelight:cooking', 'cookingtime': time, 'experience': 1.0, 'ingredients': [I(x) for x in ings],
             'recipe_book_tab': 'meals', 'result': {'item': result, 'count': count}}
        if container:
            o['container'] = {'item': container}
        rec(f'pot/{rid}', o)
    for it in ['alexscaves:hot_chocolate_bottle', 'alexscaves:seething_stew', 'alexscaves:primordial_soup', 'alexsmobs:mosquito_repellent_stew',
               'farmersdelight:honey_glazed_ham_block', 'farmersdelight:shepherds_pie_block', 'farmersdelight:roast_chicken_block']:
        off_craft(it)
    pot('hot_chocolate', ['#forge:milk', 'alexscaves:block_of_chocolate', 'minecraft:sugar'], 'alexscaves:hot_chocolate_bottle', 'minecraft:glass_bottle', 3)
    pot('honey_glazed_ham', ['farmersdelight:smoked_ham', 'minecraft:honey_bottle', 'minecraft:sweet_berries', 'farmersdelight:cooked_rice'],
        'farmersdelight:honey_glazed_ham_block', 'minecraft:bowl', time=400)
    pot('shepherds_pie', ['#forge:cooked_mutton', 'minecraft:baked_potato', '#forge:milk', '#forge:crops/onion'], 'farmersdelight:shepherds_pie_block', 'minecraft:bowl', time=400)
    pot('roast_chicken', ['minecraft:chicken', 'minecraft:baked_potato', 'minecraft:carrot', '#forge:crops/onion', 'minecraft:bread'],
        'farmersdelight:roast_chicken_block', 'minecraft:bowl', time=400)
    # рецепты AC/AM переносим 1:1 (те же ингредиенты) — в кастрюлю
    for it in ['alexscaves:seething_stew', 'alexscaves:primordial_soup', 'alexsmobs:mosquito_repellent_stew']:
        src = [r for r in by_out.get(it, []) if r['type'] in CRAFT]
        if src:
            ings = [x for x in src[0]['in'] if x != 'minecraft:bowl'][:6]
            pot(it.split(':')[1], ings, it, 'minecraft:bowl')
    # консервирование: блюдо/продукт + банка
    CAN, SICAN = 'sa_core:empty_can', 'survival_instinct:empty_can'
    for it in ['sa_combat:canned_corned_beef', 'sa_combat:canned_carrots', 'sa_combat:canned_chilli', 'sa_combat:canned_sardines',
               'sa_combat:canned_tuna', 'sa_combat:canned_fruit_cocktail', 'sa_combat:canned_mushroom_soup', 'sa_combat:canned_vegetable_soup',
               'survival_instinct:meat_can', 'survival_instinct:fruit_can']:
        off_craft(it)
    for rid, ings, res, c in [
            ('corned_beef', ['#forge:cooked_beef', '#forge:salt'], 'sa_combat:canned_corned_beef', CAN),
            ('carrots', ['minecraft:carrot', 'minecraft:carrot', '#forge:salt'], 'sa_combat:canned_carrots', CAN),
            ('chilli', ['#forge:cooked_beef', 'minecraft:beetroot', '#forge:crops/tomato'], 'sa_combat:canned_chilli', CAN),
            ('sardines', ['minecraft:cod', '#forge:salt'], 'sa_combat:canned_sardines', CAN),
            ('tuna', ['minecraft:salmon', '#forge:salt'], 'sa_combat:canned_tuna', CAN),
            ('fruit_cocktail', ['minecraft:apple', 'minecraft:melon_slice', 'minecraft:sugar'], 'sa_combat:canned_fruit_cocktail', CAN),
            ('mushroom_soup', ['minecraft:mushroom_stew'], 'sa_combat:canned_mushroom_soup', CAN),
            ('vegetable_soup', ['minecraft:beetroot_soup'], 'sa_combat:canned_vegetable_soup', CAN),
            ('corn', ['farm_and_charm:corn', 'farm_and_charm:corn', '#forge:salt'], 'sa_combat:canned_corn', CAN),
            ('spaghetti', ['candlelight:pasta_with_bolognese'], 'sa_combat:canned_spaghetti_bolognese', CAN),
            ('fruit_beverage', ['vinery:apple_juice', 'minecraft:sugar'], 'sa_combat:canned_fruit_beverage', CAN),
            ('dog_food', ['butchery:meat_scraps', 'butchery:meat_scraps'], 'sa_combat:canned_dog_food', CAN),
            ('stew_ze', ['farmersdelight:beef_stew'], 'zombie_extreme:canned_stew', CAN),
            ('soup_ze', ['farmersdelight:vegetable_soup'], 'zombie_extreme:canned_soup', CAN),
            ('meat_si', ['#forge:cooked_beef', '#forge:cooked_pork', '#forge:salt'], 'survival_instinct:meat_can', SICAN),
            ('fruit_si', ['minecraft:apple', 'minecraft:glow_berries', 'minecraft:melon_slice'], 'survival_instinct:fruit_can', SICAN)]:
        pot(f'can_{rid}', ings, res, c, time=300)

    # -------------------------------------------------- 7. соки — пресс Vinery (apple_mashing), без воды
    for it, src in [('legendarysurvivaloverhaul:carrot_juice', 'minecraft:carrot'), ('legendarysurvivaloverhaul:beetroot_juice', 'minecraft:beetroot'),
                    ('legendarysurvivaloverhaul:pumpkin_juice', 'farmersdelight:pumpkin_slice'), ('legendarysurvivaloverhaul:melon_juice', 'minecraft:melon_slice'),
                    ('legendarysurvivaloverhaul:sweet_berries_juice', 'minecraft:sweet_berries'), ('legendarysurvivaloverhaul:glow_berries_juice', 'minecraft:glow_berries'),
                    ('legendarysurvivaloverhaul:chorus_fruit_juice', 'minecraft:chorus_fruit'), ('legendarysurvivaloverhaul:cactus_juice', 'minecraft:cactus'),
                    ('legendarysurvivaloverhaul:golden_carrot_juice', 'minecraft:golden_carrot'), ('legendarysurvivaloverhaul:golden_apple_juice', 'minecraft:golden_apple'),
                    ('legendarysurvivaloverhaul:glistering_melon_juice', 'minecraft:glistering_melon_slice'), ('farmersdelight:melon_juice', None)]:
        off_craft(it)
        if src:
            rec(f'press/{it.split(":")[1]}', {'type': 'vinery:apple_mashing', 'input': I(src), 'output': {'item': it, 'count': 1}})
    off_craft('legendarysurvivaloverhaul:apple_juice')

    # -------------------------------------------------- 8. мясо: фарш — мясорубка F&C; бекон, курица — доска FD
    def cut(rid, ing, results, tool='#forge:tools/knives'):
        rec(f'cutting/{rid}', {'type': 'farmersdelight:cutting', 'ingredients': [I(ing)], 'tool': I(tool) if tool.startswith('#') else {'item': tool},
                               'result': [dict({'item': i, 'count': n}, **({'chance': ch} if ch < 1 else {})) for i, n, ch in results]})
    # FD: говядина ножом → «фарш» (это сырая котлета) — убрать; бекон из свинины — убрать; F&C: бекон/окорок/курица из мясорубки — убрать
    for r in D['recipes']:
        if r['type'] == 'farmersdelight:cutting' and set(r['out']) & {'farmersdelight:minced_beef', 'farmersdelight:bacon', 'farmersdelight:mutton_chops', 'farmersdelight:ham'}:
            disable(r['id'])
        if r['type'] == 'farm_and_charm:mincer' and set(r['out']) & {'farm_and_charm:bacon', 'farm_and_charm:lamb_ham', 'farm_and_charm:chicken_parts'}:
            disable(r['id'])
        if r['type'] == 'farmersdelight:cutting' and r['id'].endswith(('beef_conversion', 'pork_conversion', 'mutton_conversion', 'chicken_conversion')):
            disable(r['id'])
    # Сырая котлета — из фарша на доске
    cut('raw_patty', '#rpculinary:mince', [('farmersdelight:minced_beef', 1, 1)])
    # Крупные куски → порции. Кусок с весом в NBT режет RP Culinary по весу (Butchering.onBoard, блок 4); рецепт — для JEI и кусков без веса
    for src, res, n, extra in [
            ('butchery:raw_chuck_steak', 'minecraft:beef', 24, [('butchery:meat_scraps', 2, 1)]),
            ('butchery:raw_rump_steak', 'minecraft:beef', 20, [('butchery:meat_scraps', 2, 1)]),
            ('butchery:raw_ribeye_steak', 'minecraft:beef', 1, [('butchery:meat_scraps', 1, 0.6)]),
            ('butchery:raw_tbone_steak', 'minecraft:beef', 1, [('minecraft:bone', 1, 1)]),
            ('butchery:raw_sirloin_steak', 'minecraft:beef', 1, [('butchery:meat_scraps', 1, 0.6)]),
            ('butchery:raw_pork_shoulder', 'minecraft:porkchop', 4, [('butchery:meat_scraps', 1, 1)]),
            ('butchery:raw_pork_loin', 'minecraft:porkchop', 1, [('butchery:meat_scraps', 1, 0.6)]),
            ('butchery:raw_pork_leg', 'minecraft:porkchop', 2, [('minecraft:bone', 1, 1)]),
            ('butchery:raw_ham', 'minecraft:porkchop', 6, [('minecraft:bone', 1, 1)]),
            ('butchery:raw_lamb_shoulder', 'minecraft:mutton', 2, []),
            ('farm_and_charm:lamb_ham', 'minecraft:mutton', 2, [('minecraft:bone', 1, 1)]),
            ('butchery:raw_lamb_loin', 'farmersdelight:mutton_chops', 2, []),
            ('butchery:raw_lamb_rib', 'farmersdelight:mutton_chops', 2, [('minecraft:bone', 1, 1)]),
            ('butchery:raw_lamb_sirloin', 'minecraft:mutton', 1, [('butchery:meat_scraps', 1, 0.6)])]:
        cut(src.split(':')[1], src, [(res, n, 1)] + extra)
    cut('pork_belly', 'butchery:raw_pork_belly', [('farmersdelight:bacon', 20, 1)])
    cut('chicken_carcass', 'minecraft:chicken', [('farmersdelight:chicken_cuts', 2, 1), ('farm_and_charm:chicken_parts', 2, 1), ('minecraft:bone', 1, 1)])
    cut('chicken_leg', 'butchery:raw_chicken_leg', [('farm_and_charm:chicken_parts', 2, 1), ('minecraft:bone', 1, 0.5)])
    cut('cod_fillet', 'butchery:raw_cod_fillet', [('farmersdelight:cod_slice', 6, 1)])
    cut('salmon_fillet', 'butchery:raw_salmon', [('farmersdelight:salmon_slice', 6, 1)])
    cut('cod_whole', 'minecraft:cod', [('butchery:raw_cod_fillet', 2, 1), ('minecraft:bone_meal', 1, 1)])
    cut('salmon_whole', 'minecraft:salmon', [('butchery:raw_salmon', 2, 1), ('minecraft:bone_meal', 1, 1)])
    for r in D['recipes']:  # FD: целая рыба ножом сразу в ломтики — теперь через филе
        if r['type'] == 'farmersdelight:cutting' and r['in'] in (['minecraft:cod'], ['minecraft:salmon'], ['#forge:raw_fishes/cod'], ['#forge:raw_fishes/salmon']):
            disable(r['id'])
    # Фарш — мясорубка F&C из любого мяса: цвет по виду (решение 29.10)
    def mince(rid, ing, res):
        rec(f'mincer/{rid}', {'type': 'farm_and_charm:mincer', 'ingredient': I(ing), 'recipe_type': 'MEAT', 'result': {'item': res, 'count': 1}})
    DARK, RED, PINK = 'farm_and_charm:minced_beef', 'butchery:raw_beef_mince', 'butchery:raw_lamb_mince'
    for ing, res in [('minecraft:beef', DARK), ('butchery:raw_horse_meat', DARK), ('alexsmobs:moose_ribs', DARK), ('alexsmobs:kangaroo_meat', DARK),
                     ('minecraft:mutton', RED), ('butchery:raw_donkey_steak', RED), ('butchery:raw_mule_steak', RED), ('butchery:raw_llama_steak', RED),
                     ('butchery:raw_camel_meat', RED), ('butchery:raw_hoglin_chunk', RED), ('butchery:raw_polar_bear_meat', RED),
                     ('butchery:raw_panda_steak', RED), ('alexscaves:dinosaur_chop', RED), ('butchery:raw_wolf_meat', RED),
                     ('minecraft:porkchop', PINK), ('farm_and_charm:chicken_parts', PINK), ('farmersdelight:chicken_cuts', PINK),
                     ('minecraft:rabbit', PINK), ('butchery:raw_fox_meat', PINK), ('butchery:raw_cat_meat', PINK),
                     ('butchery:raw_ocelot_meat', PINK), ('butchery:meat_scraps', PINK)]:
        mince(ing.split(':')[1], ing, res)

    # Мухомор (ванильный красный гриб) — ядовитый (решение 29.10): где он нужен по рецепту, есть вариант из шампиньонов
    for rid in ('farmersdelight:cooking/mushroom_stew', 'farmersdelight:cooking/mushroom_rice'):
        ns, path = rid.split(':', 1)
        orig = read_json(f'data/{ns}/recipes/{path}.json')
        if orig:
            obj = json.loads(json.dumps(orig).replace('"minecraft:red_mushroom"', '"minecraft:brown_mushroom"'))
            obj.pop('conditions', None)
            rec(f'cooking/{path.split("/")[-1]}_brown', obj)

    # -------------------------------------------------- 9. соль, водка
    rec('mortar/rock_salt', {'type': 'butchery:pestleandmortarrecipetype', 'ingredients': [{'item': 'blocksyouneed_luna:rock_salt'}],
                             'output': {'item': 'butchery:salt', 'count': 2}})
    rec('brewing/vodka', {'type': 'brewery:brewing', 'ingredients': [I('brewery:dried_wheat'), I('minecraft:potato'), I('farm_and_charm:yeast')],
                          'material': 'WOOD', 'result': {'item': 'sa_combat:vodka'}})
