package faygolover.rpculinary.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.core.Source;
import faygolover.rpculinary.core.Weights;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Переработка мяса по одной части (решения Alex 29.10): вид мяса ({@link Source}) переходит на всё, что из него вышло.
 * <ul>
 *   <li><b>Доска FD.</b> Кусок лежит на доске; каждый удар ножом отрезает одну часть. Крупный кусок с весом в NBT — порцию
 *   (вес куска уменьшается), под конец — кость (если кусок на кости) и обрезь. Цельное и стейки — по плану
 *   {@code assets/rpculinary/board.json} (из рецептов доски), по одной части за удар.</li>
 *   <li><b>Мясорубка F&C.</b> Внутрь кладётся один кусок (пока не перемолот — второй нельзя); один оборот ручки — одна порция
 *   фарша 250 г (при неудаче — обрезки), остаток — обрезками, мелкая кость — в конце костной мукой. Кусок на крупной кости
 *   не берёт: сначала разделать на доске. Shift+ПКМ пустой рукой — забрать остаток.</li>
 * </ul>
 * Потери по навыку и опыт — как у разделки ({@link Butchering}); порог доски на 1 ниже вида.
 */
public final class Processing {
    private Processing() {}

    static final ResourceLocation BOARD = new ResourceLocation("farmersdelight", "cutting_board"),
            MINCER = new ResourceLocation("farm_and_charm", "mincer");
    private static final TagKey<Item> KNIVES = ItemTags.create(new ResourceLocation("forge", "tools/knives"));
    static final Set<String> NO_LOSS = Set.of("minecraft:bone", "minecraft:bone_meal", "butchery:meat_scraps",
            "butchery:animal_fat", "butchery:hoof", "butchery:bird_foot", "butchery:wishbone");
    private static final String STEP = "rpculinary:cut_step", MINCER_KEY = "rpculinary:mincer";
    /** Кость не крупная: стейк на косточке можно в мясорубку (в конце — костная мука). */
    private static final Set<String> SMALL_BONE = Set.of("butchery:raw_tbone_steak");
    private static final int PORTION = 250;
    /** Цвет фарша по виду (решение 29.10 §6): тёмно-красный, розовый; остальное — красный. */
    private static final Set<String> DARK = Set.of("cow", "horse", "moose", "kangaroo"),
            PINK = Set.of("pig", "chicken", "rabbit", "fox", "cat", "ocelot", "fish", "frog", "bat", "axolotl", "human", "villager",
                    "pillager", "vindicator", "evoker", "witch", "piglin");

    record Unit(String item, double chance) {}

    private static Map<String, List<Unit>> plans;

    static Map<String, List<Unit>> plans() {
        if (plans != null) return plans;
        Map<String, List<Unit>> m = new HashMap<>();
        try (InputStream in = Processing.class.getResourceAsStream("/assets/rpculinary/board.json")) {
            if (in != null) {
                JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("plans");
                for (var e : all.entrySet()) {
                    List<Unit> units = new ArrayList<>();
                    for (var r : e.getValue().getAsJsonArray()) {
                        var a = r.getAsJsonArray();
                        for (int i = 0; i < a.get(1).getAsInt(); i++) units.add(new Unit(a.get(0).getAsString(), a.get(2).getAsDouble()));
                    }
                    m.put(e.getKey(), List.copyOf(units));
                }
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: не прочитан board.json", ex);
        }
        plans = m;
        return m;
    }

    static String id(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it == null ? Items.AIR : it;
    }

    /** Кусок на крупной кости (в плане доски есть кость, и это не стейк на косточке). */
    static boolean bigBone(ItemStack st) {
        String id = id(st.getItem());
        if (SMALL_BONE.contains(id)) return false;
        List<Unit> plan = plans().get(id);
        return plan != null && plan.stream().anyMatch(u -> u.item().equals("minecraft:bone") && u.chance() >= 0.5);
    }

    static boolean meat(ItemStack st) {
        return !st.isEmpty() && !NO_LOSS.contains(id(st.getItem())) && (Butchering.meatLevel(st) >= 0 || !Source.list(st).isEmpty());
    }

    // ------------------------------------------------------------------ доска

    /** Удар ножом по доске с мясом — одна часть (зовёт {@link Butchering#onRightClick}, раньше FD). */
    static void onBoard(PlayerInteractEvent.RightClickBlock e, ServerPlayer sp, BlockPos pos) {
        ItemStack tool = sp.getMainHandItem();
        if (!tool.is(KNIVES) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        var cap = be == null ? null : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (!(cap instanceof IItemHandlerModifiable handler) || handler.getSlots() < 1) return;
        ItemStack stored = handler.getStackInSlot(0);
        int lvl = stored.isEmpty() ? -1 : Butchering.meatLevel(stored);
        if (lvl < 0 && !meat(stored)) return;  // не мясо — доска FD как обычно
        List<Unit> plan = plans().get(id(stored.getItem()));
        int grams = Weights.nbtGrams(stored);
        if (grams <= 0 && plan == null) return;
        Settings s = Settings.get();
        int threshold = Math.max(1, (lvl < 0 ? 1 : lvl) - 1);
        int skill = CookingService.level(sp);
        double loss = Butchering.loss(threshold, skill, s);
        List<ItemStack> out = new ArrayList<>();
        boolean done;
        int cutGrams;
        if (grams > 0) {  // крупный кусок: порция за удар
            ItemStack portion = portionOf(level, stored, plan);
            int g = Weights.nominal(portion.getItem()) > 0 ? Weights.nominal(portion.getItem()) : PORTION;
            if (grams >= g) {
                out.add(portion);
                grams -= g;
                cutGrams = g;
            } else {
                cutGrams = 0;
            }
            done = grams < g;
            if (done) {  // остаток: кость (если на кости), обрезь — обрезками; навык выше порога — шанс ещё порции
                if (bigBone(stored)) out.add(new ItemStack(Items.BONE));
                if (grams >= PORTION / 2) out.add(new ItemStack(item("butchery:meat_scraps"), Math.max(1, grams / PORTION)));
                if (skill > threshold && sp.getRandom().nextDouble() < s.boardBonusChance) out.add(portion.copy());
            } else {
                ItemStack rest = stored.copy();
                rest.getOrCreateTag().putInt(Weights.TAG_WEIGHT, grams);
                handler.setStackInSlot(0, rest);
            }
        } else {  // по плану: следующая часть
            int step = stored.getTag() != null ? stored.getTag().getInt(STEP) : 0;
            Unit u = plan.get(Math.min(step, plan.size() - 1));
            if (sp.getRandom().nextDouble() < u.chance()) out.add(new ItemStack(item(u.item())));
            cutGrams = Weights.nominal(item(u.item()));
            done = step + 1 >= plan.size();
            if (!done) {
                ItemStack rest = stored.copy();
                rest.getOrCreateTag().putInt(STEP, step + 1);
                handler.setStackInSlot(0, rest);
            }
        }
        if (done) {
            ItemStack rest = stored.copy();
            rest.shrink(1);
            if (rest.getTag() != null) rest.getTag().remove(STEP);
            handler.setStackInSlot(0, rest);
        }
        double kept = 0, lost = 0;
        for (ItemStack st : out) {
            String sid = id(st.getItem());
            if (!NO_LOSS.contains(sid) && sp.getRandom().nextDouble() < loss) {  // неумелый удар — в обрезки
                lost += Math.max(1, Weights.grams(st));
                st = new ItemStack(item("butchery:meat_scraps"), Math.max(1, Weights.grams(st) / PORTION));
            } else {
                kept += Weights.grams(st);
            }
            if (!sid.equals("minecraft:bone") && !sid.equals("minecraft:bone_meal")) Source.copy(stored, st);
            spawn(level, pos, st);
        }
        be.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        tool.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        sp.swing(InteractionHand.MAIN_HAND, true);
        SoundEvent knife = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("farmersdelight", "block.cutting_board.knife"));
        level.playSound(null, pos, knife != null ? knife : SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1f);
        if (lost > 0) sp.displayClientMessage(Component.translatable("rpculinary.msg.butcher_loss").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        else if (!done && grams > 0) sp.displayClientMessage(Component.translatable("rpculinary.msg.board_left", Weights.format(grams)).withStyle(ChatFormatting.GRAY), true);
        practice(sp, threshold, skill, (kept + lost + cutGrams * 0) / 1000.0);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Порция крупного куска: первый результат плана доски, иначе тот же предмет без веса в NBT. */
    static ItemStack portionOf(ServerLevel level, ItemStack piece, List<Unit> plan) {
        if (plan != null) for (Unit u : plan) if (!NO_LOSS.contains(u.item())) return new ItemStack(item(u.item()));
        return new ItemStack(piece.getItem());
    }

    // ------------------------------------------------------------------ мясорубка F&C

    /** Не мелются: кишки (на колбаски), желудок (сыр), кровь, глаза, копыта, лапки, жир. */
    private static final Set<String> NOT_MINCE = Set.of("butchery:intestines", "butchery:stomach", "butchery:bottle_of_blood",
            "butchery:blood_bucket", "butchery:eye", "butchery:hoof", "butchery:bird_foot", "butchery:animal_fat", "butchery:wishbone");
    /** Субпродукты — в ливерный фарш. */
    private static final Set<String> OFFAL = Set.of("butchery:liver", "butchery:heart", "butchery:kidney", "butchery:lungs", "butchery:brain");
    private static final String SCRAPS_ID = "butchery:meat_scraps";
    private static final int MAX_SCRAPS = 32;

    /**
     * Мясорубка F&C с мясом (решения Alex 29.10). Внутри — одна загрузка:
     * <ul>
     *   <li>крупный кусок (вес в NBT или не стакается) — один, досыпать нельзя;</li>
     *   <li>мелкое одного вида (порции, стейки) — стопкой, досыпать можно только то же самое;</li>
     *   <li>обрезки — стопкой, можно разных видов (это единственная смесь в мясорубке): 2 обрезка → 1 фарш, жирнее; разные виды —
     *   домашний.</li>
     * </ul>
     * Оборот ручки (ПКМ пустой рукой) — одна порция фарша 250 г; при неудаче — обрезки. Субпродукты — ливерный фарш.
     * Кусок на крупной кости не берёт. Shift+ПКМ пустой рукой — забрать загрузку. Не мясо — мясорубка как в F&C.
     */
    public static void onMincer(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = e.getPos();
        BlockState bs = level.getBlockState(pos);
        if (!MINCER.equals(ForgeRegistries.BLOCKS.getKey(bs.getBlock()))) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        CompoundTag pd = be.getPersistentData();
        CompoundTag cur = pd.getCompound(MINCER_KEY);
        ItemStack held = sp.getMainHandItem();
        boolean heldMeat = !held.isEmpty() && mincable(held);
        if (cur.isEmpty()) {
            if (!heldMeat) {
                if (!held.isEmpty() && meat(held) && bigBone(held))
                    sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_bone").withStyle(ChatFormatting.GRAY), true);
                else return;  // не мясо — как в F&C
            } else {
                pd.put(MINCER_KEY, load(held, sp.isCreative()));
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_in", held.getHoverName(), Weights.format(Math.max(0, gramsIn(pd.getCompound(MINCER_KEY))))).withStyle(ChatFormatting.GRAY), true);
                if (!sp.isCreative()) held.setCount(held.getCount() - unitsTaken(pd.getCompound(MINCER_KEY), held));
            }
        } else if (sp.isShiftKeyDown() && held.isEmpty()) {
            for (ItemStack st : unload(cur)) if (!sp.getInventory().add(st)) sp.drop(st, false);
            pd.remove(MINCER_KEY);
        } else if (!held.isEmpty()) {
            if (!topUp(cur, held, sp.isCreative()))
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_busy").withStyle(ChatFormatting.GRAY), true);
            else pd.put(MINCER_KEY, cur);
        } else {
            crank(level, pos, bs, sp, pd, cur);
        }
        be.setChanged();
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    static boolean mincable(ItemStack st) {
        String id = id(st.getItem());
        return !NOT_MINCE.contains(id) && (id.equals(SCRAPS_ID) || meat(st) && !bigBone(st));
    }

    /** Загрузка: {scraps: [обрезки по штуке]} или {piece: образец, n: штук, g: граммов осталось, piece_only, bone}. */
    private static CompoundTag load(ItemStack held, boolean creative) {
        CompoundTag t = new CompoundTag();
        if (id(held.getItem()).equals(SCRAPS_ID)) {
            net.minecraft.nbt.ListTag q = new net.minecraft.nbt.ListTag();
            int n = Math.min(MAX_SCRAPS, held.getCount());
            for (int i = 0; i < n; i++) q.add(held.copyWithCount(1).save(new CompoundTag()));
            t.put("scraps", q);
            t.putInt("taken", n);
            return t;
        }
        boolean single = Weights.nbtGrams(held) > 0 || held.getMaxStackSize() == 1;
        int n = single ? 1 : held.getCount();
        t.put("piece", held.copyWithCount(1).save(new CompoundTag()));
        t.putInt("g", Math.max(1, Weights.grams(held)) * n);
        t.putBoolean("single", single);
        t.putBoolean("bone", SMALL_BONE.contains(id(held.getItem())));
        t.putInt("taken", n);
        return t;
    }

    private static int unitsTaken(CompoundTag t, ItemStack held) {
        return Math.min(held.getCount(), t.getInt("taken"));
    }

    private static int gramsIn(CompoundTag t) {
        if (t.contains("scraps")) return t.getList("scraps", 10).size() * PORTION;
        return t.getInt("g");
    }

    /** Досыпать: обрезки — любые (до 32); мелкое — только то же самое; крупный кусок — нельзя. */
    private static boolean topUp(CompoundTag t, ItemStack held, boolean creative) {
        if (t.contains("scraps")) {
            if (!id(held.getItem()).equals(SCRAPS_ID)) return false;
            net.minecraft.nbt.ListTag q = t.getList("scraps", 10);
            int n = Math.min(held.getCount(), MAX_SCRAPS - q.size());
            if (n <= 0) return false;
            for (int i = 0; i < n; i++) q.add(held.copyWithCount(1).save(new CompoundTag()));
            t.put("scraps", q);
            if (!creative) held.shrink(n);
            return true;
        }
        ItemStack piece = ItemStack.of(t.getCompound("piece"));
        if (t.getBoolean("single") || !ItemStack.isSameItemSameTags(piece, held.copyWithCount(1))) return false;
        t.putInt("g", t.getInt("g") + Math.max(1, Weights.grams(held)) * held.getCount());
        if (!creative) held.setCount(0);
        return true;
    }

    /** Забрать загрузку (Shift+ПКМ): обрезки — стопкой, мелкое — штуками, остаток крупного — куском с весом. */
    private static List<ItemStack> unload(CompoundTag t) {
        List<ItemStack> out = new ArrayList<>();
        if (t.contains("scraps")) {
            for (var tag : t.getList("scraps", 10)) out.add(ItemStack.of((CompoundTag) tag));
            return out;
        }
        ItemStack piece = ItemStack.of(t.getCompound("piece"));
        int unit = Math.max(1, Weights.grams(piece)), g = t.getInt("g");
        if (t.getBoolean("single")) {
            if (g < unit) piece.getOrCreateTag().putInt(Weights.TAG_WEIGHT, g);
            out.add(piece);
        } else {
            int n = g / unit;
            if (n > 0) out.add(piece.copyWithCount(n));
        }
        return out;
    }

    /** Оборот ручки: одна порция фарша (или обрезки). */
    private static void crank(ServerLevel level, BlockPos pos, BlockState bs, ServerPlayer sp, CompoundTag pd, CompoundTag cur) {
        int skill = CookingService.level(sp);
        ItemStack out = ItemStack.EMPTY;
        boolean done;
        if (cur.contains("scraps")) {  // 2 обрезка → 1 фарш, жирнее; разные виды — домашний
            net.minecraft.nbt.ListTag q = cur.getList("scraps", 10);
            if (q.size() < 2) {
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_more_scraps").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            ItemStack a = ItemStack.of(q.getCompound(0)), b = ItemStack.of(q.getCompound(1));
            q.remove(0);
            q.remove(0);
            out = new ItemStack(minceFor(level, a, lookOf(a)));
            Source.setParts(out, Source.mix(List.of(a, b)), lookOf(a));
            out.getOrCreateTag().putBoolean(faygolover.rpculinary.core.MeatFood.FATTY, true);
            cur.put("scraps", q);
            done = q.size() < 2 && q.isEmpty();
        } else {
            ItemStack piece = ItemStack.of(cur.getCompound("piece"));
            int g = cur.getInt("g");
            boolean offal = OFFAL.contains(id(piece.getItem()));
            if (g >= PORTION) {
                boolean fail = level.getRandom().nextDouble() < Butchering.mincerLoss(skill);
                out = fail ? new ItemStack(item(SCRAPS_ID)) : new ItemStack(offal ? item("butchery:raw_beef_mince") : minceFor(level, piece, lookOf(piece)));
                if (offal && !fail) out.getOrCreateTag().putBoolean(faygolover.rpculinary.core.MeatFood.OFFAL_FORM, true);
                g -= PORTION;
            } else {
                if (g >= PORTION / 2) out = new ItemStack(item(SCRAPS_ID));
                g = 0;
            }
            if (!out.isEmpty()) Source.copy(piece, out);
            cur.putInt("g", g);
            done = g < PORTION / 2;
            if (done && g > 0) cur.putInt("g", 0);
            if (done && cur.getBoolean("bone")) spawnFront(level, pos, bs, new ItemStack(Items.BONE_MEAL));
        }
        if (!out.isEmpty()) spawnFront(level, pos, bs, out);
        if (done) {
            if (cur.contains("scraps") && !cur.getList("scraps", 10).isEmpty()) pd.put(MINCER_KEY, cur);  // один обрезок остался
            else pd.remove(MINCER_KEY);
        } else {
            pd.put(MINCER_KEY, cur);
            sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_left", Weights.format(gramsIn(cur))).withStyle(ChatFormatting.GRAY), true);
        }
        if (bs.getBlock().getStateDefinition().getProperty("crank") instanceof IntegerProperty crankProp)
            level.setBlock(pos, bs.setValue(crankProp, Math.min(10, crankProp.getPossibleValues().stream().max(Integer::compare).orElse(10))), 3);
        SoundEvent snd = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("farm_and_charm", "mincer_cranking"));
        level.playSound(null, pos, snd != null ? snd : SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 1.5f);
        practice(sp, 1, skill, PORTION / 1000.0 * 0.5);
    }

    static String lookOf(ItemStack st) {
        String l = Source.look(st);
        return l != null ? l : Source.implied(st);
    }

    /** Фарш по виду: тёмно-красный, розовый, остальное — красный; вид неизвестен — по рецепту мясорубки F&C. */
    static Item minceFor(ServerLevel level, ItemStack piece, String species) {
        if (species != null && !species.isEmpty())
            return item(DARK.contains(species) ? "farm_and_charm:minced_beef" : PINK.contains(species) ? "butchery:raw_lamb_mince" : "butchery:raw_beef_mince");
        ItemStack probe = new ItemStack(piece.getItem());
        for (var r : level.getRecipeManager().getRecipes()) {
            if (!String.valueOf(r.getId()).startsWith("rpculinary:mincer/") || r.getIngredients().isEmpty() || !r.getIngredients().get(0).test(probe)) continue;
            ItemStack res = r.getResultItem(level.registryAccess());
            if (!res.isEmpty()) return res.getItem();
        }
        return item("butchery:raw_beef_mince");
    }

    // ------------------------------------------------------------------ миска F&C: домашний фарш

    static final ResourceLocation BOWL = new ResourceLocation("farm_and_charm", "crafting_bowl");
    private static final TagKey<Item> MINCE = ItemTags.create(new ResourceLocation("rpculinary", "mince"));
    /** Миски, где мешают фарш: место → {уровень, позиция, что лежало (фарши по штуке), до какого тика смотреть}. */
    private static final Map<String, Object[]> BOWLS = new HashMap<>();

    /** Клик по миске — запомнить, какие фарши в ней лежат (после перемешивания миска их уже съела). */
    public static void onBowlClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !BOWL.equals(ForgeRegistries.BLOCKS.getKey(level.getBlockState(e.getPos()).getBlock()))) return;
        if (!(level.getBlockEntity(e.getPos()) instanceof net.minecraft.world.Container c) || c.getContainerSize() < 5) return;
        List<ItemStack> minces = new ArrayList<>();
        for (int i = 0; i < 4 && minces.size() < 2; i++) {  // рецепт берёт два фарша: по порядку слотов, из одного слота — и два
            ItemStack st = c.getItem(i);
            for (int k = 0; st.is(MINCE) && k < st.getCount() && minces.size() < 2; k++) minces.add(st.copyWithCount(1));
        }
        String k = level.dimension().location() + "|" + e.getPos().asLong();
        if (minces.size() >= 2) BOWLS.put(k, new Object[]{level, e.getPos().immutable(), minces, level.getServer().getTickCount() + 20 * 60});
    }

    /** Раз в секунду: в выходе миски фарш без состава — домашний: предмет и облик первого фарша, доли — по смеси. */
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || BOWLS.isEmpty()) return;
        var it = BOWLS.values().iterator();
        while (it.hasNext()) {
            Object[] w = it.next();
            ServerLevel level = (ServerLevel) w[0];
            if (level.getServer().getTickCount() % 10 != 0) return;
            if (level.getServer().getTickCount() > (int) w[3]) {
                it.remove();
                continue;
            }
            if (!(level.getBlockEntity((BlockPos) w[1]) instanceof net.minecraft.world.Container c) || c.getContainerSize() < 5) {
                it.remove();
                continue;
            }
            ItemStack out = c.getItem(4);
            if (out.isEmpty() || !out.is(MINCE) || Source.marked(out)) continue;
            @SuppressWarnings("unchecked") List<ItemStack> minces = (List<ItemStack>) w[2];
            ItemStack first = minces.get(0);
            ItemStack res = new ItemStack(first.getItem(), out.getCount());
            Source.setParts(res, Source.mix(minces), lookOf(first));
            if (minces.stream().allMatch(m -> m.getTag() != null && m.getTag().getBoolean(faygolover.rpculinary.core.MeatFood.FATTY)))
                res.getOrCreateTag().putBoolean(faygolover.rpculinary.core.MeatFood.FATTY, true);
            c.setItem(4, res);
            c.setChanged();
            it.remove();
        }
    }

    // ------------------------------------------------------------------ общее

    static void spawn(ServerLevel level, BlockPos pos, ItemStack st) {
        if (st.isEmpty()) return;
        ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, st);
        ie.setPickUpDelay(10);
        level.addFreshEntity(ie);
    }

    private static void spawnFront(ServerLevel level, BlockPos pos, BlockState bs, ItemStack st) {
        Direction d = bs.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? bs.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getClockWise() : Direction.NORTH;
        Vec3 at = Vec3.atCenterOf(pos).relative(d, 0.7);
        ItemEntity ie = new ItemEntity(level, at.x, at.y, at.z, st);
        ie.setDeltaMovement(d.getStepX() * 0.1, 0.05, d.getStepZ() * 0.1);
        ie.setPickUpDelay(5);
        level.addFreshEntity(ie);
    }

    private static void practice(ServerPlayer sp, int threshold, int skill, double kg) {
        double xp = Butchering.xp(threshold, skill, kg) * Settings.get().butcherXp;
        if (xp > 0) RpCoreAPI.practice(sp, RpIds.COOKING, xp);
    }
}
