package faygolover.rpculinary.server;

import faygolover.rpcore.api.NutritionProvider;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Nutrients;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.integration.Lso;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** RP Culinary — мод питания для RP Core: остальные моды семейства берут голод, воду, нутриенты и вес отсюда. */
public final class CulinaryProvider implements NutritionProvider {
    @Override
    public boolean handles(ResourceLocation feature) {
        Settings s = Settings.get();
        if (feature.equals(RpIds.FOOD_HUNGER)) return s.hungerEnabled;
        if (feature.equals(RpIds.FOOD_THIRST)) return s.thirstEnabled || Lso.loaded();
        if (feature.equals(RpIds.FOOD_WEIGHT)) return s.weightEnabled;
        return false;
    }

    @Override
    public double satiety(Player player) {
        NutritionState m = CulinaryService.state(player);
        return m == null || !Settings.get().hungerEnabled ? 1 : Metabolism.satiety(m, Settings.get());
    }

    @Override
    public double hydration(Player player) {
        NutritionState m = CulinaryService.state(player);
        if (m == null || !(player instanceof ServerPlayer sp)) return 1;
        // Выпитое через силу (больше 100 %) — для других модов просто «напоен»: перепой ведёт сама Кулинария.
        return Math.min(1, CulinaryService.hydration(sp, m, Settings.get()));
    }

    @Override
    public int thirstStage(Player player) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        NutritionState m = CulinaryService.state(player);
        if (m == null || CulinaryService.thirstMode(sp, Settings.get()) == 0) return 0;
        return Metabolism.thirstStage(CulinaryService.hydration(sp, m, Settings.get()), Settings.get());
    }

    @Override
    public void consumeNutrient(Player player, String id, double amount) {
        NutritionState m = CulinaryService.state(player);
        if (m == null || amount <= 0 || !Settings.get().nutrientsEnabled) return;
        for (int i = 0; i < NutritionState.COUNT; i++)
            if (NutritionState.NUTRIENTS[i].equals(id)) m.nutrients[i] = Math.max(0, m.nutrients[i] - amount);
    }

    @Override
    public void burnKcal(Player player, double kcal) {
        NutritionData d = player.level().isClientSide ? null : NutritionData.get(player);
        if (d != null && kcal > 0) d.extraKcal += kcal;
    }

    @Override
    public double nutrient(Player player, String id) {
        NutritionState m = CulinaryService.state(player);
        if (m == null || !Settings.get().nutrientsEnabled) return Double.NaN;
        for (int i = 0; i < NutritionState.COUNT; i++) if (NutritionState.NUTRIENTS[i].equals(id)) return m.nutrients[i];
        return Double.NaN;
    }

    @Override
    public int hungerStage(Player player) {
        NutritionState m = CulinaryService.state(player);
        return m == null ? 0 : Metabolism.hungerStage(m, Settings.get());
    }

    @Override
    public boolean poorAppetite(Player player) {
        NutritionState m = CulinaryService.state(player);
        return m != null && Nutrients.poorAppetite(m, Settings.get());
    }

    @Override
    public double weightKg(Player player) {
        NutritionState m = CulinaryService.state(player);
        return m == null ? Settings.get().defaultWeightKg : m.weightKg();
    }

    @Override
    public double bodyFatPercent(Player player) {
        NutritionState m = CulinaryService.state(player);
        return m == null ? Settings.get().defaultFatPercent : m.fatPercent();
    }

    @Override
    public void setBody(Player player, double weightKg, double fatPercent) {
        NutritionState m = CulinaryService.state(player);
        if (m == null) return;
        m.setBody(Double.isNaN(weightKg) ? m.weightKg() : weightKg, Double.isNaN(fatPercent) ? m.fatPercent() : fatPercent, Settings.get());
        changed(player);
    }

    @Override
    public void addWater(Player player, double percent) {
        if (!(player instanceof ServerPlayer sp)) return;
        NutritionState m = CulinaryService.state(player);
        if (m == null) return;
        int mode = CulinaryService.thirstMode(sp, Settings.get());
        if (mode == 2) Lso.addWater(sp, percent);
        else if (mode == 1) m.water = Math.max(0, Math.min(Math.max(100, m.water), m.water + percent));
        changed(player);
    }

    @Override
    public void vomit(Player player) {
        NutritionState m = CulinaryService.state(player);
        if (m == null) return;
        Metabolism.vomit(m);
        changed(player);
    }

    @Override
    public void feedIntravenous(Player player, double kcal, double protein, double fat, double carbs, double vitamins, int seconds) {
        NutritionState m = CulinaryService.state(player);
        if (m == null) return;
        Metabolism.feedIntravenous(m, new Food(kcal, protein, fat, carbs, vitamins, 0), seconds, Settings.get());
        changed(player);
    }

    @Override
    public void advance(Player player, double hours) {
        if (!(player instanceof ServerPlayer sp)) return;
        CulinaryCommands.advance(sp, hours, false);
    }

    private static void changed(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d != null) CulinaryService.sync(sp, d, true);
    }
}
