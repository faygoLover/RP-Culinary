# Мешки и ящики с продуктами (блок 6 плана, решения Alex 29.10 §6): стак 1, вместимость по весу, наполняются продуктом,
# поставленный блок хранит содержимое. Пустой — тот же предмет без содержимого: мешок шьют из шерсти, ящик — из досок
# (+1 продукт, чтобы было видно, для чего тара). Упаковка «9 продуктов → тара» и распаковка «тара → 9» убираются.
# Таблица → assets/rpculinary/containers.json (gen_food_pack.py), рецепты — recipes_spec.py.

BAG, CRATE, SACK, BALE = 25, 30, 20, 20  # кг
WOOL, PLANKS = '#minecraft:wool', '#minecraft:planks'
TOMATO, ONION = ['farmersdelight:tomato', 'farm_and_charm:tomato'], ['farmersdelight:onion', 'farm_and_charm:onion']

# тара → (что принимает, кг, из чего шьётся/сбивается)
CONTAINERS = {
    'farm_and_charm:beetroot_bag': (['minecraft:beetroot'], BAG, WOOL),
    'farm_and_charm:carrot_bag': (['minecraft:carrot'], BAG, WOOL),
    'farm_and_charm:cat_food_bag': (['farm_and_charm:cat_food'], BAG, WOOL),
    'farm_and_charm:corn_bag': (['farm_and_charm:corn'], BAG, WOOL),
    'farm_and_charm:dog_food_bag': (['farm_and_charm:dog_food'], BAG, WOOL),
    'farm_and_charm:flour_bag': (['farm_and_charm:flour'], BAG, WOOL),
    'farm_and_charm:lettuce_bag': (['farm_and_charm:lettuce'], BAG, WOOL),
    'farm_and_charm:onion_bag': (ONION, BAG, WOOL),
    'farm_and_charm:potato_bag': (['minecraft:potato'], BAG, WOOL),
    'farm_and_charm:strawberry_bag': (['farm_and_charm:strawberry'], BAG, WOOL),
    'farm_and_charm:tomato_bag': (TOMATO, BAG, WOOL),
    'farmersdelight:beetroot_crate': (['minecraft:beetroot'], CRATE, PLANKS),
    'farmersdelight:cabbage_crate': (['farmersdelight:cabbage'], CRATE, PLANKS),
    'farmersdelight:carrot_crate': (['minecraft:carrot'], CRATE, PLANKS),
    'farmersdelight:onion_crate': (ONION, CRATE, PLANKS),
    'farmersdelight:potato_crate': (['minecraft:potato'], CRATE, PLANKS),
    'farmersdelight:tomato_crate': (TOMATO, CRATE, PLANKS),
    'farmersdelight:rice_bag': (['farmersdelight:rice'], BAG, WOOL),
    'farmersdelight:rice_bale': (['farmersdelight:rice_panicle'], BALE, '#forge:string'),
    'herbalbrews:tea_leaf_crate': (['herbalbrews:green_tea_leaf'], CRATE, PLANKS),
    'quark:apple_crate': (['minecraft:apple'], CRATE, PLANKS),
    'quark:beetroot_crate': (['minecraft:beetroot'], CRATE, PLANKS),
    'quark:carrot_crate': (['minecraft:carrot'], CRATE, PLANKS),
    'quark:potato_crate': (['minecraft:potato'], CRATE, PLANKS),
    'quark:golden_apple_crate': (['minecraft:golden_apple'], CRATE, PLANKS),
    'quark:golden_carrot_crate': (['minecraft:golden_carrot'], CRATE, PLANKS),
    'quark:berry_sack': (['minecraft:sweet_berries'], SACK, WOOL),
    'quark:cocoa_beans_sack': (['minecraft:cocoa_beans'], SACK, WOOL),
    'quark:glowberry_sack': (['minecraft:glow_berries'], SACK, WOOL),
    'quark:nether_wart_sack': (['minecraft:nether_wart'], SACK, WOOL),
    'vinery:apple_bag': (['minecraft:apple'], BAG, WOOL),
    'vinery:cherry_bag': (['vinery:cherry'], BAG, WOOL),
    'vinery:red_grape_bag': (['vinery:red_grape'], BAG, WOOL),
    'vinery:red_jungle_grape_bag': (['vinery:jungle_grapes_red'], BAG, WOOL),
    'vinery:red_savanna_grape_bag': (['vinery:savanna_grapes_red'], BAG, WOOL),
    'vinery:red_taiga_grape_bag': (['vinery:taiga_grapes_red'], BAG, WOOL),
    'vinery:white_grape_bag': (['vinery:white_grape'], BAG, WOOL),
    'vinery:white_jungle_grape_bag': (['vinery:jungle_grapes_white'], BAG, WOOL),
    'vinery:white_savanna_grape_bag': (['vinery:savanna_grapes_white'], BAG, WOOL),
    'vinery:white_taiga_grape_bag': (['vinery:taiga_grapes_white'], BAG, WOOL),
}
