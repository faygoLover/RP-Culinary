# RP Culinary — указатель кода

Создано `python _family/tools/mapgen.py`, версия мода 0.9.3-1.20.1-forge.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 65 файлов, 10229 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.rpculinary` | 1 | 153 |
| `faygolover.rpculinary.capability` | 1 | 209 |
| `faygolover.rpculinary.client` | 9 | 1118 |
| `faygolover.rpculinary.config` | 1 | 40 |
| `faygolover.rpculinary.core` | 13 | 1961 |
| `faygolover.rpculinary.data` | 2 | 142 |
| `faygolover.rpculinary.gametest` | 3 | 1049 |
| `faygolover.rpculinary.integration` | 2 | 93 |
| `faygolover.rpculinary.item` | 2 | 184 |
| `faygolover.rpculinary.mixin` | 4 | 182 |
| `faygolover.rpculinary.network` | 6 | 311 |
| `faygolover.rpculinary.pack` | 1 | 35 |
| `faygolover.rpculinary.server` | 20 | 4752 |

## 2. Классы

### `faygolover.rpculinary`
- `RpCulinary`, 153 — RP Culinary — питание для семейства RP-модов (дизайн — docs/01_design.md): сытость в калориях вместо окорочков, вода, нутриенты, вес и жир от еды.

### `faygolover.rpculinary.capability`
- `NutritionData`, 209 — Питание игрока (сохраняется) и служебное состояние сервера (не сохраняется).

### `faygolover.rpculinary.client`
- `ClientNutrition`, 96 — Что клиент знает о своём питании (от сервера).
- `ClientSetup`, 95 — Клиент: полоски в общем HUD, скрыть ванильные окорочка (и капли LSO), клавиша окна «Питание», подсказки.
- `CookbookScreen`, 227 — Поваренная книга и личная книга рецептов (D3, решения Алекса 03.11): без красивых схем — список рецептов, справа ингредиенты картинками, станция, результат и текст рецепта, как в настоящей поваренной книге (свой текст — ключ rpcul…
- `CulinaryHud`, 168 — Полоски общего HUD (RP Core, двигать — /rphud): «Сытость» — где были ванильные окорочка, «Вода» — над ней.
- `FeedPose`, 41 — Кормит другого: ведущая рука с едой вытянута вперёд.
- `FoodTooltips`, 225 — Подсказка у еды по навыку «Кулинария» (дизайн, §6.1): 0–1 — «съедобно», 2–3 — вид еды, 4–5 — словами, 6–8 — округлённо, 9–10 — точно.
- `MeatColors`, 67 — Подкраска форм мяса в цвет вида (решения Alex 29.10, галерея q:tint, q:mince): у вида нет своего фарша, обрезков, котлеты, колбасок — берём один из трёх фаршей (или общий предмет) и красим.
- `NutritionScreen`, 148 — Окно «Питание» (клавиша K): что человек чувствует — голод, жажда, каждый нутриент словами (нехватка, на грани, норма, многовато, избыток), что приелось, телосложение.
- `PortionBars`, 51 — Шкала на иконке, как у прочности: сколько глотков осталось у начатого напитка (голубая) и укусов у надкусанного блюда (тёплая).

### `faygolover.rpculinary.config`
- `CulinaryConfig`, 40 — Настройки сервера (serverconfig/rpculinary-server.toml): по одной на каждое поле Settings с пометкой Settings.Cfg.

### `faygolover.rpculinary.core`
- `Boredom`, 98 — «Приелось» по трём осям (решения Alex 26.10): вид еды («из чего», NutritionState#CATEGORIES), способ («как приготовлено», NutritionState#METHODS) и само блюдо (id предмета).
- `Conditions`, 75 — Долгие последствия нехватки и избытка (RP Stamina Sports docs/08 п.
- `Containers`, 135 — Мешки и ящики с продуктами (блок 6 переделки, решения Alex 29.10): таблица assets/rpculinary/containers.json (tools/containers_spec.py) — тара → какой продукт принимает и сколько кг вмещает.
- `Cookbook`, 105 — Поваренные книги (D3, вариант А, решения Алекса 03.11): таблица assets/rpculinary/cookbook.json (tools/gen_cookbook.py).
- `Cooking`, 108 — Навык «Кулинария» в деле (дизайн, §6.2; стадии — решения Alex 27.10.2026): стадия блюда и опыт за готовку.
- `Food` (record), 35 — Состав порции: ккал, белки, жиры, углеводы (г), витамины (условные единицы), вода (% полоски воды).
- `MeatFood`, 138 — КБЖУ по виду мяса (решение Alex 29.10): у фарша, обрезков, котлеты, колбасок своё питание — от 100 г сырого мяса вида, а не от предмета-основы.
- `Metabolism`, 317 — Обмен веществ (дизайн, §3).
- `Nutrients`, 47 — Нехватка, избыток, баланс нутриентов и «приелось» (перенесено из RP Medicine).
- `NutritionState`, 157 — Питание одного игрока.
- `Settings`, 468 — Числа питания.
- `Source`, 159 — Состав мяса (решения Alex 29.10): «из кого» — по всей цепочке: туша → кусок → фарш → котлета → бургер.
- `Weights`, 119 — Вес и стак ингредиентов (блок 2 переделки, решения Alex 28–29.10): таблица assets/rpculinary/weights.json (генерирует tools/gen_weights.py) лежит в моде и читается одинаково на сервере и клиенте — без синхронизации.

### `faygolover.rpculinary.data`
- `DrinkRules`, 63 — Датапак rpculinary/drinks: сколько воды (проценты полоски) даёт напиток.
- `NutritionRules`, 79 — Датапак rpculinary/nutrition: состав базовых ингредиентов — тех, что не крафтятся, а идут в рецепты (зерно, овощи, сырое мясо, молоко).

### `faygolover.rpculinary.gametest`
- `CulinaryEdgeTests`, 180 — Края состояния питания: каждое поле в сохранении, смерть, возврат из Края, перезаход.
- `CulinaryGameTests`, 696 — Проверка в настоящем сервере: еда из руки и с блока, вода, замок ванильной сытости, API для соседей.
- `NbtCoverage`, 173 — Проверка «каждое поле состояния попадает в сохранение и читается обратно» без ручного списка полей.

### `faygolover.rpculinary.integration`
- `Lso`, 45 — Legendary Survival Overhaul (необязательный).
- `LsoBridge`, 48 — Обращения к классам LSO.

### `faygolover.rpculinary.item`
- `CookbookItems`, 91 — Поваренная книга раздела (выдаёт ГМ, раскладывают как лут) и личная книга рецептов повара (D3, решения Алекса 03.11).
- `ResearchNotes`, 93 — Журнал (охотника, грибника…) или записи исследований по группе экзотики — от ГМа (решения Alex 29.10.2026; обложка по группе и описание вместо пояснений — 02.11): ПКМ — изучить группу (служебный перк RP Perks).

### `faygolover.rpculinary.mixin`
- `ButcheryGrinderMixin`, 81 — Мясорубка Butchery — только колбаски (решения Alex 29.10): фарш мелет мясорубка F&C, а колбаски делаются из фарша + кишки (+ кровь — кровяная), без обрезков и не из сырой свинины.
- `CandlelightPlateMixin`, 26 — Тарелка Let's Do Candlelight: еду с неё мод скармливает напрямую в ванильную сытость (×1,3) и не сообщает, что съедено.
- `ItemStackMixin`, 40 — Стак ингредиентов по весу (таблица Weights); крупный кусок с весом в NBT — 1.
- `PackRepositoryMixin`, 35 — Встроенный пак RP Culinary (builtin/rpculinary_food) — всегда выше всех.

### `faygolover.rpculinary.network`
- `CookbookPacket` (record), 103 — Переписать рецепт из поваренной книги в личную (клиент → сервер): begin — начал, !begin — закончил.
- `FeedPosePacket` (record), 26 — Сервер: игрок начал или закончил кормить другого (рука вытянута).
- `FoodTablePacket` (record), 62 — Состав всей известной еды и её виды — для подсказок (шлётся при входе и перезагрузке датапаков).
- `Net`, 39 — Сеть мода: состояние питания игроку и таблица состава еды для подсказок.
- `PortionDonePacket` (record), 26 — Сервер: глоток или укус сделан, предмет остался в руке.
- `SyncPacket` (record), 55 — Своё питание — игроку: полоски HUD и окно «Питание».

### `faygolover.rpculinary.pack`
- `FoodPack`, 35 — Встроенный датапак «поверх всех модов» (resourcepacks/rpculinary_food, собирается tools/gen_food_pack.py): разделка туш Butchery, теги, дроп мобов, рецепты.

### `faygolover.rpculinary.server`
- `BodyEffects`, 277 — Последствия питания для тела (RP Stamina Sports docs/08): еда и питьё через силу — переполнение желудка (тяжесть → тошнота → боль, рвота, травма желудка) и перепой (тяжесть → водная интоксикация → отёк мозга); долгие последствия н…
- `Butchering`, 314 — Разделка туш Butchery (блок 3 переделки, решения Alex 28–29.10): доп.
- `ContainerFill`, 213 — Мешки и ящики с продуктами (блок 6, решения Alex 29.10 §6), таблица — Containers.
- `CookingService`, 414 — Готовка (дизайн, §6.2).
- `CulinaryCommands`, 253 — /rpculinary (ГМ, уровень 2): посмотреть питание игрока, выставить сытость, воду, нутриенты, вес и жир.
- `CulinaryEating`, 466 — Еда и питьё: укусы и глотки по ходу использования предмета, порция, еда из блока, отравление, вкусы и «приелось», сколько воды в напитке, что предмет даёт телу.
- `CulinaryPlayerEvents`, 124 — События жизни игрока для питания: восстановление выносливости, удар, вайп персонажа, что переносится при возрождении, что отправить клиенту на входе и после возрождения, таблица еды и напитков для подсказок.
- `CulinaryProvider`, 143 — RP Culinary — мод питания для RP Core: остальные моды семейства берут голод, воду, нутриенты и вес отсюда.
- `CulinaryService`, 377 — Питание на сервере (дизайн, §3–4).
- `CulinaryStatus`, 37 — Жалобы на питание для осмотра RP Medicine: врач слышит то же, что пациент видит у себя в окне «Питание» — голод и каждый нутриент не в норме, словами.
- `Exotic`, 155 — Группы экзотики (собраны заново 29.10.2026, ответы Alex в галерее; состав — tools/groups_spec.py → tools/gen_groups.py → теги rpculinary:exotic/ ).
- `ExoticEffects`, 301 — Группы экзотики действуют на того, кто ест (решения Alex 29.10.2026, группы — Exotic): от природы продукта и от того, как его приготовили.
- `Feeding`, 235 — Покормить или напоить другого (решение Alex 10.11): ПКМ едой или водой по игроку или телу вышедшего, ровно 3 секунды — один укус или глоток.
- `FoodMethods`, 71 — Способ «как приготовлено» (NutritionState#METHODS): сначала тег rpculinary:method/ (им поправлять, где угадано неверно), потом название предмета.
- `NutritionTable`, 373 — Состав любой еды (перенесено из RP Medicine; по образцу Nutritional Balance, но количественно): базовые ингредиенты — из датапака; блюдо — сумма ингредиентов рецепта, которым оно получается, делённая на число получаемых штук (жарк…
- `Processing`, 498 — Переработка мяса по одной части (решения Alex 29.10): вид мяса (Source) переходит на всё, что из него вышло.
- `Provenance`, 131 — Состав через любые станции (решения Alex 29.10): «что положил — то и в блюде».
- `Sips`, 233 — Напитки глотками (решение Alex 26.10): глоток — одна короткая анимация питья, предмет остаётся, пока не допит (шкала на иконке).
- `SkillYield`, 59 — Функция добычи rpculinary:skill_yield (блок 3 переделки): на мясе и органах в таблицах разделки туш.
- `Techniques`, 78 — Техники по умению (решения Alex 29.10.2026): без перка — «нельзя».

Классов без описания: 0 из 65.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `AddPackFindersEvent` | `FoodPack.addPacks` |
| `AddReloadListenerEvent` | `RpCulinary.reloadListeners` |
| `BlockEvent.BreakEvent` | `ContainerFill.onBreak` |
| `BlockEvent.EntityPlaceEvent` | `ContainerFill.onPlace` |
| `BuildCreativeModeTabContentsEvent` | `RpCulinary.creativeTab` |
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientSetup.onLogout` |
| `EntityJoinLevelEvent` | `ContainerFill.onItemJoin` |
| `FMLClientSetupEvent` | `ClientSetup.setup` |
| `FMLCommonSetupEvent` | `RpCulinary.commonSetup` |
| `ItemStackedOnOtherEvent` | `ContainerFill.onStackedOnOther`, `Sips.onStackedOnOther` |
| `ItemTooltipEvent` | `FoodTooltips.drinkLine`, `FoodTooltips.onTooltip`, `FoodTooltips.weightLine` |
| `LivingAttackEvent` | `CulinaryPlayerEvents.onAttack` |
| `LivingEntityUseItemEvent.Finish` | `CulinaryEating.onUseFinish` |
| `LivingEntityUseItemEvent.Start` | `CulinaryEating.onUseStart` |
| `LivingEntityUseItemEvent.Tick` | `CulinaryEating.onUseTick` |
| `ModConfigEvent.Loading` | `RpCulinary.onConfig` |
| `ModConfigEvent.Reloading` | `RpCulinary.onConfigReload` |
| `OnDatapackSyncEvent` | `CulinaryPlayerEvents.onDatapackSync` |
| `PlayerContainerEvent.Close` | `CookingService.onContainerClose` |
| `PlayerContainerEvent.Open` | `CookingService.onContainerOpen` |
| `PlayerEvent.ItemCraftedEvent` | `CookingService.onCrafted` |
| `PlayerEvent.ItemSmeltedEvent` | `CookingService.onSmelted` |
| `PlayerEvent.PlayerLoggedInEvent` | `Feeding.onLogin` |
| `PlayerEvent.PlayerLoggedOutEvent` | `CookingService.forget` |
| `PlayerInteractEvent.EntityInteract` | `Feeding.onEntityInteract` |
| `PlayerInteractEvent.RightClickBlock` | `Butchering.afterRightClick`, `Butchering.onRightClick`, `ContainerFill.onRightClickBlock`, `CookingService.onHandsCheck`, `CookingService.onRightClickBlock`, `CulinaryEating.onRightClickBlock`, `Processing.onBowlClick`, `Processing.onMincer`, `Techniques.onRightClickBlock` |
| `PlayerInteractEvent.RightClickItem` | `Sips.onRightClick` |
| `RegisterColorHandlersEvent.Item` | `MeatColors.register` |
| `RegisterCommandsEvent` | `CulinaryCommands.register` |
| `RegisterItemDecorationsEvent` | `PortionBars.register` |
| `RegisterKeyMappingsEvent` | `ClientSetup.keys` |
| `RenderGuiOverlayEvent.Pre` | `ClientSetup.onOverlay` |
| `RenderPlayerEvent.Pre` | `FeedPose.onRenderPre` |
| `RpCharacterWipeEvent` | `CulinaryPlayerEvents.onWipe` |
| `RpTimeSkipEvent` | `CulinaryCommands.onTimeSkip` |
| `ServerStoppedEvent` | `CookingService.onServerStopped` |
| `StaminaRestoredEvent` | `CulinaryPlayerEvents.onStaminaRestored` |
| `TickEvent.ClientTickEvent` | `ClientSetup.onClientTick` |
| `TickEvent.PlayerTickEvent` | `Butchering.onPlayerTick`, `CookingService.onPlayerTick`, `CulinaryService.onPlayerTick`, `ExoticEffects.onPlayerTick`, `Provenance.onPlayerTick` |
| `TickEvent.ServerTickEvent` | `Feeding.onServerTick`, `Processing.onServerTick` |

## 4. Сеть

Канал: `faygolover/rpculinary/network/Net.java`, имя `main`, версия протокола `5`.
- `SyncPacket` (направление не указано в регистрации)
- `FoodTablePacket` (направление не указано в регистрации)
- `CookbookPacket` (направление не указано в регистрации)
- `FeedPosePacket` (направление не указано в регистрации)
- `PortionDonePacket` (направление не указано в регистрации)

Классы с обработчиками пакетов (5): `CookbookPacket`, `FeedPosePacket`, `FoodTablePacket`, `PortionDonePacket`, `SyncPacket`.

## 5. Команды

- `faygolover/rpculinary/server/CulinaryCommands.java`: `set` `rpculinary` `status` `speed` `audit` `notes` `reset`

## 6. Данные

- capability: `NutritionData`, ключ `rpculinary:nutrition`
- getPersistentData: `CulinaryGameTests` ×1
- getPersistentData: `ButcheryGrinderMixin` ×1
- getPersistentData: `Butchering` ×2, теги: `rpculinary:cut_stage`
- SavedData: `ContainerFill`, файл "rpculinary_containers"
- свои файлы на диске: `CulinaryCommands`
- SavedData: `Feeding`, файл "rpculinary_fed_bodies"
- getPersistentData: `Processing` ×1, теги: `rpculinary:mincer`

## 7. Конфиги

- server: `CulinaryConfig`, вызовов define 0

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `MISC_TAB` 1, `body` 8, `bodyHandles` 2, `dayLengthSeconds` 4, `debugCommands` 1, `grantKnowledge` 1, `handsFree` 4, `hasTrait` 29, `isSkillManaged` 2, `practice` 3, `profile` 2, `registerCommand` 5, `registerModule` 1, `registerNutrition` 1, `reportCompat` 4, `rpPerReal` 1, `sanityHit` 1, `skill` 2, `skipFloor` 1, `sleepStage` 1, `stamina` 1, `staminaAttribute` 2, `stressImpulse` 1, `taste` 2, `timeRunning` 2.

Реализует розетки: NutritionProvider ← `CulinaryProvider`; Source ← `CulinaryStatus`.

События Core: `FoodEatenEvent`, `RpCharacterWipeEvent`, `RpConsumeEvent`, `RpMoodEvent`, `RpTimeSkipEvent`, `StaminaRestoredEvent`.

## 9. Связи с другими модами

Проверки `isLoaded`: `butchery` (CulinaryGameTests); `farm_and_charm` (CulinaryGameTests); `farmersdelight` (CulinaryGameTests); `kaleidoscope_cookery` (CulinaryGameTests); `legendarysurvivaloverhaul` (Lso).

Отражение (`Class.forName`):
- `CulinaryGameTests` → `net.mcreator.butchery.procedures.MeatgrinderrecipeprocedureProcedure`
- `ExoticEffects` → `mekanism.api.radiation.IRadiationManager`

Пакеты других модов семейства в коде: `rpcore` — 23 файлов.

Миксины (4):
- `ItemStackMixin` (mixins) → ItemStack.class, priority = 500
- `PackRepositoryMixin` (mixins) → PackRepository.class
- `ButcheryGrinderMixin` (mixins) → targets = "net.mcreator.butchery.procedures.MeatgrinderrecipeprocedureProcedure", remap = false
- `CandlelightPlateMixin` (mixins) → targets = "net.satisfy.candlelight.core.block.TableSetBlock", remap = false

## 10. Зарегистрированные id

- `faygolover/rpculinary/item/CookbookItems.java` (2): `cookbook` `recipe_notebook`
- `faygolover/rpculinary/item/ResearchNotes.java` (1): `research_notes`
- `faygolover/rpculinary/server/SkillYield.java` (1): `skill_yield`
