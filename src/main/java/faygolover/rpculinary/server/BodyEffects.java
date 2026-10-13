package faygolover.rpculinary.server;

import faygolover.rpcore.api.BodyNorms;
import faygolover.rpcore.api.BodyProvider;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Conditions;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Последствия питания для тела (RP Stamina Sports docs/08):
 * <ul>
 *   <li>еда и питьё через силу — переполнение желудка (тяжесть → тошнота → боль, рвота, травма желудка) и перепой
 *       (тяжесть → водная интоксикация → отёк мозга);</li>
 *   <li>долгие последствия нехватки и избытка ({@link Conditions}): белковая недостаточность, камни в почках,
 *       давление, цинга, головокружение, тошнота после жирного;</li>
 *   <li>особенности: желудок, зубы, стартовый вес.</li>
 * </ul>
 * Всё — через ощущения (правило RP Medicine): эффекты скрытые, без значков; игрок видит текст, качку экрана,
 * слышит звуки, а медик при осмотре понимает больше. С модом тела состояния и ощущения ведёт он.
 */
public final class BodyEffects {
    private BodyEffects() {}

    /** Скрытый эффект: без значка и частиц. */
    static void hidden(ServerPlayer sp, MobEffect effect, int ticks, int amplifier) {
        MobEffectInstance cur = sp.getEffect(effect);
        if (cur != null && cur.getAmplifier() >= amplifier && cur.getDuration() > ticks / 2) return;
        sp.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, false));
    }

    /** Ощущение текстом: через мод тела (как свои), иначе над панелью; не чаще раза в {@code everySeconds}. */
    static void sense(ServerPlayer sp, NutritionData d, String key, int everySeconds) {
        long now = sp.server.getTickCount();
        Long last = d.sensationTicks.get(key);
        if (last != null && now - last < everySeconds * 20L) return;
        d.sensationTicks.put(key, now);
        String full = "rpculinary.sense." + key;
        BodyProvider body = RpCoreAPI.body();
        if (body != null && body.handles(RpIds.BODY_SENSATIONS) && body.sensation(sp, full)) return;
        sp.displayClientMessage(Component.translatable(full).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    /** Состояние — мод тела (шлём только изменения). true — ведёт мод тела. */
    static boolean condition(ServerPlayer sp, NutritionData d, ResourceLocation id, double severity) {
        BodyProvider body = RpCoreAPI.body();
        if (body == null || !body.handles(RpIds.BODY_CONDITIONS)) return false;
        Double last = d.sentConditions.get(id.toString());
        double sev = Math.round(severity * 20) / 20.0;
        if (last != null && Math.abs(last - sev) < 1e-6) return true;
        if (sev <= 0 && last == null) return true;
        d.sentConditions.put(id.toString(), sev);
        if (sev <= 0) d.sentConditions.remove(id.toString());
        return body.condition(sp, id, sev);
    }

    // ------------------------------------------------------------------ шаг раз в секунду

    /** Модификаторы для общего «нет сил» (скорость, выносливость, восстановление) — копит {@link #step}. */
    record Mods(double speed, double staminaCap, double regen) {}

    static Mods step(ServerPlayer sp, NutritionData d, Settings s, double hydration) {
        NutritionState m = d.state;
        double speed = 0, cap = 1, regen = 1;
        var rnd = sp.getRandom();

        // ---- переполнение желудка
        double over = Metabolism.overfill(m, s, CulinaryService.stomachMult(sp, m, s));
        boolean iron = RpCoreAPI.hasTrait(sp, RpIds.IRON_STOMACH), weak = RpCoreAPI.hasTrait(sp, RpIds.WEAK_STOMACH);
        double nauseaAt = iron ? s.overfillNauseaIron : weak ? s.overfillNauseaWeak : s.overfillNausea;
        double painAt = iron ? s.overfillPainIron : weak ? s.overfillPainWeak : s.overfillPain;
        if (s.hungerEnabled && over >= 1) {
            vomit(sp, d, s);
            m.overfillLimitHits++;
            if (m.overfillWindowSeconds <= 0) m.overfillWindowSeconds = Math.max(0.5, s.hoursPerRpDay) * 3600;
            if (m.overfillLimitHits >= s.stomachInjuryAfter) {
                m.overfillLimitHits = 0;
                if (!condition(sp, d, RpIds.COND_STOMACH_INJURY, 1)) {
                    sp.hurt(sp.damageSources().generic(), 3f);
                    hidden(sp, MobEffects.WEAKNESS, 2400, 0);
                }
                sense(sp, d, "stomach_injury", 1);
            }
            over = 0;
        }
        if (s.hungerEnabled && over > 0) {
            speed += s.heavySpeedPenalty;
            regen *= 1 - s.heavyRegenPenalty;
            hidden(sp, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            boolean medicine = condition(sp, d, RpIds.COND_OVERFILL, Math.min(1, over));
            if (over < nauseaAt) {
                sense(sp, d, "heavy", 45);
            } else if (over < painAt) {
                hidden(sp, MobEffects.CONFUSION, 120, 0);
                sense(sp, d, "nausea", 30);
                if (rnd.nextDouble() < s.nauseaVomitPerMinute / 60.0) vomit(sp, d, s);
            } else {
                hidden(sp, MobEffects.CONFUSION, 160, 0);
                sense(sp, d, "stomach_pain", 20);
                if (!medicine && sp.tickCount % 1200 < 20) sp.hurt(sp.damageSources().generic(), 1f);
                if (rnd.nextDouble() < s.painVomitPerMinute / 60.0) vomit(sp, d, s);
            }
        } else {
            condition(sp, d, RpIds.COND_OVERFILL, 0);
        }

        // ---- перепил воды
        if (CulinaryService.thirstMode(sp, s) == 1 && m.water > 100) {
            if (m.water >= s.brainEdemaAt) {
                double sev = Math.min(1, (m.water - s.brainEdemaAt) / Math.max(1, s.maxWater - s.brainEdemaAt) * 0.5 + 0.5);
                if (!condition(sp, d, RpIds.COND_OVERHYDRATION, sev)) {
                    long now = sp.server.getTickCount();
                    if (d.edemaFaintTick < 0 || now - d.edemaFaintTick > 2400) {
                        d.edemaFaintTick = now;
                        CulinaryService.faint(sp, 10);
                    }
                }
                hidden(sp, MobEffects.CONFUSION, 200, 0);
                sense(sp, d, "brain_edema", 20);
                cap *= 0.8;
            } else if (m.water >= s.waterIntoxicationAt) {
                condition(sp, d, RpIds.COND_OVERHYDRATION, 0.3);
                hidden(sp, MobEffects.CONFUSION, 120, 0);
                hidden(sp, MobEffects.WEAKNESS, 60, 0);
                sense(sp, d, "water_intoxication", 30);
                cap *= 0.8;
            } else {
                condition(sp, d, RpIds.COND_OVERHYDRATION, 0);
                speed += 0.05;
                sense(sp, d, "overdrink", 60);
            }
        } else {
            condition(sp, d, RpIds.COND_OVERHYDRATION, 0);
        }

        // ---- долгие последствия
        for (int i = 0; i < Conditions.COUNT; i++) {
            double sev = Conditions.severity(m, i);
            ResourceLocation id = condId(i);
            boolean medicine = id != null && condition(sp, d, id, sev);
            if (sev <= 0) continue;
            switch (i) {
                case Conditions.PROTEIN_DEFICIENCY -> {
                    if (!medicine) speed += s.proteinDeficiencySpeed;
                    sense(sp, d, "protein_deficiency", 240);
                }
                case Conditions.KIDNEY_STONES -> {
                    if (rnd.nextDouble() < 1 / 480.0) {
                        if (!medicine) {
                            sp.hurt(sp.damageSources().generic(), 1f);
                            hidden(sp, MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
                        }
                        sense(sp, d, "kidney", 1);
                    }
                }
                case Conditions.HIGH_PRESSURE -> sense(sp, d, "pressure", 400);
                case Conditions.SCURVY -> {
                    if (!medicine) hidden(sp, MobEffects.WEAKNESS, 60, sev >= 1 ? 1 : 0);
                    sense(sp, d, sev >= 1 ? "scurvy_severe" : "scurvy", 180);
                }
                case Conditions.CARBS_DIZZY -> {
                    if (sp.isSprinting() && rnd.nextDouble() < 0.02) {
                        hidden(sp, MobEffects.CONFUSION, 80, 0);
                        sense(sp, d, "dizzy", 20);
                    }
                }
                default -> { }
            }
        }
        if (d.drowsyUntil > sp.server.getTickCount()) regen *= 0.9;

        // ---- вес относительно нормы (по полу и возрасту из анкеты RP Core)
        if (s.weightEnabled) {
            BodyNorms.BodyState st = BodyNorms.state(RpCoreAPI.profile(sp), m.weightKg(), m.fatPercent());
            if (st == BodyNorms.BodyState.OVER) speed += s.overweightSpeedPenalty;
            else if (st == BodyNorms.BodyState.OBESE) speed += s.obeseSpeedPenalty;
        }
        applyBodyTrait(sp, m, s);
        return new Mods(speed, cap, regen);
    }

    private static ResourceLocation condId(int i) {
        return switch (i) {
            case Conditions.PROTEIN_DEFICIENCY -> RpIds.COND_PROTEIN_DEFICIENCY;
            case Conditions.KIDNEY_STONES -> RpIds.COND_KIDNEY_STONES;
            case Conditions.HIGH_PRESSURE -> RpIds.COND_HIGH_PRESSURE;
            case Conditions.SCURVY -> RpIds.COND_SCURVY;
            default -> null;
        };
    }

    /** Рвота: желудок пустеет, немного воды уходит. */
    static void vomit(ServerPlayer sp, NutritionData d, Settings s) {
        Metabolism.vomit(d.state);
        if (CulinaryService.thirstMode(sp, s) == 1) d.state.water = Math.max(0, d.state.water - 5);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.6f, 0.6f);
        hidden(sp, MobEffects.CONFUSION, 100, 0);
        d.sensationTicks.remove("vomit");
        sense(sp, d, "vomit", 1);
    }

    // ------------------------------------------------------------------ еда

    /** После еды: желудок (отравление), зубы, тошнота после жирного, сонливость после сладкого. */
    static void afterEating(ServerPlayer sp, NutritionData d, Settings s, Food food, boolean wasFull) {
        var rnd = sp.getRandom();
        if (wasFull && food.kcal() > 0 && !d.forcedNoted) {
            d.forcedNoted = true;
            sense(sp, d, "forced", 20);
        } else if (!wasFull) {
            d.forcedNoted = false;
        }
        if (food.kcal() > 0 && RpCoreAPI.hasTrait(sp, RpIds.WEAK_STOMACH) && rnd.nextDouble() < s.weakStomachAnyFood) {
            sp.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, false, false));
            hidden(sp, MobEffects.CONFUSION, 100, 0);
            sense(sp, d, "sick", 1);
        }
        if (food.kcal() > 0 && RpCoreAPI.hasTrait(sp, RpIds.BAD_TEETH) && rnd.nextDouble() < s.badTeethChance + food.kcal() / 2000.0) {
            if (RpCoreAPI.body() == null) sp.hurt(sp.damageSources().generic(), 1f);
            sense(sp, d, "teeth", 1);
        }
        NutritionState m = d.state;
        if (m.condActive[Conditions.FAT_NAUSEA] && food.fat() >= 15) {
            hidden(sp, MobEffects.CONFUSION, 120, 0);
            sense(sp, d, "fat_nausea", 1);
        }
        if (s.nutrientsEnabled && m.nutrients[NutritionState.CARBS] > s.nutrientHigh && food.carbs() >= 30) {
            d.drowsyUntil = sp.server.getTickCount() + 600 * 20L;
            sense(sp, d, "drowsy", 60);
        }
    }

    /** Множитель шанса отравления от особенностей желудка. */
    static double poisonFactor(ServerPlayer sp, Settings s) {
        double k = 1;
        if (RpCoreAPI.hasTrait(sp, RpIds.IRON_STOMACH)) k *= s.ironStomachPoison;
        if (RpCoreAPI.hasTrait(sp, RpIds.WEAK_STOMACH)) k *= s.weakStomachPoison;
        return k;
    }

    // ------------------------------------------------------------------ стартовый вес

    /** Особенность веса (Дистрофик, Избыточный вес, Жирный) задаёт стартовое тело — один раз на особенность. */
    static void applyBodyTrait(ServerPlayer sp, NutritionState m, Settings s) {
        String trait = RpCoreAPI.hasTrait(sp, RpIds.EMACIATED) ? "emaciated"
                : RpCoreAPI.hasTrait(sp, RpIds.OBESE) ? "obese"
                : RpCoreAPI.hasTrait(sp, RpIds.OVERWEIGHT) ? "overweight" : "";
        if (trait.isEmpty() || trait.equals(m.appliedBodyTrait) || !s.weightEnabled) return;
        var p = RpCoreAPI.profile(sp);
        BodyNorms.FatNorm n = BodyNorms.fatNorm(p.sex(), p.age());
        double fat = switch (trait) {
            case "emaciated" -> Math.max(s.minFatPercent + 0.5, n.low() * 0.6);
            case "obese" -> n.obese() + 6;
            default -> (n.high() + n.obese()) / 2;
        };
        double lean = m.leanKg;
        double f = Math.min(s.maxFatPercent, fat) / 100.0;
        m.fatKg = lean * f / (1 - f);
        m.appliedBodyTrait = trait;
    }
}
