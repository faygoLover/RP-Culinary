# RP Culinary — точка входа для нового диалога (08.10.2026)

Пишет пользователь — брат автора (Alex, faygoLover). Общение **по-русски**, простыми словами. Код и запуски делает Claude.
Правила: **только локальные коммиты, никаких push**; большие пачки правок, спорное — спросить с вариантом по умолчанию; во время совместной проверки в игре только записывать замечания.

## Семейство модов (папки рядом, Minecraft Forge 1.20.1, JDK 17)
| Мод | Версия | Что делает | Требует |
|---|---|---|---|
| `RP Core` (rpcore) | 0.2.0 | API навыков/черт/тела/практики (`RpCoreAPI`, `RpIds`, `TraitProvider`, `BodyProvider`), общий HUD и редактор `/rphud`, `/rpcore`, заплатка утечек taa/Sanity | — |
| `RP Perks` (rpperks) | 1.1.0 | перки, навыки 0–10 (оружие ×5 групп, Медицина, **Кулинария**, Сила, Форма), рост от практики, вкладка «Навыки», TaCZ/taa | Core |
| `RP Stamina` (rpstamina) | 1.1.0 | выносливость; **сейчас ещё и голод** (тратится на восстановление выносливости) | Core |
| `RP Medicine (Health revork)` (rpmedicine) | 0.2.0 | медицина; **сейчас ещё и питание** (БЖУ, витамины, «приелось», жажда без LSO), вес/рост/жир/ИМТ (`core/Body`) | Core |

Принцип: каждый мод требует только Core и работает сам; связи — через Core. Perks = «лист персонажа», механику делает профильный мод; без профильного мода у Perks простой запасной вариант.
Подробно: `RP Core/docs/00_plan.md` (шаги 1–2), `RP Medicine (Health revork)/docs/10_design_final.md` §6.1, `docs/09_future_plans.md` (Кулинария, Диетолог).

## Что должен сделать RP Culinary (новый мод на Core) — СНАЧАЛА дизайн-документ и согласование
Собрать питание в одном месте:
- **Голод «как в жизни»** (решение автора 07.10): вместо ванильных окорочков — полоска сытости (элемент общего HUD Core, отключаемо); жажда — «вода» со своей полоской (жажду LSO тоже показывать полоской); ванильную сытость еды не считать — всё через **калории и нутриенты**.
- **Калории → вес и жир** (жир уже есть в Medicine: `MedicalState.bodyFat`, ИМТ, ожирение/истощение влияют на тело). Расход — от веса и **активности** (RP Stamina должна только сообщать нагрузку, свою логику голода отдать Кулинарии, когда она стоит).
- Голод: «нет сил», голодные обмороки, **не до смерти**. Сейчас в Medicine голод бьёт по мозгу (`damage_sources/starve.json`) — пересмотреть.
- Перенести из Medicine: `core/Nutrition.java`, `server/NutritionTable.java`, `data/NutritionRules`, датапак `nutrition`/`drinks`, экран `NutritionScreen`, жажду без LSO. Medicine потом читает состояние питания через Core (новый API, например `NutritionProvider`), как тело через `BodyProvider`.
- **Навык «Кулинария»** уже ведёт RP Perks (`rpcore:cooking`, перк «Кулинар» → 5): опыт за готовку через `RpCoreAPI.practice`; подсказки еды по уровню (ничего → вид → словами → цифры); перк «Диетолог».
- **«Анорексичка»** (RP Perks) — перевести на новую логику (низкий вес, плохой аппетит), как сделано с перками при Medicine (`BodyProvider.handles(...)`).
- **Внутривенное питание** (капельница Medicine, замечание 37 живого теста): поднимает за десятки минут, потом плохой аппетит.
- Моды сборки для интеграции (разобрать jar перед работой): Kaleidoscope Cookery, Tavern (пивоварение), World Liquor (напитки — в вещества Medicine), Immersive Eating, Kaleidoscope Compat. Сборка: `%APPDATA%\ElyPrismLauncher\instances\DEPARTMENT_s5_client\minecraft\mods`.
- Связано: `/rptime` (время RP: стоп/старт/сон) — план в Core, §6.2 итогового дизайна Medicine.

## Сборка и проверка
- `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot"`; шаблон проекта — `RP Core` (build.gradle, flatDir `libs/`, `blank:rpcore:<версия>`); Core jar кладётся в `libs/` каждого мода.
- Проверка загрузки мода с одним Core: `gradlew.bat runGameTestServer`. Medicine: `-Pno_perks=true`, `-Pno_stamina=true`, `-Pwith_optional=true` (TaCZ, LSO…).
- **Грабли:** ForgeGradle кэширует jar из `libs/` по версии — пересобрал соседа с той же версией → удалить `~/.gradle/caches/forge_gradle/deobf_dependencies/blank/<мод>/<версия>*`. Первую сборку с новой зависимостью Modrinth делать без `--offline`.
- Игровая проверка: сервер Medicine `gradlew.bat runServer -Pwith_optional=true` (RCON 25575, пароль `rpmtest`), клиенты `runClient -Pusername=Dev|Patient -Pquickplay=127.0.0.1`. **У Dev — служебный перк «Админ»: эффекты перков выключены**, навыки проверять на Patient.
- Готовые моды для лаунчера: `Claude Code/dist RP-семейство 2026-10-08/`.

## Отложено (не в Кулинарии)
Профессии (наборы навыков), проверка в игре роста навыков/динамических перков/Пацифиста/телосложения, LR-предметы (пачка 3 Medicine), RP Core «модификаторы».

## Состояние (08.10.2026, вечер)
Этап 1 сделан (подробно — `docs/01_design.md` §8.1).

**Сборка:** `gradlew.bat build`.

**Проверка загрузки и тесты:** `gradlew.bat runGameTestServer`.
- Вместе с семьёй: `-Pwith_family=true`.
- С модами готовки из `libs/pack` (Kaleidoscope, Farmer's Delight): `-Pwith_pack=true`.
- С LSO: `-Pwith_lso=true`.

**Medicine с Кулинарией:** `-Pwith_culinary=true`.

**Команды ГМа:**
- `/rpculinary status [игрок]`;
- `/rpculinary set <игроки> satiety|water|protein|fat|carbs|vitamins|weight|bodyfat <число>`;
- `/rpculinary reset <игроки>`;
- `/rpculinary audit [мод]` — откуда известен состав еды (отладочная: `debug_commands` в `config/rpcore-common.toml`).
- Прокрутка голода — `/rptime sleep|skip <время> <игроки> [all|food]` (RP Core); `/rpculinary advance` убран.

**Конфиг:** `serverconfig/rpculinary-server.toml`. Пункты: `hours_per_rp_day`, `weight_change_speed` и другие.

### Блок 1 (13.10.2026) — Culinary 0.2.0 на Core 0.4.0
- «Нет сил» режет максимум выносливости через розетку выносливости Core (`RpCoreAPI.staminaAttribute`), а не ищет
  `rpstamina:max_stamina` по имени. Нагрузка без мода выносливости — по бегу, как раньше (`RpCoreAPI.stamina() == null`).
- Время RP: пока `/rptime stop`, шаг обмена веществ не идёт; `/rptime sleep|skip` прокручивает голод (событие
  `RpTimeSkipEvent`, во сне — со сниженным обменом). `NutritionProvider.advance` оставлен для Medicine до блока 5.
- `CookingService.REPEATS` не копит старые блюда; GameTest и площадка исключены из релизного jar.
- Команды — в общем списке `/rpcore commands`. В игре не проверено.

## Состояние (14.10.2026) — Culinary 0.3.0 на Core 0.5.0
План — `RP Stamina Sports/docs/09_deploy_plan.md`, шаг 4; дизайн — `07_catalog_speed_weight.md` п. 3 и
`08_overeating_and_nutrient_conditions.md`. В игре не проверено.
- Калории — за восстановленную выносливость (`StaminaRestoredEvent`), `burnKcal`, `consumeNutrient`, `thirstStage`.
- Вес плавно (`[weight]`), нормы по полу и возрасту, штраф скорости за полноту.
- Еда и питьё через силу (`[overfill]`, вода до 150 %), состояния через Medicine (`BodyProvider.condition`), иначе —
  простые скрытые эффекты. Ощущения — `rpculinary.sense.*` (через Medicine, если она умеет).
- Долгие последствия — `core/Conditions` (`[conditions]`).
- Особенности: желудок, аппетит, жажда, зубы, стартовый вес (`[traits]`).

## Состояние (27.10.2026) — Culinary 0.5.0: стадии готовки и отравления (этап 1)
Решения Alex 27.10 (жалоба: повара травились своими блюдами).
- Сырое — `NutritionTable.raw`: правило питания с `"raw"`, теги `rpculinary:raw`/`not_raw`, `forge:raw_*` (кроме руды),
  «raw» в id; правило без `raw` — мясо и рыба; готовое по названию (smoked, cooked…) — нет. `smoked_ham` — не сырой.
  Сырое не помечается как блюдо (нарезка FD).
- Стадии (`core/Cooking`): удача — обычное…шедевр; неудача ниже `burnt_below` — недожарено (`undercooked_share`) или
  сгорело по глубине провала: подгорело → пережарено → уголь. Номера в NBT 0–4 прежние, новые 5–7.
- Отравление — `CulinaryService.foodPoisoning`: сырое `raw_meat_poison_chance`, стадии `poison_*`; удачное и
  непомеченное готовое не травит. Уголь естся втрое дольше.
- Чистюля (`rpcore:clean`, перк Perks 2.3.0, 3 балла): ×`clean_poison_factor` (0.5) и когда ест, и у его блюд (`c` в NBT).
- Уровень готовки может быть ниже нуля (Кухонный кошмар у новичка).

### Этап 2 (27.10.2026) — Culinary 0.6.0, Perks 2.4.0: группы экзотики
- Группы — `server/Exotic` + теги `data/rpculinary/tags/items/exotic/<группа>.json` (id сверены по S5_review 27.10).
  Блюдо наследует группу от ингредиента, если её имеют все варианты ингредиента (`NutritionTable.exoticMask`).
- Знание — служебный перк Perks `exotic_<группа>` (черта `rpculinary:exotic/<группа>`), даёт книга
  `rpculinary:research_notes` (NBT `group`, вкладка ГМа «RP: разное», `/rpculinary notes <группа> [игроки]`).
  Книга зовёт `rpperks.compat.CoreTraits.grantKnowledge` отражением. Без Perks знают все.
- Без знания: −`exotic_cook_penalty` (3) уровня, не выше «хорошего», метка `x` → +`exotic_unknown_poison` (35 %) едоку.
- «Каннибал» (`rpculinary:cannibal`) — негативный, 2 балла; знак — `positive_perks` в конфиге Perks. Механика — этап 3.
