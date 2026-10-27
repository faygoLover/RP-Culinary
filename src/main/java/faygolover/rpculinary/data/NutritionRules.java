package faygolover.rpculinary.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.server.NutritionTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Датапак {@code rpculinary/nutrition}: состав базовых ингредиентов — тех, что не крафтятся, а идут в рецепты
 * (зерно, овощи, сырое мясо, молоко). Состав готовых блюд считает {@link NutritionTable} по рецептам.
 * <pre>{"items": ["minecraft:beef", "#forge:raw_beef"], "kcal": 500, "protein": 52, "fat": 32, "carbs": 0, "vitamins": 2,
 *  "water": 0, "category": "meat"}</pre>
 * {@code kcal} можно не задавать — посчитается по БЖУ. {@code water} — сколько процентов воды даёт. Порция — один предмет.
 * {@code raw} — сырое ли (съеденное как есть травит); не задано — сырое мясо и рыба, кроме готовых по названию
 * (копчёное, жареное…).
 */
public final class NutritionRules {
    private NutritionRules() {}

    /** {@code category} — вид еды для «приелось» (−1 — не считается: напитки, исключения). */
    public record Entry(Matcher<Item> items, Food food, int priority, int category, @org.jetbrains.annotations.Nullable Boolean raw) {}

    private static volatile List<Entry> entries = List.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(new Gson(), "rpculinary/nutrition") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            List<Entry> out = new ArrayList<>();
            for (var f : files.entrySet()) {
                try {
                    JsonObject o = f.getValue().getAsJsonObject();
                    Food food = new Food(GsonHelper.getAsDouble(o, "kcal", 0), GsonHelper.getAsDouble(o, "protein", 0),
                            GsonHelper.getAsDouble(o, "fat", 0), GsonHelper.getAsDouble(o, "carbs", 0),
                            GsonHelper.getAsDouble(o, "vitamins", 0), GsonHelper.getAsDouble(o, "water", 0)).withKcal();
                    int cat = GsonHelper.getAsBoolean(o, "exempt", false) ? -1 : NutritionState.category(GsonHelper.getAsString(o, "category", ""));
                    Boolean raw = o.has("raw") ? GsonHelper.getAsBoolean(o, "raw") : null;
                    out.add(new Entry(Matcher.parse(o.get("items"), Registries.ITEM), food, GsonHelper.getAsInt(o, "priority", 0), cat, raw));
                } catch (Exception ex) {
                    RpCulinary.LOGGER.error("RP Culinary: питание {}: {}", f.getKey(), ex.getMessage());
                }
            }
            out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            entries = List.copyOf(out);
            NutritionTable.invalidate();
            RpCulinary.LOGGER.info("RP Culinary: базовых ингредиентов питания: {}", out.size());
        }
    };

    /** Состав базового ингредиента или null. */
    public static Food base(Item item) {
        Entry e = entry(item);
        return e != null ? e.food() : null;
    }

    public static Entry entry(Item item) {
        var holder = item.builtInRegistryHolder();
        for (Entry e : entries) if (e.items().matches(holder)) return e;
        return null;
    }
}
