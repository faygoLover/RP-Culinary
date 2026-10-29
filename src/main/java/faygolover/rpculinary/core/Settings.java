package faygolover.rpculinary.core;

/**
 * Числа питания. Поля с комментариями — настройки сервера ({@code rpculinary-server.toml}): конфиг заполняет их
 * по имени поля (см. {@code CulinaryConfig}). Логика ядра читает только этот класс — её можно проверять без игры.
 */
public final class Settings {
    /** Поле — настройка сервера: раздел конфига и описание. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
    public @interface Cfg {
        String section();

        String doc();
    }

    private static volatile Settings current = new Settings();

    public static Settings get() {
        return current;
    }

    public static void set(Settings s) {
        current = s;
    }

    // ------------------------------------------------------------------ общее
    @Cfg(section = "general", doc = "Голод в калориях вместо ванильных окорочков. false — ванильный голод, остальное (вода, нутриенты) работает.")
    public boolean hungerEnabled = true;
    @Cfg(section = "general", doc = "Своя жажда, если не стоит LSO.")
    public boolean thirstEnabled = true;
    @Cfg(section = "general", doc = "Нутриенты (белки, жиры, углеводы, витамины) и «приелось».")
    public boolean nutrientsEnabled = true;
    @Cfg(section = "general", doc = "Вес и жир меняются от калорий.")
    public boolean weightEnabled = true;
    @Cfg(section = "general", doc = "Сколько часов игры (в сети) — одни сутки для тела.")
    public double hoursPerRpDay = 6;
    @Cfg(section = "general", doc = "Ускорение изменения веса: 1 — как в жизни (1 кг жира ≈ 7700 ккал), 3 — втрое быстрее.")
    public double weightChangeSpeed = 3;

    // ------------------------------------------------------------------ энергия
    @Cfg(section = "energy", doc = "Запас энергии при полной сытости (100 %), ккал.")
    public double reserveKcal = 2000;
    @Cfg(section = "energy", doc = "Желудок: сколько ккал влезает за раз.")
    public double stomachKcal = 1500;
    @Cfg(section = "energy", doc = "За сколько часов игры переваривается полный желудок.")
    public double stomachEmptyHours = 1.5;
    @Cfg(section = "energy", doc = "Сытость нового игрока, 0–1.")
    public double startSatiety = 0.8;
    @Cfg(section = "energy", doc = "Обычная активность (ходьба, работа руками) — множитель к обмену веществ в покое.")
    public double activityFactor = 1.3;
    @Cfg(section = "energy", doc = "Сон — множитель расхода.")
    public double sleepFactor = 0.9;
    @Cfg(section = "energy", doc = "Холод (LSO) — множитель расхода.")
    public double coldFactor = 1.15;
    @Cfg(section = "energy", doc = "RP Stamina: ккал на каждые 100 ВОССТАНОВЛЕННОЙ выносливости (калории уходят на отдых после нагрузки; минута бега ≈ 960).")
    public double kcalPer100Stamina = 5;
    @Cfg(section = "energy", doc = "Без RP Stamina: ккал за минуту бега.")
    public double sprintKcalPerMinute = 50;
    @Cfg(section = "energy", doc = "Сколько ккал в килограмме жира.")
    public double kcalPerKgFat = 7700;
    @Cfg(section = "energy", doc = "Сколько ккал даёт килограмм мышц, когда жира почти не осталось.")
    public double kcalPerKgLean = 1800;
    @Cfg(section = "energy", doc = "Ниже этой доли жира (%) голодающий теряет мышцы.")
    public double minFatPercent = 5;
    @Cfg(section = "energy", doc = "Ниже этой доли жира (%) голодающий теряет мышцы.")
    public double defaultWeightKg = 70;
    @Cfg(section = "energy", doc = "Ниже этой доли жира (%) голодающий теряет мышцы.")
    public double defaultFatPercent = 20;
    @Cfg(section = "energy", doc = "Не легче, кг.")
    public double minWeightKg = 35;
    @Cfg(section = "energy", doc = "Жира не больше, %.")
    public double maxFatPercent = 60;
    @Cfg(section = "energy", doc = "Еда, чей состав неизвестен: ккал за единицу ванильной сытости.")
    public double kcalPerFoodPoint = 90;

    // ------------------------------------------------------------------ голод
    @Cfg(section = "hunger", doc = "Сытость ниже — «голоден».")
    public double hungryBelow = 0.3;
    @Cfg(section = "hunger", doc = "Ниже — «очень голоден», нет сил.")
    public double veryHungryBelow = 0.1;
    @Cfg(section = "hunger", doc = "Через сколько минут игры на нуле начинаются голодные обмороки.")
    public double faintAfterMinutes = 120;
    @Cfg(section = "hunger", doc = "Обмороки: промежуток между ними, минут игры.")
    public double faintIntervalMinMinutes = 10;
    @Cfg(section = "hunger", doc = "Обмороки: промежуток между ними, минут игры.")
    public double faintIntervalMaxMinutes = 20;
    @Cfg(section = "hunger", doc = "Длительность обморока, секунд.")
    public double faintMinSeconds = 10;
    @Cfg(section = "hunger", doc = "Длительность обморока, секунд.")
    public double faintMaxSeconds = 30;
    @Cfg(section = "hunger", doc = "«Нет сил» (очень голоден или очень хочет пить): выносливость, скорость, удар.")
    public double weakStaminaCap = 0.8;
    @Cfg(section = "hunger", doc = "«Нет сил» (очень голоден или очень хочет пить): выносливость, скорость, удар.")
    public double weakSpeedPenalty = 0.05;
    @Cfg(section = "hunger", doc = "«Нет сил» (очень голоден или очень хочет пить): выносливость, скорость, удар.")
    public double weakAttackPenalty = 0.1;
    @Cfg(section = "hunger", doc = "Без мода тела (RP Medicine): истощение отнимает здоровье, но не ниже стольких единиц (2 = сердце).")
    public double healthFloor = 6;
    @Cfg(section = "hunger", doc = "Без мода тела: раз во столько секунд истощение отнимает единицу здоровья.")
    public double exhaustionDamageSeconds = 60;
    @Cfg(section = "hunger", doc = "Без мода тела: сытый и напоенный восстанавливает единицу здоровья раз во столько секунд (0 — никогда).")
    public double regenSecondsPerHp = 10;
    @Cfg(section = "hunger", doc = "Без мода тела: сытый и напоенный восстанавливает единицу здоровья раз во столько секунд (0 — никогда).")
    public double regenMinSatiety = 0.5;
    @Cfg(section = "hunger", doc = "Без мода тела: сытый и напоенный восстанавливает единицу здоровья раз во столько секунд (0 — никогда).")
    public double regenMinWater = 0.3;

    // ------------------------------------------------------------------ вода
    @Cfg(section = "water", doc = "Своя вода: минус столько процентов за час игры при обычной активности.")
    public double waterLossPerHour = 15;
    @Cfg(section = "water", doc = "Суп, похлёбка, рагу (по названию предмета) поят на столько процентов, если их нет в датапаке drinks.")
    public double soupWater = 10;
    @Cfg(section = "water", doc = "Пьётся (анимация питья), но нет ни в базе RP Core, ни в drinks: поит на столько процентов (и строка в лог).")
    public double unknownDrinkWater = 10;
    @Cfg(section = "water", doc = "Вода нового игрока, %.")
    public double startWater = 90;
    @Cfg(section = "water", doc = "Вода ниже этой доли (0–1) — «хочется пить».")
    public double thirstyBelow = 0.3;
    @Cfg(section = "water", doc = "Ниже этой доли — сильная жажда, нет сил.")
    public double veryThirstyBelow = 0.1;
    @Cfg(section = "water", doc = "Через сколько минут без воды — обмороки.")
    public double dehydrationFaintAfterMinutes = 30;

    // ------------------------------------------------------------------ аппетит
    @Cfg(section = "appetite", doc = "Анорексия (черта rpcore:anorexic): желудок меньше во столько раз.")
    public double anorexicStomach = 0.6;
    @Cfg(section = "appetite", doc = "Анорексия: обмен веществ выше во столько раз.")
    public double anorexicMetabolism = 1.1;
    @Cfg(section = "appetite", doc = "Быстрый обмен веществ (черта rpcore:fast_metabolism): расход во столько раз.")
    public double fastMetabolism = 1.15;
    @Cfg(section = "appetite", doc = "Склонность к полноте (черта rpcore:slow_metabolism): расход во столько раз.")
    public double slowMetabolism = 0.85;
    @Cfg(section = "appetite", doc = "Гурман (черта rpcore:gourmet): обычная еда приедается во столько раз быстрее.")
    public double gourmetMonotony = 1.5;
    @Cfg(section = "appetite", doc = "Плохой аппетит: желудок меньше во столько раз.")
    public double poorAppetiteStomach = 0.7;
    @Cfg(section = "appetite", doc = "После внутривенного питания плохой аппетит столько часов игры.")
    public double ivPoorAppetiteHours = 3;

    // ------------------------------------------------------------------ нутриенты (перенесено из RP Medicine)
    @Cfg(section = "nutrients", doc = "Запас за грамм белка, жира, углеводов и за единицу витаминов.")
    public double proteinPerGram = 0.9;
    @Cfg(section = "nutrients", doc = "Запас за грамм белка, жира, углеводов и за единицу витаминов.")
    public double fatPerGram = 1.2;
    @Cfg(section = "nutrients", doc = "Запас за грамм белка, жира, углеводов и за единицу витаминов.")
    public double carbsPerGram = 0.45;
    @Cfg(section = "nutrients", doc = "Запас за грамм белка, жира, углеводов и за единицу витаминов.")
    public double vitaminsPerUnit = 1.0;
    @Cfg(section = "nutrients", doc = "Трата запаса в час игры при обычной активности (при «сутках» 6 часов; иначе пересчитывается).")
    public double proteinDecayPerHour = 25;
    @Cfg(section = "nutrients", doc = "Трата запаса в час игры при обычной активности (при «сутках» 6 часов; иначе пересчитывается).")
    public double fatDecayPerHour = 15;
    @Cfg(section = "nutrients", doc = "Трата запаса в час игры при обычной активности (при «сутках» 6 часов; иначе пересчитывается).")
    public double carbsDecayPerHour = 30;
    @Cfg(section = "nutrients", doc = "Трата запаса в час игры при обычной активности (при «сутках» 6 часов; иначе пересчитывается).")
    public double vitaminsDecayPerHour = 12;
    @Cfg(section = "nutrients", doc = "Предел запаса нутриента.")
    public double nutrientMax = 120;
    @Cfg(section = "nutrients", doc = "Запас нутриентов нового игрока.")
    public double nutrientStart = 60;
    @Cfg(section = "nutrients", doc = "Нехватка (ниже), избыток (выше), баланс (все в полосе).")
    public double nutrientLow = 20;
    @Cfg(section = "nutrients", doc = "Нехватка (ниже), избыток (выше), баланс (все в полосе).")
    public double nutrientHigh = 100;
    @Cfg(section = "nutrients", doc = "Нехватка (ниже), избыток (выше), баланс (все в полосе).")
    public double balancedMin = 35;
    @Cfg(section = "nutrients", doc = "Нехватка (ниже), избыток (выше), баланс (все в полосе).")
    public double balancedMax = 85;
    @Cfg(section = "nutrients", doc = "Без мода тела: нехватка углеводов — выносливость и скорость; белка — удар; избыток жира — тяжело.")
    public double carbsLowStaminaCap = 0.75;
    @Cfg(section = "nutrients", doc = "Без мода тела: нехватка углеводов — выносливость и скорость; белка — удар; избыток жира — тяжело.")
    public double carbsLowSpeedPenalty = 0.05;
    @Cfg(section = "nutrients", doc = "Без мода тела: нехватка углеводов — выносливость и скорость; белка — удар; избыток жира — тяжело.")
    public double proteinLowAttackPenalty = 0.15;
    @Cfg(section = "nutrients", doc = "Без мода тела: нехватка углеводов — выносливость и скорость; белка — удар; избыток жира — тяжело.")
    public double fatHighStaminaCap = 0.85;
    @Cfg(section = "nutrients", doc = "Без мода тела: нехватка углеводов — выносливость и скорость; белка — удар; избыток жира — тяжело.")
    public double fatHighSpeedPenalty = 0.05;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public boolean monotonyEnabled = true;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyThreshold = 5;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyDecayPerHour = 2;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyVarietyRelief = 0.5;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyUptakeStep = 0.05;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyUptakeMin = 0.8;
    @Cfg(section = "nutrients", doc = "«Приелось»: включено; с какого числа недавних порций одного вида; тает в час; другой вид освежает на; усвоение хуже на шаг за порцию сверх порога, но не ниже; есть дольше во сколько раз.")
    public double monotonyEatSlow = 1.3;
    @Cfg(section = "nutrients", doc = "«Приелось» по способу готовки (суп, жареное, выпечка…): с какого числа недавних порций; вес порции для способа (мягче вида).")
    public double methodThreshold = 7;
    @Cfg(section = "nutrients", doc = "«Приелось» по способу готовки (суп, жареное, выпечка…): с какого числа недавних порций; вес порции для способа (мягче вида).")
    public double methodWeight = 0.7;
    @Cfg(section = "nutrients", doc = "«Приелось» по самому блюду (то же самое подряд): с какого числа недавних порций.")
    public double dishThreshold = 3;
    @Cfg(section = "nutrients", doc = "Нелюбимое надоедает во столько раз быстрее и естся во столько раз дольше; сухпайки и консервы надоедают во столько раз быстрее (но усваиваются всегда).")
    public double dislikedWeight = 2;
    @Cfg(section = "nutrients", doc = "Нелюбимое надоедает во столько раз быстрее и естся во столько раз дольше; сухпайки и консервы надоедают во столько раз быстрее (но усваиваются всегда).")
    public double dislikedEatSlow = 1.3;
    @Cfg(section = "nutrients", doc = "Нелюбимое надоедает во столько раз быстрее и естся во столько раз дольше; сухпайки и консервы надоедают во столько раз быстрее (но усваиваются всегда).")
    public double rationWeight = 1.5;
    @Cfg(section = "nutrients", doc = "«Сыт и доволен»: столько разных видов еды за RP-сутки без нехватки нутриентов; восстановление выносливости до конца суток +доля.")
    public double varietyKinds = 4;
    @Cfg(section = "nutrients", doc = "«Сыт и доволен»: столько разных видов еды за RP-сутки без нехватки нутриентов; восстановление выносливости до конца суток +доля.")
    public double contentStaminaRegen = 0.1;
    @Cfg(section = "portions", doc = "Еда по частям: большое блюдо (от двух шагов) естся укусами по столько ккал; остаток от 1 до 2 шагов — последним укусом. Это же — «порция» для счётчиков «приелось».")
    public double portionStepKcal = 400;
    @Cfg(section = "portions", doc = "Еда по частям включена.")
    public boolean bitesEnabled = true;

    // ------------------------------------------------------------------ готовка (навык «Кулинария»)
    @Cfg(section = "cooking", doc = "Готовка: качество блюд и опыт навыка «Кулинария» (уровни ведёт RP Perks; без него — как 10).")
    public boolean cookingEnabled = true;
    @Cfg(section = "cooking", doc = "Разброс качества: чем больше, тем случайнее (в уровнях навыка).")
    public double qualitySpread = 1.5;
    @Cfg(section = "cooking", doc = "Неудача (недожарено или сгорело), если уровень минус половина сложности плюс случайность ниже этого.")
    public double burntBelow = -1.5;
    @Cfg(section = "cooking", doc = "Доля неудач, которые «недожарено» (остальные — сгорело), 0–1.")
    public double undercookedShare = 0.5;
    @Cfg(section = "cooking", doc = "Сгорело: провал глубже стольких уровней — уже «пережарено».")
    public double overcookedDepth = 1.5;
    @Cfg(section = "cooking", doc = "Сгорело: провал глубже стольких уровней — «уголь».")
    public double charcoalDepth = 3;
    @Cfg(section = "cooking", doc = "После клика по плите, котлу, верстаку столько секунд новые блюда в инвентаре считаются приготовленными игроком.")
    public double cookContextSeconds = 5;
    @Cfg(section = "cooking", doc = "Сырое (мясо, рыба; теги forge:raw_*, «raw» в id, правила питания), съеденное сырым: шанс пищевого отравления (тошнота; с RP Medicine — рвота).")
    public double rawMeatPoisonChance = 0.3;
    @Cfg(section = "cooking", doc = "Шанс отравления блюдом «недожарено».")
    public double poisonUndercooked = 0.15;
    @Cfg(section = "cooking", doc = "Шанс отравления блюдом «подгорело».")
    public double poisonBurnt = 0.04;
    @Cfg(section = "cooking", doc = "Шанс отравления блюдом «пережарено».")
    public double poisonOvercooked = 0.04;
    @Cfg(section = "cooking", doc = "Шанс, что от «угля» вырвет (уголь ещё и естся втрое дольше).")
    public double poisonCharcoal = 0.2;
    @Cfg(section = "cooking", doc = "«Уголь» естся во столько раз дольше.")
    public double charcoalEatSlow = 3;
    @Cfg(section = "cooking", doc = "Чистюля (черта rpcore:clean): шанс отравления в столько раз — и когда ест сам, и у блюд, которые он приготовил.")
    public double cleanPoisonFactor = 0.5;
    @Cfg(section = "exotic", doc = "Экзотика без знания группы (книга «Записи исследований»): повар готовит на столько уровней хуже, и не выше «хорошего».")
    public double exoticCookPenalty = 3;
    @Cfg(section = "exotic", doc = "Экзотика без знания: блюдо ещё и травит с таким шансом — на любой стадии, даже «хорошее».")
    public double exoticUnknownPoison = 0.35;
    @Cfg(section = "exotic", doc = "Действие экзотики на того, кто ест (от продукта, не от повара). false — выключить всё.")
    public boolean exoticEffects = true;
    @Cfg(section = "exotic", doc = "Конфетная полость: сонливость от сахара (ЖДЁТ УТОЧНЕНИЯ ALEX).")
    public boolean candyDrowsy = true;
    @Cfg(section = "exotic", doc = "Конфетная полость: шанс «зуб заныл» (как Плохие зубы) за порцию (ЖДЁТ УТОЧНЕНИЯ ALEX); 0 — выключено.")
    public double candyTeethChance = 0.25;
    @Cfg(section = "exotic", doc = "Древний страж (тег rpculinary:effect/mining_fatigue): усталость, секунд.")
    public double fatigueSeconds = 60;
    @Cfg(section = "exotic", doc = "Насекомые (тег rpculinary:effect/disgust): стресс отвращения (RP Psyche) за порцию.")
    public double disgustStress = 3;
    @Cfg(section = "exotic", doc = "Нижний мир: согрев (эффект LSO «горячая еда»), секунд.")
    public double netherWarmSeconds = 120;
    @Cfg(section = "exotic", doc = "Нижний мир: сильная жажда — минус столько процентов воды за порцию.")
    public double netherThirst = 15;
    @Cfg(section = "exotic", doc = "Домашние звери: моральный стресс (RP Psyche) за порцию.")
    public double petsStress = 6;
    @Cfg(section = "exotic", doc = "Монстры: шанс отравиться за порцию (сверху к остальному).")
    public double monstersPoison = 0.5;
    @Cfg(section = "exotic", doc = "Заражённое: сепсис RP Medicine сразу, % (дальше жар); без Medicine — отравление.")
    public double infectedSepsis = 15;
    @Cfg(section = "exotic", doc = "Заражённое: сколько часов после еды сепсис ещё растёт (как испорченная кровь Medicine).")
    public double infectedSepsisHours = 1;
    @Cfg(section = "exotic", doc = "Радиоактивное: доза облучения Mekanism за порцию, Зв; без Mekanism — отравление.")
    public double radioactiveDose = 0.3;
    @Cfg(section = "exotic", doc = "Аномальное: удар по рассудку (RP Psyche) за порцию — отсюда галлюцинации.")
    public double anomalousSanity = 6;
    @Cfg(section = "exotic", doc = "Человечина без «Каннибала»: удар по рассудку (RP Psyche) за порцию.")
    public double humanSanity = 15;
    @Cfg(section = "exotic", doc = "Человечина без «Каннибала»: шанс рвоты за порцию.")
    public double humanVomitChance = 0.7;
    @Cfg(section = "cooking", doc = "Полевой повар (черта rpcore:field_cook): на костре и в коптильне — плюс столько уровней.")
    public double fieldCookBonus = 2;
    @Cfg(section = "cooking", doc = "Кухонный кошмар (черта rpcore:kitchen_nightmare): минус столько уровней при готовке.")
    public double kitchenNightmarePenalty = 3;

    // ------------------------------------------------------------------ вес плавно (решение 14.10.2026)
    @Cfg(section = "weight", doc = "Сытость выше этой доли — часть съеденного сразу уходит в жир (толстеет).")
    public double fatGainAbove = 0.8;
    @Cfg(section = "weight", doc = "При полной сытости в жир уходит такая доля перевариваемого (плавно от fat_gain_above).")
    public double fatGainShare = 0.5;
    @Cfg(section = "weight", doc = "Сытость ниже этой доли — часть расхода тело берёт из жира (худеет); чем голоднее, тем больше, при 0 — весь.")
    public double fatBurnBelow = 0.5;
    @Cfg(section = "weight", doc = "Вес выше нормы (по полу и возрасту, RP Core): минус к скорости.")
    public double overweightSpeedPenalty = 0.03;
    @Cfg(section = "weight", doc = "Ожирение: минус к скорости.")
    public double obeseSpeedPenalty = 0.07;

    // ------------------------------------------------------------------ еда и питьё через силу
    @Cfg(section = "overfill", doc = "Желудок полон — еда всё равно естся, но во столько раз медленнее (и питьё, когда воды больше 100 %).")
    public double forcedEatSlow = 2;
    @Cfg(section = "overfill", doc = "Переполнение (доля сверх желудка): до этой — тяжесть, дальше — тошнота.")
    public double overfillNausea = 0.3;
    @Cfg(section = "overfill", doc = "Переполнение: дальше этой — сильная боль в животе.")
    public double overfillPain = 0.6;
    @Cfg(section = "overfill", doc = "Железный желудок: тошнота с такого переполнения.")
    public double overfillNauseaIron = 0.6;
    @Cfg(section = "overfill", doc = "Железный желудок: боль с такого переполнения.")
    public double overfillPainIron = 0.8;
    @Cfg(section = "overfill", doc = "Слабый желудок: тошнота с такого переполнения.")
    public double overfillNauseaWeak = 0.15;
    @Cfg(section = "overfill", doc = "Слабый желудок: боль с такого переполнения.")
    public double overfillPainWeak = 0.4;
    @Cfg(section = "overfill", doc = "Тошнота: шанс рвоты в минуту.")
    public double nauseaVomitPerMinute = 0.1;
    @Cfg(section = "overfill", doc = "Боль: шанс рвоты в минуту.")
    public double painVomitPerMinute = 0.3;
    @Cfg(section = "overfill", doc = "Тяжесть: минус к скорости и к восстановлению выносливости.")
    public double heavySpeedPenalty = 0.1;
    @Cfg(section = "overfill", doc = "Тяжесть: минус к восстановлению выносливости.")
    public double heavyRegenPenalty = 0.2;
    @Cfg(section = "overfill", doc = "Второе «до предела» за сутки тела — травма желудка.")
    public double stomachInjuryAfter = 2;
    @Cfg(section = "water", doc = "Пить через силу: вода до стольких процентов.")
    public double maxWater = 150;
    @Cfg(section = "water", doc = "Лишняя вода (выше 100 %) уходит со скоростью, % в час игры.")
    public double overWaterLossPerHour = 60;
    @Cfg(section = "water", doc = "Перепил: с такой воды (%) — водная интоксикация (тошнота, слабость, меньше выносливости).")
    public double waterIntoxicationAt = 120;
    @Cfg(section = "water", doc = "Перепил: с такой воды (%) — отёк мозга (обмороки; с RP Medicine — повреждение мозга).")
    public double brainEdemaAt = 140;

    // ------------------------------------------------------------------ долгие последствия (сутки тела)
    @Cfg(section = "conditions", doc = "Долгие последствия нехватки и избытка: включены.")
    public boolean conditionsEnabled = true;
    @Cfg(section = "conditions", doc = "Мало белка столько суток тела подряд — белковая недостаточность.")
    public double proteinDeficiencyDays = 2;
    @Cfg(section = "conditions", doc = "Много белка столько суток — камни в почках.")
    public double kidneyProteinDays = 4;
    @Cfg(section = "conditions", doc = "Мало воды (ниже 30 %) столько суток — камни в почках.")
    public double kidneyWaterDays = 3;
    @Cfg(section = "conditions", doc = "Много жиров столько суток — тошнота после жирного.")
    public double fatNauseaDays = 2;
    @Cfg(section = "conditions", doc = "Много жиров столько суток — повышенное давление.")
    public double pressureDays = 5;
    @Cfg(section = "conditions", doc = "Мало углеводов столько суток — головокружение на бегу.")
    public double carbsDizzyDays = 1;
    @Cfg(section = "conditions", doc = "Много углеводов столько суток — лишнее быстрее идёт в жир.")
    public double carbsFatDays = 1;
    @Cfg(section = "conditions", doc = "Много углеводов: лишнее идёт в жир во столько раз быстрее.")
    public double carbsFatMult = 1.5;
    @Cfg(section = "conditions", doc = "Мало витаминов столько суток — цинга (вдвое дольше — тяжёлая).")
    public double scurvyDays = 3;
    @Cfg(section = "conditions", doc = "Белковая недостаточность без RP Medicine: минус к скорости (отёки).")
    public double proteinDeficiencySpeed = 0.05;

    // ------------------------------------------------------------------ особенности (RP Perks)
    @Cfg(section = "traits", doc = "Железный желудок: шанс отравления едой во столько раз.")
    public double ironStomachPoison = 0.5;
    @Cfg(section = "traits", doc = "Слабый желудок: шанс отравления во столько раз.")
    public double weakStomachPoison = 1.5;
    @Cfg(section = "traits", doc = "Слабый желудок: любая еда — такой шанс отравиться.")
    public double weakStomachAnyFood = 0.05;
    @Cfg(section = "traits", doc = "Малоежка: желудок и переваривание во столько раз (меньше и медленнее — голод позже).")
    public double lightEaterStomach = 0.8;
    @Cfg(section = "traits", doc = "Обжора: желудок и переваривание во столько раз.")
    public double bigEaterStomach = 1.2;
    @Cfg(section = "traits", doc = "Мало пьёт: вода тратится во столько раз.")
    public double lowThirstWater = 0.75;
    @Cfg(section = "traits", doc = "Много пьёт: вода тратится во столько раз.")
    public double highThirstWater = 1.25;
    @Cfg(section = "traits", doc = "Плохие зубы: шанс боли при еде (плюс доля от калорий порции на 2000 ккал).")
    public double badTeethChance = 0.2;

    // ------------------------------------------------------------------ производные

    /** Во сколько раз тело живёт быстрее реального времени. */
    public double bodyTimeScale() {
        return 24.0 / Math.max(0.5, hoursPerRpDay);
    }

    /** Нутриенты настроены на «сутки» 6 часов: при других сутках трата пересчитывается. */
    public double nutrientTimeScale() {
        return 6.0 / Math.max(0.5, hoursPerRpDay);
    }
}
