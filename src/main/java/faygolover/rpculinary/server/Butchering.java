package faygolover.rpculinary.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import faygolover.rpculinary.core.Weights;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Разделка туш Butchery (блок 3 переделки, решения Alex 28–29.10): доп. разрезы между «родными» стадиями, потери в
 * обрезки по навыку «Кулинария», опыт за разрез и вес туш в инвентаре. Таблица {@code assets/rpculinary/carcasses.json}
 * (генерирует {@code tools/gen_food_pack.py}): блок туши → вид, порог навыка 1–10, вес (кг), доп. разрезы по стадии.
 * <p>
 * Butchery режет в своём обработчике ПКМ (стадия — свойство блока {@code blockstate}). Мы встаём раньше: на стадии с
 * доп. разрезами сначала выдаём свои таблицы (счётчик — в данных блок-сущности туши) и отменяем клик; «родной» разрез —
 * последним, его делает Butchery. На время любого разреза открыт {@link Cut}: функция {@link SkillYield} в таблицах
 * знает, кто режет, а после клика остаток (обрезки, опыт) раздаём здесь.
 */
public final class Butchering {
    private Butchering() {}

    private static final TagKey<Item> CLEAVER = tag("forge:cleaver"), C_CLEAVER = tag("c:cleaver"),
            KNIFE = tag("forge:skinning_knives"), C_KNIFE = tag("c:skinning_knives");
    private static final String CUTS = "rpculinary:cuts", CUT_STAGE = "rpculinary:cut_stage";
    private static final UUID SLOW_ID = UUID.fromString("5b0c2f6e-8a1d-4f53-9a77-3c1d2e4b7a10");
    /** Обрезки — общий отход разделки. */
    private static final ResourceLocation SCRAPS = new ResourceLocation("butchery", "meat_scraps");

    private static TagKey<Item> tag(String id) {
        return ItemTags.create(new ResourceLocation(id));
    }

    record Carcass(String species, int level, double kg, Map<Integer, List<String>> extra) {}

    /** Идущий разрез туши или нарезка на доске: кто, где, порог; сколько граммов ценного осталось и потеряно. */
    public static final class Cut {
        final ServerPlayer player;
        final BlockPos pos;
        final int threshold, skill;
        final boolean board;
        double keptGrams, lostGrams;

        Cut(ServerPlayer player, BlockPos pos, int threshold, boolean board) {
            this.player = player;
            this.pos = pos;
            this.threshold = threshold;
            this.board = board;
            this.skill = CookingService.level(player);
        }

        /** Доля ценных частей, что уходит в потери: на 2+ уровня ниже порога, на 1 ниже, на пороге; выше — 0. */
        double lossShare() {
            return loss(threshold, skill, Settings.get());
        }
    }

    public static double loss(int threshold, int skill, Settings s) {
        int behind = threshold - skill;
        return behind >= 2 ? s.butcherLossFar : behind == 1 ? s.butcherLossNear : behind == 0 ? s.butcherLossAt : 0;
    }

    private static final ThreadLocal<Cut> CURRENT = new ThreadLocal<>();

    static Cut current() {
        return CURRENT.get();
    }

    // ------------------------------------------------------------------ таблица туш

    private static Map<String, Carcass> blocks;
    /** Порог нарезки по предмету (самый простой вид, с чьей туши он падает) и по виду из NBT {@code rpculinary:source}. */
    private static final Map<String, Integer> ITEM_LEVEL = new HashMap<>(), SPECIES_LEVEL = new HashMap<>();
    private static final Map<Item, Carcass> BY_ITEM = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Carcass NONE = new Carcass("", 0, 0, Map.of());

    static Map<String, Carcass> blocks() {
        if (blocks != null) return blocks;
        Map<String, Carcass> m = new HashMap<>();
        try (InputStream in = Butchering.class.getResourceAsStream("/assets/rpculinary/carcasses.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject all = root.getAsJsonObject("blocks");
                if (root.has("items")) for (var e : root.getAsJsonObject("items").entrySet()) ITEM_LEVEL.put(e.getKey(), e.getValue().getAsInt());
                if (root.has("species")) for (var e : root.getAsJsonObject("species").entrySet()) SPECIES_LEVEL.put(e.getKey(), e.getValue().getAsInt());
                for (var e : all.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Map<Integer, List<String>> extra = new HashMap<>();
                    if (o.has("extra")) for (var x : o.getAsJsonObject("extra").entrySet()) {
                        List<String> tables = new java.util.ArrayList<>();
                        x.getValue().getAsJsonArray().forEach(t -> tables.add(t.getAsString()));
                        extra.put(Integer.parseInt(x.getKey()), List.copyOf(tables));
                    }
                    m.put(e.getKey(), new Carcass(o.get("species").getAsString(), o.get("level").getAsInt(), o.get("kg").getAsDouble(), Map.copyOf(extra)));
                }
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: не прочитан carcasses.json", ex);
        }
        blocks = m;
        return m;
    }

    static Carcass of(Block b) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        return id == null ? null : blocks().get(id.toString());
    }

    /** Туша как предмет в инвентаре (блок-предмет Butchery) или null. */
    static Carcass of(Item item) {
        Carcass c = BY_ITEM.get(item);
        if (c == null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) return null;
            c = blocks().getOrDefault(id.toString(), NONE);
            BY_ITEM.put(item, c);
        }
        return c == NONE ? null : c;
    }

    /** Мясо для нарезки: уровень вида (из NBT, иначе по предмету) или -1. */
    static int meatLevel(ItemStack st) {
        blocks();
        Integer bySource = SPECIES_LEVEL.get(Weights.source(st));
        if (bySource != null) return bySource;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        Integer byItem = id == null ? null : ITEM_LEVEL.get(id.toString());
        return byItem != null ? byItem : -1;
    }

    // ------------------------------------------------------------------ разрез

    /** Раньше Butchery и доски FD: доп. разрез или крупный кусок на доске (отменяет клик), иначе открыть {@link Cut}. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp) || !Settings.get().butcherSkill) return;
        BlockPos pos = e.getPos();
        BlockState bs = e.getLevel().getBlockState(pos);
        if (BOARD.equals(ForgeRegistries.BLOCKS.getKey(bs.getBlock()))) {
            onBoard(e, sp, pos);
            return;
        }
        Carcass c = of(bs.getBlock());
        if (c == null) return;
        ItemStack tool = sp.getMainHandItem();
        boolean cleaver = tool.is(CLEAVER) || tool.is(C_CLEAVER);
        if (!cleaver && !tool.is(KNIFE) && !tool.is(C_KNIFE)) return;
        Cut cut = new Cut(sp, pos, c.level(), false);
        List<String> extra = cleaver ? c.extra().get(stage(bs)) : null;
        BlockEntity be = e.getLevel().getBlockEntity(pos);
        if (extra != null && be != null && be.getPersistentData().getBoolean("isDrained") && e.getLevel() instanceof ServerLevel level) {
            CompoundTag pd = be.getPersistentData();
            int stage = stage(bs);
            int done = pd.getInt(CUT_STAGE) == stage + 1 ? pd.getInt(CUTS) : 0;
            if (done < extra.size()) {
                CURRENT.set(cut);
                try {
                    drop(level, pos, new ResourceLocation(extra.get(done)));
                } finally {
                    CURRENT.remove();
                }
                pd.putInt(CUT_STAGE, stage + 1);
                pd.putInt(CUTS, done + 1);
                be.setChanged();
                tool.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                sp.swing(InteractionHand.MAIN_HAND, true);
                level.levelEvent(2001, pos, Block.getId(bs));
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.NEUTRAL, 0.3f, 1f);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_STEP, SoundSource.NEUTRAL, 0.3f, 1f);
                finish(cut);
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }
        CURRENT.set(cut);
    }

    /** После всех (и отменённых): закрыть «родной» разрез. */
    public static void afterRightClick(PlayerInteractEvent.RightClickBlock e) {
        Cut cut = CURRENT.get();
        if (cut == null) return;
        CURRENT.remove();
        finish(cut);
    }

    // ------------------------------------------------------------------ доска FD (блок 4): нарезка по весу

    private static final ResourceLocation BOARD = new ResourceLocation("farmersdelight", "cutting_board");
    private static final TagKey<Item> KNIVES = tag("forge:tools/knives");
    /** Не теряются при нарезке. */
    private static final java.util.Set<String> NO_LOSS = java.util.Set.of("minecraft:bone", "minecraft:bone_meal", "butchery:meat_scraps",
            "butchery:animal_fat", "butchery:hoof", "butchery:bird_foot", "butchery:wishbone");

    private static void onBoard(PlayerInteractEvent.RightClickBlock e, ServerPlayer sp, BlockPos pos) {
        ItemStack tool = sp.getMainHandItem();
        if (!tool.is(KNIVES) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockEntity be = level.getBlockEntity(pos);
        var handler = be == null ? null : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null || handler.getSlots() < 1) return;
        ItemStack stored = handler.getStackInSlot(0);
        int lvl = stored.isEmpty() ? -1 : meatLevel(stored);
        if (lvl < 0) return;
        Cut cut = new Cut(sp, pos, Math.max(1, lvl - 1), true);  // доска — на 1 ниже туши (решение 29.10 §7)
        int grams = Weights.nbtGrams(stored);
        if (grams <= 0) {  // обычная нарезка FD: её результат проверит onItemJoin
            CURRENT.set(cut);
            return;
        }
        ItemStack portion = portionOf(level, stored);
        int g = Weights.nominal(portion.getItem()) > 0 ? Weights.nominal(portion.getItem()) : 250;
        Settings s = Settings.get();
        int n = Math.max(1, (int) (grams * (1 - s.boardTrim) / g));
        int kept = 0;
        for (int i = 0; i < n; i++) {
            if (sp.getRandom().nextDouble() < cut.lossShare()) cut.lostGrams += g;
            else kept++;
        }
        if (cut.skill > cut.threshold && sp.getRandom().nextDouble() < s.boardBonusChance) kept++;  // выше порога — шанс +1 порция
        cut.keptGrams += (double) kept * g;
        int trim = Math.max(0, grams - n * g);  // обрезь куска — обрезками целиком
        while (kept > 0) {
            ItemStack st = portion.copyWithCount(Math.min(kept, portion.getMaxStackSize()));
            kept -= st.getCount();
            spawn(level, pos, st);
        }
        spawnScraps(level, pos, trim / 250);
        handler.extractItem(0, 1, false);
        be.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        tool.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        sp.swing(InteractionHand.MAIN_HAND, true);
        var knife = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("farmersdelight", "block.cutting_board.knife"));
        level.playSound(null, pos, knife != null ? knife : SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1f);
        finish(cut);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Порция для крупного куска: первый результат рецепта доски FD, а если рецепта нет — тот же предмет без веса в NBT. */
    static ItemStack portionOf(ServerLevel level, ItemStack piece) {
        var type = ForgeRegistries.RECIPE_TYPES.getValue(new ResourceLocation("farmersdelight", "cutting"));
        ItemStack probe = new ItemStack(piece.getItem());
        if (type != null) for (var r : level.getRecipeManager().getRecipes()) {
            if (r.getType() != type || r.getIngredients().isEmpty() || !r.getIngredients().get(0).test(probe)) continue;
            ItemStack res = r.getResultItem(level.registryAccess());
            if (!res.isEmpty() && !NO_LOSS.contains(String.valueOf(ForgeRegistries.ITEMS.getKey(res.getItem())))) return new ItemStack(res.getItem());
        }
        return probe;
    }

    /** Результат обычной нарезки FD во время {@link Cut} доски: часть мяса — в потери. */
    public static void onItemJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        Cut cut = CURRENT.get();
        if (cut == null || !cut.board || e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity ie)) return;
        if (ie.distanceToSqr(Vec3.atCenterOf(cut.pos)) > 4) return;
        ItemStack st = ie.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (id == null || NO_LOSS.contains(id.toString()) || Weights.nominal(st.getItem()) <= 0) return;
        int g = Math.max(1, Weights.grams(st)), kept = 0;
        for (int i = 0; i < st.getCount(); i++) {
            if (ie.level().getRandom().nextDouble() < cut.lossShare()) cut.lostGrams += g;
            else kept++;
        }
        cut.keptGrams += (double) kept * g;
        if (kept == 0) e.setCanceled(true);
        else if (kept < st.getCount()) ie.setItem(st.copyWithCount(kept));
    }

    private static void spawnScraps(ServerLevel level, BlockPos pos, int n) {
        Item scraps = ForgeRegistries.ITEMS.getValue(SCRAPS);
        if (scraps == null || scraps == net.minecraft.world.item.Items.AIR) return;
        while (n > 0) {
            ItemStack st = new ItemStack(scraps, Math.min(n, new ItemStack(scraps).getMaxStackSize()));
            n -= st.getCount();
            spawn(level, pos, st);
        }
    }

    private static int stage(BlockState bs) {
        return bs.getBlock().getStateDefinition().getProperty("blockstate") instanceof IntegerProperty p ? bs.getValue(p) : -1;
    }

    private static void drop(ServerLevel level, BlockPos pos, ResourceLocation table) {
        var loot = level.getServer().getLootData().getLootTable(table);
        for (ItemStack st : loot.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))) spawn(level, pos, st);
    }

    private static void spawn(ServerLevel level, BlockPos pos, ItemStack st) {
        ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, st);
        ie.setPickUpDelay(10);
        level.addFreshEntity(ie);
    }

    /** Потерянное — обрезками (часть веса), опыт «Кулинарии» за разрез. */
    static void finish(Cut cut) {
        Settings s = Settings.get();
        if (cut.lostGrams > 0 && cut.player.level() instanceof ServerLevel level) {
            spawnScraps(level, cut.pos, (int) Math.max(1, Math.round(cut.lostGrams * s.butcherLostToScraps / 250.0)));
            cut.player.displayClientMessage(Component.translatable("rpculinary.msg.butcher_loss").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
        double kg = (cut.keptGrams + cut.lostGrams) / 1000.0;
        double xp = xp(cut.threshold, cut.skill, kg) * s.butcherXp;
        if (xp > 0) RpCoreAPI.practice(cut.player, RpIds.COOKING, xp);
    }

    /** Опыт за разрез: больше — за крупный разрез и сложную тушу; навык намного выше порога — мало. */
    public static double xp(int threshold, int skill, double kg) {
        if (kg <= 0) return 0;
        double relevance = Mth.clamp(1.0 - (skill - threshold) / 4.0, 0, 1);
        return (1.0 + 0.5 * threshold) * relevance * (0.5 + Math.min(kg, 40) / 20.0);
    }

    // ------------------------------------------------------------------ вес туш в инвентаре

    /** Раз в секунду: туши в инвентаре замедляют по весу (вместо эффекта Butchery по тегам — теги пустые). */
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 20 != 7) return;
        Settings s = Settings.get();
        double kg = 0;
        if (s.carcassSlowMax > 0 && !sp.isCreative() && !sp.isSpectator()) {
            var inv = sp.getInventory();
            for (var list : List.of(inv.items, inv.offhand)) for (ItemStack st : list) {
                Carcass c = st.isEmpty() ? null : of(st.getItem());
                if (c != null) kg += c.kg() * st.getCount();
            }
        }
        applySlow(sp, slow(kg, s));
    }

    /** Замедление (доля скорости) от веса туш: линейно до максимума при {@code carcass_slow_kg}. Шаг — 1 %. */
    public static double slow(double kg, Settings s) {
        if (kg <= 0 || s.carcassSlowKg <= 0) return 0;
        return Math.round(Math.min(s.carcassSlowMax, kg / s.carcassSlowKg * s.carcassSlowMax) * 100) / 100.0;
    }

    private static void applySlow(ServerPlayer sp, double slow) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier old = speed.getModifier(SLOW_ID);
        if (old != null && Math.abs(old.getAmount() + slow) < 1e-6) return;
        if (old != null) speed.removeModifier(SLOW_ID);
        if (slow > 0) speed.addTransientModifier(new AttributeModifier(SLOW_ID, "rpculinary carcass weight", -slow, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
