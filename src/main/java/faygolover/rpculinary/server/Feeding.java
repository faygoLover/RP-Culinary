package faygolover.rpculinary.server;

import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.network.FeedPosePacket;
import faygolover.rpculinary.network.Net;
import faygolover.rpcore.api.BodyProvider;
import faygolover.rpcore.api.RpCoreAPI;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Покормить или напоить другого (решение Alex 10.11): ПКМ едой или водой по игроку или телу вышедшего, ровно 3 секунды
 * — один укус или глоток. Кормящий держит руку вытянутой, у того, кого кормят, частички у рта. Телу вышедшего порция
 * запоминается и зачтётся при входе.
 */
public final class Feeding {
    private Feeding() {}

    private static final int TICKS = 60;
    private static final double RANGE = 3.5;
    private static final ResourceLocation BODY = new ResourceLocation("rpmedicine", "body_stub");
    private static final String FILE = "rpculinary_fed_bodies";

    private static final class Feed {
        final Entity target;
        final int slot;
        final ItemStack item;
        int left = TICKS;

        Feed(Entity target, int slot, ItemStack item) {
            this.target = target;
            this.slot = slot;
            this.item = item;
        }
    }

    private record Portion(ItemStack used, double fraction, boolean last) {}

    private static final Map<UUID, Feed> ACTIVE = new HashMap<>();

    static boolean feedable(ItemStack st) {
        return !st.isEmpty() && (st.isEdible() || st.getUseAnimation() == UseAnim.DRINK);
    }

    private static boolean canBeFed(Entity t) {
        return t instanceof Player || BODY.equals(ForgeRegistries.ENTITY_TYPES.getKey(t.getType()));
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || p.isShiftKeyDown() || !feedable(p.getMainHandItem()) || !canBeFed(e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.CONSUME);
        if (!(p instanceof ServerPlayer sp) || ACTIVE.containsKey(sp.getUUID())) return;
        if (!RpCoreAPI.handsFree(sp)) {
            sp.displayClientMessage(Component.translatable("rpcore.msg.no_hands"), true);
            return;
        }
        ACTIVE.put(sp.getUUID(), new Feed(e.getTarget(), sp.getInventory().selected, sp.getMainHandItem().copy()));
        pose(sp, true);
    }

    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Iterator<Map.Entry<UUID, Feed>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Feed> en = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            Feed f = en.getValue();
            if (sp == null || !valid(sp, f)) {
                if (sp != null) pose(sp, false);
                it.remove();
                continue;
            }
            if (f.left % 4 == 0) effects(f.target, f.item);
            if (--f.left > 0) continue;
            pose(sp, false);
            it.remove();
            give(sp, f.target);
        }
    }

    private static boolean valid(ServerPlayer sp, Feed f) {
        return sp.isAlive() && f.target.isAlive() && !f.target.isRemoved() && sp.level() == f.target.level()
                && sp.distanceToSqr(f.target) <= RANGE * RANGE && sp.getInventory().selected == f.slot
                && ItemStack.isSameItem(sp.getMainHandItem(), f.item) && RpCoreAPI.handsFree(sp);
    }

    private static void pose(ServerPlayer sp, boolean on) {
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), new FeedPosePacket(sp.getId(), on));
    }

    /** Частички у рта и звук: у еды — кусочки, у питья — брызги. */
    private static void effects(Entity target, ItemStack item) {
        if (!(target.level() instanceof ServerLevel level)) return;
        Vec3 at = target.getEyePosition().add(target.getLookAngle().scale(0.25)).subtract(0, 0.12, 0);
        boolean drink = item.getUseAnimation() == UseAnim.DRINK;
        if (drink) level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 4, 0.06, 0.05, 0.06, 0.04);
        else level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), at.x, at.y, at.z, 3, 0.06, 0.05, 0.06, 0.04);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), drink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.5f, 0.9f + target.level().random.nextFloat() * 0.2f);
    }

    private static void give(ServerPlayer feeder, Entity target) {
        Portion p = take(feeder, feeder.getMainHandItem());
        if (target instanceof ServerPlayer t) {
            eat(t, p.used, p.fraction, p.last);
            return;
        }
        BodyProvider body = RpCoreAPI.body();
        UUID owner = body == null ? null : body.bodyOwner(target);
        if (owner == null) return;
        Queue q = queue(feeder.server);
        CompoundTag tag = new CompoundTag();
        tag.put("item", p.used.save(new CompoundTag()));
        tag.putDouble("fraction", p.fraction);
        tag.putBoolean("last", p.last);
        q.fed.computeIfAbsent(owner, k -> new ListTag()).add(tag);
        q.setDirty();
    }

    /** Отнять одну порцию из руки кормящего: укус или глоток, надкусанное остаётся у него, тара — тоже. */
    private static Portion take(ServerPlayer feeder, ItemStack held) {
        Settings s = Settings.get();
        boolean drink = held.getUseAnimation() == UseAnim.DRINK;
        int n, left;
        double kcal = 0;
        if (drink) {
            n = Sips.total(held);
            left = Sips.left(held);
        } else {
            n = held.hasTag() && held.getTag().contains(CulinaryService.BITES_N) ? held.getTag().getInt(CulinaryService.BITES_N) : CulinaryEating.bitesFor(feeder, held, s);
            left = held.hasTag() && held.getTag().contains(CulinaryService.BITES) ? held.getTag().getInt(CulinaryService.BITES) : n;
            kcal = CulinaryEating.dishKcal(feeder, held, s);
        }
        boolean more = n > 1 && left > 1 && (drink || kcal > 0);
        double fraction = n <= 1 ? 1 : drink ? 1.0 / n : kcal <= 0 ? 1 : more ? s.biteKcal / kcal : Math.max(0.05, 1 - (n - 1) * s.biteKcal / kcal);
        ItemStack used = held.copyWithCount(1);
        if (more) {
            ItemStack piece = held.getCount() > 1 ? held.split(1) : held;
            if (drink) {
                Sips.set(piece, left - 1, n);
            } else {
                piece.getOrCreateTag().putInt(CulinaryService.BITES_N, n);
                piece.getTag().putInt(CulinaryService.BITES, left - 1);
            }
            if (piece != held && !feeder.getInventory().add(piece)) feeder.drop(piece, false);
        } else {
            ItemStack rest = drink ? Sips.emptyContainer(used) : used.getItem() instanceof BowlFoodItem ? new ItemStack(Items.BOWL) : used.getCraftingRemainingItem();
            if (!feeder.getAbilities().instabuild) held.shrink(1);
            if (rest != null && !rest.isEmpty() && !feeder.getInventory().add(rest)) feeder.drop(rest, false);
        }
        return new Portion(used, fraction, !more);
    }

    private static void eat(ServerPlayer target, ItemStack used, double fraction, boolean last) {
        CulinaryEating.feedPortion(target, used, fraction);
        if (!last) return;
        // Зелья и золотые яблоки действуют, когда доедено.
        var food = used.getFoodProperties(target);
        if (food != null) {
            for (var effect : food.getEffects())
                if (target.getRandom().nextFloat() < effect.getSecond()) target.addEffect(new MobEffectInstance(effect.getFirst()));
        }
        for (var effect : PotionUtils.getMobEffects(used)) target.addEffect(new MobEffectInstance(effect));
    }

    /** Вошёл тот, чьё тело кормили. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Queue q = queue(sp.server);
        ListTag list = q.fed.remove(sp.getUUID());
        if (list == null) return;
        q.setDirty();
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            eat(sp, ItemStack.of(c.getCompound("item")), c.getDouble("fraction"), c.getBoolean("last"));
        }
    }

    private static Queue queue(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Queue::load, Queue::new, FILE);
    }

    private static final class Queue extends SavedData {
        final Map<UUID, ListTag> fed = new HashMap<>();

        static Queue load(CompoundTag tag) {
            Queue q = new Queue();
            for (String key : tag.getAllKeys()) q.fed.put(UUID.fromString(key), tag.getList(key, Tag.TAG_COMPOUND));
            return q;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            fed.forEach((id, list) -> tag.put(id.toString(), list));
            return tag;
        }
    }
}
