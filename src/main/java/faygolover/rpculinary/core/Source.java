package faygolover.rpculinary.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Происхождение продукта (решение Alex 29.10): «из кого» мясо — по всей цепочке: туша → кусок → фарш → котлета → бургер.
 * NBT {@code rpculinary:source} — вид или несколько через запятую («kangaroo», «cow,pig»), не больше {@link #MAX}.
 * От вида зависят группы блюда (эффекты на едока), подкраска и подсказка. Продукт без пометки — вид по самому предмету
 * ({@link #implied}): ванильная говядина — корова и т. п.
 */
public final class Source {
    private Source() {}

    public static final String TAG = Weights.TAG_SOURCE;
    public static final int MAX = 3;

    /** Вид по самому предмету, если пометки нет. */
    private static final Map<String, String> IMPLIED = Map.ofEntries(
            Map.entry("minecraft:beef", "cow"), Map.entry("minecraft:cooked_beef", "cow"), Map.entry("minecraft:porkchop", "pig"),
            Map.entry("minecraft:cooked_porkchop", "pig"), Map.entry("minecraft:mutton", "sheep"), Map.entry("minecraft:cooked_mutton", "sheep"),
            Map.entry("minecraft:chicken", "chicken"), Map.entry("minecraft:cooked_chicken", "chicken"), Map.entry("minecraft:rabbit", "rabbit"),
            Map.entry("minecraft:cooked_rabbit", "rabbit"), Map.entry("farm_and_charm:minced_beef", "cow"), Map.entry("farmersdelight:minced_beef", "cow"),
            Map.entry("farmersdelight:bacon", "pig"), Map.entry("farm_and_charm:bacon", "pig"), Map.entry("farmersdelight:chicken_cuts", "chicken"),
            Map.entry("farm_and_charm:chicken_parts", "chicken"), Map.entry("farmersdelight:mutton_chops", "sheep"),
            Map.entry("alexsmobs:kangaroo_meat", "kangaroo"), Map.entry("alexsmobs:moose_ribs", "moose"), Map.entry("alexscaves:dinosaur_chop", "dinosaur"));

    /** Виды продукта: из пометки, иначе подразумеваемый по предмету; пусто — не мясо или неизвестно. */
    public static List<String> list(ItemStack st) {
        CompoundTag t = st.getTag();
        String raw = t != null ? t.getString(TAG) : "";
        if (raw.isEmpty()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
            String imp = id == null ? null : IMPLIED.get(id.toString());
            return imp == null ? List.of() : List.of(imp);
        }
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    /** Есть ли явная пометка. */
    public static boolean marked(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && !t.getString(TAG).isEmpty();
    }

    /** Поставить пометку (объединение с имеющейся, первые {@link #MAX}). */
    public static void add(ItemStack st, Iterable<String> species) {
        Set<String> all = new LinkedHashSet<>(marked(st) ? list(st) : List.of());
        for (String s : species) if (s != null && !s.isBlank()) all.add(s);
        if (all.isEmpty()) return;
        List<String> lim = new ArrayList<>(all).subList(0, Math.min(MAX, all.size()));
        st.getOrCreateTag().putString(TAG, String.join(",", lim));
    }

    public static void set(ItemStack st, String species) {
        if (species == null || species.isBlank()) return;
        st.getOrCreateTag().putString(TAG, species);
    }
}
