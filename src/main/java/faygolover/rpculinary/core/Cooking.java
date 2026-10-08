package faygolover.rpculinary.core;

/**
 * Навык «Кулинария» в деле (дизайн, §6.2): качество блюда и опыт за готовку. Уровень навыка ведёт RP Perks через
 * RP Core; без него — как 10 (правило автора). Сложность блюда — сколько разных ингредиентов в рецепте.
 */
public final class Cooking {
    private Cooking() {}

    public static final int BURNT = 0, NORMAL = 1, GOOD = 2, EXCELLENT = 3, MASTERPIECE = 4;
    public static final String[] QUALITY = {"burnt", "normal", "good", "excellent", "masterpiece"};

    /** Сложность 0–8: блюдо из одного продукта (жареное мясо, хлеб) — 0, из пяти разных — 4. */
    public static int difficulty(int complexity) {
        return Math.max(0, Math.min(8, complexity - 1));
    }

    /**
     * Качество: уровень повара против сложности плюс случайность ({@code gaussian} — нормальное, среднее 0).
     * Простое блюдо выше «хорошего» не бывает, «шедевр» — только у мастера (8+) и только сложное блюдо.
     */
    public static int roll(int level, int complexity, double gaussian, Settings s) {
        int d = difficulty(complexity);
        double score = level - d * 0.5 + gaussian * s.qualitySpread;
        int q;
        if (score < s.burntBelow) q = BURNT;
        else if (score < 3) q = NORMAL;
        else if (score < 6) q = GOOD;
        else if (score < 8.5) q = EXCELLENT;
        else q = MASTERPIECE;
        if (q == MASTERPIECE && (level < 8 || d < 3)) q = EXCELLENT;
        if (q == EXCELLENT && d < 1) q = GOOD;
        return q;
    }

    /**
     * Опыт за {@code count} одинаковых блюд: сложнее — больше; простое для своего уровня — всё меньше, потом
     * ничего; одно и то же подряд ({@code repeats}) — убывает (как опыт «Медицины» за лечение).
     */
    public static double xp(int level, int complexity, int count, int repeats) {
        int d = difficulty(complexity);
        double relevance = Math.max(0, Math.min(1, 1.0 - (level - d) / 4.0));
        if (relevance <= 0 || count <= 0) return 0;
        return (2.0 + d) * relevance * (1 + 0.3 * (count - 1)) * Math.pow(0.6, repeats);
    }

    /** Состав съеденного с учётом качества: подгорело — меньше калорий и витаминов, лучше — витамины сохраннее. */
    public static Food apply(Food f, int quality, Settings s) {
        return switch (quality) {
            case BURNT -> new Food(f.kcal() * 0.85, f.protein() * 0.85, f.fat() * 0.85, f.carbs() * 0.85, f.vitamins() * 0.5, f.water());
            case GOOD -> new Food(f.kcal(), f.protein(), f.fat(), f.carbs(), f.vitamins() * 1.1, f.water());
            case EXCELLENT -> new Food(f.kcal(), f.protein(), f.fat(), f.carbs(), f.vitamins() * 1.2, f.water());
            case MASTERPIECE -> new Food(f.kcal(), f.protein(), f.fat(), f.carbs(), f.vitamins() * 1.3, f.water());
            default -> f;
        };
    }

    /** Насколько блюдо приедается: отличное — вдвое медленнее, шедевр — вчетверо. */
    public static double monotonyWeight(int quality) {
        return quality == MASTERPIECE ? 0.25 : quality == EXCELLENT ? 0.5 : 1;
    }
}
