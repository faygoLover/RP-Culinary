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
import net.minecraft.util.FormattedCharSequence;

/**
 * Окно «Питание» (клавиша K): что человек чувствует — голод, жажда, каждый нутриент словами (нехватка, на грани,
 * норма, многовато, избыток), что приелось, телосложение. Без цифр; цифры — у Диетолога и в анализе крови.
 * Высота по содержимому, все строки переносятся по ширине окна.
 */
public class NutritionScreen extends Screen {
    private static final int MAX_W = 330;
    private static final int PAD = 10;
    private static final int ICON = 22;
    /** Пороги, как на сервере по умолчанию (для слов, не для механики). */
    private static final float LOW = 20, HIGH = 100, BAL_MIN = 35, BAL_MAX = 85;
    /** Сытость: с этой доли — «почти наелся», выше OVERFED — «переел». */
    static final float ALMOST_FULL = 0.9f, OVERFED = 1.2f;
    private static final String[] ICONS = {"protein", "fats_and_oil", "dietary_fiber", "vitamin"};
    private static final ChatFormatting[] ZONE_COLOR = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.GREEN,
            ChatFormatting.GOLD, ChatFormatting.RED};

    /** Строка окна: значок (или null), текст, отступ после. */
    private record Row(String icon, Component text, int gapAfter) {}

    public NutritionScreen() {
        super(Component.translatable("rpculinary.screen.title"));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("rpculinary.screen." + key, args);
    }

    private List<Row> rows(SyncPacket p) {
        List<Row> out = new ArrayList<>();
        if (p == null) {
            out.add(new Row(null, tr("unknown").withStyle(ChatFormatting.GRAY), 0));
            return out;
        }
        if (p.hunger()) {
            String key = p.satiety() > OVERFED ? "overfed" : !p.room() ? "full"
                    : p.satiety() >= ALMOST_FULL ? "almost_full" : "hunger_" + p.hungerStage();
            ChatFormatting c = p.satiety() > OVERFED || p.hungerStage() >= 2 ? ChatFormatting.RED
                    : key.equals("almost_full") || key.equals("full") ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            out.add(new Row(null, tr(key).withStyle(c), 3));
        }
        if (p.thirstMode() != 0)
            out.add(new Row(null, tr("thirst_" + p.thirstStage()).withStyle(p.thirstStage() >= 2 ? ChatFormatting.RED : ChatFormatting.AQUA), 3));
        if (p.poorAppetite()) out.add(new Row(null, tr("poor_appetite").withStyle(ChatFormatting.GOLD), 3));
        if (!out.isEmpty()) out.set(out.size() - 1, new Row(out.get(out.size() - 1).icon(), out.get(out.size() - 1).text(), 8));
        if (p.nutrientsOn() && p.nutrients().length >= NutritionState.COUNT) {
            float[] n = p.nutrients();
            boolean bal = true;
            for (int i = 0; i < NutritionState.COUNT; i++) {
                int z = NutritionState.zone(n[i], LOW, BAL_MIN, BAL_MAX, HIGH);
                if (z != 2) bal = false;
                String id = NutritionState.NUTRIENTS[i];
                MutableComponent line = tr("label_" + id).withStyle(ChatFormatting.GRAY).append(": ")
                        .append(tr("n_" + id + "_" + NutritionState.ZONES[z]).withStyle(ZONE_COLOR[z]));
                out.add(new Row(ICONS[i], line, 5));
            }
            if (bal) out.add(new Row(null, tr("balanced").withStyle(ChatFormatting.GREEN), 5));
            out.set(out.size() - 1, new Row(out.get(out.size() - 1).icon(), out.get(out.size() - 1).text(), 8));
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
            out.add(new Row(null, tr("fed_up", list).withStyle(ChatFormatting.GOLD), 3));
        }
        out.add(new Row(null, build(p).copy().withStyle(ChatFormatting.GRAY), 3));
        // Диетолог знает цифры: свои запасы, сытость, воду, съеденное.
        if (FoodTooltips.dietitian() && p.nutrients().length >= NutritionState.COUNT) {
            float[] n = p.nutrients();
            out.add(new Row(null, tr("dietitian_numbers", Math.round(p.satiety() * 100), Math.round(p.hydration() * 100), Math.round(n[0]),
                    Math.round(n[1]), Math.round(n[2]), Math.round(n[3]), Math.round(p.recentKcal())).withStyle(ChatFormatting.LIGHT_PURPLE), 3));
        }
        out.add(new Row(null, tr("lab_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), 0));
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
        int w = Math.min(MAX_W, width - 2 * PAD);
        int textW = w - PAD - ICON - PAD;
        List<Row> rows = rows(ClientNutrition.self);
        // Сначала высота: строки переносятся по ширине.
        int contentH = 0;
        List<List<FormattedCharSequence>> split = new ArrayList<>();
        for (Row r : rows) {
            var lines = font.split(r.text(), textW);
            split.add(lines);
            contentH += Math.max(r.icon() != null ? 16 : 0, lines.size() * 10) + r.gapAfter();
        }
        int h = Math.min(height - 2 * PAD, 26 + contentH + PAD);
        int l = (width - w) / 2;
        int t = Math.max(PAD, (height - h) / 2);
        g.fill(l, t, l + w, t + h, 0xF0201C18);
        g.renderOutline(l, t, w, h, 0xFF7A6A50);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, t + 8, 0xFFFFFF);
        int y = t + 24;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            var lines = split.get(i);
            int rowH = Math.max(r.icon() != null ? 16 : 0, lines.size() * 10);
            if (y + rowH > t + h) break;
            if (r.icon() != null)
                g.blit(new ResourceLocation(RpCulinary.MODID, "textures/gui/nutrition/" + r.icon() + ".png"), l + PAD, y, 0, 0, 16, 16, 16, 16);
            int ty = y + (r.icon() != null && lines.size() == 1 ? 4 : 0);
            for (var line : lines) {
                g.drawString(font, line, l + PAD + ICON, ty, 0xFFFFFF, false);
                ty += 10;
            }
            y += rowH + r.gapAfter();
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
