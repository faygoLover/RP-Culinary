package faygolover.rpculinary.client;

import faygolover.rpculinary.server.CulinaryService;
import faygolover.rpculinary.server.Sips;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Шкала на иконке, как у прочности: сколько глотков осталось у начатого напитка (голубая) и укусов у надкусанного
 * блюда (тёплая). Рисуется только у начатых — по пометкам в NBT.
 */
public final class PortionBars {
    private PortionBars() {}

    private static final IItemDecorator BAR = (g, font, stack, x, y) -> {
        CompoundTag t = stack.getTag();
        if (t == null) return false;
        int left, n, color;
        if (t.contains(Sips.TOTAL)) {
            left = t.getInt(Sips.LEFT);
            n = t.getInt(Sips.TOTAL);
            color = 0xFF4FB3F0;
        } else if (t.contains(CulinaryService.BITES_N)) {
            left = t.getInt(CulinaryService.BITES);
            n = t.getInt(CulinaryService.BITES_N);
            color = 0xFFE0A040;
        } else {
            return false;
        }
        if (n <= 1) return false;
        int w = Math.max(1, Math.round(13f * Math.max(0, Math.min(n, left)) / n));
        g.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        g.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 2 + w, y + 14, color);
        return false;
    };

    /** Всем, что едят или пьют (у остальных пометок не бывает). */
    public static void register(RegisterItemDecorationsEvent e) {
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack st = new ItemStack(item);
            if (st.isEdible() || st.getUseAnimation() == UseAnim.DRINK) e.register(item, BAR);
        }
    }
}
