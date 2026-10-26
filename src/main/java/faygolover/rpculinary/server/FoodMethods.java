package faygolover.rpculinary.server;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.NutritionState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Способ «как приготовлено» ({@link NutritionState#METHODS}): сначала тег {@code rpculinary:method/<способ>} (им
 * поправлять, где угадано неверно), потом название предмета. Сырые фрукты и овощи без рецепта — «свежее».
 */
public final class FoodMethods {
    private FoodMethods() {}

    private static final TagKey<Item>[] TAGS = tags();

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] tags() {
        TagKey<Item>[] t = new TagKey[NutritionState.METHODS.length];
        for (int i = 0; i < t.length; i++) t[i] = ItemTags.create(new ResourceLocation(RpCulinary.MODID, "method/" + NutritionState.METHODS[i]));
        return t;
    }

    /** Слова в id предмета по способам — порядок важен: первым найденный способ. */
    private static final List<Rule> RULES = List.of(
            new Rule("dessert", "cake", "ice_cream", "chocolate", "candy", "pudding", "jelly", "mousse", "cookie", "donut", "doughnut",
                    "waffle", "tart", "cupcake", "popsicle", "lollipop", "caramel", "cheesecake", "marshmallow", "brownie", "sweet_roll",
                    "custard", "eclair", "macaron", "gingerbread", "candied"),
            new Rule("canned", "canned", "_can", "can_", "ration", "mre", "tin_", "_tin", "tushonka", "spam", "beans"),
            new Rule("sandwich", "sandwich", "burger", "wrap", "shawarma", "hotdog", "hot_dog", "taco", "burrito", "toast_with",
                    "kebab_wrap", "sub_", "_roll"),
            new Rule("soup", "soup", "stew", "broth", "borscht", "chowder", "ramen", "gumbo", "chili", "goulash", "solyanka", "okroshka",
                    "shchi", "bisque", "curry"),
            new Rule("porridge", "porridge", "congee", "rice", "pasta", "noodle", "spaghetti", "risotto", "mashed", "fries", "puree",
                    "pilaf", "plov", "buckwheat", "oatmeal", "macaroni", "lasagna", "dumpling", "pelmeni", "baked_potato"),
            new Rule("baked", "bread", "bun", "baguette", "pie", "pastry", "pizza", "croissant", "muffin", "biscuit", "cracker", "toast",
                    "pirozhok", "pretzel", "bagel", "loaf", "flatbread", "pita"),
            new Rule("fried", "cooked_", "fried", "roast", "grilled", "steak", "cutlet", "chop", "kebab", "shashlik", "bacon", "sausage",
                    "nugget", "schnitzel", "meatball", "barbecue", "bbq", "smoked", "omelette", "omelet", "jerky", "fillet", "wings",
                    "drumstick", "patty"),
            new Rule("fresh", "salad", "raw_", "fresh_", "slice"));

    private record Rule(String method, String... words) {
        boolean matches(String path) {
            for (String w : words) if (path.contains(w)) return true;
            return false;
        }
    }

    /** Индекс способа или −1 — не определить. */
    public static int of(MinecraftServer server, ItemStack stack) {
        for (int i = 0; i < TAGS.length; i++) if (stack.is(TAGS[i])) return i;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return -1;
        String path = id.getPath();
        for (Rule r : RULES) if (r.matches(path)) return NutritionState.method(r.method);
        // Фрукты и овощи как есть, без рецепта, — свежее.
        int cat = NutritionTable.category(server, stack.getItem());
        if ((cat == NutritionState.category("fruit") || cat == NutritionState.category("vegetables"))
                && NutritionTable.source(server, stack.getItem()) == NutritionTable.Source.RULE) return NutritionState.method("fresh");
        return -1;
    }
}
