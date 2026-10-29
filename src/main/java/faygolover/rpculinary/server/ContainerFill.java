package faygolover.rpculinary.server;

import faygolover.rpculinary.core.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Мешки и ящики с продуктами (блок 6, решения Alex 29.10 §6), таблица — {@link Containers}.
 * <ul>
 *   <li>В инвентаре — как мешочек: продуктом по таре — положить; тарой по продукту — набрать; ПКМ по таре пустым курсором —
 *   достать стак.</li>
 *   <li>Поставленная тара хранит содержимое в данных мира по позиции ({@link Store}): ПКМ продуктом — положить, ПКМ пустой
 *   рукой — достать стак; сломанная — выпадает вместе с содержимым.</li>
 * </ul>
 */
public final class ContainerFill {
    private ContainerFill() {}

    // ------------------------------------------------------------------ инвентарь

    public static void onStackedOnOther(ItemStackedOnOtherEvent e) {
        ItemStack carried = e.getCarriedItem(), slot = e.getStackedOnItem();
        boolean right = e.getClickAction() == ClickAction.SECONDARY;
        if (Containers.of(slot.getItem()) != null) {
            if (!carried.isEmpty() && Containers.fill(slot, carried) > 0) {  // продуктом по таре — положить
                e.getCarriedSlotAccess().set(carried);
            } else if (carried.isEmpty() && right && Containers.count(slot) > 0) {  // пустым курсором ПКМ — достать
                e.getCarriedSlotAccess().set(Containers.take(slot));
            } else {
                return;
            }
        } else if (Containers.of(carried.getItem()) != null) {
            if (!slot.isEmpty() && Containers.fill(carried, slot) > 0) {  // тарой по продукту — набрать
                e.getSlot().set(slot.isEmpty() ? ItemStack.EMPTY : slot);
            } else if (slot.isEmpty() && right && Containers.count(carried) > 0) {  // тарой ПКМ по пустому слоту — выложить стак
                e.getSlot().set(Containers.take(carried));
            } else {
                return;
            }
        } else {
            return;
        }
        e.getSlot().setChanged();
        e.getPlayer().playSound(SoundEvents.BUNDLE_INSERT, 0.6f, 1f);
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ поставленная тара

    /** Содержимое поставленной тары: «измерение|позиция» → предмет и число. */
    public static final class Store extends SavedData {
        final Map<String, CompoundTag> boxes = new HashMap<>();

        static Store get(ServerLevel any) {
            return any.getServer().overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, "rpculinary_containers");
        }

        static Store load(CompoundTag t) {
            Store s = new Store();
            ListTag list = t.getList("boxes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag b = list.getCompound(i);
                s.boxes.put(b.getString("k"), b.getCompound("v"));
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag list = new ListTag();
            for (var en : boxes.entrySet()) {
                CompoundTag b = new CompoundTag();
                b.putString("k", en.getKey());
                b.put("v", en.getValue());
                list.add(b);
            }
            t.put("boxes", list);
            return t;
        }
    }

    static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** Тара-блок в виде предмета: содержимое → NBT предмета и обратно. */
    static ItemStack asItem(ServerLevel level, BlockPos pos, Item boxItem) {
        ItemStack st = new ItemStack(boxItem);
        CompoundTag v = Store.get(level).boxes.get(key(level, pos));
        if (v != null) st.getOrCreateTag().put(Containers.TAG, v.copy());
        return st;
    }

    static void save(ServerLevel level, BlockPos pos, ItemStack box) {
        Store s = Store.get(level);
        CompoundTag t = box.getTag();
        if (t != null && t.contains(Containers.TAG) && Containers.count(box) > 0) s.boxes.put(key(level, pos), t.getCompound(Containers.TAG).copy());
        else s.boxes.remove(key(level, pos));
        s.setDirty();
    }

    /** Кто сейчас ставит наполненную тару: игрок → (содержимое, тик). */
    private static final Map<UUID, Object[]> PLACING = new HashMap<>();
    /** Сломанная наполненная тара: место → (предмет, содержимое, тик) — приклеить содержимое к выпавшему предмету. */
    private static final Map<String, Object[]> BROKEN = new HashMap<>();

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel level)) return;
        ItemStack held = sp.getItemInHand(e.getHand());
        Item blockItem = level.getBlockState(e.getPos()).getBlock().asItem();
        var box = Containers.of(blockItem);
        if (box != null && e.getHand() == InteractionHand.MAIN_HAND && !sp.isShiftKeyDown()) {
            ItemStack virtual = asItem(level, e.getPos(), blockItem);
            boolean done = false;
            if (held.isEmpty()) {  // пустой рукой — достать стак
                ItemStack out = Containers.take(virtual);
                if (!out.isEmpty()) {
                    sp.setItemInHand(InteractionHand.MAIN_HAND, out);
                    done = true;
                }
            } else if (Containers.fill(virtual, held) > 0) {  // продуктом — положить
                done = true;
            }
            if (done) {
                save(level, e.getPos(), virtual);
                level.playSound(null, e.getPos(), SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.7f, 0.9f);
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }
        if (Containers.of(held.getItem()) != null && Containers.count(held) > 0)  // ставит наполненную — запомнить содержимое
            PLACING.put(sp.getUUID(), new Object[]{held.getItem(), held.getTag().getCompound(Containers.TAG).copy(), sp.server.getTickCount()});
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel level)) return;
        Object[] p = PLACING.remove(sp.getUUID());
        if (p == null || sp.server.getTickCount() - (int) p[2] > 2 || e.getPlacedBlock().getBlock().asItem() != p[0]) return;
        Store s = Store.get(level);
        s.boxes.put(key(level, e.getPos()), (CompoundTag) p[1]);
        s.setDirty();
    }

    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Store s = Store.get(level);
        CompoundTag v = s.boxes.remove(key(level, e.getPos()));
        if (v == null) return;
        s.setDirty();
        Item item = e.getState().getBlock().asItem();
        if (e.getPlayer() != null && e.getPlayer().isCreative()) {  // в творческом блок не выпадает — содержимое выдать отдельно
            ItemStack st = new ItemStack(item);
            st.getOrCreateTag().put(Containers.TAG, v);
            level.addFreshEntity(new ItemEntity(level, e.getPos().getX() + 0.5, e.getPos().getY() + 0.5, e.getPos().getZ() + 0.5, st));
            return;
        }
        BROKEN.put(key(level, e.getPos()), new Object[]{item, v, level.getServer().getTickCount(), e.getPos().immutable()});
    }

    /** Выпавшая из сломанной тары тара — с содержимым. */
    public static void onItemJoin(EntityJoinLevelEvent e) {
        if (BROKEN.isEmpty() || !(e.getEntity() instanceof ItemEntity ie) || !(e.getLevel() instanceof ServerLevel level)) return;
        int now = level.getServer().getTickCount();
        BROKEN.values().removeIf(v -> now - (int) v[2] > 5);
        for (var it = BROKEN.entrySet().iterator(); it.hasNext(); ) {
            Object[] v = it.next().getValue();
            if (ie.getItem().getItem() != v[0] || ie.distanceToSqr(Vec3.atCenterOf((BlockPos) v[3])) > 4) continue;
            ItemStack st = ie.getItem().copy();
            st.getOrCreateTag().put(Containers.TAG, (CompoundTag) v[1]);
            ie.setItem(st);
            it.remove();
            return;
        }
    }

    /** Для проверок: содержимое поставленной тары. */
    public static int storedCount(ServerLevel level, BlockPos pos) {
        CompoundTag v = Store.get(level).boxes.get(key(level, pos));
        return v == null ? 0 : v.getInt(Containers.N);
    }

    static Item item(ResourceLocation id) {
        Item it = ForgeRegistries.ITEMS.getValue(id);
        return it == null ? Items.AIR : it;
    }
}
