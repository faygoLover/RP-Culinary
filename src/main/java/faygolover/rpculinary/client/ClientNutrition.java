package faygolover.rpculinary.client;

import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.network.SyncPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Что клиент знает о своём питании (от сервера). */
public final class ClientNutrition {
    private ClientNutrition() {}

    /** Пока сервер ничего не прислал (нет мода на сервере) — ничего не скрываем и не рисуем. */
    public static boolean known;
    public static SyncPacket self;
    public static final Map<ResourceLocation, Food> FOODS = new HashMap<>();
    public static final Map<ResourceLocation, Integer> CATEGORIES = new HashMap<>();

    public static void set(SyncPacket p) {
        self = p;
        known = true;
    }

    public static void setFoods(Map<ResourceLocation, Food> foods, Map<ResourceLocation, Integer> cats) {
        FOODS.clear();
        FOODS.putAll(foods);
        CATEGORIES.clear();
        CATEGORIES.putAll(cats);
    }

    /** Вышли из мира — забыть. */
    public static void clear() {
        known = false;
        self = null;
        FOODS.clear();
        CATEGORIES.clear();
    }

    public static boolean hunger() {
        return known && self.hunger();
    }

    /** Этот вид еды сейчас приелся (для замедления еды на клиенте). */
    public static boolean fedUp(net.minecraft.world.item.ItemStack stack) {
        if (!known) return false;
        Integer cat = CATEGORIES.get(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
        return cat != null && cat >= 0 && (self.fedUpMask() & (1 << cat)) != 0;
    }

    /** 0 — жажды нет, 1 — своя, 2 — LSO. */
    public static int thirstMode() {
        return known ? self.thirstMode() : 0;
    }
}
