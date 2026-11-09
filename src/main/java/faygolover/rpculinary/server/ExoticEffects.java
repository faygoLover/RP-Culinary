package faygolover.rpculinary.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Cooking;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.integration.Lso;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Группы экзотики действуют на того, кто ест (решения Alex 29.10.2026, группы — {@link Exotic}): от природы продукта и от
 * того, как его приготовили. «Без знания» — блюдо приготовил повар, не знавший группу ({@link CookingService#wildCook}),
 * или едок сам ест неприготовленное и группу не знает. Особые продукты внутри групп — теги {@code rpculinary:effect/<действие>}.
 * Соседи — мягко, отражением: RP Psyche (стресс, рассудок), RP Medicine (инфекция), Mekanism (облучение), LSO (согрев).
 * Нет соседа — простая замена (отравление) или ничего. Без левитации, телепортов и прочего несерьёзного.
 */
public final class ExoticEffects {
    private ExoticEffects() {}

    static final TagKey<Item> VENOM = tag("venom"), HALLUCINOGEN = tag("hallucinogen"), LIVER_POISON = tag("liver_poison"),
            GUT = tag("gut"), BLOOD = tag("blood"), NITRATE = tag("nitrate"), FATIGUE = tag("mining_fatigue"),
            DARKNESS = tag("darkness"), NETHER = tag("nether"), INFECTED = tag("infected");

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(new ResourceLocation(RpCulinary.MODID, "effect/" + path));
    }

    private static boolean has(int mask, String group) {
        return (mask & Exotic.bit(group)) != 0;
    }

    /** Тиков в RP-сутках. */
    static long rpDayTicks(Settings s) {
        return (long) (s.rpDayHours() * 3600 * 20);
    }

    /** Часы тела игрока (см. {@link NutritionData#bodyClock}): старые отметки были по игровому времени, с него и старт. */
    static long now(ServerPlayer sp, NutritionData d) {
        if (d.bodyClock <= 0) d.bodyClock = Math.max(1, sp.server.overworld().getGameTime());
        return d.bodyClock;
    }

    /** Прокрутка {@code /rptime}: часы тела уходят вперёд на ту же долю суток. */
    public static void advance(ServerPlayer sp, double seconds) {
        NutritionData d = NutritionData.get(sp);
        if (d != null) d.bodyClock = now(sp, d) + (long) (rpDayTicks(Settings.get()) * seconds / RpCoreAPI.dayLengthSeconds());
    }

    /** Съел долю {@code fraction} предмета: действия всех его групп. */
    public static void afterEating(ServerPlayer sp, NutritionData d, ItemStack used, double fraction, Settings s) {
        if (!s.exoticEffects || fraction <= 0) return;
        int mask = NutritionTable.exoticMask(sp.server, used);
        if (mask == 0) return;
        var rnd = sp.getRandom();
        double k = Math.min(1, fraction * 2);
        int quality = CookingService.quality(used);
        boolean raw = quality < 0 && NutritionTable.raw(sp.server, used.getItem());
        boolean under = raw || quality == Cooking.UNDERCOOKED;
        boolean cookWild = CookingService.wildCook(used);
        boolean starving = Metabolism.hungerStage(d.state, s) >= 2;

        if (has(mask, "game") && under) {  // паразиты (трихинеллёз): признаки через 1–2 RP-суток
            long at = now(sp, d) + (long) (rpDayTicks(s) * s.parasiteDelayRpDays * (0.7 + 0.6 * rnd.nextDouble()));
            if (d.parasitesAt <= 0 || at < d.parasitesAt) d.parasitesAt = at;
        }
        if (has(mask, "pets") && !starving) {
            Psyche.stress(sp, s.petsStress * k);
            BodyEffects.sense(sp, d, "pets_guilt", 30);
        }
        if (has(mask, "pet_food")) {
            Psyche.stress(sp, s.petFoodStress * k * (starving ? 0.3 : 1));
            BodyEffects.sense(sp, d, "pet_food", 60);
        }
        if (has(mask, "seafood")) {
            if (under && rnd.nextDouble() < s.seafoodPoison * k * CulinaryService.poisonFactor(sp, used, s)) {
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
                if (rnd.nextDouble() < 0.5) BodyEffects.vomit(sp, d, s);
                thirst(sp, d, 10 * k, s);
                BodyEffects.sense(sp, d, "seafood_sick", 1);
            }
            if (RpCoreAPI.hasTrait(sp, Exotic.SEAFOOD_ALLERGY)) {  // аллергия — даже от хорошо приготовленного
                sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
                BodyEffects.hidden(sp, MobEffects.CONFUSION, 100, 0);
                BodyEffects.sense(sp, d, "allergy", 1);
            }
        }
        if (has(mask, "fugu") && (cookWild || quality < 0 && !Exotic.knows(sp, "fugu"))) {  // тетродотоксин: паралич
            int t = (int) (s.fuguSeconds * 20 * Math.max(0.5, k));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 3));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 2));
            sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, t, 2));
            sp.addEffect(new MobEffectInstance(MobEffects.POISON, Math.min(t, 300), 0));
            BodyEffects.sense(sp, d, "fugu", 1);
        }
        if (has(mask, "insects")) {  // отвращение слабеет с привычкой
            double habit = Math.max(0, 1 - d.insectMeals / Math.max(1, s.insectHabitPortions));
            if (habit > 0) {
                Psyche.stress(sp, s.disgustStress * k * habit);
                BodyEffects.sense(sp, d, "disgust", 30);
            }
            d.insectMeals += k;
            if (raw && used.is(VENOM)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        }
        if (has(mask, "offal")) {
            boolean dirty = cookWild || quality < 0 && !Exotic.knows(sp, "offal");
            if (used.is(GUT) && (dirty || raw) && rnd.nextDouble() < s.gutInfection * k) {  // не вычищенные кишки и желудок
                if (!Medicine.infect(sp, s.gutSepsis, 1)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
                thirst(sp, d, 15 * k, s);
                BodyEffects.sense(sp, d, "gut", 1);
            }
            if (used.is(BLOOD) && rnd.nextDouble() < s.bloodPoison * k * CulinaryService.poisonFactor(sp, used, s))
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        }
        if (has(mask, "mushrooms")) {
            if (used.is(LIVER_POISON)) {  // поганка: сначала ничего, через RP-сутки — тяжёлое отравление
                long at = now(sp, d) + (long) (rpDayTicks(s) * s.liverPoisonDelayRpDays);
                if (d.liverPoisonAt <= 0 || at < d.liverPoisonAt) d.liverPoisonAt = at;
            } else if (used.is(HALLUCINOGEN)) {  // мухомор и т. п. — ядовит всегда (решение 29.10)
                trip(sp, d, k, s);
            } else if (cookWild && rnd.nextDouble() < s.mushroomWildPoison * k) {  // повар спутал грибы
                trip(sp, d, k, s);
            }
        }
        if (has(mask, "other_worlds")) {
            if (used.is(NETHER)) {
                MobEffect hot = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("legendarysurvivaloverhaul", "hot_food"));
                if (hot != null && Lso.loaded()) sp.addEffect(new MobEffectInstance(hot, (int) (s.netherWarmSeconds * 20), 0));
                thirst(sp, d, s.netherThirst * k, s);
                BodyEffects.sense(sp, d, "nether_heat", 30);
            } else {  // Край: дезориентация, звон в ушах
                BodyEffects.hidden(sp, MobEffects.CONFUSION, (int) (s.endConfusionSeconds * 20), 0);
                BodyEffects.sense(sp, d, "end_ring", 30);
            }
        }
        if (has(mask, "monsters")) {  // токсины: знание снижает шанс, но не убирает
            boolean known = quality >= 0 ? !cookWild : Exotic.knows(sp, "monsters");
            double chance = s.monstersPoison * (known ? s.monstersKnownFactor : 1) * k * CulinaryService.poisonFactor(sp, ItemStack.EMPTY, s);
            if (rnd.nextDouble() < chance) {
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
                sp.displayClientMessage(Component.translatable("rpculinary.msg.monster_sick").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
            }
            if (used.is(NITRATE)) {
                BodyEffects.hidden(sp, MobEffects.CONFUSION, 200, 0);
                BodyEffects.sense(sp, d, "nitrate", 1);
            }
            if (used.is(FATIGUE)) sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, (int) (s.fatigueSeconds * 20), 1));
            if (used.is(DARKNESS)) sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
        }
        if (has(mask, "human") && !Exotic.cannibal(sp)) human(sp, d, k, s);
        if (has(mask, "spoiled")) {
            if (rnd.nextDouble() < s.spoiledPoison * k * BodyEffects.poisonFactor(sp, s)) {
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
                if (rnd.nextDouble() < 0.3) BodyEffects.vomit(sp, d, s);
                sp.displayClientMessage(Component.translatable("rpculinary.msg.spoiled_sick").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
            }
            if (used.is(INFECTED)) {
                if (!Medicine.infect(sp, s.infectedSepsis * k, s.infectedSepsisHours)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
                BodyEffects.sense(sp, d, "infected", 30);
            }
        }
        if (has(mask, "radioactive")) {
            if (!Mekanism.radiate(sp, s.radioactiveDose * k)) sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            BodyEffects.sense(sp, d, "radioactive", 30);
        }
        if (has(mask, "anomalous")) {
            MobEffect e = ANOMALY.get(rnd.nextInt(ANOMALY.size()));
            sp.addEffect(new MobEffectInstance(e, e == MobEffects.BLINDNESS ? 40 : e == MobEffects.CONFUSION ? 100 : 300, 0));
            Psyche.sanityHit(sp, s.anomalousSanity * k);
            BodyEffects.sense(sp, d, "anomalous", 1);
        }
        if (has(mask, "sweets")) sweets(sp, d, k, s);
    }

    /** Аномальное: одно странное ощущение — видно в темноте, лёгкость, свет режет глаза, мир плывёт. */
    static final List<MobEffect> ANOMALY = List.of(MobEffects.NIGHT_VISION, MobEffects.SLOW_FALLING, MobEffects.BLINDNESS, MobEffects.CONFUSION);

    /** Ядовитые грибы: тошнота, галлюцинации (удар по рассудку). */
    static void trip(ServerPlayer sp, NutritionData d, double k, Settings s) {
        sp.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        BodyEffects.hidden(sp, MobEffects.CONFUSION, 300, 0);
        Psyche.sanityHit(sp, s.mushroomSanity * k);
        BodyEffects.sense(sp, d, "mushroom_trip", 1);
    }

    /**
     * Сладости (решение 29.10): сразу прилив — выносливость восстанавливается быстрее пару минут, потом спад (сонливость);
     * «зуб заныл» — когда за RP-сутки съедено много сладкого, а с «Плохими зубами» — с первой конфеты.
     */
    public static void sweets(ServerPlayer sp, NutritionData d, double k, Settings s) {
        long tick = sp.server.getTickCount();
        if (s.candyDrowsy) {
            d.sugarUntil = tick + (long) (s.sugarSeconds * 20);
            d.drowsyUntil = Math.max(d.drowsyUntil, d.sugarUntil + 600 * 20L);
            BodyEffects.sense(sp, d, "sugar_rush", 120);
        }
        d.sweetPortions += k;
        boolean badTeeth = RpCoreAPI.hasTrait(sp, RpIds.BAD_TEETH);
        if ((badTeeth || d.sweetPortions >= s.sweetsTeethAfter) && sp.getRandom().nextDouble() < s.candyTeethChance * k) {
            if (RpCoreAPI.body() == null) sp.hurt(sp.damageSources().generic(), 1f);
            BodyEffects.sense(sp, d, "teeth", 1);
        }
    }

    /** Сильная жажда: своя вода или LSO. */
    static void thirst(ServerPlayer sp, NutritionData d, double percent, Settings s) {
        int mode = CulinaryService.thirstMode(sp, s);
        if (mode == 1) d.state.water = Math.max(0, d.state.water - percent);
        else if (mode == 2) Lso.addWater(sp, -percent);
    }

    /** Человечина без «Каннибала»: рассудок и рвота (куру убрана 29.10.2026: серьёзное РП). */
    public static void human(ServerPlayer sp, NutritionData d, double k, Settings s) {
        Psyche.sanityHit(sp, s.humanSanity * k);
        Psyche.stress(sp, s.humanSanity * k);
        if (sp.getRandom().nextDouble() < s.humanVomitChance * k) BodyEffects.vomit(sp, d, s);
        else BodyEffects.sense(sp, d, "human", 1);
    }

    /** Раз в секунду: отложенные паразиты и отравление поганкой; привычка к насекомым и счёт сладкого убывают. */
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 20 != 3 || !sp.isAlive()) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        Settings s = Settings.get();
        double perSecond = 20.0 / rpDayTicks(s);
        if (d.insectMeals > 0) d.insectMeals = Math.max(0, d.insectMeals - perSecond * s.insectHabitPortions / 7.0);  // за неделю
        if (d.sweetPortions > 0) d.sweetPortions = Math.max(0, d.sweetPortions - perSecond * Math.max(1, s.sweetsTeethAfter));
        if (sp.isCreative() || sp.isSpectator()) return;
        if (RpCoreAPI.timeRunning()) d.bodyClock = now(sp, d) + 20;
        long now = now(sp, d);
        if (d.parasitesAt > 0 && now >= d.parasitesAt) {
            d.parasitesAt = 0;
            if (!Medicine.infect(sp, s.parasiteSepsis, 2)) sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 10, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60 * 5, 0));
            BodyEffects.sense(sp, d, "parasites", 1);
        }
        if (d.liverPoisonAt > 0 && now >= d.liverPoisonAt) {
            d.liverPoisonAt = 0;
            sp.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 60, 1));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 10, 1));
            if (!Medicine.infect(sp, s.liverPoisonSepsis, 1)) sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 60 * 5, 1));
            BodyEffects.sense(sp, d, "liver_poison", 1);
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
