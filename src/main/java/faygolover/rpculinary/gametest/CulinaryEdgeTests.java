package faygolover.rpculinary.gametest;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Settings;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.TreeSet;

/**
 * Края состояния питания: каждое поле в сохранении, смерть, возврат из Края, перезаход. Страховка перед переводом
 * данных на общий механизм RP Core (план {@code _family/refactor}, задача T0.3): тесты обязаны проходить и до, и после.
 */
@GameTestHolder(RpCulinary.MODID)
@PrefixGameTestTemplate(false)
public final class CulinaryEdgeTests {
    private CulinaryEdgeTests() {}

    static final String T = CulinaryGameTests.T;
    /** Ветка в ForgeCaps игрока. Менять нельзя: так лежит в сохранениях живого сервера. */
    static final String KEY = "rpculinary:nutrition";

    /** Поля, которые в NBT не пишутся: снимок на 15.11.2026, разбор в docs/test_findings.md. */
    static final Set<String> NOT_SAVED = Set.of(
            "ateItemTick", "clickedTick", "cookField", "cookHeat", "cookMenu", "cookUntil", "drowsyUntil",
            "edemaFaintTick", "exertionStamina", "extraKcal", "forcedNoted", "lastHungerStage", "lastRoom",
            "lastSyncHash", "lastThirstStage", "mirroredFood", "sprintTicks", "stepTicks", "sugarUntil",
            "state.exhaustionTimer", "state.lsoWaterDebt", "state.regenTimer");
    /** Поля, которые пишутся только вместе с другим полем. */
    static final Set<String> SAVED_WITH_OTHER = Set.of(
            "state.condRecover[0]", "state.condRecover[last]", "state.ivCarbs", "state.ivFat", "state.ivKcal",
            "state.ivProtein", "state.ivVitamins");
    /**
     * Поля, которые чтение правит. {@code state.initialized}: у тела без массы чтение сбрасывает флаг намеренно
     * ({@code NutritionData.load}), чтобы тело завелось заново.
     */
    static final Set<String> NOT_RESTORED = Set.of("state.initialized");

    /** Каждое простое поле данных питания попадает в NBT и читается обратно. */
    @GameTest(template = T, batch = "culinary_edge_nbt")
    public static void nbtCoversEveryField(GameTestHelper h) {
        NbtCoverage.Result r = NbtCoverage.check(NutritionData::new, NutritionData::save, NutritionData::load, "faygolover.rpculinary");
        Set<String> newLost = new TreeSet<>(r.notSaved);
        newLost.removeAll(NOT_SAVED);
        newLost.removeAll(SAVED_WITH_OTHER);
        Set<String> nowSaved = new TreeSet<>(NOT_SAVED);
        nowSaved.removeAll(r.notSaved);
        Set<String> newBroken = new TreeSet<>(r.notRestored);
        newBroken.removeAll(NOT_RESTORED);
        StringBuilder sb = new StringBuilder();
        if (!newLost.isEmpty()) sb.append(" НЕ СОХРАНЯЮТСЯ: ").append(newLost);
        if (!newBroken.isEmpty()) sb.append(" ЧИТАЮТСЯ НЕ ТАК: ").append(newBroken);
        if (!nowSaved.isEmpty()) sb.append(" теперь сохраняются, убрать из NOT_SAVED: ").append(nowSaved);
        if (!r.errors.isEmpty()) sb.append(" ОШИБКИ: ").append(r.errors);
        if (sb.length() > 0) h.fail("Проверено полей " + r.checked + "." + sb + " Не проверялись (коллекции): " + r.skipped);
        h.succeed();
    }

    /** Старое сохранение без данных мода: читается без ошибок и даёт то же, что новый игрок. */
    @GameTest(template = T, batch = "culinary_edge_empty")
    public static void nbtEmptyLoad(GameTestHelper h) {
        NutritionData d = new NutritionData();
        d.load(new CompoundTag());
        if (!new NutritionData().save().equals(d.save())) h.fail("После чтения пустого тега состояние отличается от нового игрока");
        h.succeed();
    }

    static void mark(NutritionData d) {
        NutritionState m = d.state;
        m.stomach = 500;
        m.energy = 10;
        m.water = 5;
        m.starvingSeconds = 30;
        m.dehydratedSeconds = 40;
        m.faintCountdown = 7;
        d.insectMeals = 3;
        d.sweetPortions = 2;
        d.parasitesAt = 12345;
        d.liverPoisonAt = 23456;
        d.bodyClock = 999;
    }

    /**
     * Смерть: желудок пуст, энергия не ниже половины резерва, вода не ниже 50, счётчики голода сброшены; привычки и
     * отложенные последствия остаются.
     */
    @GameTest(template = T, batch = "culinary_edge_death")
    public static void deathResetsHungerKeepsHabits(GameTestHelper h) {
        ServerPlayer p = CulinaryGameTests.player(h);
        NutritionData d = NutritionData.get(p);
        if (d == null) { h.fail("Нет данных питания у игрока"); return; }
        mark(d);
        Settings s = Settings.get();
        ServerPlayer n = h.getLevel().getServer().getPlayerList().respawn(p, false);
        try {
            NutritionData nd = NutritionData.get(n);
            if (nd == null) { h.fail("Нет данных питания после возрождения"); return; }
            NutritionState m = nd.state;
            if (m.stomach != 0) h.fail("Желудок после смерти: " + m.stomach);
            if (m.energy < s.reserveKcal * 0.5 - 1e-6) h.fail("Энергия после смерти: " + m.energy);
            if (m.water < 50 - 1e-6) h.fail("Вода после смерти: " + m.water);
            if (m.starvingSeconds != 0 || m.dehydratedSeconds != 0 || m.faintCountdown != -1) h.fail("Счётчики голода после смерти не сброшены");
            if (nd.insectMeals != 3 || nd.sweetPortions != 2 || nd.parasitesAt != 12345 || nd.liverPoisonAt != 23456 || nd.bodyClock != 999)
                h.fail("Привычки или отложенные последствия после смерти потеряны");
        } finally {
            CulinaryGameTests.remove(h, n);
        }
        h.succeed();
    }

    /** Возврат из Края (не смерть): всё, что пишется в NBT, остаётся. */
    @GameTest(template = T, batch = "culinary_edge_end")
    public static void endReturnKeepsAll(GameTestHelper h) {
        ServerPlayer p = CulinaryGameTests.player(h);
        NutritionData d = NutritionData.get(p);
        if (d == null) { h.fail("Нет данных питания у игрока"); return; }
        mark(d);
        CompoundTag before = d.save();
        ServerPlayer n = h.getLevel().getServer().getPlayerList().respawn(p, true);
        try {
            NutritionData nd = NutritionData.get(n);
            if (nd == null) { h.fail("Нет данных питания после возрождения"); return; }
            if (!before.equals(nd.save())) h.fail("После возврата из Края данные питания изменились: было " + before + ", стало " + nd.save());
        } finally {
            CulinaryGameTests.remove(h, n);
        }
        h.succeed();
    }

    /** Перезаход: данные игрока записаны и прочитаны, ветка мода в сохранении та же. */
    @GameTest(template = T, batch = "culinary_edge_relog")
    public static void relogKeeps(GameTestHelper h) {
        ServerPlayer p = CulinaryGameTests.player(h);
        ServerPlayer q = CulinaryGameTests.player(h);
        try {
            NutritionData d = NutritionData.get(p);
            if (d == null) { h.fail("Нет данных питания у игрока"); return; }
            mark(d);
            CompoundTag saved = p.saveWithoutId(new CompoundTag());
            if (!saved.getCompound("ForgeCaps").contains(KEY)) h.fail("В сохранении игрока нет ветки ForgeCaps." + KEY);
            q.load(saved);
            NutritionData qd = NutritionData.get(q);
            if (qd == null || !d.save().equals(qd.save())) h.fail("После перезахода данные питания изменились");
        } finally {
            CulinaryGameTests.remove(h, p);
            CulinaryGameTests.remove(h, q);
        }
        h.succeed();
    }

    /**
     * Соседи только через RP Core. Без мода тела заражение отвечает «некому» (тогда еда даёт простой эффект), без
     * мода черт знание не выдаётся, без мода психики вызовы не падают. С соседями ответ даёт розетка.
     */
    @GameTest(template = T, batch = "culinary_edge_core_links")
    public static void coreLinks(GameTestHelper h) {
        ServerPlayer p = CulinaryGameTests.player(h);
        try {
            boolean body = faygolover.rpcore.api.RpCoreAPI.body() != null;
            boolean infected = faygolover.rpculinary.server.Exotic.infectForTest(p, 40, 2);
            if (infected && !body) h.fail("Без мода тела заражение «принято»");
            int learned = faygolover.rpculinary.server.Exotic.learnAnyForTest(p);
            if (faygolover.rpcore.api.RpCoreAPI.providerNames().isEmpty() && learned != -1) h.fail("Без мода черт знание «выдано»: " + learned);
            faygolover.rpculinary.server.Exotic.mindForTest(p);
        } finally {
            CulinaryGameTests.remove(h, p);
        }
        h.succeed();
    }
}
