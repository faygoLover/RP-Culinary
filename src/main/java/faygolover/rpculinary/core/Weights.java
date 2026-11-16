package faygolover.rpculinary.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Вес и стак ингредиентов (блок 2 переделки, решения Alex 28–29.10): таблица {@code assets/rpculinary/weights.json}
 * (генерирует {@code tools/gen_weights.py}) лежит в моде и читается одинаково на сервере и клиенте — без синхронизации.
 * <p>
 * Порция — 250 г, стак 16; мелкое — 32; стейк — 8; цельное — 4. Крупный кусок, орган, тушка с весом в NBT
 * ({@code rpculinary:weight}, граммы; {@code rpculinary:source} — чей) — не стакается: один предмет два состояния
 * (с весом — «Большой кусок волчатины, 4 кг», без — порция). Стак только уменьшаем, больше родного не делаем.
 */
public final class Weights {
    private Weights() {}

    public static final String TAG_WEIGHT = "rpculinary:weight";
    public static final String TAG_SOURCE = "rpculinary:source";

    private record Entry(int grams, int stack) {}

    private static final Entry NONE = new Entry(0, 0);
    private static Map<String, Entry> raw;
    /** Кэш по предмету; «нет в таблице» тоже кэшируется, но только после регистрации предмета. */
    private static final Map<Item, Entry> CACHE = new ConcurrentHashMap<>();

    private static Entry entry(Item item) {
        Entry e = CACHE.get(item);
        if (e != null) return e == NONE ? null : e;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return null;  // ещё не зарегистрирован — не запоминать
        e = raw().get(id.toString());
        CACHE.put(item, e != null ? e : NONE);
        return e;
    }

    private static Map<String, Entry> raw() {
        if (raw != null) return raw;
        Map<String, Entry> m = new HashMap<>();
        try (InputStream in = Weights.class.getResourceAsStream("/assets/rpculinary/weights.json")) {
            if (in != null) {
                JsonObject items = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
                for (var e : items.entrySet()) {
                    var a = e.getValue().getAsJsonArray();
                    m.put(e.getKey(), new Entry(a.get(0).getAsInt(), a.get(1).getAsInt()));
                }
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: не прочитан weights.json", ex);
        }
        raw = m;
        return m;
    }

    /** Вес из NBT (граммы) или 0. */
    public static int nbtGrams(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.contains(TAG_WEIGHT) ? t.getInt(TAG_WEIGHT) : 0;
    }

    /** Чей кусок (корова, волк…) или пусто. */
    public static String source(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null ? t.getString(TAG_SOURCE) : "";
    }

    /** Номинальный вес штуки по таблице (граммы) или 0, если предмета нет в таблице. */
    public static int nominal(Item item) {
        Entry e = entry(item);
        return e != null ? e.grams() : 0;
    }

    /** Вес штуки: из NBT, иначе по таблице; 0 — неизвестен. */
    public static int grams(ItemStack st) {
        int g = nbtGrams(st);
        return g > 0 ? g : nominal(st.getItem());
    }

    /**
     * Наш предел стака или -1 (не трогать): с весом в NBT — 1; иначе стак из таблицы (0 в таблице — не трогать).
     * Вызывается из миксина на {@code ItemStack.getMaxStackSize()} — должно быть быстрым.
     */
    public static int maxStack(ItemStack st) {
        if (st.isEmpty()) return -1;
        if (nbtGrams(st) > 0 || Containers.of(st.getItem()) != null) return 1;  // крупный кусок и тара — не стакаются
        Entry e = entry(st.getItem());
        return e != null && e.stack() > 0 ? e.stack() : -1;
    }

    /** Множитель питания: вес куска / номинальный вес (крупный кусок 6 кг = 24 порции по 250 г). */
    public static double nutritionFactor(ItemStack st) {
        int g = nbtGrams(st), n = nominal(st.getItem());
        return g > 0 && n > 0 ? g / (double) n : 1.0;
    }

    /** «1.2 кг» / «250 г». */
    public static String format(int grams) {
        if (grams >= 1000) {
            double kg = grams / 1000.0;
            return (kg == Math.rint(kg) ? String.valueOf((int) kg) : String.format(Locale.ROOT, "%.1f", kg).replace('.', ',')) + " кг";
        }
        return grams + " г";
    }
}
