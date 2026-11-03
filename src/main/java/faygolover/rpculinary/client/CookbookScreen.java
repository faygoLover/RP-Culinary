package faygolover.rpculinary.client;

import faygolover.rpculinary.core.Cookbook;
import faygolover.rpculinary.item.CookbookItems;
import faygolover.rpculinary.network.CookbookPacket;
import faygolover.rpculinary.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Поваренная книга и личная книга рецептов (D3, решения Алекса 03.11): без красивых схем — список рецептов, справа ингредиенты
 * картинками, станция, результат и текст рецепта, как в настоящей поваренной книге (свой текст — ключ
 * {@code rpculinary.cookbook.text.<ns>.<path>}, иначе — по станции). В поваренной книге — кнопка «Переписать»: идёт полоска,
 * потом рецепт попадает в личную книгу (проверяет сервер: время, навык, перк, обе книги при себе).
 */
public class CookbookScreen extends Screen {
    private static final int W = 330, H = 210, ROWS = 10, ROW = 18;
    private final String section;
    private final List<Recipe<?>> list = new ArrayList<>();
    private int scroll, selected, progress = -1, total, textScroll, textMax;
    private Button copy;

    private CookbookScreen(Component title, String section, List<String> ids) {
        super(title);
        this.section = section;
        var rm = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getRecipeManager();
        if (rm != null) for (String id : ids) rm.byKey(new ResourceLocation(id)).ifPresent(list::add);
    }

    public static void openBook(String section) {
        Cookbook.Book b = Cookbook.book(section);
        if (b == null) return;
        Minecraft.getInstance().setScreen(new CookbookScreen(Component.translatable("item.rpculinary.cookbook." + section), section,
                new ArrayList<>(b.recipes().keySet())));
    }

    public static void openNotebook(ItemStack notebook) {
        List<String> ids = new ArrayList<>(Cookbook.base());
        for (String r : Cookbook.learned(notebook)) if (!ids.contains(r)) ids.add(r);
        Minecraft.getInstance().setScreen(new CookbookScreen(Component.translatable("item.rpculinary.recipe_notebook"), null, ids));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        if (section != null) {
            copy = addRenderableWidget(Button.builder(Component.translatable("rpculinary.cookbook.copy"), b -> startCopy())
                    .bounds(left() + W - 110, top() + H - 26, 100, 20).build());
            updateButton();
        }
    }

    private Recipe<?> current() {
        return selected >= 0 && selected < list.size() ? list.get(selected) : null;
    }

    private ItemStack notebook() {
        var p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(CookbookItems.NOTEBOOK.get())) return st;
        }
        return ItemStack.EMPTY;
    }

    private void updateButton() {
        if (copy == null) return;
        Recipe<?> r = current();
        ItemStack nb = notebook();
        boolean known = r != null && (Cookbook.base().contains(r.getId().toString()) || !nb.isEmpty() && Cookbook.learned(nb).contains(r.getId().toString()));
        copy.active = r != null && !nb.isEmpty() && !known && progress < 0;
        copy.setMessage(Component.translatable(known ? "rpculinary.cookbook.known" : nb.isEmpty() ? "rpculinary.cookbook.no_notebook" : "rpculinary.cookbook.copy"));
    }

    private void startCopy() {
        Recipe<?> r = current();
        if (r == null) return;
        int ings = (int) r.getIngredients().stream().filter(i -> !i.isEmpty()).count();
        total = Cookbook.copyTicks(ings);
        progress = 0;
        Net.CHANNEL.sendToServer(new CookbookPacket(true, section, r.getId().toString(), ings));
        updateButton();
    }

    @Override
    public void tick() {
        if (progress < 0) return;
        if (++progress >= total) {
            Recipe<?> r = current();
            if (r != null) Net.CHANNEL.sendToServer(new CookbookPacket(false, section, r.getId().toString(), 0));
            progress = -1;
        }
        updateButton();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left() + 8, y0 = top() + 24;
        if (mx >= x0 && mx < x0 + 120 && my >= y0 && my < y0 + ROWS * ROW && progress < 0) {
            int i = scroll + (int) ((my - y0) / ROW);
            if (i < list.size()) {
                selected = i;
                textScroll = 0;
                updateButton();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= left() + 136) {  // справа — прокрутка текста рецепта (длинные тексты не обрезаем)
            textScroll = Math.max(0, Math.min(textMax, textScroll - 3 * (int) Math.signum(delta)));
            return true;
        }
        scroll =Math.max(0, Math.min(Math.max(0, list.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, 0xF0EDE3CF);
        g.fill(x + 132, y + 22, x + 133, y + H - 8, 0xFF9C8B6A);
        g.drawString(font, title, x + 8, y + 8, 0xFF3B2A1A, false);
        // список
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Recipe<?> r = list.get(scroll + i);
            int ry = y + 24 + i * ROW;
            if (scroll + i == selected) g.fill(x + 6, ry - 1, x + 130, ry + ROW - 1, 0x40806040);
            ItemStack out = r.getResultItem(minecraft.level.registryAccess());
            g.renderItem(out, x + 8, ry);
            g.drawString(font, font.plainSubstrByWidth(out.getHoverName().getString(), 100), x + 26, ry + 4, 0xFF3B2A1A, false);
        }
        if (list.isEmpty()) g.drawString(font, Component.translatable("rpculinary.cookbook.empty"), x + 8, y + 26, 0xFF6B5A44, false);
        Recipe<?> r = current();
        if (r != null) renderRecipe(g, r, x + 140, y + 22, mx, my);
        if (progress >= 0) {
            int bw = 100, bx = x + W - 110, by = y + H - 34;
            g.fill(bx, by, bx + bw, by + 5, 0xFF6B5A44);
            g.fill(bx, by, bx + bw * progress / Math.max(1, total), by + 5, 0xFF4FA36A);
        }
        super.render(g, mx, my, pt);
    }

    private void renderRecipe(GuiGraphics g, Recipe<?> r, int x, int y, int mx, int my) {
        ItemStack out = r.getResultItem(minecraft.level.registryAccess());
        g.drawString(font, out.getHoverName(), x, y, 0xFF3B2A1A, false);
        String type = String.valueOf(net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS.getKey(r.getSerializer()));
        String rtype = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()));
        String stKey = "rpculinary.station." + rtype.replace(':', '.');
        Component station = I18n.exists(stKey) ? Component.translatable(stKey) : Component.literal(rtype);
        g.drawString(font, Component.translatable("rpculinary.cookbook.station", station), x, y + 12, 0xFF6B5A44, false);
        if (section != null) {
            int need = Cookbook.skill(section, r.getId().toString());
            g.drawString(font, Component.translatable("rpculinary.cookbook.skill", need), x, y + 22, 0xFF6B5A44, false);
        }
        // ингредиенты картинками (варианты тега меняются раз в секунду) → результат
        long t = System.currentTimeMillis() / 1000;
        int ix = x, iy = y + 36, n = 0;
        Map<String, Integer> names = new LinkedHashMap<>();
        for (Ingredient ing : r.getIngredients()) {
            ItemStack[] its = ing.getItems();
            if (its.length == 0) continue;
            ItemStack show = its[(int) (t % its.length)];
            g.renderItem(show, ix + (n % 7) * 18, iy + (n / 7) * 18);
            if (mx >= ix + (n % 7) * 18 && mx < ix + (n % 7) * 18 + 16 && my >= iy + (n / 7) * 18 && my < iy + (n / 7) * 18 + 16)
                g.renderTooltip(font, show, mx, my);
            names.merge(its[0].getHoverName().getString(), 1, Integer::sum);
            n++;
        }
        int ay = iy + ((Math.max(1, n) - 1) / 7) * 18;
        g.drawString(font, "→", ix + Math.min(n, 7) * 18 + 2, ay + 4, 0xFF3B2A1A, false);
        g.renderItem(out, ix + Math.min(n, 7) * 18 + 12, ay);
        g.renderItemDecorations(font, out, ix + Math.min(n, 7) * 18 + 12, ay);
        // текст рецепта
        List<String> parts = new ArrayList<>();
        names.forEach((k, v) -> parts.add(v > 1 ? k + " ×" + v : k));
        String id = r.getId().getNamespace() + "." + r.getId().getPath().replace('/', '.');
        String textKey = "rpculinary.cookbook.text." + id;
        String howKey = "rpculinary.cookbook.how." + rtype.replace(':', '.');
        Component text = I18n.exists(textKey) ? Component.translatable(textKey)
                : Component.translatable(I18n.exists(howKey) ? howKey : "rpculinary.cookbook.how.default", String.join(", ", parts), out.getHoverName());
        int ty = ay + 24, bottom = top() + H - (section != null ? 40 : 10);
        List<FormattedCharSequence> lines = font.split(text, W - 162);
        int fit = Math.max(1, (bottom - ty) / 10);
        textMax = Math.max(0, lines.size() - fit);
        textScroll = Math.min(textScroll, textMax);
        for (int i = textScroll; i < lines.size() && i < textScroll + fit; i++, ty += 10)
            g.drawString(font, lines.get(i), x, ty, 0xFF4A3A28, false);
        if (textScroll > 0) g.drawString(font, "▲", x + W - 160, ay + 24, 0xFF9C8B6A, false);
        if (textScroll < textMax) g.drawString(font, "▼", x + W - 160, ay + 24 + (fit - 1) * 10, 0xFF9C8B6A, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
