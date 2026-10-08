package faygolover.rpculinary.integration;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Legendary Survival Overhaul (необязательный). Когда он стоит и его жажда включена, вода — его: Кулинария только
 * рисует её своей полоской и не даёт ей убить. Классы LSO трогает только {@link LsoBridge}.
 */
public final class Lso {
    private Lso() {}

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("legendarysurvivaloverhaul");
        return loaded;
    }

    /** Вода LSO 0–1 или −1 (LSO нет, его жажда выключена, у игрока нет данных). */
    public static double hydration(ServerPlayer sp) {
        return loaded() ? LsoBridge.hydration(sp) : -1;
    }

    /** Поменять воду LSO на {@code percent} процентов полоски. */
    public static void addWater(ServerPlayer sp, double percent) {
        if (loaded()) LsoBridge.addWater(sp, percent);
    }

    /** Холодно по LSO (температура ниже нормы). */
    public static boolean cold(ServerPlayer sp) {
        return loaded() && LsoBridge.cold(sp);
    }
}
