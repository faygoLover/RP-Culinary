package faygolover.rpculinary.server;

import faygolover.rpcore.api.BodyProvider;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.StaminaProvider;
import faygolover.rpcore.api.event.ExertionEvent;
import faygolover.rpcore.api.event.FoodEatenEvent;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Nutrients;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.data.DrinkRules;
import faygolover.rpculinary.data.NutritionRules;
import faygolover.rpculinary.integration.Lso;
import faygolover.rpculinary.network.FoodTablePacket;
import faygolover.rpculinary.network.Net;
import faygolover.rpculinary.network.SyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

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

    private static final UUID SPEED_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c11");
    private static final UUID ATTACK_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c12");
    private static final UUID STAMINA_ID = UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c13");
    private static final ResourceKey<DamageType> LSO_DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("legendarysurvivaloverhaul", "dehydration"));
    private static final Random RANDOM = new Random();

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

    /** Во сколько раз желудок меньше обычного: анорексия, плохой аппетит. */
    public static double stomachMult(Player p, NutritionState m, Settings s) {
        return (anorexic(p) ? s.anorexicStomach : 1) * (Nutrients.poorAppetite(m, s) ? s.poorAppetiteStomach : 1);
    }

    /** 0 — жажды нет, 1 — своя, 2 — LSO. */
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
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        ensureInit(sp, d);
        Settings s = Settings.get();
        long tick = sp.server.getTickCount();
        FoodData fd = sp.getFoodData();
        if (s.hungerEnabled) {
            int cur = fd.getFoodLevel();
            // Прирост ванильной сытости сразу после клика по блоку — съел с блока (торт, тарелка). Без клика прирост
            // не считаем: на «Мирной» ваниль сама подкармливает игрока — это не еда.
            if (d.mirroredFood >= 0 && cur > d.mirroredFood && d.ateItemTick != tick && d.clickedTick >= tick - 1 && active(sp))
                eatFromBlock(sp, d, d.clickedBlock, cur - d.mirroredFood);
            int lock = Metabolism.hasRoom(d.state, s, stomachMult(sp, d.state, s)) ? FOOD_ROOM : FOOD_FULL;
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
        Metabolism.Input in = new Metabolism.Input();
        in.dt = 1;
        if (d.exertionStamina > 0) in.exertionKcal = d.exertionStamina / 100.0 * s.kcalPer100Stamina;
        else if (RpCoreAPI.stamina() == null) in.exertionKcal = d.sprintTicks / 20.0 / 60.0 * s.sprintKcalPerMinute;
        d.exertionStamina = 0;
        d.sprintTicks = 0;
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
        }
        double hydration = hydration(sp, m, s);
        applyEffects(sp, m, s, hydration);
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
    static void applyEffects(ServerPlayer sp, NutritionState m, Settings s, double hydration) {
        double speed = 0, attack = 0, stamina = 1;
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
    }

    private static void modifier(ServerPlayer sp, Attribute attr, UUID id, String name, double amount) {
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst == null) return;
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

    // ------------------------------------------------------------------ еда

    /**
     * Приелось — ест без аппетита, дольше. Длительность не меняем (анимация руки считает её от «родной»
     * длительности предмета), а откатываем часть тиков — одинаково на клиенте и сервере (как в RP Medicine).
     */
    public static void onUseTick(LivingEntityUseItemEvent.Tick e) {
        if (!(e.getEntity() instanceof Player p) || !e.getItem().isEdible()) return;
        Settings s = Settings.get();
        boolean fedUp;
        if (p.level().isClientSide) {
            fedUp = faygolover.rpculinary.client.ClientNutrition.fedUp(e.getItem());
        } else {
            NutritionState m = state(p);
            fedUp = m != null && p instanceof ServerPlayer sp && Nutrients.fedUp(m, NutritionTable.category(sp.server, e.getItem().getItem()), s);
        }
        if (fedUp && slowTick(p.tickCount, s.monotonyEatSlow) && e.getDuration() < e.getItem().getUseDuration())
            e.setDuration(e.getDuration() + 1);
    }

    /** Откатить ли этот тик: доля откатов 1 − 1/factor, равномерно по времени. */
    static boolean slowTick(int tick, double factor) {
        if (factor <= 1.0) return false;
        double k = 1 - 1 / Math.min(factor, 10);
        return Math.floor(tick * k) != Math.floor((tick - 1) * k);
    }

    /** Доел или допил предмет. */
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !active(sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        ensureInit(sp, d);
        ItemStack used = e.getItem();
        Settings s = Settings.get();
        double water = thirstMode(sp, s) == 1 ? DrinkRules.water(used) : 0;
        if (water <= 0 && thirstMode(sp, s) == 1 && soup(used)) water = s.soupWater;
        Food food = itemFood(sp, used);
        if (food == null && water <= 0) return;
        if (used.isEdible()) d.ateItemTick = sp.server.getTickCount();
        if (food == null) food = Food.ZERO;
        int quality = CookingService.quality(used);
        boolean gourmet = RpCoreAPI.hasTrait(sp, RpIds.GOURMET);
        if (quality >= 0) food = faygolover.rpculinary.core.Cooking.apply(food, quality, s);
        // Гурман: хорошее радует сильнее (витамины), однообразное надоедает быстрее.
        if (gourmet && quality >= faygolover.rpculinary.core.Cooking.GOOD)
            food = new Food(food.kcal(), food.protein(), food.fat(), food.carbs(), food.vitamins() * 1.1, food.water());
        double monotony = quality >= 0 ? faygolover.rpculinary.core.Cooking.monotonyWeight(quality) : 1;
        if (gourmet) monotony *= quality >= faygolover.rpculinary.core.Cooking.GOOD ? 0.5 : s.gourmetMonotony;
        if (water > 0) food = food.plus(new Food(0, 0, 0, 0, 0, water));
        int cat = NutritionTable.category(sp.server, used.getItem());
        consume(sp, d, food, cat, used, monotony);
        double poison = s.rawMeatPoisonChance * (RpCoreAPI.hasTrait(sp, RpIds.CLEAN) ? s.cleanPoisonFactor : 1);
        if (rawMeat(sp, used, cat) && sp.getRandom().nextDouble() < poison) {
            sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            sp.displayClientMessage(Component.translatable("rpculinary.msg.raw_meat").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
        }
    }

    /** Сырое мясо и рыба: базовый продукт (не блюдо) вида «мясо» или «рыба»; тухлятину и паучий глаз ведёт ваниль. */
    static boolean rawMeat(ServerPlayer sp, ItemStack st, int cat) {
        if (cat != NutritionState.category("meat") && cat != NutritionState.category("fish")) return false;
        if (st.is(net.minecraft.world.item.Items.ROTTEN_FLESH) || st.is(net.minecraft.world.item.Items.SPIDER_EYE)) return false;
        return NutritionTable.source(sp.server, st.getItem()) == NutritionTable.Source.RULE;
    }

    /** Суп, похлёбка, рагу — по названию предмета (у модов готовки их десятки). */
    static boolean soup(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String p = id.getPath();
        return p.contains("soup") || p.contains("stew") || p.contains("broth") || p.contains("borscht") || p.contains("chowder")
                || p.contains("porridge") || p.contains("congee");
    }

    /** Состав съеденного предмета с учётом качества (Kaleidoscope меняет сытость готового блюда) или null. */
    @Nullable
    public static Food itemFood(ServerPlayer sp, ItemStack stack) {
        Item item = stack.getItem();
        FoodProperties fp = stack.getFoodProperties(sp);
        if (fp == null && NutritionRules.base(item) == null) return null;
        Food base = NutritionTable.get(sp.server, item);
        if (base == null) return null;
        FoodProperties def = item.getFoodProperties();
        if (fp != null && def != null && def.getNutrition() > 0 && fp.getNutrition() != def.getNutrition())
            base = base.scale(Math.max(0.3, Math.min(2, fp.getNutrition() / (double) def.getNutrition())));
        return base;
    }

    /** Кликнул по блоку: если в этот тик прибавится ванильная сытость — значит, съел с него. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        d.clickedBlock = sp.level().getBlockState(e.getPos()).getBlock();
        d.clickedTick = sp.server.getTickCount();
    }

    /** Кусок с блока: калории — по ванильной сытости куска, доли БЖУ — по составу блюда. */
    static void eatFromBlock(ServerPlayer sp, NutritionData d, @Nullable Block block, int points) {
        Settings s = Settings.get();
        double kcal = points * s.kcalPerFoodPoint;
        Item item = block != null ? block.asItem() : null;
        Food base = item != null ? NutritionTable.get(sp.server, item) : null;
        Food food = base != null ? base.withKcalScaled(kcal) : new Food(kcal, kcal * 0.08 / 4, kcal * 0.12 / 9, kcal * 0.8 / 4, 2, 0);
        int cat = item != null ? NutritionTable.category(sp.server, item) : -1;
        consume(sp, d, food, cat, item != null ? new ItemStack(item) : ItemStack.EMPTY, 1);
    }

    /** Учесть съеденное: в желудок и нутриенты, сообщения, событие для других модов. */
    public static void consume(ServerPlayer sp, NutritionData d, Food food, int cat, ItemStack stack, double monotonyWeight) {
        Settings s = Settings.get();
        NutritionState m = d.state;
        boolean fed = Metabolism.eat(m, food, cat, s, monotonyWeight);
        if (fed) sp.displayClientMessage(Component.translatable("rpculinary.monotony." + NutritionState.CATEGORIES[cat])
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        boolean room = Metabolism.hasRoom(m, s, stomachMult(sp, m, s));
        if (!room && d.lastRoom && food.kcal() > 0) {
            sp.displayClientMessage(Component.translatable("rpculinary.msg.full").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f,
                    0.9f + sp.getRandom().nextFloat() * 0.2f);
        }
        d.lastRoom = room;
        MinecraftForge.EVENT_BUS.post(new FoodEatenEvent(sp, stack.copy(), food.kcal(), food.protein(), food.fat(), food.carbs(),
                food.vitamins(), food.water()));
        sync(sp, d, true);
    }

    // ------------------------------------------------------------------ прочие события

    public static void onExertion(ExertionEvent e) {
        NutritionData d = NutritionData.get(e.getEntity());
        if (d != null) d.exertionStamina += e.stamina();
    }

    /** Голод и жажда больше не убивают: ванильный голодный урон и обезвоживание LSO отменяются (обмороки — наши). */
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Settings s = Settings.get();
        if (e.getSource().is(LSO_DEHYDRATION)) e.setCanceled(true);
        else if (s.hungerEnabled && e.getSource().is(DamageTypes.STARVE) && sp.getHealth() - e.getAmount() < s.healthFloor - 1e-3)
            e.setCanceled(true);
    }

    /** Смерть и возвращение из Края: тело то же; после смерти желудок пуст, но не на грани. */
    public static void onClone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        NutritionData from = NutritionData.get(e.getOriginal());
        NutritionData to = NutritionData.get(e.getEntity());
        e.getOriginal().invalidateCaps();
        if (from == null || to == null) return;
        to.state.copyFrom(from.state);
        if (e.isWasDeath()) {
            Settings s = Settings.get();
            NutritionState m = to.state;
            m.stomach = 0;
            m.energy = Math.max(m.energy, s.reserveKcal * 0.5);
            m.water = Math.max(m.water, 50);
            m.starvingSeconds = 0;
            m.dehydratedSeconds = 0;
            m.faintCountdown = -1;
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        state(sp);
        sendFoodTable(sp);
        NutritionData d = NutritionData.get(sp);
        if (d != null) sync(sp, d, true);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d != null) sync(sp, d, true);
    }

    public static void onDatapackSync(OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) sendFoodTable(e.getPlayer());
        else for (ServerPlayer sp : e.getPlayerList().getPlayers()) sendFoodTable(sp);
    }

    static void sendFoodTable(ServerPlayer sp) {
        MinecraftServer server = sp.server;
        Net.send(sp, new FoodTablePacket(NutritionTable.snapshot(server), NutritionTable.categories(server)));
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
}
