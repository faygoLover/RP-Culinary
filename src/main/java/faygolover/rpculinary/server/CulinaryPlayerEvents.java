package faygolover.rpculinary.server;

import static faygolover.rpculinary.server.CulinaryService.*;
import static faygolover.rpculinary.server.CulinaryEating.*;
import faygolover.rpcore.api.event.StaminaRestoredEvent;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.network.FoodTablePacket;
import faygolover.rpculinary.network.Net;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import faygolover.rpcore.api.Consumables;
import faygolover.rpcore.api.data.PlayerStore;
import faygolover.rpcore.api.event.RpCharacterWipeEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * События жизни игрока для питания: восстановление выносливости, удар, вайп персонажа, что переносится при
 * возрождении, что отправить клиенту на входе и после возрождения, таблица еды и напитков для подсказок.
 * Выделено из {@link CulinaryService}.
 */
public final class CulinaryPlayerEvents {
    private CulinaryPlayerEvents() {}

    // ------------------------------------------------------------------ прочие события

    /** Калории платятся за отдых после нагрузки: сколько выносливости восстановилось (RP Stamina 1.4+). */
    public static void onStaminaRestored(StaminaRestoredEvent e) {
        NutritionData d = NutritionData.get(e.getEntity());
        if (d != null) d.exertionStamina += e.amount();
    }

    /** Голод и жажда больше не убивают: ванильный голодный урон и обезвоживание LSO отменяются (обмороки — наши). */
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Settings s = Settings.get();
        if (e.getSource().is(LSO_DEHYDRATION)) e.setCanceled(true);
        else if (s.hungerEnabled && e.getSource().is(DamageTypes.STARVE) && sp.getHealth() - e.getAmount() < s.healthFloor - 1e-3)
            e.setCanceled(true);
    }

    /** Вайп персонажа: питание и вес как у нового. Слушаем после мода тела: вес берём уже обнулённый. */
    public static void onWipe(RpCharacterWipeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        d.state.copyFrom(new NutritionState());
        d.bodyClock = 0;
        d.parasitesAt = d.liverPoisonAt = 0;
        d.insectMeals = d.sweetPortions = 0;
        d.drowsyUntil = d.sugarUntil = -1;
        d.sentConditions.clear();
        ensureInit(sp, d);
        sync(sp, d, true);
    }

    /** Смерть и возвращение из Края: тело то же; после смерти желудок пуст, но не на грани. */
    /**
     * Что переносится при возрождении (зовёт RP Core через {@code NutritionData.STORE}): состояние питания и отметки
     * тела; после смерти желудок пуст, энергия не ниже половины резерва, вода не ниже 50, счётчики голода сброшены.
     */
    public static void copyOnClone(NutritionData from, NutritionData to, boolean wasDeath) {
        to.state.copyFrom(from.state);
        to.bodyClock = from.bodyClock;
        to.parasitesAt = from.parasitesAt;
        to.liverPoisonAt = from.liverPoisonAt;
        to.insectMeals = from.insectMeals;
        to.sweetPortions = from.sweetPortions;
        if (wasDeath) {
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

    /**
     * Отправить клиенту заново (зовёт RP Core): на входе — таблица еды и полный снимок, после возрождения — снимок.
     * При смене измерения не отправляется, как и раньше.
     */
    public static void resync(ServerPlayer sp, PlayerStore.Sync why) {
        if (why == PlayerStore.Sync.DIMENSION) return;
        if (why == PlayerStore.Sync.LOGIN) {
            state(sp);
            sendFoodTable(sp);
        }
        NutritionData d = NutritionData.get(sp);
        if (d != null) sync(sp, d, true);
    }

    public static void onDatapackSync(OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) sendFoodTable(e.getPlayer());
        else for (ServerPlayer sp : e.getPlayerList().getPlayers()) sendFoodTable(sp);
    }

    static void sendFoodTable(ServerPlayer sp) {
        MinecraftServer server = sp.server;
        var foods = new HashMap<>(NutritionTable.snapshot(server));
        foods.putAll(NutritionTable.meatSnapshot());  // мясные части блюд — для подсказки по составу (ключи rpculinary_meat:<ns>/<path>)
        Net.send(sp, new FoodTablePacket(foods, NutritionTable.categories(server), drinkTable()));
    }

    /** Глотки и вода (всего, %) по общей базе RP Core — для подсказок и шкалы на клиенте. */
    static Map<ResourceLocation, int[]> drinkTable() {
        Map<ResourceLocation, int[]> out = new HashMap<>();
        Consumables.snapshot().forEach((id, e) -> {
            if (e.hasWater() || e.sips() > 1) out.put(id, new int[]{e.sips(), (int) Math.round(e.hasWater() ? e.water() : 0)});
        });
        var w = Consumables.waterPotion();
        if (w != null) out.put(FoodTablePacket.WATER_POTION, new int[]{w.sips(), (int) Math.round(w.water())});
        return out;
    }
}
