package faygolover.rpculinary.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Датапак {@code rpculinary/drinks}: сколько воды (проценты полоски) даёт напиток. Для зелий можно указать, какое
 * зелье ({@code "potion": "minecraft:water"} — бутылка воды, а не любое зелье).
 * <pre>{"items": ["minecraft:potion"], "potion": "minecraft:water", "amount": 35}</pre>
 */
public final class DrinkRules {
    private DrinkRules() {}

    record Drink(double amount, String potion) {}

    private static volatile Map<ResourceLocation, Drink> drinks = Map.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(new Gson(), "rpculinary/drinks") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Drink> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Drink d = new Drink(GsonHelper.getAsDouble(o, "amount"), GsonHelper.getAsString(o, "potion", ""));
                    for (JsonElement it : GsonHelper.getAsJsonArray(o, "items")) {
                        ResourceLocation rl = ResourceLocation.tryParse(it.getAsString());
                        if (rl != null) out.put(rl, d);
                    }
                } catch (Exception ex) {
                    RpCulinary.LOGGER.error("RP Culinary: ошибка в drinks {}: {}", e.getKey(), ex.getMessage());
                }
            }
            drinks = Map.copyOf(out);
        }
    };

    /** Сколько воды даёт выпитое (0 — не напиток). */
    public static double water(ItemStack used) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(used.getItem());
        Drink d = id != null ? drinks.get(id) : null;
        if (d == null) return 0;
        if (!d.potion().isEmpty()) {
            ResourceLocation p = ForgeRegistries.POTIONS.getKey(PotionUtils.getPotion(used));
            if (p == null || !p.toString().equals(d.potion())) return 0;
        }
        return d.amount();
    }
}
