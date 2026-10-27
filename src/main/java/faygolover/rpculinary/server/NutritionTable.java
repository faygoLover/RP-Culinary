package faygolover.rpculinary.server;

import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.data.NutritionRules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Состав любой еды (перенесено из RP Medicine; по образцу Nutritional Balance, но количественно): базовые ингредиенты — из датапака;
 * блюдо — сумма ингредиентов рецепта, которым оно получается, делённая на число получаемых штук (жарка и
 * варка состав сохраняют). Рекурсия по рецептам до {@link #MAX_DEPTH}, циклы отсекаются. Еда без рецепта и
 * без записи — оценка по сытости предмета (мясо — белок и жир, остальное — углеводы). Результаты кэшируются
 * до перезагрузки датапаков.
 */
public final class NutritionTable {
    private NutritionTable() {}

    private static final int MAX_DEPTH = 6;
    /** Во сколько раз калории по рецепту могут отличаться от ванильной сытости блюда. */
    private static final double SANE_RATIO = 2.0;
    private static final Map<Item, Food> CACHE = new HashMap<>();
    /** Вид еды (−1 — не считается). */
    private static final Map<Item, Integer> CATEGORY = new HashMap<>();
    /** Откуда состав: датапак, рецепт, оценка по ванильной сытости (для {@code /rpculinary audit}). */
    public enum Source { RULE, RECIPE, ESTIMATE }

    private static final Map<Item, Source> SOURCE = new HashMap<>();
    /** Сложность блюда: сколько разных ингредиентов в рецепте (базовый продукт — 0). */
    private static final Map<Item, Integer> COMPLEXITY = new HashMap<>();
    /** Группы экзотики (битовая маска {@link Exotic#GROUPS}): свои и унаследованные от ингредиентов. */
    private static final Map<Item, Integer> EXOTIC = new HashMap<>();
    /** Сырое ли (кэш {@link #raw}). */
    private static final Map<Item, Boolean> RAW = new HashMap<>();
    /** Датапак: сырое сверх правил ({@code #rpculinary:raw}) и точно не сырое ({@code #rpculinary:not_raw}). */
    static final net.minecraft.tags.TagKey<Item> RAW_TAG = net.minecraft.tags.ItemTags.create(new ResourceLocation("rpculinary", "raw"));
    static final net.minecraft.tags.TagKey<Item> NOT_RAW_TAG = net.minecraft.tags.ItemTags.create(new ResourceLocation("rpculinary", "not_raw"));
    /** Слова в id, по которым продукт уже готов (копчёный окорок, вяленое мясо). */
    private static final String[] COOKED_WORDS = {"cooked", "smoked", "roast", "fried", "grilled", "baked", "dried", "jerky",
            "salted", "cured", "boiled", "steamed", "stew", "soup", "pickled", "fermented", "burnt", "charred"};
    @Nullable
    private static Map<Item, List<Recipe<?>>> byResult;
    @Nullable
    private static RegistryAccess access;

    public static synchronized void invalidate() {
        CACHE.clear();
        CATEGORY.clear();
        SOURCE.clear();
        COMPLEXITY.clear();
        RAW.clear();
        EXOTIC.clear();
        byResult = null;
    }

    private static synchronized void index(MinecraftServer server) {
        if (byResult != null) return;
        access = server.registryAccess();
        byResult = new HashMap<>();
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            ItemStack out;
            try {
                out = r.getResultItem(access);
            } catch (Exception e) {
                continue;
            }
            if (out == null || out.isEmpty() || r.getIngredients().isEmpty()) continue;
            byResult.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
        }
        // Детерминированно: по id рецепта.
        for (List<Recipe<?>> l : byResult.values()) l.sort((a, b) -> a.getId().compareTo(b.getId()));
    }

    /** Состав порции предмета или null (не еда и не ингредиент). */
    @Nullable
    public static synchronized Food get(MinecraftServer server, Item item) {
        index(server);
        return resolve(item, 0, new HashSet<>());
    }

    @Nullable
    private static Food resolve(Item item, int depth, Set<Item> path) {
        if (CACHE.containsKey(item)) return CACHE.get(item);
        NutritionRules.Entry entry = NutritionRules.entry(item);
        if (entry != null) {
            CACHE.put(item, entry.food());
            CATEGORY.put(item, entry.category());
            SOURCE.put(item, Source.RULE);
            EXOTIC.put(item, Exotic.directMask(item));
            return entry.food();
        }
        if (depth >= MAX_DEPTH || !path.add(item)) return null;
        Food found = null;
        List<Recipe<?>> recipes = byResult != null ? byResult.getOrDefault(item, List.of()) : List.of();
        int foundCat = -1;
        int foundKnown = 0;
        int foundDistinct = 0;
        int foundExotic = 0;
        // Из всех рецептов блюда — тот, где известно больше всего ингредиентов (у Kaleidoscope есть «гибкие»
        // рецепты с минимумом ингредиентов рядом с полными); при равенстве — первый по id.
        for (Recipe<?> r : recipes) {
            Food sum = Food.ZERO;
            int known = 0;
            Set<Item> distinct = new HashSet<>();
            double[] byCat = new double[NutritionState.CATEGORIES.length];
            int exotic = 0;
            for (Ingredient ing : r.getIngredients()) {
                Item src = ingredientItem(ing, depth, path);
                exotic |= ingredientExotic(ing);
                if (src == null) continue;
                Food part = CACHE.get(src);
                if (part == null) continue;
                sum = sum.plus(part);
                known++;
                distinct.add(src);
                int c = CATEGORY.getOrDefault(src, -1);
                if (c >= 0) byCat[c] += Math.max(1, part.kcal());
            }
            if (known <= foundKnown || sum.isEmpty()) continue;
            int count = Math.max(1, r.getResultItem(access).getCount());
            found = sum.scale(1.0 / count);
            foundKnown = known;
            foundDistinct = distinct.size();
            foundExotic = exotic;
            // Вид блюда — вид ингредиента с наибольшими калориями.
            double best = 0;
            foundCat = -1;
            for (int c = 0; c < byCat.length; c++) if (byCat[c] > best) {
                best = byCat[c];
                foundCat = c;
            }
        }
        if (found != null) found = sane(item, found);
        path.remove(item);
        Source source = Source.RECIPE;
        if (found == null) {
            source = Source.ESTIMATE;
            found = estimate(item);
            FoodProperties fp = found != null ? item.getFoodProperties(new ItemStack(item), null) : null;
            foundCat = fp != null && fp.isMeat() ? NutritionState.category("meat") : -1;
        }
        // Промежуточные «нет» не кэшируем: на другом пути цикл может разрешиться.
        if (found != null) {
            CACHE.put(item, found);
            CATEGORY.put(item, foundCat);
            SOURCE.put(item, source);
            COMPLEXITY.put(item, source == Source.RECIPE ? Math.max(1, foundDistinct) : 0);
            EXOTIC.put(item, Exotic.directMask(item) | (source == Source.RECIPE ? foundExotic : 0));
        }
        return found;
    }

    /** Ингредиент рецепта: первый вариант, у которого известен состав. */
    @Nullable
    private static Item ingredientItem(Ingredient ing, int depth, Set<Item> path) {
        for (ItemStack st : ing.getItems()) {
            if (resolve(st.getItem(), depth + 1, path) != null) return st.getItem();
        }
        return null;
    }

    /**
     * Группы экзотики ингредиента: общие для всех его вариантов (тег, где есть и обычное мясо, блюдо экзотикой не
     * делает). Вариант — его группы с наследованными, если уже посчитан, иначе только свои (порошок, крошка).
     */
    private static int ingredientExotic(Ingredient ing) {
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return 0;
        int mask = -1;
        for (ItemStack st : items) {
            Integer known = EXOTIC.get(st.getItem());
            mask &= known != null ? known : Exotic.directMask(st.getItem());
            if (mask == 0) return 0;
        }
        return mask;
    }

    /** Группы экзотики предмета (битовая маска {@link Exotic#GROUPS}): свои и от ингредиентов рецепта. */
    public static synchronized int exoticMask(MinecraftServer server, Item item) {
        if (get(server, item) == null) return Exotic.directMask(item);
        return EXOTIC.getOrDefault(item, 0);
    }

    /** Откуда состав предмета или null (не еда). */
    @Nullable
    public static synchronized Source source(MinecraftServer server, Item item) {
        if (get(server, item) == null) return null;
        return SOURCE.get(item);
    }

    /** Сложность блюда: разных ингредиентов в рецепте; базовый продукт и неизвестное — 0. */
    public static synchronized int complexity(MinecraftServer server, Item item) {
        if (get(server, item) == null) return 0;
        return COMPLEXITY.getOrDefault(item, 0);
    }

    /**
     * Сырое (решения Alex 27.10.2026): съеденное как есть может отравить, в блюда не помечается. Сырое — по датапаку
     * ({@code raw} в правиле питания, теги {@code rpculinary:raw}/{@code not_raw}), по тегам {@code forge:raw_*}
     * (кроме руды), по «raw» в id; правило без {@code raw} — мясо и рыба. Готовое по названию (копчёное…) — не сырое.
     * Тухлятину и паучий глаз ведёт ваниль (и группа «Монстры»).
     */
    public static synchronized boolean raw(MinecraftServer server, Item item) {
        Boolean r = RAW.get(item);
        if (r == null) {
            r = computeRaw(server, item);
            RAW.put(item, r);
        }
        return r;
    }

    private static boolean computeRaw(MinecraftServer server, Item item) {
        if (item == net.minecraft.world.item.Items.ROTTEN_FLESH || item == net.minecraft.world.item.Items.SPIDER_EYE) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        var holder = item.builtInRegistryHolder();
        if (holder.is(NOT_RAW_TAG)) return false;
        if (holder.is(RAW_TAG)) return true;
        NutritionRules.Entry e = NutritionRules.entry(item);
        if (e != null && e.raw() != null) return e.raw();
        String path = id.getPath();
        if (cookedName(path)) return false;
        int cat = category(server, item);
        boolean meaty = cat == NutritionState.category("meat") || cat == NutritionState.category("fish");
        if (e != null) return meaty;
        FoodProperties fp = item.getFoodProperties(new ItemStack(item), null);
        if (fp == null) return false;
        boolean named = path.startsWith("raw") || path.contains("_raw");
        boolean tagged = holder.tags().anyMatch(t -> t.location().getNamespace().equals("forge")
                && t.location().getPath().startsWith("raw_") && !t.location().getPath().startsWith("raw_materials"));
        if (!named && !tagged) return false;
        return meaty || fp.isMeat() || cat < 0;
    }

    /** Готовое по названию: «cooked», «smoked», «fried»… */
    static boolean cookedName(String path) {
        for (String w : COOKED_WORDS) if (path.contains(w)) return true;
        return false;
    }

    /** Вид еды предмета (−1 — не считается). */
    public static synchronized int category(MinecraftServer server, Item item) {
        if (get(server, item) == null) return -1;
        return CATEGORY.getOrDefault(item, -1);
    }

    /**
     * Сверка с замыслом автора мода: если калории по рецепту отличаются от ванильной сытости блюда больше чем вдвое
     * (рецепт на несколько порций, котёл на компанию), калории подгоняются к границе, а доли БЖУ остаются.
     */
    private static Food sane(Item item, Food f) {
        FoodProperties fp = item.getFoodProperties(new ItemStack(item), null);
        if (fp == null || fp.getNutrition() <= 0 || f.kcal() <= 0) return f;
        double est = fp.getNutrition() * faygolover.rpculinary.core.Settings.get().kcalPerFoodPoint;
        double ratio = f.kcal() / est;
        if (ratio > SANE_RATIO) return f.withKcalScaled(est * SANE_RATIO);
        if (ratio < 1 / SANE_RATIO) return f.withKcalScaled(est / SANE_RATIO);
        return f;
    }

    /** Еда без рецепта и записи — по сытости: {@code kcalPerFoodPoint} (~90) ккал за единицу ванильной сытости. */
    @Nullable
    private static Food estimate(Item item) {
        FoodProperties fp = item.getFoodProperties(new ItemStack(item), null);
        if (fp == null) return null;
        double kcal = fp.getNutrition() * faygolover.rpculinary.core.Settings.get().kcalPerFoodPoint;
        if (fp.isMeat()) return new Food(kcal, kcal * 0.45 / 4, kcal * 0.55 / 9, 0, 1, 0);
        return new Food(kcal, kcal * 0.08 / 4, kcal * 0.12 / 9, kcal * 0.8 / 4, 4, 0);
    }

    /** Таблица для клиента (подсказки): вся еда и известные ингредиенты. */
    public static Map<ResourceLocation, Food> snapshot(MinecraftServer server) {
        Map<ResourceLocation, Food> out = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            boolean food = item.isEdible() || NutritionRules.base(item) != null;
            if (!food) continue;
            Food f = get(server, item);
            if (f != null && !f.isEmpty()) out.put(ForgeRegistries.ITEMS.getKey(item), f);
        }
        return out;
    }

    /** Виды еды для клиента (только известные). */
    public static Map<ResourceLocation, Integer> categories(MinecraftServer server) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (!item.isEdible() && NutritionRules.base(item) == null) continue;
            int c = category(server, item);
            if (c >= 0) out.put(ForgeRegistries.ITEMS.getKey(item), c);
        }
        return out;
    }
}
