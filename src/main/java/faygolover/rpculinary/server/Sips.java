package faygolover.rpculinary.server;

import faygolover.rpcore.api.Consumables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Напитки глотками (решение Alex 26.10): глоток — одна короткая анимация питья, предмет остаётся, пока не допит
 * (шкала на иконке). Сколько глотков — в общей базе RP Core ({@code "sips"}); простая вода — 20 % за глоток.
 * Неполные одинаковые напитки переливаются: перетащить один на другой в инвентаре или Shift+ПКМ с ними в обеих руках
 * (из второй руки — в основную). Пометки — в NBT предмета: {@link #LEFT} — сколько осталось, {@link #TOTAL} — сколько
 * было у целого.
 */
public final class Sips {
    private Sips() {}

    public static final String LEFT = "rpculinary_sips", TOTAL = "rpculinary_sips_n";

    /** Сколько глотков у целого напитка (1 — пьётся за раз; не напиток — 0). */
    public static int total(ItemStack st) {
        if (st.isEmpty() || st.getUseAnimation() != UseAnim.DRINK) return 0;
        if (st.hasTag() && st.getTag().contains(TOTAL)) return Math.max(1, st.getTag().getInt(TOTAL));
        Consumables.Entry e = Consumables.forItem(st);
        if (e != null) return e.sips();
        return clientTotal != null ? Math.max(1, clientTotal.applyAsInt(st)) : 1;
    }

    /** Клиент: глотки по таблице с сервера (на выделенном клиенте базы RP Core нет). */
    public static volatile java.util.function.ToIntFunction<ItemStack> clientTotal;

    /** Сколько глотков осталось. */
    public static int left(ItemStack st) {
        int n = total(st);
        if (n <= 1) return n;
        return st.hasTag() && st.getTag().contains(LEFT) ? Math.max(1, Math.min(n, st.getTag().getInt(LEFT))) : n;
    }

    static void set(ItemStack st, int left, int total) {
        if (left >= total) {
            CompoundTag t = st.getTag();
            if (t != null) {
                t.remove(LEFT);
                t.remove(TOTAL);
                if (t.isEmpty()) st.setTag(null);
            }
            return;
        }
        st.getOrCreateTag().putInt(LEFT, left);
        st.getTag().putInt(TOTAL, total);
    }

    /** Тот же напиток без учёта пометок глотков (зелье — то же зелье). */
    static boolean same(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        CompoundTag ta = a.hasTag() ? a.getTag().copy() : new CompoundTag();
        CompoundTag tb = b.hasTag() ? b.getTag().copy() : new CompoundTag();
        for (CompoundTag t : new CompoundTag[]{ta, tb}) {
            t.remove(LEFT);
            t.remove(TOTAL);
        }
        return ta.equals(tb);
    }

    /** Пустая тара после переливания до дна. */
    static ItemStack emptyContainer(ItemStack st) {
        if (st.getItem() instanceof PotionItem) return new ItemStack(Items.GLASS_BOTTLE);
        if (st.is(Items.MILK_BUCKET)) return new ItemStack(Items.BUCKET);
        ItemStack r = st.getCraftingRemainingItem();
        return r == null ? ItemStack.EMPTY : r;
    }

    /**
     * Перелить из {@code from} в {@code to} (по одному предмету, неполные или один полный). Возвращает, что стало с
     * {@code from} (null — переливать нечего).
     */
    static ItemStack pour(ItemStack from, ItemStack to) {
        if (from.getCount() != 1 || to.getCount() != 1 || !same(from, to)) return null;
        int n = total(to);
        if (n <= 1) return null;
        int a = left(from), b = left(to);
        if (b >= n || a <= 0) return null;
        int moved = Math.min(a, n - b);
        set(to, b + moved, n);
        if (a - moved <= 0) return emptyContainer(from);
        ItemStack rest = from.copy();
        set(rest, a - moved, n);
        return rest;
    }

    /** Перетащил неполный напиток на такой же в инвентаре — перелить в тот, что в ячейке. */
    public static void onStackedOnOther(ItemStackedOnOtherEvent e) {
        ItemStack carried = e.getCarriedItem(), target = e.getStackedOnItem();
        ItemStack rest = pour(carried, target);
        if (rest == null) return;
        e.getCarriedSlotAccess().set(rest);
        e.getSlot().setChanged();
        e.setCanceled(true);
    }

    /** Shift+ПКМ с одинаковыми напитками в обеих руках — перелить из второй руки в основную. */
    public static void onRightClick(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (!p.isShiftKeyDown() || e.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        ItemStack rest = pour(off, main);
        if (rest == null) return;
        p.setItemInHand(InteractionHand.OFF_HAND, rest);
        e.setCancellationResult(InteractionResult.SUCCESS);
        e.setCanceled(true);
    }
}
