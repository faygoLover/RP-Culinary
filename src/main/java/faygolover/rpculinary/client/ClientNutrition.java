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
    /** Напитки: глотков, вода всего (%) — по базе RP Core с сервера. */
    public static final Map<ResourceLocation, int[]> DRINKS = new HashMap<>();

    public static void set(SyncPacket p) {
        self = p;
        known = true;
    }

    public static void setFoods(Map<ResourceLocation, Food> foods, Map<ResourceLocation, Integer> cats, Map<ResourceLocation, int[]> drinks) {
        FOODS.clear();
        FOODS.putAll(foods);
        CATEGORIES.clear();
        CATEGORIES.putAll(cats);
        DRINKS.clear();
        DRINKS.putAll(drinks);
        faygolover.rpculinary.server.Sips.clientTotal = st -> {
            int[] d = drink(st);
            return d == null ? 1 : d[0];
        };
    }

    /** Глотки и вода напитка по таблице (зелье воды — отдельно), или null. */
    public static int[] drink(net.minecraft.world.item.ItemStack st) {
        if (st.getItem() instanceof net.minecraft.world.item.PotionItem) {
            var potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(st);
            return potion == net.minecraft.world.item.alchemy.Potions.WATER
                    ? DRINKS.get(faygolover.rpculinary.network.FoodTablePacket.WATER_POTION) : null;
        }
        return DRINKS.get(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem()));
    }

    /** Вышли из мира — забыть. */
    /** Сервер засчитал глоток или укус: снять «использую», зажатая ПКМ начнёт следующий. */
    public static void portionDone() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && p.isUsingItem()) p.stopUsingItem();
    }

    public static void clear() {
        known = false;
        self = null;
        FOODS.clear();
        CATEGORIES.clear();
        DRINKS.clear();
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

    /** Ест (или пьёт) через силу: желудок полон / воды уже больше 100 % — медленнее. */
    public static boolean forced(boolean drink) {
        if (!known) return false;
        if (drink) return self.thirstMode() == 1 && self.hydration() >= 1f;
        return self.hunger() && !self.room();
    }

    /** 0 — жажды нет, 1 — своя, 2 — LSO. */
    public static int thirstMode() {
        return known ? self.thirstMode() : 0;
    }
}
