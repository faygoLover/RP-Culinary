package faygolover.rpculinary.integration;

import net.minecraft.server.level.ServerPlayer;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureCapability;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureProvider;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstCapability;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstProvider;
import sfiomn.legendarysurvivaloverhaul.config.Config;

/** Обращения к классам LSO. Загружается, только если LSO установлен (через {@link Lso}). */
final class LsoBridge {
    private LsoBridge() {}

    /** Ниже этой температуры LSO — холодно (норма LSO — 16–24). */
    private static final float COLD = 16f;

    static double hydration(ServerPlayer sp) {
        if (!Config.Baked.thirstEnabled) return -1;
        ThirstCapability c = sp.getCapability(ThirstProvider.THIRST_CAPABILITY).resolve().orElse(null);
        return c == null ? -1 : Math.max(0, Math.min(1, c.getHydrationLevel() / (double) ThirstCapability.MAX_HYDRATION));
    }

    static void addWater(ServerPlayer sp, double percent) {
        if (!Config.Baked.thirstEnabled) return;
        ThirstCapability c = sp.getCapability(ThirstProvider.THIRST_CAPABILITY).resolve().orElse(null);
        if (c == null) return;
        int max = ThirstCapability.MAX_HYDRATION;
        c.setHydrationLevel((int) Math.max(0, Math.min(max, Math.round(c.getHydrationLevel() + percent / 100.0 * max))));
        c.setDirty();
    }

    static void clearExhaustion(ServerPlayer sp) {
        if (!Config.Baked.thirstEnabled) return;
        ThirstCapability c = sp.getCapability(ThirstProvider.THIRST_CAPABILITY).resolve().orElse(null);
        if (c != null && c.getThirstExhaustion() != 0) c.setExhaustion(0);
    }

    static double percentPerPoint() {
        return 100.0 / Math.max(1, ThirstCapability.MAX_HYDRATION);
    }

    static boolean cold(ServerPlayer sp) {
        if (!Config.Baked.temperatureEnabled) return false;
        TemperatureCapability c = sp.getCapability(TemperatureProvider.TEMPERATURE_CAPABILITY).resolve().orElse(null);
        return c != null && c.getTemperatureLevel() < COLD;
    }
}
