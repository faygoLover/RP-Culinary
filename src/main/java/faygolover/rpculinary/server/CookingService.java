package faygolover.rpculinary.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Cooking;
import faygolover.rpculinary.core.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Готовка (дизайн, §6.2). Моды готовки не сообщают «приготовлено» (котёл Farmer's Delight, кастрюли Kaleidoscope),
 * поэтому так: игрок поработал у плиты, котла, верстака (клик по блоку или открыл его окно) — и в ближайшие
 * секунды у него в инвентаре появились новые блюда (еда, чей состав посчитан по рецепту). Их он и приготовил:
 * блюдам ставится качество, повару — опыт «Кулинарии» (через RP Core). Верстак в инвентаре и печь сообщают о
 * результате сами — их считаем точно. Подпись повара — позже (дизайн, §6.2).
 */
public final class CookingService {
    private CookingService() {}

    /** NBT блюда: {@code rpculinary: {q: стадия, c: 1 — готовил Чистюля, x: 1 — экзотика без знания}}. */
    public static final String TAG = "rpculinary", Q = "q", CLEAN = "c", WILD = "x";
    /** Признаки метки ({@link #put}). */
    public static final int F_CLEAN = 1, F_WILD = 2;
    /** Моды готовки: их блоки и окна — «плита». */
    static final Set<String> COOKING_MODS = Set.of("farmersdelight", "kaleidoscope_cookery", "kaleidoscope_tavern", "brewery", "vinery",
            "candlelight", "farm_and_charm", "herbalbrews", "bakery", "meadow");
    /** Моды со своим качеством блюд (Kaleidoscope): наше не ставим — сдвигаем их (если умеем) и даём опыт. */
    static final Set<String> OWN_QUALITY = Set.of("kaleidoscope_cookery", "kaleidoscope_tavern");
    /** Ещё «плиты» — датапаком: блоки в теге {@code rpculinary:cooking_stations}. */
    static final TagKey<Block> STATIONS = BlockTags.create(new ResourceLocation(RpCulinary.MODID, "cooking_stations"));
    /** «Плиты» с огнём (тег {@code rpculinary:heat_stations}): жарка, варка, запекание — бывает недожарено и подгорело. */
    static final TagKey<Block> HEAT = BlockTags.create(new ResourceLocation(RpCulinary.MODID, "heat_stations"));
    /** Полуфабрикаты (тег {@code rpculinary:no_quality}): фарш, тесто, нарезка, сырая пицца, сыр — без качества и опыта. */
    static final TagKey<Item> NO_QUALITY = net.minecraft.tags.ItemTags.create(new ResourceLocation(RpCulinary.MODID, "no_quality"));

    /** Повторы: повар → блюдо → {сколько подряд, когда последнее (тик)}. Через 10 минут счёт обнуляется. */
    private static final Map<UUID, Map<Item, long[]>> REPEATS = new HashMap<>();
    private static final long REPEAT_RESET_TICKS = 20 * 60 * 10;
    private static final java.util.Random RANDOM = new java.util.Random();

    // ------------------------------------------------------------------ навык и метка

    /** Уровень «Кулинарии»: от RP Perks; без него — 10 (правило автора). */
    public static int level(Player p) {
        var lvl = RpCoreAPI.skill(p, RpIds.COOKING);
        return lvl.isPresent() ? lvl.getAsInt() : 10;
    }

    /** Качество блюда или −1 (не готовил игрок, или у мода своё качество). */
    public static int quality(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains(TAG)) return -1;
        CompoundTag c = t.getCompound(TAG);
        return c.contains(Q) ? c.getInt(Q) : -1;
    }

    /** Готовил Чистюля: блюдо реже травит. */
    public static boolean cleanCook(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.getCompound(TAG).getBoolean(CLEAN);
    }

    /** Готовили экзотику без знания группы: блюдо сверху ещё и травит. */
    public static boolean wildCook(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.getCompound(TAG).getBoolean(WILD);
    }

    static boolean stamped(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.contains(TAG);
    }

    /** Блюдо (не продукт): съедобное, состав посчитан по рецепту, не сырое и не полуфабрикат, ещё без метки. */
    public static boolean dish(ServerPlayer sp, ItemStack st) {
        return !st.isEmpty() && st.isEdible() && !stamped(st)
                && NutritionTable.source(sp.server, st.getItem()) == NutritionTable.Source.RECIPE
                && !NutritionTable.raw(sp.server, st.getItem()) && !st.is(NO_QUALITY);
    }

    // ------------------------------------------------------------------ «у плиты»

    static boolean station(Block b) {
        if (b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE || b == Blocks.CRAFTING_TABLE) return true;
        if (b.defaultBlockState().is(STATIONS) || b.defaultBlockState().is(HEAT)) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        return id != null && COOKING_MODS.contains(id.getNamespace());
    }

    /** Греет ли «плита»: печь, костёр, плиты и сковороды модов (тег), иначе — холодная сборка. */
    public static boolean heat(Block b) {
        return b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock || b instanceof net.minecraft.world.level.block.CampfireBlock
                || b.defaultBlockState().is(HEAT);
    }

    /** Костёр и коптильня — «в поле» (Полевой повар). */
    static boolean field(Block b) {
        return b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE || b == Blocks.SMOKER;
    }

    /**
     * Уровень, с которым блюдо готовится: навык, Кухонный кошмар, Полевой повар у костра. Ниже нуля бывает
     * (Кухонный кошмар у новичка) — тогда неудачи ещё чаще.
     */
    static double cookingLevel(ServerPlayer sp, NutritionData d) {
        Settings s = Settings.get();
        double l = level(sp);
        if (RpCoreAPI.hasTrait(sp, RpIds.KITCHEN_NIGHTMARE)) l -= s.kitchenNightmarePenalty;
        if (d != null && d.cookField && RpCoreAPI.hasTrait(sp, RpIds.FIELD_COOK)) l += s.fieldCookBonus;
        return Math.max(-5, Math.min(10, l));
    }

    /** Начать (или продлить) «готовку»: запомнить, сколько блюд уже было, чтобы новые узнать. */
    static void open(ServerPlayer sp, NutritionData d) {
        long now = sp.server.getTickCount();
        // Отсчёт — если его ещё нет (после верстака в инвентаре «готовка» уже идёт, а отсчёта нет).
        if (d.cookBaseline == null || (d.cookUntil < now && !d.cookMenu)) d.cookBaseline = counts(sp);
        d.cookUntil = Math.max(d.cookUntil, now + (long) (Settings.get().cookContextSeconds * 20));
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Settings.get().cookingEnabled) return;
        Block b = sp.level().getBlockState(e.getPos()).getBlock();
        if (!station(b)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        open(sp, d);
        d.cookField = field(b);
        d.cookHeat = heat(b);
    }

    public static void onContainerOpen(PlayerContainerEvent.Open e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Settings.get().cookingEnabled) return;
        var menu = e.getContainer();
        boolean cooking = menu instanceof AbstractFurnaceMenu || menu instanceof CraftingMenu;
        if (!cooking) {
            try {
                ResourceLocation id = ForgeRegistries.MENU_TYPES.getKey(menu.getType());
                cooking = id != null && COOKING_MODS.contains(id.getNamespace());
            } catch (UnsupportedOperationException ignored) {
                // Окно без типа (инвентарь игрока) — не плита.
            }
        }
        if (!cooking) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        open(sp, d);
        d.cookMenu = true;
        d.cookField = menu instanceof net.minecraft.world.inventory.SmokerMenu;
        // Окно мода — от блока, по которому кликнули (клик раньше окна).
        if (menu instanceof AbstractFurnaceMenu) d.cookHeat = true;
        else if (menu instanceof CraftingMenu) d.cookHeat = false;
    }

    public static void onContainerClose(PlayerContainerEvent.Close e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null || !d.cookMenu) return;
        d.cookMenu = false;
        d.cookUntil = Math.max(d.cookUntil, sp.server.getTickCount() + (long) (Settings.get().cookContextSeconds * 20));
    }

    /** Верстак (и в инвентаре) и печь сообщают сами: столько-то таких блюд пометить при следующей проверке. */
    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        pending(e.getEntity(), e.getCrafting(), false);
    }

    public static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        pending(e.getEntity(), e.getSmelting(), true);
    }

    private static void pending(Player p, ItemStack result, boolean heat) {
        if (!(p instanceof ServerPlayer sp) || !Settings.get().cookingEnabled || result.isEmpty()) return;
        if (!dish(sp, result)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        d.cookPending.merge(result.getItem(), result.getCount(), Integer::sum);
        if (heat) d.cookPendingHeat.add(result.getItem());
        d.cookUntil = Math.max(d.cookUntil, sp.server.getTickCount() + (long) (Settings.get().cookContextSeconds * 20));
    }

    // ------------------------------------------------------------------ проверка

    /** Раз в полсекунды, пока игрок «у плиты»: новые блюда — приготовленные им. */
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null || (!d.cookMenu && d.cookPending.isEmpty() && d.cookBaseline == null)) return;
        long now = sp.server.getTickCount();
        // Точные (верстак, печь): пометить столько, сколько сделано.
        if (!d.cookPending.isEmpty()) {
            for (var en : d.cookPending.entrySet()) {
                stamp(sp, en.getKey(), en.getValue(), d.cookPendingHeat.contains(en.getKey()));
                // У модов со своим качеством блюдо остаётся непомеченным — не засчитать его второй раз как «прибавилось».
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(en.getKey());
                if (d.cookBaseline != null && id != null && OWN_QUALITY.contains(id.getNamespace()))
                    d.cookBaseline.merge(en.getKey(), en.getValue(), Integer::sum);
            }
            d.cookPending.clear();
            d.cookPendingHeat.clear();
        }
        // «У плиты»: что прибавилось.
        if (d.cookBaseline != null) {
            Map<Item, Integer> cur = counts(sp);
            for (var en : cur.entrySet()) {
                int before = d.cookBaseline.getOrDefault(en.getKey(), 0);
                if (en.getValue() > before) stamp(sp, en.getKey(), en.getValue() - before, d.cookHeat);
            }
            // Отсчёт — от того, что есть сейчас: убывшее (съел, переложил) потом не считается новым.
            d.cookBaseline = counts(sp);
        }
        if (!d.cookMenu && now > d.cookUntil) d.cookBaseline = null;
    }

    /** Сколько каких непомеченных блюд у игрока (инвентарь и то, что держит курсором). */
    static Map<Item, Integer> counts(ServerPlayer sp) {
        Map<Item, Integer> out = new HashMap<>();
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (dish(sp, st)) out.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        ItemStack carried = sp.containerMenu.getCarried();
        if (dish(sp, carried)) out.merge(carried.getItem(), carried.getCount(), Integer::sum);
        return out;
    }

    /** Пометить {@code n} блюд качеством (по навыку); повару — опыт. У модов со своим качеством — только опыт. */
    static void stamp(ServerPlayer sp, Item item, int n, boolean heat) {
        if (n <= 0) return;
        if (Techniques.spoilPreserves(sp, item, n)) return;  // заготовка без умения «Заготовщик» не вышла
        Settings s = Settings.get();
        // Опыт — от настоящего уровня навыка, качество — с поправками перков.
        int level = level(sp);
        double cookLevel = cookingLevel(sp, NutritionData.get(sp));
        int complexity = NutritionTable.complexity(sp.server, item);
        // Экзотика без знания группы: заметно хуже и не выше «хорошего».
        boolean wild = Exotic.unknownMask(sp, NutritionTable.exoticMask(sp.server, item)) != 0;
        if (wild) cookLevel -= s.exoticCookPenalty;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && OWN_QUALITY.contains(id.getNamespace())) {
            if (id.getNamespace().equals("kaleidoscope_cookery")) shiftKaleidoscope(sp, item, n, cookLevel, complexity, heat, s);
            award(sp, item, level, complexity, n);
            return;
        }
        int[] byQuality = new int[Cooking.QUALITY.length];
        for (int i = 0; i < n; i++) {
            int q = Cooking.roll(cookLevel, complexity, RANDOM.nextGaussian(), RANDOM.nextDouble(), wild ? Cooking.GOOD : Cooking.MASTERPIECE, s);
            // Собрано без огня (салат, сэндвич): не подгорает и не недожаривается — худшее «обычное».
            if (!heat && !Cooking.success(q)) q = Cooking.NORMAL;
            byQuality[q]++;
        }
        int flags = (RpCoreAPI.hasTrait(sp, RpIds.CLEAN) ? F_CLEAN : 0) | (wild ? F_WILD : 0);
        int done = 0;
        for (int q = 0; q < byQuality.length; q++) {
            if (byQuality[q] == 0) continue;
            int marked = mark(sp, item, byQuality[q], q, flags);
            done += marked;
            if (marked > 0 && q == Cooking.MASTERPIECE)
                sp.displayClientMessage(Component.translatable("rpculinary.msg.masterpiece").withStyle(ChatFormatting.GOLD), true);
            else if (marked > 0 && !Cooking.success(q))
                sp.displayClientMessage(Component.translatable("rpculinary.msg." + Cooking.QUALITY[q]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
        if (done > 0) award(sp, item, level, complexity, done);
    }

    /** Качество Kaleidoscope в NBT предмета: 0 — превосходное, 1 — отличное, 2 — обычное, 3 — плохое. */
    static final String KC_QUALITY = "kaleidoscope_cookery:quality";

    /**
     * Kaleidoscope оценивает блюдо сам (ловкость при готовке); навык сдвигает его оценку на ступень: наш бросок
     * «отлично» и выше — на ступень лучше, неудача (недожарено, сгорело) — на ступень хуже.
     */
    private static void shiftKaleidoscope(ServerPlayer sp, Item item, int n, double cookLevel, int complexity, boolean heat, Settings s) {
        int up = 0, down = 0;
        for (int i = 0; i < n; i++) {
            int q = Cooking.roll(cookLevel, complexity, RANDOM.nextGaussian(), RANDOM.nextDouble(), s);
            if (q == Cooking.EXCELLENT || q == Cooking.MASTERPIECE) up++;
            else if (heat && !Cooking.success(q)) down++;
        }
        shiftKaleidoscope(sp, item, up, -1);
        shiftKaleidoscope(sp, item, down, +1);
    }

    /** Сдвинуть качество {@code k} штук (−1 — лучше, +1 — хуже). Стопку целиком — на месте, иначе отделить. */
    private static void shiftKaleidoscope(ServerPlayer sp, Item item, int k, int delta) {
        Inventory inv = sp.getInventory();
        java.util.List<ItemStack> sources = new java.util.ArrayList<>();
        sources.add(sp.containerMenu.getCarried());
        for (int i = 0; i < inv.getContainerSize(); i++) sources.add(inv.getItem(i));
        for (ItemStack st : sources) {
            if (k <= 0) return;
            if (!st.is(item) || stamped(st) || st.getTag() == null || !st.getTag().contains(KC_QUALITY)) continue;
            int q = st.getTag().getInt(KC_QUALITY);
            int nq = Math.max(0, Math.min(3, q + delta));
            if (nq == q) continue;
            int take = Math.min(k, st.getCount());
            if (take == st.getCount()) {
                st.getTag().putInt(KC_QUALITY, nq);
            } else {
                st.shrink(take);
                ItemStack out = new ItemStack(item, take);
                out.setTag(st.getTag().copy());
                out.getTag().putInt(KC_QUALITY, nq);
                if (!sp.getInventory().add(out)) sp.drop(out, false);
            }
            k -= take;
        }
    }

    /**
     * Пометить качеством {@code n} непомеченных штук: целую стопку — на месте (в курсоре, в слоте), от большей
     * отделить нужное и положить в инвентарь. Возвращает, сколько помечено.
     */
    private static int mark(ServerPlayer sp, Item item, int n, int quality, int flags) {
        int left = n;
        ItemStack carried = sp.containerMenu.getCarried();
        left -= markIn(sp, carried, item, left, quality, flags);
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) left -= markIn(sp, inv.getItem(i), item, left, quality, flags);
        return n - left;
    }

    private static int markIn(ServerPlayer sp, ItemStack st, Item item, int need, int quality, int flags) {
        if (need <= 0 || !st.is(item) || stamped(st)) return 0;
        if (st.getCount() <= need) {
            put(st, quality, flags);
            return st.getCount();
        }
        st.shrink(need);
        ItemStack out = new ItemStack(item, need);
        put(out, quality, flags);
        if (!sp.getInventory().add(out)) sp.drop(out, false);
        return need;
    }

    /** Поставить метку: стадия и признаки ({@link #F_CLEAN} — готовил Чистюля, {@link #F_WILD} — экзотика без знания). */
    public static void put(ItemStack st, int quality, int flags) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(Q, quality);
        if ((flags & F_CLEAN) != 0) tag.putBoolean(CLEAN, true);
        if ((flags & F_WILD) != 0) tag.putBoolean(WILD, true);
        st.getOrCreateTag().put(TAG, tag);
    }

    private static void award(ServerPlayer sp, Item item, int level, int complexity, int count) {
        long now = sp.level().getGameTime();
        Map<Item, long[]> mine = REPEATS.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        // Старые блюда (счёт уже обнулился бы) не копим, пока повар в сети.
        mine.values().removeIf(r -> now - r[1] > REPEAT_RESET_TICKS || now < r[1]);
        long[] rep = mine.computeIfAbsent(item, k -> new long[]{0, now});
        if (now - rep[1] > REPEAT_RESET_TICKS) rep[0] = 0;
        double xp = Cooking.xp(level, complexity, count, (int) rep[0]);
        rep[0]++;
        rep[1] = now;
        if (xp > 0) RpCoreAPI.practice(sp, RpIds.COOKING, xp);
    }

    public static void forget(PlayerEvent.PlayerLoggedOutEvent e) {
        REPEATS.remove(e.getEntity().getUUID());
    }

    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
        REPEATS.clear();
    }
}
