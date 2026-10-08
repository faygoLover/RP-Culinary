package faygolover.rpculinary.core;

/** Нехватка, избыток, баланс нутриентов и «приелось» (перенесено из RP Medicine). */
public final class Nutrients {
    private Nutrients() {}

    public static boolean low(NutritionState m, int i, Settings s) {
        return s.nutrientsEnabled && m.nutrients[i] < s.nutrientLow;
    }

    public static boolean high(NutritionState m, int i, Settings s) {
        return s.nutrientsEnabled && m.nutrients[i] > s.nutrientHigh;
    }

    public static boolean balanced(NutritionState m, Settings s) {
        if (!s.nutrientsEnabled) return false;
        for (double v : m.nutrients) if (v < s.balancedMin || v > s.balancedMax) return false;
        return true;
    }

    public static boolean anyLow(NutritionState m, Settings s) {
        for (int i = 0; i < NutritionState.COUNT; i++) if (low(m, i, s)) return true;
        return false;
    }

    /** Приелось: этот вид ели слишком часто, и человек не голоден (голодному не до вкуса). */
    public static boolean fedUp(NutritionState m, int cat, Settings s) {
        return s.monotonyEnabled && cat >= 0 && cat < NutritionState.CATEGORIES.length
                && m.monotony[cat] >= s.monotonyThreshold && !anyLow(m, s) && Metabolism.hungerStage(m, s) < 2;
    }

    /** Битовая маска видов, которые сейчас приелись. */
    public static int fedUpMask(NutritionState m, Settings s) {
        int mask = 0;
        for (int i = 0; i < NutritionState.CATEGORIES.length; i++) if (fedUp(m, i, s)) mask |= 1 << i;
        return mask;
    }

    /** Плохой аппетит: что-то сильно приелось или недавно была капельница. */
    public static boolean poorAppetite(NutritionState m, Settings s) {
        if (m.poorAppetiteSeconds > 0) return true;
        for (int i = 0; i < NutritionState.CATEGORIES.length; i++)
            if (fedUp(m, i, s) && m.monotony[i] >= s.monotonyThreshold + 2) return true;
        return false;
    }
}
