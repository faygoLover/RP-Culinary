# Предложение новых групп экзотики и групп рецептов (29.10.2026). Источник документа docs/groups_2026-10-29.md
# (tools/gen_groups_doc.py). Запись без «:» — все предметы с таким id в любом моде; с «:» — ровно этот предмет.
# Колонки «ингредиент» и «еда» скрипт делит сам: ингредиент — предмет входит хотя бы в один рецепт еды (cands.json, nin > 0).

GROUPS = [
    dict(key='game', title='Дичь и редкие животные',
         why='Мясо диких и редких зверей, птиц, рептилий и земноводных, включая реликтовых из Alex\'s Caves. Это не скотина с фермы.',
         effect='Сырое или недожаренное заражает **паразитами** (трихинеллёз, как от медвежатины в жизни). Признаки появляются через 1–2 RP-суток: '
                'боли в мышцах, слабость, температура (Medicine, лёгкая инфекция). Если мясо прожарено правильно, ничего не будет.',
         knowledge='Без знания чаще выходит «недожарено». Знание — книга или перк «Охотник» (сейчас его нет).',
         items=['kangaroo_meat', 'cooked_kangaroo_meat', 'kangaroo_burger', 'moose_ribs', 'cooked_moose_ribs',
                'raw_camel_meat', 'cooked_camel_meat', 'raw_llama_steak', 'cooked_llama_steak',
                'raw_polar_bear_meat', 'cooked_polar_bear_meat', 'raw_panda_steak', 'cooked_panda_steak',
                'raw_sniffer_steak', 'cooked_sniffer_steak', 'raw_turtle_meat', 'cooked_turtle_meat',
                'raw_dolphin_meat', 'cooked_dolphin_meat', 'raw_bat_meat', 'cooked_bat_meat', 'raw_fox_meat', 'cooked_fox_meat',
                'raw_gray_frog_leg', 'cooked_gray_frog_leg', 'raw_green_frog_leg', 'cooked_green_frog_leg',
                'raw_orange_frog_leg', 'cooked_orange_frog_leg', 'frog_heart',
                'raw_blue_axolotl_fillet', 'cooked_blue_axolotl_fillet', 'raw_brown_axolotl_fillet', 'cooked_brown_axolotl_fillet',
                'raw_cyan_axolotl_fillet', 'cooked_cyan_axolotl_fillet', 'raw_gold_axolotl_fillet', 'cooked_gold_axolotl_fillet',
                'raw_pink_axolotl_fillet', 'cooked_pink_axolotl_fillet',
                'emu_egg', 'boiled_emu_egg', 'caiman_egg', 'crocodile_egg', 'platypus_egg', 'terrapin_egg', 'minecraft:turtle_egg',
                'dinosaur_chop', 'cooked_dinosaur_chop', 'atlatitan_egg', 'grottoceratops_egg', 'relicheirus_egg',
                'subterranodon_egg', 'tremorsaurus_egg', 'vallumraptor_egg', 'primordial_soup',
                'raw_horse_meat', 'cooked_horse_meat', 'raw_donkey_steak', 'cooked_donkey_steak', 'raw_mule_steak', 'cooked_mule_steak'],
         book=dict(kind='journal', skill=3), answers='конина, ослятина, мулятина — сюда; динозавры остаются здесь (29.10)'),

    dict(key='pets', title='Домашние питомцы',
         why='Животные, которых держат рядом с собой: кошка, собака (волк), оцелот.',
         effect='**Моральный стресс** (Psyche), если игрок не голодает. При сильном голоде стресса нет: это выживание. Тело не страдает.',
         knowledge='Знание не нужно: готовится как обычное мясо.',
         items=['raw_cat_meat', 'cooked_cat_meat', 'raw_wolf_meat', 'cooked_wolf_meat', 'raw_ocelot_meat', 'cooked_ocelot_meat']),

    dict(key='pet_food', title='Корм для животных',
         why='Собачий и кошачий корм, куриный корм. Человек это есть может, но вряд ли захочет. «Киббл» сюда не входит: это корм для людей.',
         effect='Небольшой **стресс от унижения** (Psyche), меньше при голоде. Питательность обычная, сухой корм надоедает быстро.',
         knowledge='Знание не нужно.',
         items=['dog_food', 'cat_food', 'sa_combat:canned_dog_food', 'farm_and_charm:chicken_feed']),

    dict(key='seafood', title='Моллюски и ракообразные',
         why='Мидии, кальмары, омары, креветки, морские огурцы, щитни. Портятся быстрее рыбы.',
         effect='Недоготовленное даёт **сильное пищевое отравление** (рвота, обезвоживание). '
                'Можно добавить негативный перк «Аллергия на морепродукты»: у него сыпь и отёк даже от хорошо приготовленного.',
         knowledge='Без знания чаще выходит «недожарено».', book=dict(kind='journal', skill=3),
         items=['cooked_mussel', 'lobster_tail', 'cooked_lobster_tail', 'shrimp_fried_rice', 'calamari', 'tentacles',
                'minecraft:sea_pickle', 'triops_eggs', 'trilocaris_tail', 'cooked_trilocaris_tail', 'squid_ink_pasta']),

    dict(key='fugu', title='Ядовитая рыба (иглобрюх)',
         why='Иглобрюх (фугу): ваниль, Butchery и его туша. Яд тетродотоксин сидит в печени и коже.',
         effect='Без знания — **паралич**: сильное замедление, слабость, тяжело дышать (выносливость на нуле). '
                'Через 2–5 минут проходит; если игрок без сознания и один, может умереть (Medicine). Со знанием рыба безопасна.',
         knowledge='Знание обязательно: это единственная группа, где «знание» решает, жить или нет.', book=dict(kind='journal', skill=7),
         items=['minecraft:pufferfish', 'butchery:pufferfish', 'raw_pufferfish', 'cooked_pufferfish']),

    dict(key='deep_sea', title='Глубоководная и редкая рыба',
         why='Рыба из глубин и пещер Alex\'s Caves и Mobs, легендарная рыба Stardew Fishing.',
         effect='**Без эффекта** на едока (решение Alex 29.10).',
         knowledge='Без знания готовится хуже.', book=dict(kind='journal', skill=4),
         items=['lanternfish', 'cooked_lanternfish', 'tripodfish', 'cooked_tripodfish', 'sea_pig', 'deep_sea_sushi_roll',
                'blobfish', 'flying_fish', 'stardew_fishing:blazing_oarfish',
                'stardew_fishing:chromatic_arapaima', 'stardew_fishing:crystalline_snakehead', 'stardew_fishing:cyclops_mahimahi',
                'stardew_fishing:demon_gar', 'stardew_fishing:golden_snook', 'stardew_fishing:goliath_grouper',
                'stardew_fishing:sabretoothed_tigerfish', 'stardew_fishing:storm_tarpon', 'stardew_fishing:vampire_payara'],
         answers='сом — обычная рыба, убран (29.10)'),

    dict(key='insects', title='Насекомые и членистоногие',
         why='Личинки, куколки, пчёлы, чешуйницы, пауки, паучьи глаза.',
         effect='**Отвращение** (стресс, Psyche). С привычкой оно слабеет: после N порций за неделю его нет. '
                'Белка много. Пещерный паук и паучий глаз сырыми травят ядом.',
         knowledge='Знание не нужно для еды, но без него хуже готовится.', book=dict(kind='journal', skill=2),
         items=['maggot', 'leafcutter_ant_pupa', 'raw_bee_back_meat', 'cooked_bee_back_meat', 'bee_wings', 'honey_stomach',
                'raw_silverfish_chunks', 'cooked_silverfish_chunks', 'raw_spider_leg', 'cooked_spider_leg',
                'raw_cave_spider_leg', 'cooked_cave_spider_leg', 'minecraft:spider_eye', 'minecraft:fermented_spider_eye']),

    dict(key='offal', title='Субпродукты и кровь',
         why='Печень, сердце, почки, лёгкие, кишки, желудок, мозг, глаза, кровь и кровяная колбаса. Для советской кухни это обычная еда, '
             'но их надо уметь чистить.',
         effect='Кишки и желудок, не вычищенные без знания, дают **кишечную инфекцию** (понос, обезвоживание; Medicine). '
                'Остальное без вреда. Сырая кровь — шанс отравления.',
         knowledge='Без знания при разделке и готовке хуже выход (обрезки) и выше шанс инфекции.', book=dict(kind='journal', skill=3),
         items=['brain', 'eye', 'heart', 'cooked_heart', 'kidney', 'cooked_kidney', 'liver', 'cooked_liver', 'lungs', 'cooked_lungs',
                'intestines', 'cooked_intestines', 'stomach', 'cooked_stomach', 'bottle_of_blood', 'blood_bucket',
                'raw_blood_sausage', 'cooked_blood_sausage', 'raw_blood_sausages', 'cooked_blood_sausages', 'crackling']),

    dict(key='mushrooms', title='Дикие грибы',
         why='Грибы, которые легко спутать: мухомор (ванильный красный), поганки, светогрибы, трутовики, кислотные грибы, '
             'грибы Нижнего мира. Коричневый (шампиньон) — обычный, его в группе нет.',
         effect='Без знания каждая порция блюда с ними может **отравить**: мухомор — галлюцинации и тошнота, '
                'поганка — отложенное тяжёлое отравление через RP-сутки (печень; Medicine). Трутовики почти несъедобны: '
                'жёсткие, мало питания.',
         knowledge='Знание («Грибник» или книга) снимает риск. Это и есть суть группы.', book=dict(kind='journal', skill=4),
         items=['minecraft:red_mushroom', 'toadstool', 'glowshroom', 'large_brown_shelf_mushroom', 'large_red_shelf_mushroom',
                'small_brown_shelf_mushroom', 'small_red_shelf_mushroom', 'acid_mushroom', 'crushed_acid_mushroom', 'cyclofungi'],
         answers='мухомор — ядовитый; где он нужен по рецепту — добавить вариант с обычным грибом (29.10)'),

    dict(key='other_worlds', title='Нижний мир и Край',
         why='Еда и мясо из других измерений: хоглин, страйдер, нарост и грибы Нижнего мира, хорус, эндермены, шалкеры, '
             'эндермиты, дракон. Нужна, только если эти миры есть в РП.',
         effect='Нижний мир — **жар и сильная жажда** (согрев LSO, минус вода). Край — **дезориентация**: короткая тошнота '
                'и звон в ушах, без телепорта.',
         knowledge='Без знания готовится хуже.', book=dict(kind='research', skill=6),
         items=['raw_hoglin_chunk', 'cooked_hoglin_chunk', 'raw_strider_meat', 'cooked_strider_meat', 'minecraft:nether_wart',
                'minecraft:crimson_fungus', 'minecraft:warped_fungus', 'farmersdelight:nether_salad',
                'minecraft:chorus_fruit', 'minecraft:popped_chorus_fruit', 'chorus_fruit_juice', 'chorus_wine',
                'raw_enderman_steak', 'cooked_enderman_steak', 'enderman_heart', 'enderman_intestines', 'cooked_enderman_intestines',
                'enderman_kidney', 'cooked_enderman_kidney', 'enderman_liver', 'cooked_enderman_liver', 'cooked_enderman_lungs',
                'cooked_enderman_stomach', 'raw_endermite_chunks', 'cooked_endermite_chunks', 'raw_shulker_meat', 'cooked_shulker_meat',
                'raw_dragon_meat', 'cooked_dragon_meat'],
         answers='миры есть, группа нужна (29.10)'),

    dict(key='monsters', title='Монстры и мутанты',
         why='Существа вне обычной фауны: крипер, страж и древний страж, смотритель, разоритель, загадочное мясо SCP, '
             'кровяной мешок, мясистое основание жгучего пуха (Зона).',
         effect='**Токсины**: отравление даже у хорошо приготовленного, шанс ниже со знанием. Плюс по виду: '
                'крипер — головная боль и тошнота (нитраты, как от селитры), древний страж — усталость (замедление копания), '
                'смотритель — кратковременная темнота в глазах.',
         knowledge='Знание снижает шанс отравления, но не убирает его.', book=dict(kind='research', skill=7),
         items=['raw_creeper_leg', 'cooked_creeper_leg', 'raw_creeper_steak', 'cooked_creeper_steak',
                'raw_guardian_meat', 'cooked_guardian_meat', 'raw_elder_guardian_meat', 'cooked_elder_guardian_meat',
                'raw_warden_meat', 'cooked_warden_meat', 'raw_ravager_meat', 'cooked_ravager_meat',
                'scpo:mystery_meat', 'blood_sac', 'fl_zone_arts:pukh_flesh', 'butchery:flesh',
                'csfoundation:flesh', 'csfoundation:cooked_flesh']),

    dict(key='human', title='Человечина и гуманоиды',
         why='Человеческое мясо и мясо жителей, разбойников, поборников, вызывателей, ведьм.',
         effect='**Удар по рассудку и стресс** (Psyche), шанс рвоты. Куру убрана. У «Каннибала» эффекта нет.',
         knowledge='Знание не нужно.',
         items=['rawhumanmeat', 'cookedhumanmeat', 'raw_villager_steak', 'cooked_villager_steak', 'raw_pillager_meat',
                'cooked_pillager_meat', 'raw_vindicator_meat', 'cooked_vindicator_meat', 'raw_evoker_meat', 'cooked_evoker_meat',
                'raw_witch_meat', 'cooked_witch_meat']),

    dict(key='spoiled', title='Испорченное и заражённое',
         why='Гнилая плоть, заражённая плоть и кровь, гнилые органы и фрукты, ядовитый картофель, вонючая рыба. '
             'Сюда же позже встанет порча, когда она появится.',
         effect='**Пищевое отравление**, высокий шанс. Заражённое — сепсис и жар (Medicine). «Железный желудок» снижает шанс.',
         knowledge='Знание не помогает: испорченное есть испорченное.',
         items=['minecraft:rotten_flesh', 'zombie_extreme:infected_flesh', 'bottle_of_infected_blood', 'rotten_heart',
                'rotten_intestines', 'rotten_kidney', 'rotten_liver', 'withered_heart', 'rotten_apple', 'survival_instinct:rotten_orange',
                'rotten_tomato', 'vinery:rotten_cherry', 'minecraft:poisonous_potato', 'stinky_fish', 'scpo:zombie_candy']),

    dict(key='radioactive', title='Радиоактивное',
         why='Радиоактивные овощи и плоть CS, рыба токсичных пещер Alex\'s Caves, яйцо сотрясозиллы.',
         effect='**Доза облучения** (Mekanism). Антирадин помогает. Без Mekanism — отравление.',
         knowledge='Знание не помогает.',
         items=['radioactive_carrot', 'radioactive_potato', 'radioactive_flesh', 'radgill', 'cooked_radgill', 'tremorzilla_egg']),

    dict(key='anomalous', title='Аномальное',
         why='Еда с аномальными свойствами: конфеты SCP, засекреченная газировка, древний фрукт, яблоко тьмы, '
             'лакомство биомов, мимикрем, радужное желе, питательный гидрогель CS.',
         effect='**Галлюцинации** (удар по рассудку, Psyche) и одно случайное странное ощущение: свет режет глаза, лёгкость, '
                'видно в темноте. Без левитации и телепортов.',
         knowledge='Знание не помогает: это аномалия, а не кулинария.',
         items=['scpo:candy_pink', 'scpo:candygreen', 'scpo:candyred', 'scpo:redacted_soda_can', 'quark:ancient_fruit', 'darkened_apple',
                'biome_treat', 'mimicream', 'rainbow_jelly', 'nutriogloop', 'sack_of_sating'],
         answers='газировки SCP обычные, кроме засекреченной (29.10)'),

    dict(key='sweets', title='Сладости',
         why='Конфеты, леденцы, мармелад, жвачка, шоколад, карамель, глазурь, маршмеллоу, пончики, сладкая газировка.',
         effect='**Сахар**: быстрый прилив сил (выносливость +), через 10 минут — сонливость (−восстановление). '
                'Много сладкого за RP-сутки — «зуб заныл» (с перком «Плохие зубы» чаще). Подробно объясню отдельно, как просили.',
         knowledge='Знание не нужно. Варить карамель и леденцы — кондитерское дело, см. группы рецептов.',
         items=['rock_candy_black', 'rock_candy_blue', 'rock_candy_brown', 'rock_candy_cyan', 'rock_candy_gray', 'rock_candy_green',
                'rock_candy_light_blue', 'rock_candy_light_gray', 'rock_candy_lime', 'rock_candy_magenta', 'rock_candy_orange',
                'rock_candy_pink', 'rock_candy_purple', 'rock_candy_red', 'rock_candy_white', 'candy_cane', 'caramel', 'caramel_apple',
                'gummy_ring_blue', 'gummy_ring_green', 'gummy_ring_pink', 'gummy_ring_red', 'gummy_ring_yellow', 'jelly_bean',
                'gumball_pile', 'licoroot', 'large_peppermint', 'small_peppermint', 'peppermint_powder', 'sugar_glass',
                'block_of_chocolate', 'block_of_frosting', 'block_of_chocolate_frosting', 'block_of_vanilla_frosting',
                'chocolate_ice_cream', 'chocolate_ice_cream_scoop', 'purple_soda_bottle', 'hot_chocolate_bottle',
                'supplementaries:candy', 'sugar_cube', 'candys', 'chocolate_bar', 'chocolate_box', 'donut', 'scpo:cupcake',
                'scpo:candyblue', 'marshmallow', 'roasted_marshmallow', 'marshmallow_on_a_stick', 'coke_can', 'cotton_soda_can',
                'carmin_soda', 'stitch_soda', 'blue_soda', 'red_soda', 'lemon_soda']),
]

# Группы рецептов: умение (перк) открывает технику. Варианты «без перка»: «нельзя» (станция не работает, рецепт не выходит)
# или «брак» (выходит, но с реальной опасностью).
RECIPES = [
    dict(title='Консервирование', perk='«Заготовщик» (новый)',
         what='Консервы в кастрюле FD + банка (13 рецептов датапака RP Culinary), соленья и маринады (позже).',
         without='Нельзя или **брак** — вздутая банка: ботулизм (тяжёлое отравление, слабость, двоится в глазах; Medicine). '
                 'Готовые консервы из лута не затрагиваются.'),
    dict(title='Копчение, засолка, вяление', perk='«Заготовщик»',
         what='Коптильня (118 рецептов `smoking` по мясу и рыбе), сушилка F&C (6 рецептов `drying`), состояния «солёное» и «копчёное» из блока 2 плана.',
         without='Обычная жарка на коптильне остаётся у всех. Без перка копчение и засолка получаются **недокопчёными**: '
                 'не хранятся дольше, у дичи остаётся риск паразитов.'),
    dict(title='Самогоноварение и крепкое', perk='«Винокур» (новый)',
         what='Водка на станции Brewery (датапак), 8 сортов виски Brewery.',
         without='Нельзя или **брак** — сивуха: тяжёлое похмелье, шанс отравления метанолом (слепота на время, Medicine). '
                 'Магазинная и лутовая водка — без изменений.'),
    dict(title='Пивоварение', perk='«Винокур»',
         what='6 сортов пива Brewery и тёмное варево (16 рецептов `brewing` вместе с виски).',
         without='Нельзя или **брак** — кислая брага: слабый эффект, расстройство желудка.'),
    dict(title='Виноделие и сидр', perk='«Винокур» или отдельный «Винодел»',
         what='Брожение в бочке Vinery (24 рецепта `wine_fermentation`): вино, сидр, медовуха; пресс для яблок (4 рецепта).',
         without='Нельзя или **брак** — уксус вместо вина.'),
    dict(title='Сыроделие и масло', perk='«Сыровар» (новый) или в «Заготовщик»',
         what='Моцарелла и масло F&C (миска), сыр Refurbished и SA, топлёное масло SA.',
         without='Нельзя: без закваски сыра не будет.'),
    dict(title='Колбасы и сосиски', perk='без перка, по уровню навыка «Кулинария»',
         what='Мясорубка Butchery и кастрюля F&C (блок 5 плана).',
         without='Ниже порога навыка — больше обрезков и хуже качество, как разделка туш.'),
    dict(title='Кондитерское дело', perk='без перка, по уровню навыка',
         what='Карамель, леденцы, глазурь, торты (Alex\'s Caves, FD, F&C).',
         without='Ниже порога — «подгорело» (карамель сгорает быстро).'),
]

# Особые действия внутри групп (теги rpculinary:effect/<имя>, решения 29.10)
EFFECTS = {
    'venom': ['raw_cave_spider_leg', 'minecraft:spider_eye'],                        # сырое — яд
    'hallucinogen': ['minecraft:red_mushroom', 'acid_mushroom', 'crushed_acid_mushroom', 'glowshroom'],  # мухомор и т. п.
    'liver_poison': ['toadstool'],                                                   # поганка: отложенное тяжёлое отравление
    'gut': ['intestines', 'cooked_intestines', 'stomach', 'cooked_stomach'],         # не вычищенное — кишечная инфекция
    'blood': ['bottle_of_blood', 'blood_bucket'],                                    # сырая кровь — шанс отравления
    'nitrate': ['raw_creeper_leg', 'cooked_creeper_leg', 'raw_creeper_steak', 'cooked_creeper_steak'],
    'mining_fatigue': ['raw_elder_guardian_meat', 'cooked_elder_guardian_meat'],
    'darkness': ['raw_warden_meat', 'cooked_warden_meat'],
    'tough': ['large_brown_shelf_mushroom', 'large_red_shelf_mushroom', 'small_brown_shelf_mushroom', 'small_red_shelf_mushroom'],
    'infected': ['zombie_extreme:infected_flesh', 'bottle_of_infected_blood'],
    'nether': ['raw_hoglin_chunk', 'cooked_hoglin_chunk', 'raw_strider_meat', 'cooked_strider_meat', 'minecraft:nether_wart',
               'minecraft:crimson_fungus', 'minecraft:warped_fungus', 'farmersdelight:nether_salad'],
}

# Техники по умению (решения 29.10): станции «Винокура» (теги блоков) и заготовки «Заготовщика» (теги предметов)
VINTNER_BLOCKS = ['brewery:wooden_brewingstation', 'brewery:copper_brewingstation', 'brewery:netherite_brewingstation', 'brewery:brew_kettle',
                  'brewery:brew_oven', 'brewery:brew_whistle', 'brewery:brew_timer', 'brewery:barrel_main', 'brewery:barrel_main_head',
                  'brewery:barrel_right', 'brewery:barrel_head_right', 'vinery:fermentation_barrel']
PRESERVER_ITEMS = ['farmersdelight:smoked_ham']  # + все консервы (canned_*, *_can) — gen_groups.py
