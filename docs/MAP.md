# RP Culinary — карта

Версия на момент написания: 0.9.3 (16.11.2026). Факты по классам, событиям, пакетам — `docs/INDEX.md`.

## 1. Что делает и чего не делает

Питание: сытость в калориях вместо ванильных окорочков, вода, белки-жиры-углеводы-витамины, вес и жир тела от
съеденного, «приелось», вкусы персонажа. Еда — глотками и укусами, можно кормить другого. Готовка с навыком
«Кулинария» (стадии блюда, качество, поваренные книги), разделка туш и переработка мяса с сохранением «из кого»,
мешки и ящики с продуктами, группы экзотической еды с последствиями (паразиты, отравления, отвращение).
Для остальных модов семейства — мод питания RP Core (`NutritionProvider`).

За этим — не сюда:
- последствия для тела (сепсис, обморок, ощущения, осмотр) — мод тела через `RpCoreAPI.body()`;
- стресс и рассудок от еды — `RpCoreAPI.stressImpulse`, `sanityHit`;
- что считается водой, кофе, алкоголем — общая база `Consumables` RP Core (датапак);
- расход калорий от нагрузки приходит снаружи: соседи зовут `NutritionProvider.burnKcal` (реализация — `server/CulinaryProvider`);
- навык «Кулинария» и знания групп экзотики хранит RP Perks (сюда `RpCoreAPI.skill`, `grantKnowledge`, `hasTrait`).

## 2. Как течёт работа

**Шаг раз в секунду.** `server/CulinaryService.onPlayerTick` → `step`: ядро `core/Metabolism` (переваривание
желудка, расход энергии и воды, нутриенты, вес и жир) → стадии голода и жажды → `server/BodyEffects.step`
(переполнение, последствия нехватки — `core/Conditions`) → `applyEffects` (модификаторы скорости, удара,
выносливости) или `withoutBody` (простые эффекты, если мода тела нет) → `stageMessages` → `sync` клиенту.

**Съел.** `LivingEntityUseItemEvent` → `server/CulinaryEating.onUseStart / onUseTick / onUseFinish`: предмет едят
укусами (`bite`) и пьют глотками (`server/Sips`, `sip`) → `eatPortion(игрок, данные, предмет, доля)` →
состав порции — `server/NutritionTable` (базовые ингредиенты из датапака `nutrition` + разбор рецептов + вид мяса
`core/MeatFood`) → `consume`: в желудок, «приелось» (`core/Boredom`), вкусы → `foodPoisoning` →
`server/ExoticEffects.afterEating` (группы экзотики) → события Core `FoodEatenEvent`, `RpConsumeEvent`.
Еда с блока (торт и т. п.) — `CulinaryEating.onRightClickBlock` → `eatFromBlock`.

**Покормить другого.** `server/Feeding.onEntityInteract` (ПКМ едой по игроку или телу вышедшего) → таймер в
`Feeding.onServerTick` → `CulinaryEating.feedPortion`.

**Готовка.** `server/CookingService`: открыл станцию (`onContainerOpen`) → скрафтил или испёк (`onCrafted`,
`onSmelted`) → стадия блюда и опыт по навыку (`core/Cooking`) → качество в NBT предмета. Состав блюда «что
положил — то и получил»: `server/Provenance`. Техники по умению (без перка «нельзя»): `server/Techniques`.

**Мясо.** туша Butchery → `server/Butchering` (разделка, добыча по навыку — `SkillYield`) → `server/Processing`
(мясорубка, миска, колбаски; вид мяса `core/Source` идёт по всей цепочке) → блюдо. Подкраска — `client/MeatColors`.

**Клиенту.** `network/SyncPacket` (свои полоски и окно «Питание»), `FoodTablePacket` (состав всей еды для
подсказок — на входе и после `/reload`), `PortionDonePacket`, `FeedPosePacket` → `client/ClientNutrition` →
`CulinaryHud`, `NutritionScreen` (клавиша K), `FoodTooltips`.

**Данные игрока.** `capability/NutritionData.register()` → `PlayerStore` RP Core. Правило возрождения —
`CulinaryPlayerEvents.copyOnClone`; что слать клиенту — `CulinaryPlayerEvents.resync` (вход: таблица еды и снимок;
возрождение: снимок; смена измерения: ничего).

**Настройки.** поле с пометкой `@Cfg(section, doc)` в `core/Settings` — и всё: `config/CulinaryConfig` привязывает
все помеченные поля через `SettingsBinder` RP Core.

## 3. Карта пакетов

| Пакет | За что отвечает | С чего начать читать |
|---|---|---|
| `rpculinary` | вход мода, подписки, регистрация в Core | `RpCulinary` |
| `core` | формулы и таблицы без Minecraft (модульные тесты) | `Metabolism`, `NutritionState`, `Settings`; готовка — `Cooking`; мясо — `Source`, `MeatFood`; `Boredom`, `Nutrients`, `Conditions`; таблицы — `Weights`, `Containers`, `Cookbook` |
| `server` | серверная механика (20 классов) | шаг — `CulinaryService`; еда — `CulinaryEating`, `Sips`, `Feeding`; состав — `NutritionTable`, `FoodMethods`; готовка — `CookingService`, `Provenance`, `Techniques`; мясо — `Butchering`, `Processing`, `SkillYield`; экзотика — `Exotic`, `ExoticEffects`; тело — `BodyEffects`; жизнь игрока — `CulinaryPlayerEvents`; Core — `CulinaryProvider`, `CulinaryStatus`; мешки — `ContainerFill`; ГМ — `CulinaryCommands` |
| `capability` | данные игрока | `NutritionData` |
| `config` | привязка `Settings` к конфигу | `CulinaryConfig` |
| `data` | правила из датапака: состав ингредиентов, вода в напитках | `NutritionRules`, `DrinkRules` |
| `network` | пакеты | `Net` |
| `client` | HUD, окно «Питание», подсказки, книги | `ClientSetup` |
| `item` | поваренные книги, журналы и записи исследований | `CookbookItems`, `ResearchNotes` |
| `integration` | Legendary Survival Overhaul (жажда) | `Lso` (проверка), `LsoBridge` (классы LSO) |
| `mixin` | чужие станции и стак по весу | по имени миксина |
| `pack` | встроенный датапак «поверх всех модов» | `FoodPack` |
| `gametest` | игровые тесты | `CulinaryGameTests`, `CulinaryEdgeTests`, `NbtCoverage` |

## 4. Хочу добавить… — рецепты

| Что | Где | Образец | Не забыть |
|---|---|---|---|
| настройку | поле с `@Cfg(section = "…", doc = "…")` в `core/Settings` | `hungerEnabled` | больше ничего: ключ (`hunger_enabled`) и раздел появятся сами; типы — `double` и `boolean` |
| состав базового ингредиента | JSON в `data/<ns>/rpculinary/nutrition/` | файлы в `resources/data/rpculinary/rpculinary/nutrition` | блюда считаются из рецептов сами — им состав не писать; `per_grams` — на сколько граммов дан состав |
| воду в напитке | датапак `rpculinary/drinks` (`data/DrinkRules`); общие свойства напитка — `Consumables` RP Core | — | — |
| вес и размер стака ингредиента | `tools/gen_weights.py` → `assets/rpculinary/weights.json` | — | файл создаётся скриптом, руками не править |
| новую группу экзотики | имя в `server/Exotic.GROUPS` (+ навык для знания в `SKILL`), состав — `tools/groups_spec.py` → `gen_groups.py` (теги), действие — блок `if (has(mask, "имя"))` в `ExoticEffects.afterEating` | `insects` | знание группы — служебная черта `rpculinary:exotic/<имя>`, её выдаёт RP Perks; документ `docs/groups_2026-10-29.md` |
| последствие еды для тела | `server/BodyEffects` (свои), `ExoticEffects` (экзотика); настоящую болезнь — `RpCoreAPI.body().infect/condition` с простым эффектом взамен | `ExoticEffects.Medicine.infect` | без мода тела должно работать запасное |
| влияние еды на психику | `ExoticEffects.Psyche.stress / sanityHit` (через RP Core) | «pets» | классы RP Psyche не трогать |
| новый способ «как приготовлено» или вид еды | `core/NutritionState.METHODS / CATEGORIES`, определение — `server/FoodMethods`, тег `rpculinary:method/…` | — | индексы пишутся в сохранение: добавлять в конец |
| станцию готовки чужого мода | `server/CookingService` (какие меню считать готовкой), состав — `Provenance` | — | миксин — только если мод кормит в обход событий (`CandlelightPlateMixin`) |
| ступень переработки мяса | `server/Processing` | мясорубка | вид мяса (`core/Source`) должен перейти на результат |
| рецепт в поваренную книгу | `tools/recipes_spec.py` → `gen_cookbook.py` → `assets/rpculinary/cookbook.json` | — | скрипт |
| новое поле состояния | `core/NutritionState` (+ `copyFrom`) или `capability/NutritionData`; запись и чтение — `NutritionData.save/load` | `bodyClock` | перенос при возрождении — `CulinaryPlayerEvents.copyOnClone`; тест `NbtCoverage` найдёт несохранённое |
| ответ другим модам о питании | метод в `NutritionProvider` (RP Core, с ответом по умолчанию) + реализация в `server/CulinaryProvider` | `hungerStage` | — |
| команду ГМа | `server/CulinaryCommands` | `/rpculinary set` | `RpCoreAPI.registerCommand` |

## 5. Данные

| Что | Где хранится | Ключ | Что при смерти | Как попадает клиенту |
|---|---|---|---|---|
| питание игрока (`NutritionState` + отметки тела) | у игрока, `PlayerStore` RP Core | `rpculinary:nutrition` | переносится; желудок пуст, энергия не ниже половины резерва, вода не ниже 50, счётчики голода сброшены | `SyncPacket` |
| качество, состав, вид мяса, укусы и глотки | NBT предмета | `rpculinary_bites`, `rpculinary_bites_n` и ключи `Source`, `Cooking` | — | с предметом |
| что лежит в мешке или ящике | блок и предмет (`ContainerFill`) | таблица `containers.json` | — | — |

Не сохраняются 8 служебных полей состояния (перечень — `docs/test_findings.md`): решения, нужны ли они после
перезахода, нет. Ключи и имена полей не менять.

## 6. Связи

- Берёт из Core: `body()` (обморок, заражение, последствия, ощущения, вес и жир при первом знакомстве),
  `stamina()`, `skill` и `practice` («Кулинария»), `hasTrait`, `grantKnowledge`, `stressImpulse`, `sanityHit`,
  `profile` и `taste`, `dayLengthSeconds`, `Consumables`, `Matcher`; события
  `StaminaRestoredEvent`, `RpTimeSkipEvent`, `RpCharacterWipeEvent`; `PlayerStore`, `SettingsBinder`.
- Даёт в Core: `NutritionProvider` (`server/CulinaryProvider`), строки осмотра (`CulinaryStatus`); шлёт
  `FoodEatenEvent`, `RpConsumeEvent`, `RpMoodEvent`.
- Соседей по семейству не импортирует и не ищет отражением (до 0.9.3 так было с Psyche, Medicine, Perks).
- Чужие: Legendary Survival Overhaul (жажда: `integration/Lso`), Butchery, Farm & Charm, Let's Do Candlelight
  (миксины и id), Mekanism (доза облучения — отражением в его API, `ExoticEffects.Mekanism`).

## 7. Конфиги

`serverconfig/rpculinary-server.toml` — 196 ключей, по одному на поле `Settings` с пометкой `@Cfg`; раздел и
описание — из пометки. Описание в файле пишется с пробелом в начале и границы чисел −1e9…1e9 — так сложилось,
менять нельзя (файл на сервере). Команда `/rpculinary` меняет `consumptionSpeed` на ходу через `CulinaryConfig.setDouble`.

## 8. Сеть

`network/Net`. Клиенту: `SyncPacket`, `FoodTablePacket`, `PortionDonePacket`, `FeedPosePacket`. На сервер:
`CookbookPacket` (переписать рецепт в личную книгу) — сервер сам проверяет книгу в руках и рецепт.

## 9. Ловушки

- **Таблицы в `assets/rpculinary/*.json` и теги групп создают скрипты `tools/`** — править спецификацию и запускать
  скрипт, а не JSON руками. `scripts/gen_nutrition.py` не знает про `per_grams` — не запускать.
- **Три класса `CulinaryService`, `CulinaryEating`, `CulinaryPlayerEvents` связаны статическим импортом:** метод
  вызывается без имени класса; где он лежит — смотреть в `docs/INDEX.md`.
- **Ванильная сытость заперта** (`FOOD_ROOM`, `FOOD_FULL` в `CulinaryService`): её держат в коридоре, чтобы игрок
  мог есть и не лечился сытостью. Чужой мод, который кормит напрямую в ванильную сытость, обходит калории — нужен миксин.
- **Порядок случайных чисел в `ExoticEffects.afterEating`** общий на все группы: перестановка блоков меняет исходы.
- **Часы тела** (`NutritionData.bodyClock`) идут по RP-времени игрока, а не по времени мира: отложенные эффекты
  (паразиты, поганка) считаются от них.
- **Встроенный пак `FoodPack` всегда выше остальных** (`PackRepositoryMixin`): правки рецептов чужих модов идут туда.
- Капельница (питание через мод тела) переживает смерть — известная находка, не исправлена.

## 10. Тесты

`python ../_family/family.py verify --tests "RP Culinary"` — 26 игровых тестов: `CulinaryGameTests` (еда из руки
и с блока, вода, замок сытости, API для соседей), `CulinaryEdgeTests` + `NbtCoverage` (каждое поле в сохранении,
смерть, возврат из Края, перезаход, связи через Core). Модульные (`gradlew.bat test`): `MetabolismTest`,
`CookingTest` — числа обмена веществ; при правке `core/` должны проходить без изменения ожиданий.

## 11. Документы в docs/

| Файл | Состояние |
|---|---|
| `MAP.md`, `INDEX.md`, `test_findings.md` | действуют |
| `01_design.md` | действует как общий дизайн (§3 обмен веществ, §6 кулинария); частности уточнены решениями ниже |
| `groups_2026-10-29.md`, `drinks_2026-10-26.md`, `ingredients_2026-10-28.md`, `butchery_carcasses_2026-10-28.md` | действуют: справочники состава и решений Алекса |
| `02_plan_rework_2026-10-29.md`, `03_audit_and_plan_2026-10-29.md` | история: план переделки, выполнен |
| `04_recipes_visibility_2026-11-02.md` | действует: поваренные книги |
| `05_open_items_2026-11-06.md` | открытые вопросы — сверять с кодом |
| `ideas_archive.md`, `reference/` | архив идей и справочные материалы |
