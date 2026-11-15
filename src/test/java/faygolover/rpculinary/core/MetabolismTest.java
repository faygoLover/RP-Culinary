package faygolover.rpculinary.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MetabolismTest {
    private static NutritionState fresh(Settings s) {
        NutritionState m = new NutritionState();
        m.init(s, 70, 20);
        return m;
    }

    private static Metabolism.Input step(double seconds) {
        Metabolism.Input in = new Metabolism.Input();
        in.dt = seconds;
        in.random = new Random(1);
        return in;
    }

    private static void run(NutritionState m, Settings s, double hours) {
        for (int i = 0; i < hours * 60; i++) Metabolism.tick(m, step(60), s);
    }

    @Test
    void numbersMatchDesign() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        // 70 кг, 20 % жира: сухой вес 56 кг → ~1580 ккал в покое; с обычной активностью ~2050 за РП-сутки.
        assertEquals(1579.6, Metabolism.basalKcalPerDay(m), 0.1);
        double perHour = Metabolism.normalKcalPerHour(m, s);
        assertTrue(perHour > 300 && perHour < 400, "около 340 ккал за час игры, было " + perHour);
        // Сытость с 80 % до нуля — около пяти часов игры.
        double hours = 0.8 * s.reserveKcal / perHour;
        assertTrue(hours > 4 && hours < 6, "часов до голода: " + hours);
    }

    @Test
    void eatingFillsStomachThenEnergy() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        double before = Metabolism.satiety(m, s);
        Metabolism.eat(m, new Food(500, 50, 30, 0, 2, 0), 0, s);
        assertEquals(500, m.stomach, 1e-9);
        assertEquals(before + 0.25, Metabolism.satiety(m, s), 1e-9, "сытость поднимается сразу");
        run(m, s, 1.5);
        assertEquals(0, m.stomach, 1e-6, "за полтора часа желудок пуст");
        assertTrue(m.nutrients[NutritionState.PROTEIN] > s.nutrientStart, "белок прибавился");
    }

    @Test
    void stomachLimitsEating() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        assertTrue(Metabolism.hasRoom(m, s, 1));
        Metabolism.eat(m, new Food(1500, 0, 0, 375, 0, 0), -1, s);
        assertFalse(Metabolism.hasRoom(m, s, 1), "полный желудок — больше не лезет");
        NutritionState small = fresh(s);
        Metabolism.eat(small, new Food(1000, 0, 0, 250, 0, 0), -1, s);
        assertTrue(Metabolism.hasRoom(small, s, 1));
        assertFalse(Metabolism.hasRoom(small, s, s.anorexicStomach), "анорексия: желудок меньше");
    }

    @Test
    void surplusGoesToFatWithSpeed() {
        Settings s = new Settings();
        // Ускорение задаём явно: значение по умолчанию менялось (0.9.1: 3 → 1), тест проверяет множитель, а не его.
        s.weightChangeSpeed = 3;
        NutritionState m = fresh(s);
        m.energy = s.reserveKcal;
        double fat = m.fatKg;
        // 7700 ккал лишних — при ускорении ×3 это 3 кг жира.
        Metabolism.gainFat(m, 7700, s);
        assertEquals(fat + 3, m.fatKg, 1e-9);
        s.weightEnabled = false;
        Metabolism.gainFat(m, 7700, s);
        assertEquals(fat + 3, m.fatKg, 1e-9, "вес выключен — не меняется");
    }

    @Test
    void starvationBurnsFatThenMuscleButNotBelowMinimum() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        m.energy = 0;
        double fat = m.fatKg;
        run(m, s, 1);
        assertEquals(3, Metabolism.hungerStage(m, s), "энергия на нуле — голодает");
        assertTrue(m.fatKg < fat, "худеет");
        assertEquals(56, m.leanKg, 1e-9, "пока есть жир — мышцы целы");
        m.fatKg = 2;
        double lean = m.leanKg;
        run(m, s, 1);
        assertTrue(m.leanKg < lean, "жира почти нет — тают мышцы");
        run(m, s, 2000);
        assertTrue(m.weightKg() >= s.minWeightKg - 1e-6, "не легче минимума: " + m.weightKg());
    }

    @Test
    void hungerStages() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        assertEquals(0, Metabolism.hungerStage(m, s));
        m.energy = s.reserveKcal * 0.2;
        assertEquals(1, Metabolism.hungerStage(m, s));
        m.energy = s.reserveKcal * 0.05;
        assertEquals(2, Metabolism.hungerStage(m, s));
        m.stomach = 100;
        m.energy = 0;
        assertEquals(2, Metabolism.hungerStage(m, s), "в желудке ещё есть — не голодает");
        s.hungerEnabled = false;
        assertEquals(0, Metabolism.hungerStage(m, s));
    }

    @Test
    void faintsOnlyAfterLongStarvation() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        m.energy = 0;
        int faints = 0;
        for (int i = 0; i < 119; i++) faints += Metabolism.tick(m, step(60), s) > 0 ? 1 : 0;
        assertEquals(0, faints, "до двух часов — без обмороков");
        int first = 0;
        for (int i = 0; i < 2 && first == 0; i++) first = Metabolism.tick(m, step(60), s);
        assertTrue(first >= s.faintMinSeconds && first <= s.faintMaxSeconds, "первый обморок: " + first);
        int next = 0;
        for (int i = 0; i < 9; i++) next += Metabolism.tick(m, step(60), s) > 0 ? 1 : 0;
        assertEquals(0, next, "следующий — не раньше чем через 10 минут");
        Metabolism.eat(m, new Food(1000, 0, 0, 250, 0, 0), -1, s);
        Metabolism.tick(m, step(60), s);
        assertEquals(0, m.starvingSeconds, "поел — голодание сброшено");
    }

    @Test
    void waterDropsAndDrinkRestores() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        m.water = 100;
        run(m, s, 1);
        assertEquals(100 - s.waterLossPerHour, m.water, 0.5, "минус 15 % за час");
        Metabolism.eat(m, new Food(0, 0, 0, 0, 0, 10), -1, s);
        assertEquals(100 - s.waterLossPerHour + 10, m.water, 0.5);
        Metabolism.Input lso = step(3600);
        lso.ownWater = false;
        double w = m.water;
        Metabolism.tick(m, lso, s);
        assertEquals(w, m.water, 1e-9, "с LSO своя вода не тратится");
    }

    @Test
    void exertionBurnsMore() {
        Settings s = new Settings();
        NutritionState a = fresh(s), b = fresh(s);
        Metabolism.Input calm = step(60);
        Metabolism.Input run = step(60);
        run.exertionKcal = 50;
        Metabolism.tick(a, calm, s);
        Metabolism.tick(b, run, s);
        assertEquals(a.energy - 50, b.energy, 1e-6);
        assertTrue(b.water < a.water, "на бегу хочется пить сильнее");
    }

    @Test
    void monotonyAndPoorAppetite() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        Food fish = new Food(200, 30, 5, 0, 1, 0);
        int fish0 = NutritionState.category("fish");
        boolean fed = false;
        for (int i = 0; i < 6; i++) fed = Metabolism.eat(m, fish, fish0, s);
        assertTrue(fed, "шестая рыба подряд — приелась");
        assertFalse(Nutrients.poorAppetite(m, s));
        Metabolism.eat(m, fish, fish0, s);
        Metabolism.eat(m, fish, fish0, s);
        assertTrue(Nutrients.poorAppetite(m, s), "сильно приелось — плохой аппетит");
    }

    @Test
    void intravenousFeedingIsSlowAndSpoilsAppetite() {
        Settings s = new Settings();
        NutritionState m = fresh(s);
        m.energy = 0;
        Metabolism.feedIntravenous(m, new Food(1000, 20, 0, 200, 30, 0), 1800, s);
        assertEquals(0, Metabolism.satiety(m, s), 1e-9, "сразу не поднимает");
        assertTrue(Nutrients.poorAppetite(m, s));
        run(m, s, 0.5);
        assertTrue(m.energy > 600, "за полчаса влилось, энергия " + m.energy);
        assertEquals(0, m.ivSeconds, 1e-9);
        run(m, s, s.ivPoorAppetiteHours);
        assertFalse(Nutrients.poorAppetite(m, s), "аппетит вернулся");
    }

    @Test
    void advanceMatchesTicks() {
        Settings s = new Settings();
        NutritionState a = fresh(s), b = fresh(s);
        Metabolism.advance(a, 2, new Metabolism.Input(), s);
        run(b, s, 2);
        assertEquals(b.energy, a.energy, 1e-6);
        assertEquals(b.water, a.water, 1e-6);
    }

    @Test
    void rpDayLengthScalesHunger() {
        Settings six = new Settings();
        Settings twelve = new Settings();
        twelve.hoursPerRpDay = 12;
        NutritionState a = fresh(six), b = fresh(twelve);
        run(a, six, 1);
        run(b, twelve, 1);
        double spentA = six.reserveKcal * six.startSatiety - a.energy;
        double spentB = twelve.reserveKcal * twelve.startSatiety - b.energy;
        assertEquals(spentA / 2, spentB, 1e-6, "сутки вдвое длиннее — тратится вдвое медленнее");
    }
}
