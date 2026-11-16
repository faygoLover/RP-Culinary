package faygolover.rpculinary.server;

import faygolover.rpcore.api.Consumables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.Nullable;

/**
 * Напитки глотками (решение Alex 26.10): глоток — одна короткая анимация питья, предмет остаётся, пока не допит
 * (шкала на иконке). Сколько глотков — в общей базе RP Core ({@code "sips"}); простая вода — 20 % за глоток.
 * Неполные одинаковые напитки переливаются: перетащить один на другой в инвентаре или Shift+ПКМ с ними в обеих руках
 * (из второй руки — в основную). Пометки — в NBT предмета: {@link #LEFT} — сколько осталось, {@link #TOTAL} — сколько
 * было у целого.
 * <p>Пустую бутылку SA можно наполнить (30.10): так же перетащить на неё воду (галлон, бутылку, зелье воды) или
 * Shift+ПКМ — вода во второй руке, пустые в основной. Из чистой воды — чистая бутылка, из грязной — грязная; глотков
 * переходит сколько есть (у всей воды глоток — 20 %).
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
    public static volatile ToIntFunction<ItemStack> clientTotal;

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

    /** Пустая тара → что в ней будет с чистой и с грязной водой. */
    private record Refill(String clean, String dirty) {}

    private static final Map<String, Refill> REFILL = Map.of(
            "sa_core:water_bottle_empty", new Refill("sa_core:water_bottle", "sa_core:dirty_water_bottle"));
    /** Откуда можно наливать: чистая вода и грязная (фляжки LSO не трогаем — у них своя механика). */
    private static final Set<String> CLEAN = Set.of("sa_core:water_bottle", "survival_instinct:bottle_of_water",
            "survival_instinct:gallon_of_water", "legendarysurvivaloverhaul:purified_water_bottle");
    private static final Set<String> DIRTY = Set.of("sa_core:dirty_water_bottle");

    private static String id(ItemStack st) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id == null ? "" : id.toString();
    }

    /** Вода в {@code st}: 1 — чистая, -1 — грязная (и простое зелье воды), 0 — не источник. */
    private static int waterKind(ItemStack st) {
        String id = id(st);
        if (CLEAN.contains(id)) return 1;
        if (DIRTY.contains(id)) return -1;
        if (st.is(Items.POTION) && PotionUtils.getPotion(st) == Potions.WATER) return -1;
        return 0;
    }

    @Nullable
    private static ItemStack item(String id) {
        var it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it == null || it == Items.AIR ? null : new ItemStack(it);
    }

    /**
     * Наполнить одну пустую из {@code empties} водой из {@code from}. Возвращает {остаток источника, наполненная}
     * (null — нельзя). {@code empties} уменьшается на одну.
     */
    @Nullable
    static ItemStack[] refill(ItemStack from, ItemStack empties) {
        Refill r = REFILL.get(id(empties));
        if (r == null || from.getCount() != 1) return null;
        int kind = waterKind(from);
        if (kind == 0) return null;
        ItemStack full = item(kind > 0 ? r.clean() : r.dirty());
        if (full == null) return null;
        int n = Math.max(1, total(full)), a = left(from);
        if (a <= 0) return null;
        int moved = Math.min(a, n);
        set(full, moved, n);
        empties.shrink(1);
        ItemStack rest;
        if (a - moved <= 0) {
            rest = emptyContainer(from);
        } else {
            rest = from.copy();
            set(rest, a - moved, total(from));
        }
        return new ItemStack[]{rest, full};
    }

    /** Пустая тара после переливания до дна. */
    static ItemStack emptyContainer(ItemStack st) {
        String self = id(st);
        for (var e : REFILL.entrySet())
            if (e.getValue().clean().equals(self) || e.getValue().dirty().equals(self)) {
                ItemStack empty = item(e.getKey());
                if (empty != null) return empty;
            }
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
        // Водой по пустой бутылке — наполнить одну, она идёт в инвентарь (делает сервер, клиент только не мешает).
        Player p = e.getPlayer();
        if (p.level().isClientSide) {
            if (refill(carried.copy(), target.copy()) != null) {
                e.setCanceled(true);
                return;
            }
        } else {
            ItemStack[] filled = refill(carried, target);
            if (filled != null) {
                e.getCarriedSlotAccess().set(filled[0]);
                e.getSlot().setChanged();
                if (!p.getInventory().add(filled[1])) p.drop(filled[1], false);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL,
                        SoundSource.PLAYERS, 0.6f, 1f);
                e.setCanceled(true);
                return;
            }
        }
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
        if (p.level().isClientSide) {
            if (refill(off.copy(), main.copy()) != null) {
                e.setCancellationResult(InteractionResult.SUCCESS);
                e.setCanceled(true);
                return;
            }
        }
        ItemStack[] filled = p.level().isClientSide ? null : refill(off, main);
        if (filled != null) {
            p.setItemInHand(InteractionHand.OFF_HAND, filled[0]);
            if (!p.getInventory().add(filled[1])) p.drop(filled[1], false);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS, 0.6f, 1f);
            e.setCancellationResult(InteractionResult.SUCCESS);
            e.setCanceled(true);
            return;
        }
        ItemStack rest = pour(off, main);
        if (rest == null) return;
        p.setItemInHand(InteractionHand.OFF_HAND, rest);
        e.setCancellationResult(InteractionResult.SUCCESS);
        e.setCanceled(true);
    }
}
