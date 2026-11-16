# ЗАЩИТА ОТ ЗАПУСКА (16.11.2026): скрипт не знает поле per_grams и перезапишет датапак состава без него.
import sys as _guard_sys
if "--force" not in _guard_sys.argv:
    raise SystemExit("Скрипт отстал от файлов, которые он перезаписывает: скрипт не знает поле per_grams и перезапишет датапак состава без него. Сначала сверь; запуск всё равно — с ключом --force.")
_guard_sys.argv.remove("--force")
# Датапак RP Culinary по умолчанию: состав базовых ингредиентов (rpculinary/nutrition).
# Напитки (сколько поят) с Culinary 0.4.0 — в общей базе RP Core (rpcore/consumables, scripts/gen_consumables.py там);
# свой датапак rpculinary/drinks остаётся для сторонних наборов, по умолчанию пуст.
# Перенесено из RP Medicine (scripts/gen_data.py) в 0.3.0; дополнено продуктами Kaleidoscope и Farmer's Delight.
# Запуск: python scripts/gen_nutrition.py — перезаписывает src/main/resources/data/rpculinary/rpculinary/nutrition (и стирает старую drinks).
# Руками JSON там не править: правки — здесь.
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data", "rpculinary", "rpculinary")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


for sub in ("nutrition", "drinks"):
    shutil.rmtree(os.path.join(DATA, sub), ignore_errors=True)

WINES = ["aegis_wine", "apple_wine", "bolvar_wine", "bottle_mojang_noir", "chenet_wine", "cherry_wine", "chorus_wine", "clark_wine",
         "cristel_wine", "glowing_wine", "jellie_wine", "lilitu_wine", "magnetic_wine", "mellohi_wine", "noir_wine", "red_wine",
         "solaris_wine", "stal_wine", "strad_wine", "villagers_fright"]
WHISKEY = ["whiskey_ak", "whiskey_carrasconlabel", "whiskey_cristelwalker", "whiskey_highland_hearth", "whiskey_jamesons_malt",
           "whiskey_jojannik", "whiskey_lilitusinglemalt", "whiskey_maggoallan", "whiskey_smokey_reverie"]

# Питание: базовые ингредиенты (не крафтятся, идут в рецепты). Порция — один предмет: ккал (0 — по БЖУ),
# белки, жиры, углеводы (г), витамины (условные единицы). Состав блюд мод считает сам по рецептам.
def mc(*ids): return [f"minecraft:{i}" for i in ids]
def ns(n, *ids): return [f"{n}:{i}" for i in ids]
BUTCHERY_MEAT = ["raw_bat_meat", "raw_bee_back_meat", "raw_camel_meat", "raw_cat_meat", "raw_chuck_steak", "raw_donkey_steak",
                 "raw_dragon_meat", "raw_elder_guardian_meat", "raw_enderman_steak", "raw_evoker_meat", "raw_fox_meat", "raw_guardian_meat",
                 "raw_hoglin_chunk", "raw_horse_meat", "raw_lamb_loin", "raw_lamb_rib", "raw_lamb_shoulder", "raw_lamb_sirloin",
                 "raw_leg_of_lamb", "raw_llama_steak", "raw_mule_steak", "raw_ocelot_meat", "raw_panda_steak", "raw_pillager_meat",
                 "raw_polar_bear_meat", "raw_pork_belly", "raw_pork_leg", "raw_pork_loin", "raw_pork_shoulder", "raw_ravager_meat",
                 "raw_ribeye_steak", "raw_rump_steak", "raw_shulker_meat", "raw_sirloin_steak", "raw_sniffer_steak", "raw_strider_meat",
                 "raw_tbone_steak", "raw_turtle_meat", "raw_villager_steak", "raw_vindicator_meat", "raw_warden_meat", "raw_witch_meat",
                 "raw_wolf_meat", "rawhumanmeat", "raw_ham", "raw_dolphin_meat", "raw_creeper_steak"]
BUTCHERY_SMALL = ["raw_chicken_leg", "raw_chicken_wing", "raw_cave_spider_leg", "raw_spider_leg", "raw_creeper_leg", "raw_gray_frog_leg",
                  "raw_green_frog_leg", "raw_orange_frog_leg", "raw_endermite_chunks", "raw_silverfish_chunks"]
BUTCHERY_FISH = ["raw_cod_fillet", "raw_blue_axolotl_fillet", "raw_brown_axolotl_fillet", "raw_cyan_axolotl_fillet", "raw_gold_axolotl_fillet",
                 "raw_pink_axolotl_fillet", "raw_pufferfish", "raw_salmon", "calamari"]
STARDEW_FISH = ["blazing_oarfish", "chromatic_arapaima", "crystalline_snakehead", "cyclops_mahimahi", "demon_gar", "golden_snook",
                "goliath_grouper", "sabretoothed_tigerfish", "storm_tarpon", "vampire_payara"]
GRAPES = ["red_grape", "white_grape", "jungle_grapes_red", "jungle_grapes_white", "savanna_grapes_red", "savanna_grapes_white",
          "taiga_grapes_red", "taiga_grapes_white"]
NUTRITION = {
    # Зерно и мука
    "grain_wheat": (mc("wheat"), 0, 12, 2, 70, 2),
    "grain_other": (ns("farm_and_charm", "barley", "oat") + ns("farmersdelight", "rice"), 0, 11, 3, 68, 3),
    "corn": (ns("farm_and_charm", "corn"), 0, 3, 1, 19, 3),
    # Овощи
    "potato": (mc("potato"), 0, 2, 0, 20, 6),
    "potato_poison": (mc("poisonous_potato"), 0, 1, 0, 10, 0),
    "carrot": (mc("carrot"), 0, 1, 0, 10, 10),
    "beetroot": (mc("beetroot"), 0, 2, 0, 10, 8),
    "pumpkin": (mc("pumpkin"), 0, 4, 0, 26, 20),
    "tomato": (ns("farmersdelight", "tomato") + ns("farm_and_charm", "tomato") + ns("kaleidoscope_cookery", "tomato")
               + ["#forge:crops/tomato", "#forge:vegetables/tomato"], 0, 1, 0, 5, 8),
    "peppers": (ns("kaleidoscope_cookery", "red_chili", "green_chili") + ["#forge:crops/chili", "#forge:crops/chilipepper"], 0, 1, 0, 5, 15),
    "cabbage": (ns("farmersdelight", "cabbage") + ns("farm_and_charm", "lettuce") + ns("kaleidoscope_cookery", "lettuce")
                + ["#forge:crops/lettuce"], 0, 1, 0, 6, 12),
    "onion": (ns("farmersdelight", "onion") + ns("farm_and_charm", "onion"), 0, 1, 0, 9, 6),
    "mushroom": (mc("brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus"), 0, 2, 0, 3, 3),
    "kelp": (mc("kelp"), 0, 1, 0, 3, 4),
    # Фрукты и ягоды
    "apple": (mc("apple"), 0, 0, 0, 25, 10),
    "melon": (mc("melon_slice"), 0, 0, 0, 8, 6),
    "berries": (mc("sweet_berries", "glow_berries") + ns("farm_and_charm", "strawberry") + ns("vinery", "cherry"), 0, 0, 0, 6, 8),
    "grapes": ([f"vinery:{g}" for g in GRAPES] + ns("kaleidoscope_tavern", "grape", "green_grape", "ice_grape", "gold_grape"), 0, 1, 0, 17, 8),
    "chorus": (mc("chorus_fruit"), 0, 1, 0, 15, 3),
    # Сладкое
    "sugar": (mc("sugar", "sugar_cane"), 0, 0, 0, 10, 0),
    "honey": (mc("honey_bottle"), 0, 0, 0, 80, 1),
    "cocoa": (mc("cocoa_beans"), 0, 2, 5, 6, 1),
    # Животное
    "egg": (mc("egg"), 0, 6, 5, 1, 2),
    "milk": (mc("milk_bucket"), 0, 32, 36, 48, 4),
    "butter": (ns("farm_and_charm", "butter"), 0, 1, 80, 0, 2),
    "cheese": (ns("candlelight", "mozzarella"), 0, 22, 22, 2, 2),
    "yeast": (ns("farm_and_charm", "yeast"), 0, 4, 0, 2, 2),
    "beef": (mc("beef"), 0, 52, 30, 0, 2),
    "ham": (ns("farmersdelight", "ham", "smoked_ham"), 0, 45, 25, 0, 2),
    "pork": (mc("porkchop"), 0, 50, 40, 0, 2),
    "chicken": (mc("chicken"), 0, 46, 14, 0, 2),
    "mutton": (mc("mutton"), 0, 48, 40, 0, 2),
    "rabbit": (mc("rabbit"), 0, 40, 8, 0, 2),
    "fish": (mc("cod", "tropical_fish", "pufferfish") + [f"stardew_fishing:{f}" for f in STARDEW_FISH], 0, 35, 4, 0, 3),
    "fish_fat": (mc("salmon"), 0, 40, 25, 0, 4),
    "rotten": (mc("rotten_flesh"), 0, 20, 10, 0, 0),
    "spider_eye": (mc("spider_eye"), 0, 6, 2, 0, 0),
    "butchery_meat": ([f"butchery:{i}" for i in BUTCHERY_MEAT], 0, 45, 25, 0, 2),
    "butchery_small": ([f"butchery:{i}" for i in BUTCHERY_SMALL], 0, 22, 10, 0, 1),
    "butchery_fish": ([f"butchery:{i}" for i in BUTCHERY_FISH], 0, 30, 5, 0, 3),
    "butchery_mince": (ns("butchery", "raw_beef_mince", "raw_lamb_mince"), 0, 40, 30, 0, 2),
    "butchery_sausage": (ns("butchery", "raw_sausage", "raw_blood_sausage"), 0, 22, 28, 6, 2),
    "butchery_organs": (ns("butchery", "heart", "kidney", "lungs", "stomach", "intestines", "brain"), 0, 30, 8, 1, 10),
    "butchery_liver": (ns("butchery", "liver"), 0, 30, 6, 4, 25),
    "butchery_fat": (ns("butchery", "animal_fat", "crackling"), 0, 10, 80, 0, 0),
    "blood": (ns("butchery", "bottle_of_blood"), 0, 8, 1, 0, 2),
    # Напитки (калории спирта — ккал)
    "beer": (ns("brewery", "beer_barley", "beer_haley", "beer_hops", "beer_nettle", "beer_oat", "beer_wheat"), 150, 1, 0, 12, 1),
    "spirits": ([f"brewery:{w}" for w in WHISKEY] + ns("brewery", "dark_brew"), 250, 0, 0, 0, 0),
    "wine": ([f"vinery:{w}" for w in WINES] + ns("vinery", "apple_cider", "kelp_cider", "mead"), 120, 0, 0, 4, 1),
    "juice": (ns("vinery", "apple_juice", "red_grapejuice", "white_grapejuice", "red_jungle_grapejuice", "white_jungle_grapejuice",
                 "red_savanna_grapejuice", "white_savanna_grapejuice", "red_taiga_grapejuice", "white_taiga_grapejuice"), 0, 0, 0, 25, 10),
    "coffee": (ns("herbalbrews", "coffee"), 0, 0, 0, 2, 0),
    "coffee_milk": (ns("herbalbrews", "milk_coffee"), 0, 3, 3, 5, 0),
    "tea": ([f"herbalbrews:{t}_tea" for t in ["black", "green", "oolong", "yerba_mate", "hibiscus", "lavender", "rooibos"]], 0, 0, 0, 1, 3),
    # Let's Do: сырьё для чая, кофе и пива (в рецепты; сами по себе почти не питают).
    "tea_leaves": (ns("herbalbrews", "tea_blossom", "yerba_mate_leaf", "rooibos_leaf", "dried_green_tea", "dried_black_tea",
                      "dried_oolong_tea", "hibiscus", "lavender") + ns("farm_and_charm", "nettle", "ribwort"), 0, 0, 0, 1, 2),
    "coffee_beans": (ns("herbalbrews", "coffee_beans"), 0, 1, 1, 2, 0),
    "hops": (ns("brewery", "hops"), 0, 0, 0, 1, 1),
}
# Вид еды для «приелось»; напитки и пищевые добавки не надоедают (exempt).
FOOD_CATEGORY = {
    "grain_wheat": "grain", "grain_other": "grain", "corn": "grain", "potato": "vegetables", "potato_poison": "vegetables",
    "carrot": "vegetables", "beetroot": "vegetables", "pumpkin": "vegetables", "tomato": "vegetables", "peppers": "vegetables", "cabbage": "vegetables",
    "onion": "vegetables", "mushroom": "mushrooms", "kelp": "vegetables", "apple": "fruit", "melon": "fruit", "berries": "fruit",
    "grapes": "fruit", "chorus": "fruit", "sugar": "sweet", "honey": "sweet", "cocoa": "sweet", "egg": "eggs", "milk": "dairy",
    "butter": "dairy", "cheese": "dairy", "beef": "meat", "ham": "meat", "pork": "meat", "chicken": "meat", "mutton": "meat", "rabbit": "meat",
    "fish": "fish", "fish_fat": "fish", "rotten": "meat", "spider_eye": "meat", "butchery_meat": "meat", "butchery_small": "meat",
    "butchery_fish": "fish", "butchery_mince": "meat", "butchery_sausage": "meat", "butchery_organs": "meat", "butchery_liver": "meat",
    "butchery_fat": "meat",
}
for name, (items, kcal, prot, fat, carbs, vit) in NUTRITION.items():
    obj = {"items": items, "protein": prot, "fat": fat, "carbs": carbs, "vitamins": vit}
    if name in FOOD_CATEGORY:
        obj["category"] = FOOD_CATEGORY[name]
    else:
        obj["exempt"] = True
    if kcal:
        obj["kcal"] = kcal
    write(f"{DATA}/nutrition/{name}.json", obj)
