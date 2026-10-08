package faygolover.rpculinary.core;

/** Питание одного игрока. Чистые данные: сохраняет их {@code NutritionData}, считает {@link Metabolism}. */
public final class NutritionState {
    public static final int PROTEIN = 0, FAT = 1, CARBS = 2, VITAMINS = 3, COUNT = 4;
    public static final String[] NUTRIENTS = {"protein", "fat", "carbs", "vitamins"};
    /**
     * Виды еды для «приелось»: однообразная еда одного вида надоедает (по виду, а не по предмету). Вид блюда — вид
     * ингредиента с наибольшими калориями.
     */
    public static final String[] CATEGORIES = {"meat", "fish", "grain", "vegetables", "fruit", "sweet", "dairy"};

    public static int category(String id) {
        for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(id)) return i;
        return -1;
    }

    /** Первое знакомство: числа ещё не выставлены (вес возьмём у мода тела, если он есть). */
    public boolean initialized;

    /** Запас энергии, ккал (0 — голодает). */
    public double energy;
    /** Съедено, но ещё не переварено, ккал. */
    public double stomach;
    /** Вода, % (0–100). */
    public double water = 100;
    public final double[] nutrients = {60, 60, 60, 60};
    /** Недавние порции по видам («приелось»). */
    public final double[] monotony = new double[CATEGORIES.length];
    /** Съедено за последние часы, ккал (для подсказок Диетолога). */
    public double recentKcal;

    /** Тело: жир и всё остальное («сухой» вес: мышцы, кости, вода). */
    public double fatKg;
    public double leanKg;

    /** Сколько секунд игры подряд энергия на нуле / вода на нуле. */
    public double starvingSeconds;
    public double dehydratedSeconds;
    /** До следующего обморока, секунд (−1 — не назначен). */
    public double faintCountdown = -1;
    /** Плохой аппетит ещё столько секунд (капельница). */
    public double poorAppetiteSeconds;
    /** Внутривенное питание: сколько ещё влить и за сколько секунд. */
    public double ivKcal, ivProtein, ivFat, ivCarbs, ivVitamins, ivSeconds;

    /** Счётчики «раз в N секунд» без мода тела (восстановление и истощение). */
    public double regenTimer;
    public double exhaustionTimer;

    public double weightKg() {
        return fatKg + leanKg;
    }

    public double fatPercent() {
        double w = weightKg();
        return w > 0 ? fatKg / w * 100 : 0;
    }

    /** Выставить вес и долю жира (ГМ, первое знакомство). */
    public void setBody(double weightKg, double fatPercent, Settings s) {
        double w = Math.max(s.minWeightKg, weightKg);
        double f = Math.max(0, Math.min(s.maxFatPercent, fatPercent));
        fatKg = w * f / 100;
        leanKg = w - fatKg;
    }

    /** Новый игрок: сытость, вода и нутриенты из настроек, обычное телосложение. */
    public void init(Settings s, double weightKg, double fatPercent) {
        energy = Math.min(1, s.startSatiety) * s.reserveKcal;
        stomach = 0;
        water = s.startWater;
        java.util.Arrays.fill(nutrients, s.nutrientStart);
        java.util.Arrays.fill(monotony, 0);
        setBody(Double.isNaN(weightKg) ? s.defaultWeightKg : weightKg, Double.isNaN(fatPercent) ? s.defaultFatPercent : fatPercent, s);
        initialized = true;
    }

    public void copyFrom(NutritionState o) {
        initialized = o.initialized;
        energy = o.energy;
        stomach = o.stomach;
        water = o.water;
        System.arraycopy(o.nutrients, 0, nutrients, 0, COUNT);
        System.arraycopy(o.monotony, 0, monotony, 0, monotony.length);
        recentKcal = o.recentKcal;
        fatKg = o.fatKg;
        leanKg = o.leanKg;
        starvingSeconds = o.starvingSeconds;
        dehydratedSeconds = o.dehydratedSeconds;
        faintCountdown = o.faintCountdown;
        poorAppetiteSeconds = o.poorAppetiteSeconds;
        ivKcal = o.ivKcal;
        ivProtein = o.ivProtein;
        ivFat = o.ivFat;
        ivCarbs = o.ivCarbs;
        ivVitamins = o.ivVitamins;
        ivSeconds = o.ivSeconds;
    }
}
