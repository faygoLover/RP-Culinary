package faygolover.rpculinary.client;

import faygolover.rpcore.client.hud.Anchor;
import faygolover.rpcore.client.hud.HudElement;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.network.SyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Полоски общего HUD (RP Core, двигать — {@code /rphud}): «Сытость» — где были ванильные окорочка, «Вода» — над ней.
 * Сытость: тёмная часть — запас, светлая — ещё в желудке; переел — оранжевая кромка сверху.
 */
public final class CulinaryHud {
    private CulinaryHud() {}

    private static final ResourceLocation ICONS = new ResourceLocation(RpCulinary.MODID, "textures/gui/hud_icons.png");
    private static final int BAR = 81;

    private static boolean visible(boolean preview) {
        var mc = Minecraft.getInstance();
        if (preview) return true;
        return mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator();
    }

    private static boolean blink() {
        return (System.currentTimeMillis() / 400) % 2 == 0;
    }

    /** Полоска сытости. */
    public static final class Satiety implements HudElement {
        @Override
        public ResourceLocation id() {
            return new ResourceLocation(RpCulinary.MODID, "satiety");
        }

        @Override
        public Component title() {
            return Component.translatable("rpculinary.hud.satiety");
        }

        @Override
        public int width() {
            return BAR + 10;
        }

        @Override
        public int height() {
            return 9;
        }

        @Override
        public boolean hasIcon() {
            return true;
        }

        @Override
        public Anchor defaultAnchor() {
            return Anchor.BOTTOM_CENTER;
        }

        /** Правый край — там же, где у ванильных окорочков (центр + 91). */
        @Override
        public int defaultX() {
            return 45;
        }

        @Override
        public int defaultY() {
            return -30;
        }

        @Override
        public void render(GuiGraphics g, float partialTick, boolean preview) {
            if (!visible(preview) || (!preview && !ClientNutrition.hunger())) return;
            SyncPacket p = ClientNutrition.self;
            double sat = preview ? 0.7 : p.satiety();
            double stomachShare = preview ? 0.2 : Math.max(0, Math.min(sat, p.stomach()));
            int stage = preview ? 0 : p.hungerStage();
            if (faygolover.rpcore.client.hud.HudLayout.iconShown(this)) g.blit(ICONS, 0, 0, 0, 0, 9, 9, 32, 16);
            int x0 = 10;
            g.fill(x0, 0, x0 + BAR, 9, 0xA0000000);
            int full = (int) Math.round(Math.min(1, sat) * (BAR - 2));
            int stomach = (int) Math.round(Math.min(1, Math.min(sat, stomachShare)) * (BAR - 2));
            int color = stage >= 2 ? (blink() ? 0xFFB03020 : 0xFF802018) : stage == 1 ? 0xFFC07028 : 0xFFB8862E;
            g.fill(x0 + 1, 1, x0 + 1 + full, 8, color);
            // Ещё в желудке — светлее (правый край полосы).
            if (stomach > 0) g.fill(x0 + 1 + Math.max(0, full - stomach), 1, x0 + 1 + full, 8, 0xFFE6C27A);
            // Переел — шкала идёт вторым кругом другим цветом (это не значок: видно, что съел через силу).
            if (sat > 1.0) {
                int over = (int) Math.round(Math.min(1, (sat - 1) / 0.75) * (BAR - 2));
                g.fill(x0 + 1, 1, x0 + 1 + over, 8, 0xFFD0502A);
                g.fill(x0 + 1, 1, x0 + 1 + over, 3, 0xFFFF8A30);
            }
            g.renderOutline(x0, 0, BAR, 9, 0xC0303030);
        }
    }

    /** Полоска воды (своя или LSO). */
    public static final class Water implements HudElement {
        private static final int W = 51;

        @Override
        public ResourceLocation id() {
            return new ResourceLocation(RpCulinary.MODID, "water");
        }

        @Override
        public Component title() {
            return Component.translatable("rpculinary.hud.water");
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return 7;
        }

        @Override
        public boolean hasIcon() {
            return true;
        }

        @Override
        public Anchor defaultAnchor() {
            return Anchor.BOTTOM_CENTER;
        }

        /** Над сытостью, правым краем к центру + 91. */
        @Override
        public int defaultX() {
            return 65;
        }

        @Override
        public int defaultY() {
            return -41;
        }

        @Override
        public void render(GuiGraphics g, float partialTick, boolean preview) {
            if (!visible(preview) || (!preview && ClientNutrition.thirstMode() == 0)) return;
            SyncPacket p = ClientNutrition.self;
            double hyd = preview ? 0.6 : p.hydration();
            int stage = preview ? 0 : p.thirstStage();
            if (faygolover.rpcore.client.hud.HudLayout.iconShown(this)) g.blit(ICONS, 0, 0, 9, 0, 7, 7, 32, 16);
            int x0 = 9, bw = W - x0;
            g.fill(x0, 0, x0 + bw, 7, 0xA0000000);
            int fill = (int) Math.round(Math.max(0, Math.min(1, hyd)) * (bw - 2));
            int color = stage >= 2 ? (blink() ? 0xFFB03020 : 0xFF3050A0) : stage == 1 ? 0xFF3A78B8 : 0xFF3C9BE0;
            g.fill(x0 + 1, 1, x0 + 1 + fill, 6, color);
            // Выпил через силу (воды больше 100 %) — второй круг.
            if (hyd > 1.0) {
                int over = (int) Math.round(Math.min(1, (hyd - 1) / 0.5) * (bw - 2));
                g.fill(x0 + 1, 1, x0 + 1 + over, 6, 0xFF6A3CC8);
            }
            g.renderOutline(x0, 0, bw, 7, 0xC0303030);
        }
    }
}
