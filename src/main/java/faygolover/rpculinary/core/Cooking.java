package faygolover.rpculinary.core;

/**
 * Навык «Кулинария» в деле (дизайн, §6.2; стадии — решения Alex 27.10.2026): стадия блюда и опыт за готовку. Уровень
 * навыка ведёт RP Perks через RP Core; без него — как 10 (правило автора). Сложность блюда — сколько разных
 * ингредиентов в рецепте.
 * <p>
 * Стадии. Удача: обычное → хорошее → отличное → шедевр. Неудача — недожарено или сгорело: подгорело → пережарено →
 * уголь (чем хуже бросок, тем дальше). Номера в NBT старые (0–4) сохранены, новые стадии — после них.
 */
public final class Cooking {
    private Cooking() {}

    public static final int BURNT = 0, NORMAL = 1, GOOD = 2, EXCELLENT = 3, MASTERPIECE = 4,
            UNDERCOOKED = 5, OVERCOOKED = 6, CHARCOAL = 7;
    public static final String[] QUALITY = {"burnt", "normal", "good", "excellent", "masterpiece",
            "undercooked", "overcooked", "charcoal"};

    /** Сложность 0–8: блюдо из одного продукта (жареное мясо, хлеб) — 0, из пяти разных — 4. */
    public static int difficulty(int complexity) {
        return Math.max(0, Math.min(8, complexity - 1));
    }

    /** Удачная стадия (обычное и выше). */
    public static boolean success(int q) {
        return q == NORMAL || q == GOOD || q == EXCELLENT || q == MASTERPIECE;
    }

    /** Хорошее и выше. */
    public static boolean goodOrBetter(int q) {
        return q == GOOD || q == EXCELLENT || q == MASTERPIECE;
    }

    /** Сгорело: подгорело, пережарено, уголь. */
    public static boolean burned(int q) {
        return q == BURNT || q == OVERCOOKED || q == CHARCOAL;
    }

    /**
     * Стадия: уровень повара против сложности плюс случайность ({@code gaussian} — нормальное, среднее 0).
     * Простое блюдо выше «хорошего» не бывает, «шедевр» — только у мастера (8+) и только сложное блюдо.
     * Ниже порога {@link Settings#burntBelow} — неудача: {@code coin} (равномерное 0–1) решает, недожарено или
     * сгорело, глубина провала — насколько сгорело. {@code cap} — выше этой стадии не бывает (экзотика без знания).
     */
    public static int roll(double level, int complexity, double gaussian, double coin, int cap, Settings s) {
        int d = difficulty(complexity);
        double score = level - d * 0.5 + gaussian * s.qualitySpread;
        if (score < s.burntBelow) {
            if (coin < s.undercookedShare) return UNDERCOOKED;
            double depth = s.burntBelow - score;
            return depth < s.overcookedDepth ? BURNT : depth < s.charcoalDepth ? OVERCOOKED : CHARCOAL;
        }
        int q;
        if (score < 3) q = NORMAL;
        else if (score < 6) q = GOOD;
        else if (score < 8.5) q = EXCELLENT;
        else q = MASTERPIECE;
        if (q == MASTERPIECE && (level < 8 || d < 3)) q = EXCELLENT;
        if (q == EXCELLENT && d < 1) q = GOOD;
        return Math.min(q, cap);
    }

    /** Без потолка. */
    public static int roll(double level, int complexity, double gaussian, double coin, Settings s) {
        return roll(level, complexity, gaussian, coin, MASTERPIECE, s);
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

    /** Состав съеденного с учётом стадии: сгоревшее — меньше калорий и витаминов, лучше — витамины сохраннее. */
    public static Food apply(Food f, int quality, Settings s) {
        return switch (quality) {
            case BURNT -> f.scaleNutrition(0.85, 0.5);
            case OVERCOOKED -> f.scaleNutrition(0.65, 0.25);
            case CHARCOAL -> f.scaleNutrition(0.2, 0);
            case GOOD -> f.scaleNutrition(1, 1.1);
            case EXCELLENT -> f.scaleNutrition(1, 1.2);
            case MASTERPIECE -> f.scaleNutrition(1, 1.3);
            default -> f;
        };
    }

    /** Шанс отравиться блюдом этой стадии (до множителей желудка и чистоплотности). */
    public static double poisonChance(int quality, Settings s) {
        return switch (quality) {
            case UNDERCOOKED -> s.poisonUndercooked;
            case BURNT -> s.poisonBurnt;
            case OVERCOOKED -> s.poisonOvercooked;
            case CHARCOAL -> s.poisonCharcoal;
            default -> 0;
        };
    }

    /** Насколько блюдо приедается: отличное — вдвое медленнее, шедевр — вчетверо. */
    public static double monotonyWeight(int quality) {
        return quality == MASTERPIECE ? 0.25 : quality == EXCELLENT ? 0.5 : 1;
    }
}
