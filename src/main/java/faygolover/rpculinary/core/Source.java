package faygolover.rpculinary.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Состав мяса (решения Alex 29.10): «из кого» — по всей цепочке: туша → кусок → фарш → котлета → бургер. NBT
 * {@code rpculinary:meat} — вид → доли ({@code {cow:2, pig:1}}), всегда сокращённые на общий делитель; больше одного вида —
 * «домашнее». {@code rpculinary:look} — вид, в цвет которого красится форма (у домашнего — первый фарш смеси). Старая пометка
 * {@code rpculinary:source} («cow» или «cow,pig») читается как равные доли. Продукт без пометки — вид по предмету ({@link #implied}).
 */
public final class Source {
    private Source() {}

    public static final String MEAT = "rpculinary:meat", LOOK = "rpculinary:look", LEGACY = Weights.TAG_SOURCE;
    public static final int MAX = 6;

    /** Вид по самому предмету, если пометки нет. */
    private static final Map<String, String> IMPLIED = Map.ofEntries(
            Map.entry("minecraft:beef", "cow"), Map.entry("minecraft:cooked_beef", "cow"), Map.entry("minecraft:porkchop", "pig"),
            Map.entry("minecraft:cooked_porkchop", "pig"), Map.entry("minecraft:mutton", "sheep"), Map.entry("minecraft:cooked_mutton", "sheep"),
            Map.entry("minecraft:chicken", "chicken"), Map.entry("minecraft:cooked_chicken", "chicken"), Map.entry("minecraft:rabbit", "rabbit"),
            Map.entry("minecraft:cooked_rabbit", "rabbit"), Map.entry("farm_and_charm:minced_beef", "cow"), Map.entry("farmersdelight:minced_beef", "cow"),
            Map.entry("farmersdelight:beef_patty", "cow"), Map.entry("butchery:raw_beef_mince", "sheep"), Map.entry("butchery:raw_lamb_mince", "pig"),
            Map.entry("farmersdelight:bacon", "pig"), Map.entry("farm_and_charm:bacon", "pig"), Map.entry("farmersdelight:chicken_cuts", "chicken"),
            Map.entry("farm_and_charm:chicken_parts", "chicken"), Map.entry("farmersdelight:mutton_chops", "sheep"),
            Map.entry("butchery:raw_sausage", "pig"), Map.entry("butchery:cooked_sausage", "pig"), Map.entry("butchery:raw_blood_sausage", "pig"),
            Map.entry("butchery:cooked_blood_sausage", "pig"), Map.entry("minecraft:cod", "fish"), Map.entry("minecraft:salmon", "fish"),
            Map.entry("alexsmobs:kangaroo_meat", "kangaroo"), Map.entry("alexsmobs:moose_ribs", "moose"), Map.entry("alexscaves:dinosaur_chop", "dinosaur"));

    public static String implied(ItemStack st) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id == null ? null : IMPLIED.get(id.toString());
    }

    /** Состав: вид → доли (порядок — как добавлялись). Без пометки — подразумеваемый вид или пусто. */
    public static Map<String, Integer> parts(ItemStack st) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag t = st.getTag();
        if (t != null && t.contains(MEAT)) {
            CompoundTag m = t.getCompound(MEAT);
            for (String k : m.getAllKeys()) if (m.getInt(k) > 0) out.put(k, m.getInt(k));
        } else if (t != null && !t.getString(LEGACY).isEmpty()) {
            for (String s : t.getString(LEGACY).split(",")) if (!s.isBlank()) out.put(s.trim(), 1);
        } else {
            String imp = implied(st);
            if (imp != null) out.put(imp, 1);
        }
        return out;
    }

    /** Виды продукта (без долей). */
    public static List<String> list(ItemStack st) {
        return new ArrayList<>(parts(st).keySet());
    }

    /** Есть ли явная пометка. */
    public static boolean marked(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && (t.contains(MEAT) || !t.getString(LEGACY).isEmpty());
    }

    /** Больше одного вида — «домашнее». */
    public static boolean home(ItemStack st) {
        return marked(st) && parts(st).size() > 1;
    }

    /** Вид для подкраски: «облик», иначе первый вид состава; null — нет пометки. */
    public static String look(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t != null && !t.getString(LOOK).isEmpty()) return t.getString(LOOK);
        if (!marked(st)) return null;
        var p = parts(st);
        return p.isEmpty() ? null : p.keySet().iterator().next();
    }

    /** Записать состав (сократить на общий делитель, не больше {@link #MAX} видов) и облик. */
    public static void setParts(ItemStack st, Map<String, Integer> parts, String look) {
        Map<String, Integer> p = new LinkedHashMap<>();
        parts.entrySet().stream().filter(e -> e.getValue() > 0).limit(MAX).forEach(e -> p.put(e.getKey(), e.getValue()));
        if (p.isEmpty()) return;
        int g = 0;
        for (int v : p.values()) g = gcd(g, v);
        CompoundTag m = new CompoundTag();
        for (var e : p.entrySet()) m.putInt(e.getKey(), e.getValue() / Math.max(1, g));
        CompoundTag t = st.getOrCreateTag();
        t.remove(LEGACY);
        t.put(MEAT, m);
        if (look != null && !look.isEmpty()) t.putString(LOOK, look);
    }

    /** Добавить виды (по доле каждому) к имеющемуся составу. */
    public static void add(ItemStack st, Iterable<String> species) {
        Map<String, Integer> p = marked(st) ? parts(st) : new LinkedHashMap<>();
        for (String s : species) if (s != null && !s.isBlank()) p.merge(s, 1, Integer::sum);
        setParts(st, p, look(st));
    }

    /** Один вид целиком. */
    public static void set(ItemStack st, String species) {
        if (species == null || species.isBlank()) return;
        setParts(st, Map.of(species, 1), species);
    }

    /** Переписать состав и облик с другого продукта (кусок → порция, фарш → котлета). */
    public static void copy(ItemStack from, ItemStack to) {
        if (!marked(from)) {
            String imp = implied(from);
            if (imp != null && !imp.equals(implied(to))) set(to, imp);
            return;
        }
        setParts(to, parts(from), look(from));
    }

    /**
     * Смесь: каждый продукт — равная доля (одна штука = одна порция), его виды — по своим долям.
     * (свинина+говядина) + (свинина+курица) = 2 свинина + говядина + курица. Облик — первого продукта.
     */
    public static Map<String, Integer> mix(List<ItemStack> units) {
        int lcm = 1;
        List<Map<String, Integer>> all = new ArrayList<>();
        for (ItemStack u : units) {
            var p = parts(u);
            if (p.isEmpty()) continue;
            all.add(p);
            int tot = p.values().stream().mapToInt(Integer::intValue).sum();
            lcm = lcm / gcd(lcm, tot) * tot;
            if (lcm > 1_000_000) lcm = 1_000_000;
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var p : all) {
            int tot = p.values().stream().mapToInt(Integer::intValue).sum();
            for (var e : p.entrySet()) out.merge(e.getKey(), e.getValue() * (lcm / Math.max(1, tot)), Integer::sum);
        }
        return out;
    }

    static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    /** «говядина 2, свинина 1» — для подсказки (ключи переводов {@code rpculinary.source.<вид>}). */
    public static List<Map.Entry<String, Integer>> entries(ItemStack st) {
        return new ArrayList<>(parts(st).entrySet());
    }
}
