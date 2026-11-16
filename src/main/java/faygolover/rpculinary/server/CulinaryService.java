package faygolover.rpculinary.server;

import static faygolover.rpculinary.server.CulinaryEating.*;
import static faygolover.rpculinary.server.CulinaryPlayerEvents.*;
import faygolover.rpcore.api.BodyProvider;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.StaminaProvider;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Nutrients;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.integration.Lso;
import faygolover.rpculinary.network.Net;
import faygolover.rpculinary.network.SyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;
import faygolover.rpculinary.client.ClientNutrition;

/**
 * Питание на сервере (дизайн, §3–4).
 * <ul>
 *   <li>Раз в секунду — шаг обмена веществ: сытость, вода, нутриенты, вес, обмороки, «нет сил».</li>
 *   <li>Ванильная сытость остаётся, но служит только замком «есть ли место в желудке»: 7 — есть место, 20 — полон.
 *   Насыщение всегда 0, поэтому ванильное лечение от еды и голодный урон не работают. Ванильные окорочка скрыты.</li>
 *   <li>Съеденное считаем по составу: предмет — по событию «доел», блюдо с блока (торт, тарелка Kaleidoscope) —
 *   по приросту ванильной сытости после клика по блоку.</li>
 * </ul>
 */
public final class CulinaryService {
    private CulinaryService() {}

    /** Ванильная сытость-замок: есть место в желудке / желудок полон. */
    public static final int FOOD_ROOM = 7, FOOD_FULL = 20;

    static final UUID SPEED_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c11");
    static final UUID ATTACK_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c12");
    static final UUID STAMINA_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c13");
    static final UUID REGEN_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c14");
    /** «Сыт и доволен»: чуть быстрее восстанавливается выносливость до конца RP-суток. */
    static final UUID CONTENT_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c15");
    /** Еда по частям: сколько укусов осталось и сколько их у целого блюда (NBT предмета). */
    public static final String BITES = "rpculinary_bites", BITES_N = "rpculinary_bites_n";
    static final ResourceKey<DamageType> LSO_DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("legendarysurvivaloverhaul", "dehydration"));
    static final Random RANDOM = new Random();

    // ------------------------------------------------------------------ доступ

    /** Питание игрока (сервер), с первым знакомством. */
    @Nullable
    public static NutritionState state(Player p) {
        // Питание считает сервер; у клиента — только то, что прислали (ClientNutrition).
        if (p.level().isClientSide) return null;
        NutritionData d = NutritionData.get(p);
        if (d == null) return null;
        ensureInit(p, d);
        return d.state;
    }

    static void ensureInit(Player p, NutritionData d) {
        if (d.state.initialized) return;
        BodyProvider body = RpCoreAPI.body();
        double w = body != null ? body.weightKg(p) : Double.NaN;
        double f = body != null ? body.bodyFatPercent(p) : Double.NaN;
        d.state.init(Settings.get(), w, f);
    }

    public static boolean anorexic(Player p) {
        return RpCoreAPI.hasTrait(p, RpIds.ANOREXIC);
    }

    /** Обмен веществ от черт: анорексия, быстрый обмен, склонность к полноте. */
    public static double metabolism(Player p, Settings s) {
        double k = anorexic(p) ? s.anorexicMetabolism : 1;
        if (RpCoreAPI.hasTrait(p, RpIds.FAST_METABOLISM)) k *= s.fastMetabolism;
        if (RpCoreAPI.hasTrait(p, RpIds.SLOW_METABOLISM)) k *= s.slowMetabolism;
        return k;
    }

    /** Во сколько раз желудок меньше обычного: анорексия, плохой аппетит, Малоежка, Обжора. */
    public static double stomachMult(Player p, NutritionState m, Settings s) {
        double k = (anorexic(p) ? s.anorexicStomach : 1) * (Nutrients.poorAppetite(m, s) ? s.poorAppetiteStomach : 1);
        if (RpCoreAPI.hasTrait(p, RpIds.LIGHT_EATER)) k *= s.lightEaterStomach;
        if (RpCoreAPI.hasTrait(p, RpIds.BIG_EATER)) k *= s.bigEaterStomach;
        return k;
    }

    /** Скорость переваривания (Малоежка — медленнее, голод приходит позже; Обжора — быстрее). */
    static double digest(Player p, Settings s) {
        double k = 1;
        if (RpCoreAPI.hasTrait(p, RpIds.LIGHT_EATER)) k *= s.lightEaterStomach;
        if (RpCoreAPI.hasTrait(p, RpIds.BIG_EATER)) k *= s.bigEaterStomach;
        return k;
    }

    /** Трата воды (Мало пьёт, Много пьёт). */
    static double waterUse(Player p, Settings s) {
        double k = 1;
        if (RpCoreAPI.hasTrait(p, RpIds.LOW_THIRST)) k *= s.lowThirstWater;
        if (RpCoreAPI.hasTrait(p, RpIds.HIGH_THIRST)) k *= s.highThirstWater;
        return k;
    }

    /** 0 — жажды нет, 1 — своя, 2 — LSO. */
    /** Снять с воды LSO накопленный по времени долг — целыми делениями. */
    static void payLsoWater(ServerPlayer sp, NutritionState m) {
        double point = Lso.percentPerPoint();
        if (m.lsoWaterDebt < point) return;
        double whole = Math.floor(m.lsoWaterDebt / point) * point;
        m.lsoWaterDebt -= whole;
        Lso.addWater(sp, -whole);
    }

    public static int thirstMode(ServerPlayer sp, Settings s) {
        if (Lso.hydration(sp) >= 0) return 2;
        return s.thirstEnabled ? 1 : 0;
    }

    /** Вода 0–1 (без жажды — 1). */
    public static double hydration(ServerPlayer sp, NutritionState m, Settings s) {
        double lso = Lso.hydration(sp);
        if (lso >= 0) return lso;
        return s.thirstEnabled ? m.water / 100.0 : 1;
    }

    static boolean active(ServerPlayer sp) {
        return !sp.isCreative() && !sp.isSpectator() && sp.isAlive();
    }

    // ------------------------------------------------------------------ тик

    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        if (plateEater == sp) plateEater = null;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        ensureInit(sp, d);
        Settings s = Settings.get();
        long tick = sp.server.getTickCount();
        // С LSO воду тратим мы, по времени RP: его усталость жажды снимаем каждый тик, чтобы он сам не тратил.
        if (s.lsoThirstByTime && s.thirstEnabled) Lso.clearExhaustion(sp);
        FoodData fd = sp.getFoodData();
        if (s.hungerEnabled) {
            int cur = fd.getFoodLevel();
            // Прирост ванильной сытости сразу после клика по блоку — съел с блока (торт, тарелка). Без клика прирост
            // не считаем: на «Мирной» ваниль сама подкармливает игрока — это не еда.
            if (d.mirroredFood >= 0 && cur > d.mirroredFood && d.ateItemTick != tick && d.clickedTick >= tick - 1 && active(sp)) {
                if (!d.clickedFood.isEmpty()) ate(sp, d.clickedFood);
                else eatFromBlock(sp, d, d.clickedBlock, cur - d.mirroredFood);
            }
            // Полный желудок не мешает есть — через силу (медленнее, переполнение). Не лезет только «до предела».
            int lock = Metabolism.overfill(d.state, s, stomachMult(sp, d.state, s)) >= 1 ? FOOD_FULL : FOOD_ROOM;
            fd.setFoodLevel(lock);
            fd.setSaturation(0);
            fd.setExhaustion(0);
            d.mirroredFood = lock;
        } else if (d.mirroredFood >= 0) {
            // Голод выключили на ходу — вернуть ванильную сытость полной.
            fd.setFoodLevel(20);
            d.mirroredFood = -1;
        }
        if (sp.isSprinting()) d.sprintTicks++;
        if (++d.stepTicks >= 20) {
            d.stepTicks = 0;
            step(sp, d, s);
        }
    }

    /** Шаг раз в секунду. */
    static void step(ServerPlayer sp, NutritionData d, Settings s) {
        NutritionState m = d.state;
        if (!active(sp)) {
            d.exertionStamina = 0;
            d.sprintTicks = 0;
            sync(sp, d, false);
            return;
        }
        int thirst = thirstMode(sp, s);
        s.liveDayHours = liveDayHours();
        Metabolism.Input in = new Metabolism.Input();
        in.dt = 1;
        in.lsoWater = thirst == 2 && s.lsoThirstByTime;
        // Калории за отдых: сколько выносливости восстановилось (RP Stamina); без неё — по бегу.
        if (d.exertionStamina > 0) in.exertionKcal = d.exertionStamina / 100.0 * s.kcalPer100Stamina;
        else if (RpCoreAPI.stamina() == null) in.exertionKcal = d.sprintTicks / 20.0 / 60.0 * s.sprintKcalPerMinute;
        in.exertionKcal += d.extraKcal;
        d.exertionStamina = 0;
        d.extraKcal = 0;
        d.sprintTicks = 0;
        in.digest = digest(sp, s);
        in.water = waterUse(sp, s);
        in.sleeping = sp.isSleeping();
        in.cold = Lso.cold(sp);
        in.metabolism = metabolism(sp, s);
        in.ownWater = thirst == 1;
        in.externalHydration = thirst == 2 ? Lso.hydration(sp) : -1;
        in.random = RANDOM;
        // Время RP стоит (/rptime stop) — голод, вода и вес не меняются.
        if (RpCoreAPI.timeRunning()) {
            int faint = Metabolism.tick(m, in, s);
            if (faint > 0) faint(sp, faint);
            payLsoWater(sp, m);
        }
        double hydration = hydration(sp, m, s);
        BodyEffects.Mods mods = BodyEffects.step(sp, d, s, hydration);
        applyEffects(sp, m, s, Math.min(1, hydration), mods);
        if (!RpCoreAPI.bodyHandles(RpIds.BODY_HEALTH)) withoutBody(sp, m, s, hydration);
        stageMessages(sp, d, m, s, hydration);
        sync(sp, d, false);
    }

    /** Обморок: через мод тела (RP Medicine), иначе — темнота и слабость. */
    static void faint(ServerPlayer sp, int seconds) {
        BodyProvider body = RpCoreAPI.body();
        if (body != null && body.faint(sp, seconds)) return;
        int t = seconds * 20;
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, t + 20, 0, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 4, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, t, 2, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 1, false, false));
        sp.displayClientMessage(Component.translatable("rpculinary.msg.faint").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    /** «Нет сил» от голода и жажды; без мода тела — ещё и нехватка нутриентов (с Medicine их действие ведёт она). */
    static void applyEffects(ServerPlayer sp, NutritionState m, Settings s, double hydration, BodyEffects.Mods mods) {
        double speed = mods.speed(), attack = 0, stamina = mods.staminaCap();
        boolean weak = Metabolism.hungerStage(m, s) >= 2 || Metabolism.thirstStage(hydration, s) >= 2;
        if (weak) {
            speed += s.weakSpeedPenalty;
            attack += s.weakAttackPenalty;
            stamina *= s.weakStaminaCap;
        }
        if (!RpCoreAPI.bodyHandles(RpIds.BODY_NUTRITION)) {
            if (Nutrients.low(m, NutritionState.CARBS, s)) {
                stamina *= s.carbsLowStaminaCap;
                speed += s.carbsLowSpeedPenalty;
            }
            if (Nutrients.low(m, NutritionState.PROTEIN, s)) attack += s.proteinLowAttackPenalty;
            if (Nutrients.high(m, NutritionState.FAT, s)) {
                stamina *= s.fatHighStaminaCap;
                speed += s.fatHighSpeedPenalty;
            }
        }
        modifier(sp, Attributes.MOVEMENT_SPEED, SPEED_ID, "rpculinary.weak_speed", -Math.min(0.5, speed));
        modifier(sp, Attributes.ATTACK_DAMAGE, ATTACK_ID, "rpculinary.weak_attack", -Math.min(0.8, attack));
        // Максимум выносливости — через розетку выносливости RP Core (без мода выносливости — пропускаем).
        Attribute max = RpCoreAPI.staminaAttribute(StaminaProvider.Stat.MAX);
        if (max != null) modifier(sp, max, STAMINA_ID, "rpculinary.weak_stamina", stamina - 1);
        // Тяжесть в животе, сонливость после сладкого — медленнее отдыхает.
        Attribute regen = RpCoreAPI.staminaAttribute(StaminaProvider.Stat.REGEN);
        if (regen != null) {
            modifier(sp, regen, REGEN_ID, "rpculinary.regen", mods.regen() - 1);
            boolean content = m.contentGiven && m.varietyDay == rpDay(sp);
            modifier(sp, regen, CONTENT_ID, "rpculinary.content", content ? Settings.get().contentStaminaRegen : 0);
        }
    }

    /** RP-сутки: время мира / 24000 (RP Time растягивает сутки — и они растягиваются). */
    static long rpDay(ServerPlayer sp) {
        return Math.floorDiv(sp.level().getDayTime(), 24000L);
    }

    static void modifier(ServerPlayer sp, Attribute attr, UUID id, String name, double amount) {
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst == null) return;
        // Шаг 1 %: плавно меняющиеся множители иначе шлют UpdateAttributes каждый тик (замер трафика 26.10).
        amount = Math.round(amount * 100) / 100.0;
        AttributeModifier old = inst.getModifier(id);
        if (Math.abs(amount) < 1e-6) {
            if (old != null) inst.removeModifier(id);
            return;
        }
        if (old != null && Math.abs(old.getAmount() - amount) < 1e-6) return;
        if (old != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Без мода тела: сытый и напоенный понемногу лечится, истощённый теряет здоровье до порога (не до смерти). */
    static void withoutBody(ServerPlayer sp, NutritionState m, Settings s, double hydration) {
        if (s.hungerEnabled && s.regenSecondsPerHp > 0 && sp.isHurt() && Metabolism.satiety(m, s) >= s.regenMinSatiety
                && hydration >= s.regenMinWater && sp.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            if (++m.regenTimer >= s.regenSecondsPerHp) {
                m.regenTimer = 0;
                sp.heal(1);
            }
        } else {
            m.regenTimer = 0;
        }
        boolean exhausted = (Metabolism.hungerStage(m, s) == 3 && m.starvingSeconds >= s.faintAfterMinutes * 60)
                || m.dehydratedSeconds >= s.dehydrationFaintAfterMinutes * 60;
        if (exhausted && sp.getHealth() > s.healthFloor) {
            if (++m.exhaustionTimer >= s.exhaustionDamageSeconds) {
                m.exhaustionTimer = 0;
                sp.hurt(sp.damageSources().starve(), (float) Math.min(1, sp.getHealth() - s.healthFloor));
            }
        } else {
            m.exhaustionTimer = 0;
        }
    }

    /** Сообщения, когда становится хуже: голоден, очень голоден, голодает; хочется пить и т. д. */
    static void stageMessages(ServerPlayer sp, NutritionData d, NutritionState m, Settings s, double hydration) {
        int hs = Metabolism.hungerStage(m, s);
        int ts = thirstMode(sp, s) == 0 ? 0 : Metabolism.thirstStage(hydration, s);
        if (d.lastHungerStage >= 0 && hs > d.lastHungerStage)
            sp.displayClientMessage(Component.translatable("rpculinary.msg.hunger_" + hs).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        else if (d.lastThirstStage >= 0 && ts > d.lastThirstStage)
            sp.displayClientMessage(Component.translatable("rpculinary.msg.thirst_" + ts).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
        d.lastHungerStage = hs;
        d.lastThirstStage = ts;
    }

    // ------------------------------------------------------------------ клиенту

    /** Послать состояние, если заметно изменилось (или {@code force}). */
    public static void sync(ServerPlayer sp, NutritionData d, boolean force) {
        Settings s = Settings.get();
        NutritionState m = d.state;
        double hyd = hydration(sp, m, s);
        int thirst = thirstMode(sp, s);
        float[] nu = new float[NutritionState.COUNT];
        for (int i = 0; i < nu.length; i++) nu[i] = (float) m.nutrients[i];
        SyncPacket p = new SyncPacket(s.hungerEnabled, thirst, (float) Metabolism.satiety(m, s), (float) (m.stomach / Math.max(1, s.reserveKcal)),
                (float) hyd, Metabolism.hungerStage(m, s), thirst == 0 ? 0 : Metabolism.thirstStage(hyd, s), Nutrients.poorAppetite(m, s),
                Metabolism.hasRoom(m, s, stomachMult(sp, m, s)), nu, Nutrients.fedUpMask(m, s), (float) m.weightKg(), (float) m.fatPercent(),
                (float) m.recentKcal, s.nutrientsEnabled);
        long h = 17;
        h = h * 31 + Math.round(p.satiety() * 400);
        h = h * 31 + Math.round(p.stomach() * 100);
        h = h * 31 + Math.round(p.hydration() * 400);
        h = h * 31 + p.hungerStage() * 7L + p.thirstStage() + thirst * 101L + (p.hunger() ? 1 : 0);
        h = h * 31 + (p.poorAppetite() ? 1 : 0) + (p.room() ? 2 : 0) + (p.nutrientsOn() ? 4 : 0);
        for (float v : nu) h = h * 31 + Math.round(v);
        h = h * 31 + p.fedUpMask();
        h = h * 31 + Math.round(p.weightKg() * 10);
        h = h * 31 + Math.round(p.fatPercent() * 10);
        if (!force && h == d.lastSyncHash) return;
        d.lastSyncHash = h;
        Net.send(sp, p);
    }

    /**
     * Сутки тела в реальных часах: время тела RP Core (длина суток и {@code /time speed} RP Time; слои 06.11).
     * Старый Core без скорости — по длине суток.
     */
    static double liveDayHours() {
        try {
            double r = RpCoreAPI.rpPerReal();
            if (r > 0) return 24.0 / r;
        } catch (NoSuchMethodError old) {
            // Core до 0.13.0.
        }
        return RpCoreAPI.dayLengthSeconds() / 3600.0;
    }
}
