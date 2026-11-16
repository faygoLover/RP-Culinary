package faygolover.rpculinary.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.List;

/**
 * КБЖУ по виду мяса (решение Alex 29.10): у фарша, обрезков, котлеты, колбасок своё питание — от 100 г сырого мяса вида,
 * а не от предмета-основы. Таблица видов — по справочным данным (сырое мясо, на 100 г: ккал считаются из белка и жира 4/9);
 * экзотика и монстры — по ближайшему реальному мясу. Домашнее — по долям. Блюдо с мясом — рецепт, где мясная часть
 * пересчитана по составу ({@link #dish}).
 */
public final class MeatFood {
    private MeatFood() {}

    /** Вид → {белок, жир, витамины} на 100 г сырого. */
    private static final Map<String, double[]> SPECIES = Map.ofEntries(
            Map.entry("cow", new double[]{18.6, 12.4, 0.8}), Map.entry("pig", new double[]{16.0, 21.6, 0.8}),
            Map.entry("sheep", new double[]{16.3, 15.3, 0.8}), Map.entry("goat", new double[]{20.6, 2.3, 0.8}),
            Map.entry("chicken", new double[]{18.2, 12.0, 0.6}), Map.entry("rabbit", new double[]{21.0, 8.0, 0.7}),
            Map.entry("horse", new double[]{20.2, 7.0, 0.9}), Map.entry("donkey", new double[]{20.0, 4.0, 0.9}),
            Map.entry("mule", new double[]{20.0, 4.5, 0.9}), Map.entry("llama", new double[]{22.0, 3.5, 0.9}),
            Map.entry("camel", new double[]{20.0, 5.0, 0.9}), Map.entry("kangaroo", new double[]{22.0, 1.3, 1.0}),
            Map.entry("moose", new double[]{21.0, 1.5, 1.0}), Map.entry("polar_bear", new double[]{20.1, 8.3, 1.4}),
            Map.entry("panda", new double[]{20.0, 8.0, 1.0}), Map.entry("wolf", new double[]{20.0, 7.0, 0.8}),
            Map.entry("fox", new double[]{20.0, 7.0, 0.8}), Map.entry("cat", new double[]{19.0, 7.0, 0.8}),
            Map.entry("ocelot", new double[]{19.0, 7.0, 0.8}), Map.entry("turtle", new double[]{19.8, 0.5, 0.7}),
            Map.entry("dolphin", new double[]{24.0, 2.0, 1.2}), Map.entry("frog", new double[]{16.4, 0.3, 0.5}),
            Map.entry("bat", new double[]{19.0, 3.0, 0.7}), Map.entry("axolotl", new double[]{16.0, 1.0, 0.5}),
            Map.entry("sniffer", new double[]{21.0, 5.0, 0.9}), Map.entry("hoglin", new double[]{16.0, 19.0, 0.8}),
            Map.entry("strider", new double[]{18.0, 5.0, 0.6}), Map.entry("dinosaur", new double[]{22.0, 6.0, 0.9}),
            Map.entry("fish", new double[]{19.0, 3.0, 0.9}), Map.entry("squid", new double[]{15.6, 1.4, 0.6}),
            Map.entry("bee", new double[]{18.0, 5.0, 1.0}), Map.entry("spider", new double[]{18.0, 3.0, 0.6}),
            Map.entry("cave_spider", new double[]{18.0, 3.0, 0.6}), Map.entry("silverfish", new double[]{19.0, 5.0, 0.6}),
            Map.entry("endermite", new double[]{17.0, 3.0, 0.5}), Map.entry("enderman", new double[]{19.0, 3.0, 0.5}),
            Map.entry("shulker", new double[]{18.0, 1.0, 0.5}), Map.entry("guardian", new double[]{20.0, 3.0, 0.8}),
            Map.entry("elder_guardian", new double[]{20.0, 3.0, 0.8}), Map.entry("warden", new double[]{20.0, 5.0, 0.5}),
            Map.entry("creeper", new double[]{16.0, 6.0, 0.5}), Map.entry("human", new double[]{18.0, 14.0, 0.8}),
            Map.entry("villager", new double[]{18.0, 14.0, 0.8}), Map.entry("pillager", new double[]{18.0, 14.0, 0.8}),
            Map.entry("vindicator", new double[]{18.0, 14.0, 0.8}), Map.entry("evoker", new double[]{18.0, 14.0, 0.8}),
            Map.entry("witch", new double[]{18.0, 14.0, 0.8}), Map.entry("piglin", new double[]{16.0, 18.0, 0.8}),
            Map.entry("zombie", new double[]{15.0, 8.0, 0.2}), Map.entry("phantom", new double[]{15.0, 2.0, 0.4}),
            Map.entry("slime", new double[]{5.0, 0.0, 0.1}), Map.entry("ender_dragon", new double[]{25.0, 5.0, 1.0}),
            Map.entry("skeleton", new double[]{5.0, 1.0, 0.1}));
    /** Субпродукты (ливерный фарш): печень. */
    private static final double[] OFFAL = {20.0, 4.0, 3.0};
    private static final double[] UNKNOWN = {18.0, 10.0, 0.7};

    /** Форма мяса: вес сырого мяса в штуке (г), множители белка и жира, вид-основа спрайта (для подкраски). */
    public record Form(int rawGrams, double protein, double fat, String nativeSpecies, String gender) {}

    /** Формы, у которых питание и цвет — от вида (подкраска, «домашний»). */
    public static final Map<String, Form> FORMS = Map.ofEntries(
            Map.entry("farm_and_charm:minced_beef", new Form(250, 1, 1, "cow", "m")),
            Map.entry("butchery:raw_beef_mince", new Form(250, 1, 1, "sheep", "m")),
            Map.entry("butchery:raw_lamb_mince", new Form(250, 1, 1, "pig", "m")),
            Map.entry("butchery:meat_scraps", new Form(250, 0.6, 1.8, "pig", "pl")),
            Map.entry("farmersdelight:minced_beef", new Form(250, 1, 1, "cow", "f")),
            Map.entry("farmersdelight:beef_patty", new Form(250, 1, 1, "cow", "f")),
            Map.entry("butchery:raw_sausage", new Form(1000, 0.9, 1.1, "pig", "pl")),
            Map.entry("butchery:cooked_sausage", new Form(1000, 0.9, 1.1, "pig", "pl")),
            Map.entry("butchery:raw_blood_sausage", new Form(1000, 0.8, 1.0, "pig", "f")),
            Map.entry("butchery:cooked_blood_sausage", new Form(1000, 0.8, 1.0, "pig", "f")),
            Map.entry("brewery:sausage", new Form(250, 0.9, 1.1, "pig", "f")));
    /** Фарш из обрезков жирнее, мяса в нём меньше (2 обрезка → 1 фарш). */
    public static final String FATTY = "rpculinary:fatty", OFFAL_FORM = "rpculinary:offal";

    /** Ливерное и жирное переходят на следующую форму (фарш → колбаски → жареные), если такими были все исходные. */
    public static void carryFlags(List<ItemStack> from, ItemStack to) {
        if (from.isEmpty() || form(to) == null) return;
        for (String flag : new String[]{FATTY, OFFAL_FORM})
            if (from.stream().allMatch(s -> s.getTag() != null && s.getTag().getBoolean(flag))) to.getOrCreateTag().putBoolean(flag, true);
    }

    public static Form form(ItemStack st) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id == null ? null : FORMS.get(id.toString());
    }

    static double[] profile(String species) {
        return SPECIES.getOrDefault(species, UNKNOWN);
    }

    /** Питание {@code grams} сырого мяса по составу (доли) — белок, жир, витамины; ккал = 4·Б + 9·Ж. */
    public static Food meat(Map<String, Integer> parts, double grams, boolean offal) {
        if (parts.isEmpty()) return null;
        double tot = parts.values().stream().mapToInt(Integer::intValue).sum();
        double p = 0, f = 0, v = 0;
        for (var e : parts.entrySet()) {
            double[] pr = offal ? OFFAL : profile(e.getKey());
            double share = e.getValue() / tot;
            p += pr[0] * share;
            f += pr[1] * share;
            v += pr[2] * share;
        }
        double k = grams / 100.0;
        return new Food(p * k * 4 + f * k * 9, p * k, f * k, 0, v * k, 0);
    }

    /**
     * Питание продукта с учётом состава: формы (фарш, обрезки, котлета, колбаски) — от вида целиком; прочее (блюдо) —
     * рецепт, где мясная часть {@code meatPart} пересчитана по составу. Без пометки — как есть.
     */
    public static Food adjust(ItemStack st, Food base, Food meatPart) {
        if (base == null || !Source.marked(st)) return base;
        var parts = Source.parts(st);
        Form fm = form(st);
        boolean offal = st.getTag() != null && st.getTag().getBoolean(OFFAL_FORM);
        if (fm != null) {
            int g = Weights.nbtGrams(st);
            double grams = g > 0 ? g : fm.rawGrams();
            boolean fatty = st.getTag() != null && st.getTag().getBoolean(FATTY);
            Food m = meat(parts, grams, offal);
            if (m == null) return base;
            double pm = fm.protein() * (fatty ? 0.7 : 1), fmul = fm.fat() * (fatty ? 1.5 : 1);
            return new Food(m.protein() * pm * 4 + m.fat() * fmul * 9 + base.carbs() * 4, m.protein() * pm, m.fat() * fmul,
                    base.carbs(), m.vitamins(), base.water());
        }
        // Крупный кусок с весом в NBT: мясная часть — на весь вес, как и base.
        return dish(base, meatPart == null ? null : meatPart.scale(Weights.nutritionFactor(st)), parts);
    }

    /** Блюдо: мясная часть рецепта (по белку — сколько граммов мяса) заменяется мясом состава. */
    public static Food dish(Food base, Food meatPart, Map<String, Integer> parts) {
        if (meatPart == null || meatPart.protein() <= 0 || parts.isEmpty()) return base;
        double grams = meatPart.protein() / UNKNOWN[0] * 100;
        Food m = meat(parts, grams, false);
        if (m == null) return base;
        double p = Math.max(0, base.protein() - meatPart.protein() + m.protein());
        double f = Math.max(0, base.fat() - meatPart.fat() + m.fat());
        double v = Math.max(0, base.vitamins() - meatPart.vitamins() + m.vitamins());
        return new Food(p * 4 + f * 9 + base.carbs() * 4, p, f, base.carbs(), v, base.water());
    }
}
