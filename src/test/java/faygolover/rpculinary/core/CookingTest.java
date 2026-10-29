package faygolover.rpculinary.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CookingTest {
    private static double share(int level, int complexity, int quality) {
        Settings s = new Settings();
        Random r = new Random(7);
        int hit = 0, n = 4000;
        for (int i = 0; i < n; i++) if (Cooking.roll(level, complexity, r.nextGaussian(), r.nextDouble(), s) == quality) hit++;
        return hit / (double) n;
    }

    private static double failShare(double level, int complexity) {
        Settings s = new Settings();
        Random r = new Random(11);
        int hit = 0, n = 4000;
        for (int i = 0; i < n; i++) if (!Cooking.success(Cooking.roll(level, complexity, r.nextGaussian(), r.nextDouble(), s))) hit++;
        return hit / (double) n;
    }

    @Test
    void noviceFailsSometimesMasterNever() {
        assertTrue(share(0, 1, Cooking.BURNT) > 0.03, "новичок иногда сжигает даже яичницу");
        assertTrue(share(0, 1, Cooking.UNDERCOOKED) > 0.03, "и недожаривает");
        assertEquals(0, failShare(10, 1), 1e-9, "мастер не портит");
        assertTrue(failShare(5, 5) < 0.01, "высокий навык почти без неудач и на сложном");
        assertTrue(failShare(0, 5) > failShare(0, 1), "сложное блюдо — чаще неудачи");
        assertTrue(failShare(0, 1) > failShare(3, 1), "слабый навык — чаще неудачи");
        assertTrue(failShare(-3, 1) > 0.5, "Кухонный кошмар у новичка — больше половины неудач");
    }

    @Test
    void failureDepthGoesToCharcoal() {
        Settings s = new Settings();
        // Монетка 0.9 — сгорело (не недожарено); глубина провала решает стадию.
        assertEquals(Cooking.BURNT, Cooking.roll(0, 1, -1.1, 0.9, s));
        assertEquals(Cooking.OVERCOOKED, Cooking.roll(0, 1, -2.2, 0.9, s));
        assertEquals(Cooking.CHARCOAL, Cooking.roll(0, 1, -4, 0.9, s));
        assertEquals(Cooking.UNDERCOOKED, Cooking.roll(0, 1, -4, 0.1, s));
        assertEquals(Cooking.GOOD, Cooking.roll(10, 6, 2, 0.5, Cooking.GOOD, s), "потолок — хорошее");
    }

    @Test
    void poisonByStage() {
        Settings s = new Settings();
        for (int q : new int[]{Cooking.NORMAL, Cooking.GOOD, Cooking.EXCELLENT, Cooking.MASTERPIECE})
            assertEquals(0, Cooking.poisonChance(q, s), 1e-9, "удачное блюдо не травит");
        assertTrue(Cooking.poisonChance(Cooking.BURNT, s) <= Cooking.poisonChance(Cooking.OVERCOOKED, s), "пережарено — не безопаснее подгоревшего");
        assertTrue(Cooking.poisonChance(Cooking.OVERCOOKED, s) < Cooking.poisonChance(Cooking.UNDERCOOKED, s), "недожарено — средний риск");
        assertTrue(Cooking.poisonChance(Cooking.CHARCOAL, s) > Cooking.poisonChance(Cooking.UNDERCOOKED, s), "уголь — опаснее всего");
        Food f = new Food(400, 20, 10, 50, 10, 0);
        assertTrue(Cooking.apply(f, Cooking.CHARCOAL, s).kcal() < Cooking.apply(f, Cooking.OVERCOOKED, s).kcal());
    }

    @Test
    void simpleDishTopsAtGood() {
        assertEquals(0, share(10, 1, Cooking.EXCELLENT), 1e-9, "жареное мясо — не выше «хорошего»");
        assertEquals(0, share(10, 1, Cooking.MASTERPIECE), 1e-9);
        assertTrue(share(10, 1, Cooking.GOOD) > 0.9);
    }

    @Test
    void masterpieceOnlyForMastersAndComplexDishes() {
        assertEquals(0, share(7, 6, Cooking.MASTERPIECE), 1e-9, "уровень 7 — без шедевров");
        assertTrue(share(10, 4, Cooking.MASTERPIECE) > 0.05, "мастер и сложное блюдо — бывает шедевр");
        assertEquals(0, share(10, 2, Cooking.MASTERPIECE), 1e-9, "из двух продуктов шедевра не сделать");
    }

    @Test
    void xpFallsForTrivialDishesAndRepeats() {
        assertTrue(Cooking.xp(0, 1, 1, 0) > 0, "новичку и простое — опыт");
        assertEquals(0, Cooking.xp(5, 1, 1, 0), 1e-9, "уровню 5 жарить мясо — без опыта");
        assertTrue(Cooking.xp(5, 7, 1, 0) > Cooking.xp(3, 4, 1, 0), "сложнее — больше опыта");
        assertTrue(Cooking.xp(2, 3, 1, 3) < Cooking.xp(2, 3, 1, 0), "одно и то же подряд — меньше");
        assertTrue(Cooking.xp(2, 3, 4, 0) < 4 * Cooking.xp(2, 3, 1, 0), "четыре за раз — меньше, чем по одному");
    }

    @Test
    void qualityChangesWhatYouGet() {
        Settings s = new Settings();
        Food f = new Food(400, 20, 10, 50, 10, 0);
        assertTrue(Cooking.apply(f, Cooking.BURNT, s).kcal() < f.kcal());
        assertTrue(Cooking.apply(f, Cooking.MASTERPIECE, s).vitamins() > f.vitamins());
        NutritionState a = new NutritionState(), b = new NutritionState();
        a.init(s, 70, 20);
        b.init(s, 70, 20);
        Metabolism.eat(a, f, 0, s, 1);
        Metabolism.eat(b, f, 0, s, Cooking.monotonyWeight(Cooking.EXCELLENT));
        assertTrue(b.monotony[0] < a.monotony[0], "вкусное приедается медленнее");
    }
}
