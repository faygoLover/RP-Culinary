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
        List<String> species = Source.list(stored);
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
            if (!species.isEmpty() && !sid.equals("minecraft:bone") && !sid.equals("minecraft:bone_meal")) Source.add(st, species);
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

    /** Мясорубка F&C с мясом: положить кусок, крутить, забрать остаток. Не мясо — как в F&C (мука и т. п.). */
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
        if (cur.isEmpty()) {
            if (held.isEmpty() || !meat(held)) return;
            if (bigBone(held)) {
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_bone").withStyle(ChatFormatting.GRAY), true);
            } else {
                ItemStack one = held.copyWithCount(1);
                CompoundTag t = new CompoundTag();
                t.put("piece", one.save(new CompoundTag()));
                t.putInt("g", Math.max(1, Weights.grams(one)));
                t.putBoolean("bone", SMALL_BONE.contains(id(one.getItem())));
                pd.put(MINCER_KEY, t);
                if (!sp.isCreative()) held.shrink(1);
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_in", one.getHoverName(), Weights.format(t.getInt("g"))).withStyle(ChatFormatting.GRAY), true);
            }
        } else if (sp.isShiftKeyDown() && held.isEmpty()) {  // забрать остаток
            ItemStack piece = ItemStack.of(cur.getCompound("piece"));
            int g = cur.getInt("g");
            if (g < Weights.grams(piece)) piece.getOrCreateTag().putInt(Weights.TAG_WEIGHT, g);
            pd.remove(MINCER_KEY);
            if (!sp.getInventory().add(piece)) sp.drop(piece, false);
        } else if (!held.isEmpty()) {
            sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_busy").withStyle(ChatFormatting.GRAY), true);
        } else {  // оборот ручки — одна порция
            ItemStack piece = ItemStack.of(cur.getCompound("piece"));
            int g = cur.getInt("g");
            List<String> species = Source.list(piece);
            int skill = CookingService.level(sp);
            ItemStack out;
            if (g >= PORTION) {
                boolean fail = sp.getRandom().nextDouble() < Butchering.mincerLoss(skill);
                out = fail ? new ItemStack(item("butchery:meat_scraps")) : new ItemStack(minceFor(level, piece, species));
                g -= PORTION;
            } else {
                out = new ItemStack(item("butchery:meat_scraps"));
                g = 0;
            }
            if (!species.isEmpty()) Source.add(out, species);
            spawnFront(level, pos, bs, out);
            if (g <= 0) {
                if (cur.getBoolean("bone")) spawnFront(level, pos, bs, new ItemStack(Items.BONE_MEAL));
                pd.remove(MINCER_KEY);
            } else {
                cur.putInt("g", g);
                pd.put(MINCER_KEY, cur);
                sp.displayClientMessage(Component.translatable("rpculinary.msg.mincer_left", Weights.format(g)).withStyle(ChatFormatting.GRAY), true);
            }
            if (bs.getBlock().getStateDefinition().getProperty("crank") instanceof IntegerProperty crank)
                level.setBlock(pos, bs.setValue(crank, Math.min(10, crank.getPossibleValues().stream().max(Integer::compare).orElse(10))), 3);
            SoundEvent snd = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("farm_and_charm", "mincer_cranking"));
            level.playSound(null, pos, snd != null ? snd : SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, 1.5f);
            practice(sp, 1, skill, PORTION / 1000.0 * 0.5);
        }
        be.setChanged();
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Фарш по виду: тёмный, красный, розовый; вид неизвестен — по рецепту мясорубки F&C для этого куска. */
    static Item minceFor(ServerLevel level, ItemStack piece, List<String> species) {
        String sp = species.isEmpty() ? "" : species.get(0);
        if (!sp.isEmpty()) return item(DARK.contains(sp) ? "farm_and_charm:minced_beef" : PINK.contains(sp) ? "butchery:raw_lamb_mince" : "butchery:raw_beef_mince");
        ItemStack probe = new ItemStack(piece.getItem());
        for (var r : level.getRecipeManager().getRecipes()) {
            if (!String.valueOf(r.getId()).startsWith("rpculinary:mincer/") || r.getIngredients().isEmpty() || !r.getIngredients().get(0).test(probe)) continue;
            ItemStack res = r.getResultItem(level.registryAccess());
            if (!res.isEmpty()) return res.getItem();
        }
        return item("butchery:raw_beef_mince");
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
