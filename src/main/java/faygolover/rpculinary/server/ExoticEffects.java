package faygolover.rpculinary.server;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.integration.Lso;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Экзотика действует на того, кто ест (решения Alex 27.10.2026): от природы продукта, а не от перка повара. Группы —
 * {@link Exotic}; особые продукты внутри групп — теги {@code rpculinary:effect/<действие>}. Соседи — мягко, отражением:
 * RP Psyche (стресс, рассудок), RP Medicine (сепсис → жар), Mekanism (облучение), LSO (согрев). Нет соседа — простая
 * замена (отравление) или ничего.
 */
public final class ExoticEffects {
    private ExoticEffects() {}

    static final TagKey<Item> FATIGUE = tag("mining_fatigue"), DISGUST = tag("disgust"), DARKNESS = tag("darkness"),
            RANDOM_POTION = tag("random_potion"), CREEPER = tag("creeper");

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(new ResourceLocation(RpCulinary.MODID, "effect/" + path));
    }

    private static int bit(String group) {
        return 1 << Exotic.index(group);
    }

    /** Съел долю {@code fraction} предмета: действия всех его групп. */
    public static void afterEating(ServerPlayer sp, NutritionData d, ItemStack used, double fraction, Settings s) {
        if (!s.exoticEffects || fraction <= 0) return;
        int mask = NutritionTable.exoticMask(sp.server, used.getItem());
        if (mask == 0) return;
        var rnd = sp.getRandom();
        double k = Math.min(1, fraction * 2);
        if ((mask & bit("candy")) != 0) {
            if (s.candyDrowsy) {
                d.drowsyUntil = Math.max(d.drowsyUntil, sp.server.getTickCount() + 600 * 20L);
                BodyEffects.sense(sp, d, "drowsy", 60);
            }
            if (rnd.nextDouble() < s.candyTeethChance * k) {
                if (faygolover.rpcore.api.RpCoreAPI.body() == null) sp.hurt(sp.damageSources().generic(), 1f);
                BodyEffects.sense(sp, d, "teeth", 1);
            }
        }
        if (used.is(FATIGUE)) sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, (int) (s.fatigueSeconds * 20), 1));
        if (used.is(DISGUST)) {
            Psyche.stress(sp, s.disgustStress * k);
            BodyEffects.sense(sp, d, "disgust", 30);
        }
        if ((mask & bit("nether")) != 0) {
            MobEffect hot = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendarysurvivaloverhaul", "hot_food"));
            if (hot != null && Lso.loaded()) sp.addEffect(new MobEffectInstance(hot, (int) (s.netherWarmSeconds * 20), 0));
            thirst(sp, d, s.netherThirst * k, s);
            BodyEffects.sense(sp, d, "nether_heat", 30);
        }
        if ((mask & bit("end")) != 0 && !used.is(Items.CHORUS_FRUIT) && rnd.nextDouble() < s.endHiccupChance * k) {
            hiccup(sp);
            BodyEffects.hidden(sp, MobEffects.CONFUSION, 120, 0);
            BodyEffects.sense(sp, d, "end_hiccup", 1);
        }
        if ((mask & bit("pets")) != 0) {
            Psyche.stress(sp, s.petsStress * k);
            BodyEffects.sense(sp, d, "pets_guilt", 30);
        }
        if ((mask & bit("monsters")) != 0) {
            if (rnd.nextDouble() < s.monstersPoison * k * CulinaryService.poisonFactor(sp, ItemStack.EMPTY, s)) {
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
                sp.displayClientMessage(Component.translatable("rpculinary.msg.monster_sick").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
            }
            if (used.is(DARKNESS)) sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
            if (used.is(RANDOM_POTION)) {
                MobEffect e = WITCH.get(rnd.nextInt(WITCH.size()));
                sp.addEffect(new MobEffectInstance(e, e == MobEffects.LEVITATION ? 60 : 400, 0));
            }
            if (used.is(CREEPER)) {
                BodyEffects.hidden(sp, MobEffects.CONFUSION, 200, 0);
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 0.6f, 1.2f);
                sp.setDeltaMovement(sp.getDeltaMovement().add((rnd.nextDouble() - 0.5) * 0.6, 0.25, (rnd.nextDouble() - 0.5) * 0.6));
                sp.hurtMarked = true;
                BodyEffects.sense(sp, d, "creeper_shake", 1);
            }
        }
        if ((mask & bit("infected")) != 0) {
            if (!Medicine.infect(sp, s.infectedSepsis * k, s.infectedSepsisHours)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
            BodyEffects.sense(sp, d, "infected", 30);
        }
        if ((mask & bit("radioactive")) != 0) {
            if (!Mekanism.radiate(sp, s.radioactiveDose * k)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            BodyEffects.sense(sp, d, "radioactive", 30);
        }
        if ((mask & bit("anomalous")) != 0) {
            int n = 1 + rnd.nextInt(2);
            for (int i = 0; i < n; i++) {
                MobEffect e = ANOMALY.get(rnd.nextInt(ANOMALY.size()));
                sp.addEffect(new MobEffectInstance(e, e == MobEffects.LEVITATION ? 40 : 200 + rnd.nextInt(200), 0));
            }
            Psyche.sanityHit(sp, s.anomalousSanity * k);
            BodyEffects.sense(sp, d, "anomalous", 1);
        }
        if ((mask & bit("human")) != 0 && !Exotic.cannibal(sp)) human(sp, d, k, s);
    }

    /** Ведьма: случайное зелье. */
    static final List<MobEffect> WITCH = List.of(MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.REGENERATION,
            MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.INVISIBILITY, MobEffects.NIGHT_VISION, MobEffects.FIRE_RESISTANCE,
            MobEffects.JUMP, MobEffects.HUNGER, MobEffects.LEVITATION, MobEffects.GLOWING);
    /** Аномальное: случайное странное. */
    static final List<MobEffect> ANOMALY = List.of(MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.NIGHT_VISION,
            MobEffects.LEVITATION, MobEffects.GLOWING, MobEffects.SLOW_FALLING, MobEffects.DARKNESS, MobEffects.MOVEMENT_SPEED);

    /** Сильная жажда: своя вода или LSO. */
    static void thirst(ServerPlayer sp, NutritionData d, double percent, Settings s) {
        int mode = CulinaryService.thirstMode(sp, s);
        if (mode == 1) d.state.water = Math.max(0, d.state.water - percent);
        else if (mode == 2) Lso.addWater(sp, -percent);
    }

    /** «Икота» Края: короткий случайный телепорт, как от хоруса, но ближе. */
    static void hiccup(ServerPlayer sp) {
        var level = sp.serverLevel();
        var rnd = sp.getRandom();
        for (int i = 0; i < 16; i++) {
            double x = sp.getX() + (rnd.nextDouble() - 0.5) * 8;
            double y = Mth.clamp(sp.getY() + (rnd.nextInt(8) - 4), level.getMinBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight() - 1);
            double z = sp.getZ() + (rnd.nextDouble() - 0.5) * 8;
            if (sp.isPassenger()) sp.stopRiding();
            Vec3 from = sp.position();
            if (sp.randomTeleport(x, y, z, true)) {
                level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(sp));
                level.playSound(null, x, y, z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
                sp.resetFallDistance();
                return;
            }
        }
    }

    /** Человечина без «Каннибала»: рассудок, рвота, а при повторах — куру. */
    public static void human(ServerPlayer sp, NutritionData d, double k, Settings s) {
        Psyche.sanityHit(sp, s.humanSanity * k);
        Psyche.stress(sp, s.humanSanity * k);
        if (sp.getRandom().nextDouble() < s.humanVomitChance * k) BodyEffects.vomit(sp, d, s);
        else BodyEffects.sense(sp, d, "human", 1);
        d.humanMeals += k;
        if (d.humanMeals >= s.kuruAfterMeals && d.kuruSeconds <= 0) {
            d.kuruSeconds = s.kuruHours * 3600;
            RpCulinary.LOGGER.info("RP Culinary: у {} началась куру", sp.getScoreboardName());
        }
    }

    /** Раз в секунду: куру (дрожь, смех, координация) и забывание съеденной человечины. */
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 20 != 0 || !sp.isAlive()) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null || d.humanMeals <= 0 && d.kuruSeconds <= 0) return;
        Settings s = Settings.get();
        if (d.humanMeals > 0) d.humanMeals = Math.max(0, d.humanMeals - 1 / Math.max(60, s.hoursPerRpDay * 3600));
        if (d.kuruSeconds <= 0 || sp.isCreative() || sp.isSpectator()) return;
        d.kuruSeconds = Math.max(0, d.kuruSeconds - 1);
        // Координация: всегда чуть медленнее ноги и руки.
        BodyEffects.hidden(sp, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
        BodyEffects.hidden(sp, MobEffects.DIG_SLOWDOWN, 60, 0);
        var rnd = sp.getRandom();
        if (rnd.nextInt(90) == 0) {
            BodyEffects.hidden(sp, MobEffects.CONFUSION, 80, 0);
            BodyEffects.sense(sp, d, "kuru_tremor", 60);
        }
        if (rnd.nextInt(240) == 0) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.5f, 1.4f);
            BodyEffects.sense(sp, d, "kuru_laugh", 120);
        }
    }

    // ------------------------------------------------------------------ соседи (мягко)

    /** RP Psyche: стресс и рассудок. */
    static final class Psyche {
        static void stress(ServerPlayer sp, double amount) {
            if (amount <= 0) return;
            try {
                Class.forName("faygolover.rppsyche.server.PsycheService").getMethod("impulse", ServerPlayer.class, double.class).invoke(null, sp, amount);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Нет RP Psyche — без стресса.
            }
        }

        static void sanityHit(ServerPlayer sp, double amount) {
            if (amount <= 0) return;
            try {
                Class<?> svc = Class.forName("faygolover.rppsyche.server.PsycheService");
                if (!(Boolean) svc.getMethod("affected", Player.class).invoke(null, sp)) return;
                Object d = Class.forName("faygolover.rppsyche.capability.PsycheData").getMethod("get", Player.class).invoke(null, sp);
                if (d == null) return;
                var f = d.getClass().getField("sanityHit");
                f.setDouble(d, f.getDouble(d) + amount);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                // Нет RP Psyche — без удара по рассудку.
            }
        }
    }

    /** RP Medicine: заражение — сепсис (дальше жар) и его рост ещё какое-то время. false — Medicine нет. */
    static final class Medicine {
        static boolean infect(ServerPlayer sp, double sepsis, double hours) {
            try {
                Object m = Class.forName("faygolover.rpmedicine.server.Medical").getMethod("state", Entity.class).invoke(null, sp);
                if (m == null) return false;
                var sep = m.getClass().getField("sepsis");
                sep.setDouble(m, Math.min(100, Math.max(sep.getDouble(m), sepsis)));
                var spoiled = m.getClass().getField("spoiledBloodSeconds");
                spoiled.setDouble(m, Math.max(spoiled.getDouble(m), hours * 3600));
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        }
    }

    /** Mekanism: доза облучения. false — Mekanism нет. */
    static final class Mekanism {
        static boolean radiate(ServerPlayer sp, double sv) {
            try {
                Class<?> api = Class.forName("mekanism.api.radiation.IRadiationManager");
                Object mgr = api.getField("INSTANCE").get(null);
                if (!(Boolean) api.getMethod("isRadiationEnabled").invoke(mgr)) return false;
                api.getMethod("radiate", net.minecraft.world.entity.LivingEntity.class, double.class).invoke(mgr, sp, sv);
                return true;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                return false;
            }
        }
    }
}
