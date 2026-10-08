package faygolover.rpculinary.client;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.network.SyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно «Питание» (клавиша N): что человек чувствует — голод, жажда, чего не хватает, что приелось, телосложение.
 * Ощущениями, без цифр; цифры — у врача (анализ крови RP Medicine) и, позже, у Диетолога.
 */
public class NutritionScreen extends Screen {
    private static final int W = 270;
    private static final int H = 180;
    /** Пороги, как на сервере по умолчанию (для слов, не для механики). */
    private static final float LOW = 20, HIGH = 100, BAL_MIN = 35, BAL_MAX = 85;

    private record Feel(String icon, Component text) {}

    public NutritionScreen() {
        super(Component.translatable("rpculinary.screen.title"));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("rpculinary.screen." + key, args);
    }

    private List<Feel> feelings(SyncPacket p) {
        List<Feel> out = new ArrayList<>();
        if (p.hunger()) {
            String key = p.satiety() > 1.05 ? "overfed" : !p.room() ? "full" : "hunger_" + p.hungerStage();
            out.add(new Feel(null, tr(key).withStyle(p.hungerStage() >= 2 ? ChatFormatting.RED : ChatFormatting.WHITE)));
        }
        if (p.thirstMode() != 0)
            out.add(new Feel(null, tr("thirst_" + p.thirstStage()).withStyle(p.thirstStage() >= 2 ? ChatFormatting.RED : ChatFormatting.AQUA)));
        if (p.poorAppetite()) out.add(new Feel(null, tr("poor_appetite").withStyle(ChatFormatting.GOLD)));
        if (p.nutrientsOn() && p.nutrients().length >= NutritionState.COUNT) {
            float[] n = p.nutrients();
            if (n[NutritionState.PROTEIN] < LOW) out.add(new Feel("protein", tr("low_protein").withStyle(ChatFormatting.RED)));
            if (n[NutritionState.FAT] < LOW) out.add(new Feel("fats_and_oil", tr("low_fat").withStyle(ChatFormatting.RED)));
            if (n[NutritionState.CARBS] < LOW) out.add(new Feel("dietary_fiber", tr("low_carbs").withStyle(ChatFormatting.RED)));
            if (n[NutritionState.VITAMINS] < LOW) out.add(new Feel("vitamin", tr("low_vitamins").withStyle(ChatFormatting.RED)));
            if (n[NutritionState.FAT] > HIGH) out.add(new Feel("fats_and_oil", tr("heavy").withStyle(ChatFormatting.GOLD)));
            boolean bal = true;
            for (float v : n) if (v < BAL_MIN || v > BAL_MAX) bal = false;
            if (bal) out.add(new Feel("vitamin", tr("balanced").withStyle(ChatFormatting.GREEN)));
        }
        return out;
    }

    /** Телосложение словами (по доле жира), вес — примерно. */
    private static Component build(SyncPacket p) {
        float fat = p.fatPercent();
        String word = fat >= 32 ? "obese" : fat >= 25 ? "overweight" : fat < 6 ? "exhausted" : fat < 12 ? "thin" : "normal";
        return tr("build", tr("build_" + word), Math.round(p.weightKg()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = (width - W) / 2;
        int t = (height - H) / 2;
        g.fill(l, t, l + W, t + H, 0xF0201C18);
        g.renderOutline(l, t, W, H, 0xFF7A6A50);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, t + 8, 0xFFFFFF);
        SyncPacket p = ClientNutrition.self;
        if (p == null) {
            g.drawCenteredString(font, tr("unknown").withStyle(ChatFormatting.GRAY), width / 2, t + 40, 0xFFFFFF);
            super.render(g, mx, my, pt);
            return;
        }
        int y = t + 26;
        for (Feel f : feelings(p)) {
            if (f.icon() != null)
                g.blit(new ResourceLocation(RpCulinary.MODID, "textures/gui/nutrition/" + f.icon() + ".png"), l + 10, y - 5, 0, 0, 16, 16, 16, 16);
            for (var line : font.split(f.text(), W - 44)) {
                g.drawString(font, line, l + 32, y, 0xFFFFFF, false);
                y += 10;
            }
            y += 6;
        }
        if (p.fedUpMask() != 0) {
            MutableComponent list = Component.empty();
            boolean first = true;
            for (int i = 0; i < NutritionState.CATEGORIES.length; i++) {
                if ((p.fedUpMask() & (1 << i)) == 0) continue;
                if (!first) list.append(", ");
                list.append(Component.translatable("rpculinary.food_category." + NutritionState.CATEGORIES[i]));
                first = false;
            }
            g.drawString(font, tr("fed_up", list).withStyle(ChatFormatting.GOLD), l + 10, t + H - 39, 0xFFFFFF, false);
        }
        g.drawString(font, build(p).copy().withStyle(ChatFormatting.GRAY), l + 10, t + H - 26, 0xFFFFFF, false);
        // Диетолог знает цифры: свои запасы, сытость, воду, съеденное.
        if (FoodTooltips.dietitian() && p.nutrients().length >= NutritionState.COUNT) {
            float[] n = p.nutrients();
            g.drawString(font, tr("dietitian_numbers", Math.round(p.satiety() * 100), Math.round(p.hydration() * 100), Math.round(n[0]),
                    Math.round(n[1]), Math.round(n[2]), Math.round(n[3]), Math.round(p.recentKcal())).withStyle(ChatFormatting.LIGHT_PURPLE),
                    l + 10, t + H - 52, 0xFFFFFF, false);
        }
        g.drawString(font, tr("lab_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), l + 10, t + H - 13, 0xFFFFFF, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
