package faygolover.rpculinary.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Мешки и ящики с продуктами (блок 6 переделки, решения Alex 29.10): таблица {@code assets/rpculinary/containers.json}
 * ({@code tools/containers_spec.py}) — тара → какой продукт принимает и сколько кг вмещает. Тара не стакается; содержимое —
 * в NBT {@code rpculinary:fill} ({@code id}, {@code n}); пустая — тот же предмет без содержимого. Продукт с NBT (качество,
 * вес) не кладётся: в таре — только простые продукты.
 */
public final class Containers {
    private Containers() {}

    public static final String TAG = "rpculinary:fill", ID = "id", N = "n";

    public record Box(List<String> accept, double kg) {}

    private static final Box NONE = new Box(List.of(), 0);
    private static Map<String, Box> raw;
    private static final Map<Item, Box> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Map<String, Box> raw() {
        if (raw != null) return raw;
        Map<String, Box> m = new HashMap<>();
        try (InputStream in = Containers.class.getResourceAsStream("/assets/rpculinary/containers.json")) {
            if (in != null) {
                JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("containers");
                for (var e : all.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    List<String> acc = new java.util.ArrayList<>();
                    o.getAsJsonArray("accept").forEach(x -> acc.add(x.getAsString()));
                    m.put(e.getKey(), new Box(List.copyOf(acc), o.get("kg").getAsDouble()));
                }
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: не прочитан containers.json", ex);
        }
        raw = m;
        return m;
    }

    /** Тара или null. Быстро: зовётся из стака предмета. */
    public static Box of(Item item) {
        Box b = CACHE.get(item);
        if (b == null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) return null;  // ещё не зарегистрирован — не запоминать
            b = raw().getOrDefault(id.toString(), NONE);
            CACHE.put(item, b);
        }
        return b == NONE ? null : b;
    }

    public static boolean accepts(Box b, ItemStack product) {
        if (b == null || product.isEmpty() || product.hasTag()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(product.getItem());
        return id != null && b.accept().contains(id.toString());
    }

    /** Сколько штук продукта вмещается (по весу; неизвестный вес — 200 г). */
    public static int capacity(Box b, Item product) {
        int g = Weights.nominal(product);
        return Math.max(1, (int) (b.kg() * 1000 / Math.max(1, g > 0 ? g : 200)));
    }

    public static Item content(ItemStack box) {
        CompoundTag t = box.getTag();
        if (t == null || !t.contains(TAG)) return Items.AIR;
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(t.getCompound(TAG).getString(ID)));
        return it == null ? Items.AIR : it;
    }

    public static int count(ItemStack box) {
        CompoundTag t = box.getTag();
        return t == null || !t.contains(TAG) ? 0 : t.getCompound(TAG).getInt(N);
    }

    public static void set(ItemStack box, Item item, int n) {
        if (n <= 0 || item == Items.AIR) {
            CompoundTag t = box.getTag();
            if (t != null) {
                t.remove(TAG);
                if (t.isEmpty()) box.setTag(null);
            }
            return;
        }
        CompoundTag f = new CompoundTag();
        f.putString(ID, String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
        f.putInt(N, n);
        box.getOrCreateTag().put(TAG, f);
    }

    /** Положить из {@code product} в тару сколько влезет; возвращает, сколько положено (product уменьшается). */
    public static int fill(ItemStack box, ItemStack product) {
        Box b = of(box.getItem());
        if (!accepts(b, product)) return 0;
        int n = count(box);
        if (n > 0 && content(box) != product.getItem()) return 0;  // в таре уже другой продукт (томат FD и F&C)
        int moved = Math.min(product.getCount(), capacity(b, product.getItem()) - n);
        if (moved <= 0) return 0;
        set(box, product.getItem(), n + moved);
        product.shrink(moved);
        return moved;
    }

    /** Достать из тары стак продукта (или пусто). */
    public static ItemStack take(ItemStack box) {
        int n = count(box);
        Item it = content(box);
        if (n <= 0 || it == Items.AIR) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(it);
        int k = Math.min(n, out.getMaxStackSize());
        out.setCount(k);
        set(box, it, n - k);
        return out;
    }
}
