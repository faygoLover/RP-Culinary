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
    @Cfg(section = "energy", doc = "RP Stamina: ккал на каждые 100 потраченной выносливости (минута бега ≈ 960).")
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

    // ------------------------------------------------------------------ готовка (навык «Кулинария»)
    @Cfg(section = "cooking", doc = "Готовка: качество блюд и опыт навыка «Кулинария» (уровни ведёт RP Perks; без него — как 10).")
    public boolean cookingEnabled = true;
    @Cfg(section = "cooking", doc = "Разброс качества: чем больше, тем случайнее (в уровнях навыка).")
    public double qualitySpread = 1.5;
    @Cfg(section = "cooking", doc = "Подгорает, если уровень минус половина сложности плюс случайность ниже этого.")
    public double burntBelow = -1.5;
    @Cfg(section = "cooking", doc = "После клика по плите, котлу, верстаку столько секунд новые блюда в инвентаре считаются приготовленными игроком.")
    public double cookContextSeconds = 5;
    @Cfg(section = "cooking", doc = "Сырое мясо и рыба: шанс пищевого отравления (тошнота; с RP Medicine — рвота).")
    public double rawMeatPoisonChance = 0.3;
    @Cfg(section = "cooking", doc = "Чистюля (черта rpcore:clean): шанс отравления во столько раз ниже.")
    public double cleanPoisonFactor = 0.2;
    @Cfg(section = "cooking", doc = "Полевой повар (черта rpcore:field_cook): на костре и в коптильне — плюс столько уровней.")
    public double fieldCookBonus = 2;
    @Cfg(section = "cooking", doc = "Кухонный кошмар (черта rpcore:kitchen_nightmare): минус столько уровней при готовке.")
    public double kitchenNightmarePenalty = 3;

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
