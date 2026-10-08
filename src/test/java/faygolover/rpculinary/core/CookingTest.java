package faygolover.rpculinary.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CookingTest {
    private static double share(int level, int complexity, int quality) {
        Settings s = new Settings();
        Random r = new Random(7);
        int hit = 0, n = 4000;
        for (int i = 0; i < n; i++) if (Cooking.roll(level, complexity, r.nextGaussian(), s) == quality) hit++;
        return hit / (double) n;
    }

    @Test
    void noviceBurnsSometimesMasterNever() {
        assertTrue(share(0, 1, Cooking.BURNT) > 0.1, "новичок иногда сжигает даже яичницу");
        assertEquals(0, share(10, 1, Cooking.BURNT), 1e-9, "мастер не сжигает");
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
