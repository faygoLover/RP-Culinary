package faygolover.rpculinary.core;

import java.util.Random;

/**
 * Обмен веществ (дизайн, §3). Еда попадает в желудок и переваривается в запас энергии; запас тратится на обмен
 * веществ в покое (от «сухого» веса, формула Кетча — Макардла) и на нагрузку. Лишнее уходит в жир, нехватка
 * берётся из жира (а без жира — из мышц). Тело живёт только в сети и быстрее реального времени
 * ({@link Settings#bodyTimeScale}). Все времена здесь — секунды игры (реальные, в сети).
 */
public final class Metabolism {
    private Metabolism() {}

    /** Входные данные шага: сколько прошло, нагрузка, сон и прочее. */
    public static final class Input {
        /** Секунд игры с прошлого шага. */
        public double dt;
        /** Нагрузка за шаг сверх обычной, ккал (от RP Stamina или бега). */
        public double exertionKcal;
        public boolean sleeping;
        public boolean cold;
        /** Множитель обмена веществ (перки, анорексия). */
        public double metabolism = 1;
        /** Скорость переваривания (Малоежка медленнее, Обжора быстрее). */
        public double digest = 1;
        /** Трата воды (Мало пьёт, Много пьёт). */
        public double water = 1;
        /** Вести свою воду (нет LSO, жажда включена). */
        public boolean ownWater = true;
        /** Вода из другого мода (LSO), 0–1; −1 — нет. От неё считаются обмороки от жажды. */
        public double externalHydration = -1;
        /** Воду LSO тратим мы, по времени: долг копится в {@link NutritionState#lsoWaterDebt}. */
        public boolean lsoWater;
        /** Прокрутка: еда и своя вода не опускаются ниже этой доли шкалы (и ниже, чем были). 0 — без порога. */
        public double floor;
        public Random random = new Random();
    }

    // ------------------------------------------------------------------ состояние

    /** Сытость: 0 — пусто, 1 — сыт, больше 1 — переел. */
    public static double satiety(NutritionState m, Settings s) {
        return (m.energy + m.stomach) / Math.max(1, s.reserveKcal);
    }

    /** 0 — сыт, 1 — голоден, 2 — очень голоден, 3 — голодает (энергия из жира). */
    public static int hungerStage(NutritionState m, Settings s) {
        if (!s.hungerEnabled) return 0;
        if (m.energy <= 0 && m.stomach <= 0) return 3;
        double sat = satiety(m, s);
        if (sat < s.veryHungryBelow) return 2;
        if (sat < s.hungryBelow) return 1;
        return 0;
    }

    /** 0 — напоен, 1 — хочет пить, 2 — очень хочет, 3 — обезвожен. По воде 0–1. */
    public static int thirstStage(double hydration, Settings s) {
        if (hydration <= 0) return 3;
        if (hydration < s.veryThirstyBelow) return 2;
        if (hydration < s.thirstyBelow) return 1;
        return 0;
    }

    /** Обмен веществ в покое за сутки, ккал (Кетч — Макардл). */
    public static double basalKcalPerDay(NutritionState m) {
        return 370 + 21.6 * Math.max(10, m.leanKg);
    }

    /** Обычный расход за час игры, ккал (для подсказок и проверки чисел). */
    public static double normalKcalPerHour(NutritionState m, Settings s) {
        return basalKcalPerDay(m) / 24.0 * s.activityFactor * s.bodyTimeScale();
    }

    /** Переполнение желудка: доля сверх вместимости (0 — есть место, 1 — вдвое больше, «до предела»). */
    public static double overfill(NutritionState m, Settings s, double mult) {
        double cap = stomachCapacity(s, mult);
        return cap <= 0 ? 0 : Math.max(0, (m.stomach - cap) / cap);
    }

    /** Сколько ккал влезает в желудок ({@code mult} — анорексия, плохой аппетит). */
    public static double stomachCapacity(Settings s, double mult) {
        return s.stomachKcal * mult;
    }

    /** Есть ли место в желудке. */
    public static boolean hasRoom(NutritionState m, Settings s, double mult) {
        return !s.hungerEnabled || m.stomach < stomachCapacity(s, mult) - 1e-6;
    }

    // ------------------------------------------------------------------ еда

    /**
     * Съесть порцию вида {@code cat} (−1 — вид не считается). Возвращает true, если еда приелась: усваивается хуже
     * (не ниже {@code monotonyUptakeMin}). Другой вид еды чуть «освежает» остальные.
     */
    public static boolean eat(NutritionState m, Food f, int cat, Settings s) {
        return eat(m, f, cat, s, 1);
    }

    /** То же; {@code monotonyWeight} — насколько порция приедается (вкусное — меньше 1). */
    public static boolean eat(NutritionState m, Food f, int cat, Settings s, double monotonyWeight) {
        return eat(m, f, new Boredom.Meal(cat, -1, "", monotonyWeight, false, false), s);
    }

    /** Съедено: вид, способ, блюдо и вес в порциях — {@link Boredom}. true — уже приелось. */
    public static boolean eat(NutritionState m, Food f, Boredom.Meal x, Settings s) {
        boolean fed = Boredom.fedUp(m, x, s);
        Food g = f.scale(Boredom.uptake(m, x, s));
        if (s.hungerEnabled) m.stomach += Math.max(0, g.kcal());
        m.recentKcal += Math.max(0, g.kcal());
        if (s.nutrientsEnabled) {
            add(m, NutritionState.PROTEIN, g.protein() * s.proteinPerGram, s);
            add(m, NutritionState.FAT, g.fat() * s.fatPerGram, s);
            add(m, NutritionState.CARBS, g.carbs() * s.carbsPerGram, s);
            add(m, NutritionState.VITAMINS, g.vitamins() * s.vitaminsPerUnit, s);
        }
        // Пить можно и через силу — до maxWater (лишнее уходит быстрее и вредит).
        m.water = Math.max(0, Math.min(Math.max(100, s.maxWater), m.water + g.water()));
        Boredom.add(m, x, s);
        return fed;
    }

    private static void add(NutritionState m, int i, double v, Settings s) {
        m.nutrients[i] = Math.max(0, Math.min(s.nutrientMax, m.nutrients[i] + v));
    }

    /** Внутривенное питание: состав растекается за {@code seconds} секунд игры, после — плохой аппетит. */
    public static void feedIntravenous(NutritionState m, Food f, double seconds, Settings s) {
        m.ivKcal += f.kcal();
        m.ivProtein += f.protein();
        m.ivFat += f.fat();
        m.ivCarbs += f.carbs();
        m.ivVitamins += f.vitamins();
        m.ivSeconds = Math.max(m.ivSeconds, Math.max(1, seconds));
        m.poorAppetiteSeconds = Math.max(m.poorAppetiteSeconds, m.ivSeconds + s.ivPoorAppetiteHours * 3600);
    }

    /** Рвота: непереваренное пропадает. */
    public static void vomit(NutritionState m) {
        m.stomach = 0;
    }

    // ------------------------------------------------------------------ шаг

    /** Шаг обмена веществ. Возвращает длительность обморока в секундах, если он случился сейчас (иначе 0). */
    public static int tick(NutritionState m, Input in, Settings s) {
        double dt = in.dt;
        if (dt <= 0) return 0;
        double basalPerSecond = basalKcalPerDay(m) / 86400.0 * s.bodyTimeScale();
        double factor = s.activityFactor * (in.sleeping ? s.sleepFactor : 1) * (in.cold ? s.coldFactor : 1) * in.metabolism;
        double spend = basalPerSecond * factor * dt + Math.max(0, in.exertionKcal);
        double normal = basalPerSecond * s.activityFactor * dt;
        // Активность относительно обычной: от неё — трата воды и нутриентов.
        double activity = normal > 0 ? Math.max(0.5, Math.min(3, spend / normal)) : 1;

        // Внутривенное питание — мимо желудка.
        double ivShare = 0;
        if (m.ivSeconds > 0) {
            ivShare = Math.min(1, dt / m.ivSeconds);
            if (s.nutrientsEnabled) {
                add(m, NutritionState.PROTEIN, m.ivProtein * ivShare * s.proteinPerGram, s);
                add(m, NutritionState.FAT, m.ivFat * ivShare * s.fatPerGram, s);
                add(m, NutritionState.CARBS, m.ivCarbs * ivShare * s.carbsPerGram, s);
                add(m, NutritionState.VITAMINS, m.ivVitamins * ivShare * s.vitaminsPerUnit, s);
            }
        }
        double ivKcal = m.ivKcal * ivShare;
        if (ivShare > 0) {
            m.ivKcal -= ivKcal;
            m.ivProtein -= m.ivProtein * ivShare;
            m.ivFat -= m.ivFat * ivShare;
            m.ivCarbs -= m.ivCarbs * ivShare;
            m.ivVitamins -= m.ivVitamins * ivShare;
            m.ivSeconds = Math.max(0, m.ivSeconds - dt);
            if (m.ivSeconds <= 0) m.ivKcal = m.ivProtein = m.ivFat = m.ivCarbs = m.ivVitamins = 0;
        }

        if (s.hungerEnabled) {
            double rate = s.stomachKcal * Math.max(0.1, in.digest) / Math.max(60, s.stomachEmptyHours * 3600);
            double digested = Math.min(m.stomach, rate * dt);
            m.stomach -= digested;
            // Вес меняется плавно (решение 14.10.2026): сытый выше fatGainAbove — часть съеденного сразу в жир;
            // ниже fatBurnBelow — часть расхода тело берёт из жира (чем голоднее, тем больше). Худеть можно без обмороков.
            double sat = satiety(m, s);
            double toFat = 0, fromFat = 0;
            if (s.weightEnabled && sat > s.fatGainAbove) {
                double k = Math.min(1, (sat - s.fatGainAbove) / Math.max(0.01, 1 - s.fatGainAbove)) * s.fatGainShare;
                if (m.condActive[Conditions.CARBS_HIGH]) k *= s.carbsFatMult;
                toFat = digested * Math.min(1, k);
            }
            if (s.weightEnabled && sat < s.fatBurnBelow && m.fatPercent() > s.minFatPercent) {
                fromFat = spend * Math.min(1, (s.fatBurnBelow - sat) / Math.max(0.01, s.fatBurnBelow));
            }
            gainFat(m, toFat, s);
            burn(m, fromFat, s);
            m.energy += digested - toFat + ivKcal - (spend - fromFat);
            if (m.energy > s.reserveKcal) {
                gainFat(m, m.energy - s.reserveKcal, s);
                m.energy = s.reserveKcal;
            }
            if (m.energy < 0) {
                burn(m, -m.energy, s);
                m.energy = 0;
            }
            if (m.energy <= 0 && m.stomach <= 0) m.starvingSeconds += dt;
            else if (satiety(m, s) >= s.veryHungryBelow) m.starvingSeconds = 0;
        } else {
            m.starvingSeconds = 0;
        }

        double nts = s.nutrientTimeScale();
        double hydration = -1;
        if (in.ownWater && s.thirstEnabled) {
            m.water = Math.max(0, m.water - s.waterLossPerHour * nts * activity * Math.max(0, in.water) * dt / 3600.0);
            if (m.water > 100) m.water = Math.max(100, m.water - s.overWaterLossPerHour * dt / 3600.0);
            hydration = m.water / 100;
        } else if (in.externalHydration >= 0) {
            hydration = in.externalHydration;
            if (in.lsoWater && s.thirstEnabled)
                m.lsoWaterDebt += s.waterLossPerHour * nts * activity * Math.max(0, in.water) * dt / 3600.0;
        }
        if (hydration < 0) m.dehydratedSeconds = 0;
        else if (hydration <= 0) m.dehydratedSeconds += dt;
        else if (hydration >= s.veryThirstyBelow) m.dehydratedSeconds = 0;

        if (s.nutrientsEnabled) {
            double[] decay = {s.proteinDecayPerHour, s.fatDecayPerHour, s.carbsDecayPerHour, s.vitaminsDecayPerHour};
            for (int i = 0; i < NutritionState.COUNT; i++)
                m.nutrients[i] = Math.max(0, m.nutrients[i] - decay[i] * nts * activity * dt / 3600.0);
        }
        Boredom.decay(m, s.monotonyDecayPerHour * nts * dt / 3600.0);
        Conditions.update(m, s, hydration < 0 ? -1 : Math.min(1, hydration), dt);
        if (m.overfillWindowSeconds > 0) {
            m.overfillWindowSeconds = Math.max(0, m.overfillWindowSeconds - dt);
            if (m.overfillWindowSeconds <= 0) m.overfillLimitHits = 0;
        }
        m.recentKcal = Math.max(0, m.recentKcal * (1 - Math.min(1, dt / (s.rpDayHours() * 3600))));
        m.poorAppetiteSeconds = Math.max(0, m.poorAppetiteSeconds - dt);

        return faint(m, in, s, dt);
    }

    /** Голодные обмороки и от жажды: первый — как только подошёл срок, дальше — через случайные промежутки. */
    private static int faint(NutritionState m, Input in, Settings s, double dt) {
        boolean risk = (s.hungerEnabled && m.starvingSeconds >= s.faintAfterMinutes * 60)
                || m.dehydratedSeconds >= s.dehydrationFaintAfterMinutes * 60;
        if (!risk) {
            m.faintCountdown = -1;
            return 0;
        }
        if (m.faintCountdown < 0) m.faintCountdown = 0;
        m.faintCountdown -= dt;
        if (m.faintCountdown > 0) return 0;
        m.faintCountdown = lerp(s.faintIntervalMinMinutes, s.faintIntervalMaxMinutes, in.random.nextDouble()) * 60;
        return (int) Math.round(lerp(s.faintMinSeconds, s.faintMaxSeconds, in.random.nextDouble()));
    }

    /** Лишние калории — в жир (не больше {@code maxFatPercent}). */
    static void gainFat(NutritionState m, double kcal, Settings s) {
        if (!s.weightEnabled || kcal <= 0) return;
        double max = s.maxFatPercent >= 99 ? Double.MAX_VALUE : m.leanKg * s.maxFatPercent / (100 - s.maxFatPercent);
        m.fatKg = Math.min(max, m.fatKg + kcal * s.weightChangeSpeed / s.kcalPerKgFat);
    }

    /** Нехватка — из жира, а когда его почти нет, из мышц (не легче {@code minWeightKg}). */
    static void burn(NutritionState m, double kcal, Settings s) {
        if (!s.weightEnabled || kcal <= 0) return;
        if (m.fatPercent() > s.minFatPercent) {
            m.fatKg = Math.max(0, m.fatKg - kcal * s.weightChangeSpeed / s.kcalPerKgFat);
        } else {
            double lean = m.leanKg - kcal * s.weightChangeSpeed / s.kcalPerKgLean;
            m.leanKg = Math.max(Math.max(10, s.minWeightKg - m.fatKg), lean);
        }
    }

    /** Прокрутка времени (команда ГМа): шаги по минуте, без обмороков. */
    /**
     * Порог прокрутки: еда и своя вода не ниже доли {@code floor} шкалы — и не ниже, чем были до прокрутки, если уже
     * были ниже. Голодать и сохнуть от прокрутки нельзя, только проголодаться.
     */
    static void floor(NutritionState m, double floor, double startSatiety, double startWater, boolean ownWater, Settings s) {
        double minSat = Math.min(floor, startSatiety) * Math.max(1, s.reserveKcal);
        if (m.energy + m.stomach < minSat) m.energy = Math.max(m.energy, minSat - m.stomach);
        m.starvingSeconds = 0;
        if (ownWater) {
            double minWater = Math.min(floor * 100, startWater);
            if (m.water < minWater) m.water = minWater;
            m.dehydratedSeconds = 0;
        }
    }

    public static void advance(NutritionState m, double hours, Input base, Settings s) {
        double left = hours * 3600;
        double startSatiety = satiety(m, s), startWater = m.water;
        while (left > 0) {
            Input in = new Input();
            in.dt = Math.min(60, left);
            in.sleeping = base.sleeping;
            in.metabolism = base.metabolism;
            in.digest = base.digest;
            in.water = base.water;
            in.ownWater = base.ownWater;
            in.externalHydration = base.externalHydration;
            in.lsoWater = base.lsoWater;
            in.random = base.random;
            tick(m, in, s);
            left -= in.dt;
            if (base.floor > 0) floor(m, base.floor, startSatiety, startWater, base.ownWater, s);
        }
        m.faintCountdown = -1;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
