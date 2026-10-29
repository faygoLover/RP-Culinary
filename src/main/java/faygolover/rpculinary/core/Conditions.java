package faygolover.rpculinary.core;

/**
 * Долгие последствия нехватки и избытка (RP Stamina Sports docs/08 п. 2): если элемент долго вне нормы, через
 * несколько суток тела появляется состояние; оно проходит, когда питание в норме половину того срока.
 * <p>
 * Прогресс 0…2: с причиной растёт на «1 за срок», состояние появляется на 1 (у цинги на 2 — тяжёлая). Без причины,
 * пока состояния нет, прогресс тает с той же скоростью; когда состояние есть — копится выздоровление (1 за полсрока).
 */
public final class Conditions {
    private Conditions() {}

    public static final int PROTEIN_DEFICIENCY = 0, KIDNEY_STONES = 1, HIGH_PRESSURE = 2, SCURVY = 3,
            CARBS_DIZZY = 4, CARBS_HIGH = 5, FAT_NAUSEA = 6, COUNT = 7;
    public static final String[] IDS = {"protein_deficiency", "kidney_stones", "high_pressure", "scurvy",
            "carbs_dizzy", "carbs_high", "fat_nausea"};

    /** Есть ли состояние. */
    public static boolean active(NutritionState m, int i) {
        return m.condActive[i];
    }

    /** Тяжесть 0–1 (у цинги: 0,5 — обычная, 1 — тяжёлая). */
    public static double severity(NutritionState m, int i) {
        if (!m.condActive[i]) return 0;
        if (i == SCURVY) return m.condProgress[i] >= 2 ? 1 : 0.5;
        return 1;
    }

    /** Шаг: {@code hydration} — вода 0–1 (−1 — не ведётся). */
    public static void update(NutritionState m, Settings s, double hydration, double dt) {
        if (!s.conditionsEnabled || !s.nutrientsEnabled) {
            java.util.Arrays.fill(m.condActive, false);
            java.util.Arrays.fill(m.condProgress, 0);
            java.util.Arrays.fill(m.condRecover, 0);
            return;
        }
        double day = s.rpDayHours() * 3600;
        double low = s.nutrientLow, high = s.nutrientHigh;
        double pr = m.nutrients[NutritionState.PROTEIN], fat = m.nutrients[NutritionState.FAT];
        double cb = m.nutrients[NutritionState.CARBS], vit = m.nutrients[NutritionState.VITAMINS];
        step(m, PROTEIN_DEFICIENCY, pr < low ? 1 / (s.proteinDeficiencyDays * day) : 0, s.proteinDeficiencyDays * day, dt);
        double kidney = Math.max(pr > high ? 1 / (s.kidneyProteinDays * day) : 0,
                hydration >= 0 && hydration < 0.3 ? 1 / (s.kidneyWaterDays * day) : 0);
        step(m, KIDNEY_STONES, kidney, Math.min(s.kidneyProteinDays, s.kidneyWaterDays) * day, dt);
        step(m, HIGH_PRESSURE, fat > high ? 1 / (s.pressureDays * day) : 0, s.pressureDays * day, dt);
        step(m, SCURVY, vit < low ? 1 / (s.scurvyDays * day) : 0, s.scurvyDays * day, dt);
        step(m, CARBS_DIZZY, cb < low ? 1 / (s.carbsDizzyDays * day) : 0, s.carbsDizzyDays * day, dt);
        step(m, CARBS_HIGH, cb > high ? 1 / (s.carbsFatDays * day) : 0, s.carbsFatDays * day, dt);
        step(m, FAT_NAUSEA, fat > high ? 1 / (s.fatNauseaDays * day) : 0, s.fatNauseaDays * day, dt);
    }

    /** {@code rate} — прирост прогресса в секунду (0 — причины нет); {@code onset} — срок, секунд. */
    private static void step(NutritionState m, int i, double rate, double onset, double dt) {
        if (rate > 0) {
            m.condProgress[i] = Math.min(2, m.condProgress[i] + rate * dt);
            m.condRecover[i] = 0;
            if (m.condProgress[i] >= 1) m.condActive[i] = true;
            return;
        }
        if (!m.condActive[i]) {
            m.condProgress[i] = Math.max(0, m.condProgress[i] - dt / Math.max(1, onset));
            return;
        }
        m.condRecover[i] += dt / Math.max(1, onset / 2);
        if (m.condRecover[i] >= 1) {
            m.condActive[i] = false;
            m.condProgress[i] = 0;
            m.condRecover[i] = 0;
        }
    }
}
