package faygolover.rpculinary.server;

import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Source;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Состав через любые станции (решения Alex 29.10): «что положил — то и в блюде». Пока игрок «у плиты» (контекст готовки
 * {@link CookingService}), помеченное составом мяса, что ушло из его инвентаря, запоминается поштучно. Новый непомеченный
 * продукт, появившийся у него, получает состав из ушедшего — но только из того, что входит в рецепт этого продукта
 * ({@link NutritionTable#usesIngredient}): котлета из фарша кенгуру — «кенгуру», а хлеб рядом — нет. Облик — первого
 * мясного ингредиента. Доска, мясорубки, миска и туши метят сами ({@link Processing}, {@link Butchering}).
 */
public final class Provenance {
    private Provenance() {}

    private static final class Ctx {
        Map<String, Integer> marked = new HashMap<>();
        Map<String, ItemStack> samples = new HashMap<>();
        Map<Item, Integer> plain = new HashMap<>();
        final List<ItemStack> gone = new ArrayList<>();
    }

    private static final Map<UUID, Ctx> CTX = new HashMap<>();

    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 10 != 5) return;
        NutritionData d = NutritionData.get(sp);
        boolean active = d != null && (d.cookMenu || sp.server.getTickCount() <= d.cookUntil || d.cookBaseline != null);
        if (!active) {
            CTX.remove(sp.getUUID());
            return;
        }
        Ctx c = CTX.get(sp.getUUID());
        Ctx now = new Ctx();
        count(sp, now);
        if (c == null) {
            CTX.put(sp.getUUID(), now);
            return;
        }
        for (var en : c.marked.entrySet()) {  // ушло помеченное — в станцию
            int left = en.getValue() - now.marked.getOrDefault(en.getKey(), 0);
            for (int i = 0; i < left && c.gone.size() < 64; i++) c.gone.add(c.samples.get(en.getKey()));
        }
        boolean changed = false;
        if (!c.gone.isEmpty()) {
            for (var en : now.plain.entrySet()) {  // появилось новое непомеченное — из ушедших ингредиентов его рецепта
                int added = en.getValue() - c.plain.getOrDefault(en.getKey(), 0);
                if (added <= 0) continue;
                List<ItemStack> used = new ArrayList<>();
                for (ItemStack g : c.gone) if (NutritionTable.usesIngredient(sp.server, en.getKey(), g)) used.add(g);
                if (used.isEmpty()) continue;
                mark(sp, en.getKey(), added, Source.mix(used), Processing.lookOf(used.get(0)));
                changed = true;
            }
        }
        if (changed) {
            now = new Ctx();
            count(sp, now);
        }
        now.gone.addAll(c.gone);
        CTX.put(sp.getUUID(), now);
    }

    /** Помеченное — по составу (ключ — предмет и NBT), непомеченная еда — по предмету. */
    private static void count(ServerPlayer sp, Ctx c) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(inv.getItem(i), c);
        add(sp.containerMenu.getCarried(), c);
    }

    private static void add(ItemStack st, Ctx c) {
        if (st.isEmpty()) return;
        if (Source.marked(st)) {
            String k = st.getItem() + "|" + st.getTag();
            c.marked.merge(k, st.getCount(), Integer::sum);
            c.samples.putIfAbsent(k, st.copyWithCount(1));
        } else if (st.isEdible() || faygolover.rpculinary.core.Weights.nominal(st.getItem()) > 0) {
            c.plain.merge(st.getItem(), st.getCount(), Integer::sum);
        }
    }

    /** Пометить {@code n} новых штук предмета составом: отделить их от непомеченного стака. */
    private static void mark(ServerPlayer sp, Item item, int n, Map<String, Integer> parts, String look) {
        Inventory inv = sp.getInventory();
        ItemStack carried = sp.containerMenu.getCarried();
        if (carried.is(item) && !Source.marked(carried) && n > 0) {
            int k = Math.min(n, carried.getCount());
            if (k == carried.getCount()) {
                Source.setParts(carried, parts, look);
            } else {
                carried.shrink(k);
                ItemStack part = new ItemStack(item, k);
                Source.setParts(part, parts, look);
                if (!inv.add(part)) sp.drop(part, false);
            }
            n -= k;
        }
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item) || Source.marked(st)) continue;
            int k = Math.min(n, st.getCount());
            if (k == st.getCount()) {
                Source.setParts(st, parts, look);
            } else {
                ItemStack part = st.split(k);
                Source.setParts(part, parts, look);
                if (!inv.add(part)) sp.drop(part, false);
            }
            n -= k;
        }
        sp.containerMenu.broadcastChanges();
    }
}
