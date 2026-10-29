package faygolover.rpculinary.server;

import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Source;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Происхождение через любые станции (решение Alex 29.10): «что положил — то и в блюде». Пока игрок «у плиты» (контекст
 * готовки {@link CookingService}: открыто меню станции или недавно кликнул по ней), всё помеченное видом мяса, что ушло из
 * его инвентаря, копится; новые непомеченные продукты, появившиеся у него, получают эти виды. Так котлета из фарша кенгуру
 * — тоже «кенгуру», и бургер с ней. Доска, мясорубки и туши метят сами ({@link Processing}, {@link Butchering}).
 */
public final class Provenance {
    private Provenance() {}

    private static final class Ctx {
        Map<String, Integer> marked = new HashMap<>();
        Map<Item, Integer> plain = new HashMap<>();
        final Set<String> pending = new LinkedHashSet<>();
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
        Map<String, Integer> marked = new HashMap<>();
        Map<Item, Integer> plain = new HashMap<>();
        count(sp, marked, plain);
        if (c == null) {
            c = new Ctx();
            c.marked = marked;
            c.plain = plain;
            CTX.put(sp.getUUID(), c);
            return;
        }
        for (var en : c.marked.entrySet())  // ушло помеченное — в станцию
            if (marked.getOrDefault(en.getKey(), 0) < en.getValue()) c.pending.add(en.getKey());
        if (!c.pending.isEmpty()) {
            for (var en : plain.entrySet()) {  // появилось новое непомеченное — из того, что ушло
                int added = en.getValue() - c.plain.getOrDefault(en.getKey(), 0);
                if (added > 0) mark(sp, en.getKey(), added, c.pending);
            }
            marked.clear();
            plain.clear();
            count(sp, marked, plain);
        }
        c.marked = marked;
        c.plain = plain;
    }

    /** Помеченное — по видам (ключ — строка видов), непомеченная еда — по предмету. */
    private static void count(ServerPlayer sp, Map<String, Integer> marked, Map<Item, Integer> plain) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(inv.getItem(i), marked, plain);
        add(sp.containerMenu.getCarried(), marked, plain);
    }

    private static void add(ItemStack st, Map<String, Integer> marked, Map<Item, Integer> plain) {
        if (st.isEmpty()) return;
        if (Source.marked(st)) {
            for (String s : Source.list(st)) marked.merge(s, st.getCount(), Integer::sum);
        } else if (st.isEdible() || faygolover.rpculinary.core.Weights.nominal(st.getItem()) > 0) {
            plain.merge(st.getItem(), st.getCount(), Integer::sum);
        }
    }

    /** Пометить {@code n} новых штук предмета видами: отделить их от непомеченного стака. */
    private static void mark(ServerPlayer sp, Item item, int n, Set<String> species) {
        Inventory inv = sp.getInventory();
        ItemStack carried = sp.containerMenu.getCarried();
        if (carried.is(item) && !Source.marked(carried) && n > 0) {
            int k = Math.min(n, carried.getCount());
            if (k == carried.getCount()) {
                Source.add(carried, species);
            } else {
                carried.shrink(k);
                ItemStack part = new ItemStack(item, k);
                Source.add(part, species);
                if (!inv.add(part)) sp.drop(part, false);
            }
            n -= k;
        }
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item) || Source.marked(st)) continue;
            int k = Math.min(n, st.getCount());
            if (k == st.getCount()) {
                Source.add(st, species);
            } else {
                ItemStack part = st.split(k);
                Source.add(part, species);
                if (!inv.add(part)) sp.drop(part, false);
            }
            n -= k;
        }
        sp.containerMenu.broadcastChanges();
    }
}
