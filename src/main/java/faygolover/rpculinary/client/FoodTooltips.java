package faygolover.rpculinary.client;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.NutritionState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Подсказка у еды по навыку «Кулинария» (дизайн, §6.1): 0–1 — «съедобно», 2–3 — вид еды, 4–5 — словами, 6–8 —
 * округлённо, 9–10 — точно. Без RP Perks навык не ведётся — всё видно, как на 10.
 */
public final class FoodTooltips {
    private FoodTooltips() {}

    static boolean dietitian() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && RpCoreAPI.hasTrait(mc.player, RpIds.DIETITIAN);
    }

    static int level() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        if (mc.player.isCreative() || dietitian()) return 10;
        var lvl = RpCoreAPI.skill(mc.player, RpIds.COOKING);
        return lvl.isPresent() ? lvl.getAsInt() : 10;
    }

    public static void onTooltip(ItemTooltipEvent e) {
        if (e.getEntity() == null) return;
        weightLine(e);
        if (!ClientNutrition.known) return;
        drinkLine(e);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
        Food f = id != null ? ClientNutrition.FOODS.get(id) : null;
        if (f != null) f = f.scale(faygolover.rpculinary.core.Weights.nutritionFactor(e.getItemStack()));
        if (f == null || f.kcal() <= 0 && f.water() <= 0) return;
        List<Component> t = e.getToolTip();
        // Еда по частям: надкусанное — сколько осталось; большое целое — на сколько укусов (по шагу из таблицы).
        var tag = e.getItemStack().getTag();
        if (tag != null && tag.getInt(faygolover.rpculinary.server.CulinaryService.BITES_N) > 1) {
            t.add(tr("bites_left", tag.getInt(faygolover.rpculinary.server.CulinaryService.BITES),
                    tag.getInt(faygolover.rpculinary.server.CulinaryService.BITES_N)).withStyle(ChatFormatting.YELLOW));
        } else {
            double step = faygolover.rpculinary.core.Settings.get().portionStepKcal;
            if (step > 0 && f.kcal() >= 2 * step && e.getItemStack().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT)
                t.add(tr("bites", (int) Math.floor(f.kcal() / step)).withStyle(ChatFormatting.GRAY));
        }
        int q = faygolover.rpculinary.server.CookingService.quality(e.getItemStack());
        if (q >= 0 && q < faygolover.rpculinary.core.Cooking.QUALITY.length) {
            ChatFormatting c = switch (q) {
                case faygolover.rpculinary.core.Cooking.BURNT, faygolover.rpculinary.core.Cooking.OVERCOOKED -> ChatFormatting.DARK_GRAY;
                case faygolover.rpculinary.core.Cooking.CHARCOAL -> ChatFormatting.DARK_RED;
                case faygolover.rpculinary.core.Cooking.UNDERCOOKED -> ChatFormatting.RED;
                case faygolover.rpculinary.core.Cooking.GOOD -> ChatFormatting.GREEN;
                case faygolover.rpculinary.core.Cooking.EXCELLENT -> ChatFormatting.AQUA;
                case faygolover.rpculinary.core.Cooking.MASTERPIECE -> ChatFormatting.GOLD;
                default -> ChatFormatting.WHITE;
            };
            t.add(tr("quality", Component.translatable("rpculinary.quality." + faygolover.rpculinary.core.Cooking.QUALITY[q])).withStyle(c));
        }
        int lvl = level();
        int cat = ClientNutrition.CATEGORIES.getOrDefault(id, -1);
        if (lvl < 2) {
            t.add(tr("edible").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (cat >= 0) t.add(tr("category", Component.translatable("rpculinary.food_category." + NutritionState.CATEGORIES[cat])).withStyle(ChatFormatting.GRAY));
        if (lvl < 4) return;
        if (lvl < 6) {
            words(t, f);
            return;
        }
        if (lvl < 9) {
            long kcal = Math.round(f.kcal() / 10.0) * 10;
            t.add(tr("rounded", kcal, amount(f.protein() * 4, f.kcal()), amount(f.fat() * 9, f.kcal()), amount(f.carbs() * 4, f.kcal()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            t.add(tr("exact", Math.round(f.kcal()), Math.round(f.protein()), Math.round(f.fat()), Math.round(f.carbs()), Math.round(f.vitamins()))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (f.water() > 0) t.add(tr("water", Math.round(f.water())).withStyle(ChatFormatting.AQUA));
        if (dietitian()) advice(t, f);
    }

    /** Напиток: сколько воды (голубым), у пьющихся глотками — сколько осталось. */
    /** Вес куска: «Корова · 6 кг» у крупных (вес в NBT), «250 г» у остальных из таблицы весов. */
    static void weightLine(ItemTooltipEvent e) {
        var st = e.getItemStack();
        int g = faygolover.rpculinary.core.Weights.grams(st);
        if (g <= 0) return;
        String src = faygolover.rpculinary.core.Weights.source(st);
        Component w = Component.literal(faygolover.rpculinary.core.Weights.format(g));
        e.getToolTip().add(src.isEmpty() ? tr("weight", w).withStyle(ChatFormatting.GRAY)
                : tr("weight_source", Component.translatable("rpculinary.source." + src), w).withStyle(ChatFormatting.GRAY));
    }

    static void drinkLine(ItemTooltipEvent e) {
        if (ClientNutrition.thirstMode() == 0) return;
        int[] d = ClientNutrition.drink(e.getItemStack());
        if (d == null) return;
        int n = Math.max(1, d[0]), water = d[1];
        if (n > 1) {
            int left = faygolover.rpculinary.server.Sips.left(e.getItemStack());
            e.getToolTip().add(tr("sips", left, n, Math.round(water / (double) n)).withStyle(ChatFormatting.AQUA));
        } else if (water != 0) {
            e.getToolTip().add(tr("drink_water", water).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Диетолог: совет лично тебе — по своему состоянию (что прислал сервер). */
    private static void advice(List<Component> t, Food f) {
        var p = ClientNutrition.self;
        if (p == null) return;
        if (p.hunger() && (!p.room() || p.satiety() > 1.0)) {
            t.add(tr("advice_full").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            return;
        }
        if (p.nutrientsOn() && p.nutrients().length >= NutritionState.COUNT) {
            float[] n = p.nutrients();
            double[] gain = {f.protein() * 0.9, f.fat() * 1.2, f.carbs() * 0.45, f.vitamins()};
            MutableComponent list = null;
            for (int i = 0; i < NutritionState.COUNT; i++) {
                if (n[i] >= LOW || gain[i] < 8) continue;
                Component name = tr("nutrient_" + NutritionState.NUTRIENTS[i]);
                list = list == null ? name.copy() : list.append(", ").append(name);
            }
            if (list != null) {
                t.add(tr("advice_covers", list).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                return;
            }
            if (n[NutritionState.FAT] > HIGH && gain[NutritionState.FAT] >= 8) {
                t.add(tr("advice_too_fatty").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                return;
            }
        }
        if (p.thirstMode() != 0 && p.thirstStage() >= 1 && f.water() > 0)
            t.add(tr("advice_thirst").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    /** Пороги нехватки и избытка — как на сервере по умолчанию (для совета, не для механики). */
    private static final float LOW = 20, HIGH = 100;

    /** Словами: сытность, чего много, витамины. */
    private static void words(List<Component> t, Food f) {
        String size = f.kcal() < 120 ? "light" : f.kcal() < 350 ? "filling" : "very_filling";
        MutableComponent line = tr("size_" + size);
        double k = Math.max(1, f.kcal());
        if (f.protein() * 4 / k > 0.3) line.append(", ").append(tr("much_protein"));
        if (f.fat() * 9 / k > 0.45) line.append(", ").append(tr("fatty"));
        if (f.carbs() * 4 / k > 0.6) line.append(", ").append(tr("much_carbs"));
        if (f.vitamins() >= 8) line.append(", ").append(tr("vitamins"));
        else if (f.vitamins() <= 1) line.append(", ").append(tr("no_vitamins"));
        t.add(line.withStyle(ChatFormatting.GOLD));
        if (f.water() > 0) t.add(tr("water_words").withStyle(ChatFormatting.AQUA));
    }

    /** Доля калорий от нутриента: много / средне / мало. */
    private static Component amount(double part, double kcal) {
        double share = part / Math.max(1, kcal);
        return tr(share > 0.35 ? "much" : share > 0.12 ? "some" : "little");
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("rpculinary.tooltip." + key, args);
    }
}
