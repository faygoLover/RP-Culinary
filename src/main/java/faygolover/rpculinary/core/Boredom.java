package faygolover.rpculinary.core;

/**
 * «Приелось» по трём осям (решения Alex 26.10): вид еды («из чего», {@link NutritionState#CATEGORIES}), способ
 * («как приготовлено», {@link NutritionState#METHODS}) и само блюдо (id предмета). Блюдо надоедает сильнее всего,
 * вид и способ — мягче. Счётчики копятся калориями: порция {@link Settings#portionStepKcal} — единица; укус большого
 * блюда — его доля. Любимое не приедается, нелюбимое — вдвое быстрее, сухпайки — быстрее, но усваиваются всегда.
 */
public final class Boredom {
    private Boredom() {}

    /** Что съедено: вид, способ, блюдо (−1 / пусто — нет), вес в порциях и отношение к нему. */
    public record Meal(int cat, int method, String dish, double portions, boolean favourite, boolean disliked) {
        public boolean ration() {
            return method == NutritionState.method("canned");
        }
    }

    /** Ось, по которой приелось сильнее всего. */
    public enum Axis { NONE, DISH, CATEGORY, METHOD }

    /** Насколько приелось: &lt; 0 — нет; 0 — ровно на пороге; дальше — на столько «порций» сверх. */
    public static double excess(NutritionState m, Meal x, Settings s) {
        double e = Double.NEGATIVE_INFINITY;
        if (x.cat >= 0 && x.cat < m.monotony.length) e = Math.max(e, m.monotony[x.cat] - s.monotonyThreshold);
        if (x.method >= 0 && x.method < m.methodMono.length) e = Math.max(e, m.methodMono[x.method] - s.methodThreshold);
        if (x.dish != null && !x.dish.isEmpty()) e = Math.max(e, m.dishMono.getOrDefault(x.dish, 0.0) - s.dishThreshold);
        return e;
    }

    /** Какая ось сейчас самая «надоевшая» (для строки ощущения). */
    public static Axis axis(NutritionState m, Meal x, Settings s) {
        double dish = x.dish == null || x.dish.isEmpty() ? Double.NEGATIVE_INFINITY : m.dishMono.getOrDefault(x.dish, 0.0) - s.dishThreshold;
        double cat = x.cat >= 0 && x.cat < m.monotony.length ? m.monotony[x.cat] - s.monotonyThreshold : Double.NEGATIVE_INFINITY;
        double met = x.method >= 0 && x.method < m.methodMono.length ? m.methodMono[x.method] - s.methodThreshold : Double.NEGATIVE_INFINITY;
        double top = Math.max(dish, Math.max(cat, met));
        if (top < 0) return Axis.NONE;
        if (top == dish) return Axis.DISH;
        return top == cat ? Axis.CATEGORY : Axis.METHOD;
    }

    /**
     * Голод глушит разборчивость: сыт — в полную силу, голоден — вдвое слабее, очень голоден (и при нехватке
     * нутриентов) — не приедается совсем.
     */
    public static double hungerFactor(NutritionState m, Settings s) {
        if (Nutrients.anyLow(m, s)) return 0;
        int st = Metabolism.hungerStage(m, s);
        return st >= 2 ? 0 : st == 1 ? 0.5 : 1;
    }

    /** Усвоение этой порции (1 — полностью). */
    public static double uptake(NutritionState m, Meal x, Settings s) {
        if (!s.monotonyEnabled || x.favourite || x.ration()) return 1;
        double h = hungerFactor(m, s);
        double e = excess(m, x, s);
        if (h <= 0 || e < 0) return 1;
        return Math.max(s.monotonyUptakeMin, 1 - s.monotonyUptakeStep * (e + 1) * h);
    }

    /** Приелось ли (для замедления еды и строки ощущения). */
    public static boolean fedUp(NutritionState m, Meal x, Settings s) {
        return s.monotonyEnabled && !x.favourite && hungerFactor(m, s) > 0 && excess(m, x, s) >= 0;
    }

    /** Записать съеденное в счётчики. Другие виды и способы чуть «освежаются». */
    public static void add(NutritionState m, Meal x, Settings s) {
        if (!s.monotonyEnabled || x.favourite) return;
        double w = Math.max(0, x.portions);
        if (x.disliked) w *= s.dislikedWeight;
        if (x.ration()) w *= s.rationWeight;
        if (x.cat >= 0 && x.cat < m.monotony.length) {
            for (int i = 0; i < m.monotony.length; i++)
                m.monotony[i] = i == x.cat ? m.monotony[i] + w : Math.max(0, m.monotony[i] - s.monotonyVarietyRelief * Math.min(1, x.portions));
        }
        if (x.method >= 0 && x.method < m.methodMono.length) {
            for (int i = 0; i < m.methodMono.length; i++)
                m.methodMono[i] = i == x.method ? m.methodMono[i] + w * s.methodWeight
                        : Math.max(0, m.methodMono[i] - s.monotonyVarietyRelief * 0.5 * Math.min(1, x.portions));
        }
        if (x.dish != null && !x.dish.isEmpty()) {
            m.dishMono.merge(x.dish, w, Double::sum);
            while (m.dishMono.size() > NutritionState.DISH_MEMORY) {
                String eldest = m.dishMono.keySet().iterator().next();
                m.dishMono.remove(eldest);
            }
        }
    }

    /** Счётчики тают со временем. */
    public static void decay(NutritionState m, double amount) {
        for (int i = 0; i < m.monotony.length; i++) m.monotony[i] = Math.max(0, m.monotony[i] - amount);
        for (int i = 0; i < m.methodMono.length; i++) m.methodMono[i] = Math.max(0, m.methodMono[i] - amount);
        m.dishMono.replaceAll((k, v) -> v - amount);
        m.dishMono.values().removeIf(v -> v <= 0);
    }
}
