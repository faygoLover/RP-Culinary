package faygolover.rpculinary.capability;

import faygolover.rpculinary.server.CulinaryPlayerEvents;
import faygolover.rpcore.api.data.PlayerStore;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.NutritionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.jetbrains.annotations.Nullable;
import faygolover.rpculinary.core.Conditions;
import faygolover.rpculinary.server.CulinaryService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Питание игрока (сохраняется) и служебное состояние сервера (не сохраняется). */
public final class NutritionData {
    public static final Capability<NutritionData> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(RpCulinary.MODID, "nutrition");
    /** Общий жизненный цикл данных игрока (RP Core). Заполняется в {@link #register()}. */
    public static PlayerStore<NutritionData> STORE;

    /**
     * Описать данные для RP Core. Вызывается из конструктора мода. Имя ветки в сохранении — прежнее
     * ({@code rpculinary:nutrition}). Что переносится при возрождении и что отправляется клиенту —
     * {@code CulinaryPlayerEvents.copyOnClone} и {@code CulinaryPlayerEvents.resync}.
     */
    public static void register() {
        STORE = PlayerStore.of(ID, NutritionData.class, CAP, NutritionData::new)
                .save(NutritionData::save)
                .load(NutritionData::load)
                .clone((from, to, ctx) -> CulinaryPlayerEvents.copyOnClone(from, to, ctx.wasDeath()))
                .resync(CulinaryPlayerEvents::resync)
                .register();
    }

    public final NutritionState state = new NutritionState();

    // ---- служебное (сервер)
    /** Ванильная сытость, которую выставили в прошлый тик (по ней видно, что игрок что-то съел с блока). */
    public int mirroredFood = -1;
    /** Тик, в который съеден предмет (его учли через событие — ванильный прирост не считать). */
    public long ateItemTick = -1;
    /** Блок, по которому игрок кликнул в этот тик (торт, тарелка Kaleidoscope). */
    @Nullable
    public Block clickedBlock;
    public long clickedTick = -1;
    /** Что лежало на тарелке Refurbished Furniture, по которой кликнули: мод не сообщает, что съедено. */
    public ItemStack clickedFood = ItemStack.EMPTY;
    /** С прошлого шага: восстановленная выносливость RP Stamina (калории за отдых), тики бега без неё. */
    public double exertionStamina;
    /** Сверх обычного с прошлого шага (спорт: восстановление мышц, дожигание), ккал. */
    public double extraKcal;
    /** Последние отправленные мод тела тяжести состояний (слать только изменения). */
    public final Map<String, Double> sentConditions = new HashMap<>();
    /** Ощущения: ключ → тик, когда показано в последний раз. */
    public final Map<String, Long> sensationTicks = new HashMap<>();
    /** Тик, до которого «сонливость после сладкого» (−10 % восстановления выносливости). */
    public long drowsyUntil = -1;
    /** Тик, до которого прилив сил после сладкого (быстрее восстановление выносливости). */
    public long sugarUntil = -1;
    /** Время мира, когда проявятся паразиты (дичь сырая) и отравление поганкой; 0 — нет. Сохраняется. */
    public long parasitesAt, liverPoisonAt;
    /** Часы тела в тиках: идут в сети и не на паузе. Отметки выше — по ним; 0 — ещё не заведены (старт от игрового времени). */
    public long bodyClock;
    /** Привычка к насекомым (порций за RP-неделю) и сладкое за RP-сутки — убывают. Сохраняется. */
    public double insectMeals, sweetPortions;
    /** Тик, когда в последний раз был «обморок от отёка мозга» (без мода тела). */
    public long edemaFaintTick = -1;
    /** Ел через силу — сообщение один раз за приём. */
    public boolean forcedNoted;
    public int sprintTicks;
    public int stepTicks;
    /** Последнее, что ушло клиенту (слать, только когда изменилось). */
    public long lastSyncHash;
    public int lastHungerStage = -1;
    public int lastThirstStage = -1;
    public boolean lastRoom = true;
    /** Готовка: «у плиты» до такого тика, открыто окно плиты, сколько было блюд, сколько сделано верстаком/печью. */
    public long cookUntil = -1;
    public boolean cookMenu;
    /** Готовит «в поле»: костёр, коптильня (Полевой повар). */
    public boolean cookField;
    /** Последняя «плита» греет (печь, плита, сковорода) — неудачи бывают; холодная сборка (верстак, доска) — нет. */
    public boolean cookHeat;
    /** Из {@link #cookPending}: что вынуто из печи (тепло), остальное — с верстака. */
    public final Set<Item> cookPendingHeat = new HashSet<>();
    @Nullable
    public Map<Item, Integer> cookBaseline;
    public final Map<Item, Integer> cookPending = new HashMap<>();

    @Nullable
    public static NutritionData get(Player p) {
        return p.getCapability(CAP).resolve().orElse(null);
    }

    public CompoundTag save() {
        NutritionState m = state;
        CompoundTag t = new CompoundTag();
        t.putBoolean("init", m.initialized);
        t.putDouble("energy", m.energy);
        t.putDouble("stomach", m.stomach);
        t.putDouble("water", m.water);
        for (int i = 0; i < NutritionState.COUNT; i++) t.putDouble(NutritionState.NUTRIENTS[i], m.nutrients[i]);
        for (int i = 0; i < NutritionState.CATEGORIES.length; i++)
            if (m.monotony[i] > 0) t.putDouble("mono_" + NutritionState.CATEGORIES[i], m.monotony[i]);
        for (int i = 0; i < NutritionState.METHODS.length; i++)
            if (m.methodMono[i] > 0) t.putDouble("mono_m_" + NutritionState.METHODS[i], m.methodMono[i]);
        CompoundTag dishes = new CompoundTag();
        m.dishMono.forEach(dishes::putDouble);
        t.put("mono_dishes", dishes);
        t.putLong("variety_day", m.varietyDay);
        t.putInt("variety_mask", m.varietyMask);
        t.putBoolean("content_given", m.contentGiven);
        t.putDouble("recent_kcal", m.recentKcal);
        t.putDouble("fat_kg", m.fatKg);
        t.putDouble("lean_kg", m.leanKg);
        t.putDouble("starving", m.starvingSeconds);
        t.putDouble("dehydrated", m.dehydratedSeconds);
        t.putDouble("faint_in", m.faintCountdown);
        t.putDouble("poor_appetite", m.poorAppetiteSeconds);
        CompoundTag cond = new CompoundTag();
        for (int i = 0; i < Conditions.COUNT; i++) {
            if (m.condProgress[i] <= 0 && !m.condActive[i]) continue;
            CompoundTag c = new CompoundTag();
            c.putDouble("p", m.condProgress[i]);
            c.putDouble("r", m.condRecover[i]);
            c.putBoolean("a", m.condActive[i]);
            cond.put(Conditions.IDS[i], c);
        }
        t.put("conditions", cond);
        t.putInt("overfill_hits", m.overfillLimitHits);
        t.putDouble("overfill_window", m.overfillWindowSeconds);
        t.putString("body_trait", m.appliedBodyTrait);
        if (m.ivSeconds > 0) {
            CompoundTag iv = new CompoundTag();
            iv.putDouble("kcal", m.ivKcal);
            iv.putDouble("protein", m.ivProtein);
            iv.putDouble("fat", m.ivFat);
            iv.putDouble("carbs", m.ivCarbs);
            iv.putDouble("vitamins", m.ivVitamins);
            iv.putDouble("seconds", m.ivSeconds);
            t.put("iv", iv);
        }
        if (bodyClock > 0) t.putLong("body_clock", bodyClock);
        if (parasitesAt > 0) t.putLong("parasites_at", parasitesAt);
        if (liverPoisonAt > 0) t.putLong("liver_poison_at", liverPoisonAt);
        if (insectMeals > 0) t.putDouble("insect_meals", insectMeals);
        if (sweetPortions > 0) t.putDouble("sweet_portions", sweetPortions);
        return t;
    }

    public void load(CompoundTag t) {
        bodyClock = t.getLong("body_clock");
        parasitesAt = t.getLong("parasites_at");
        liverPoisonAt = t.getLong("liver_poison_at");
        insectMeals = t.getDouble("insect_meals");
        sweetPortions = t.getDouble("sweet_portions");
        NutritionState m = state;
        m.initialized = t.getBoolean("init");
        m.energy = t.getDouble("energy");
        m.stomach = t.getDouble("stomach");
        m.water = t.contains("water") ? t.getDouble("water") : 100;
        for (int i = 0; i < NutritionState.COUNT; i++)
            if (t.contains(NutritionState.NUTRIENTS[i])) m.nutrients[i] = t.getDouble(NutritionState.NUTRIENTS[i]);
        for (int i = 0; i < NutritionState.CATEGORIES.length; i++) m.monotony[i] = t.getDouble("mono_" + NutritionState.CATEGORIES[i]);
        for (int i = 0; i < NutritionState.METHODS.length; i++) m.methodMono[i] = t.getDouble("mono_m_" + NutritionState.METHODS[i]);
        m.dishMono.clear();
        CompoundTag dishes = t.getCompound("mono_dishes");
        for (String k : dishes.getAllKeys()) m.dishMono.put(k, dishes.getDouble(k));
        m.varietyDay = t.contains("variety_day") ? t.getLong("variety_day") : -1;
        m.varietyMask = t.getInt("variety_mask");
        m.contentGiven = t.getBoolean("content_given");
        m.recentKcal = t.getDouble("recent_kcal");
        m.fatKg = t.getDouble("fat_kg");
        m.leanKg = t.getDouble("lean_kg");
        m.starvingSeconds = t.getDouble("starving");
        m.dehydratedSeconds = t.getDouble("dehydrated");
        m.faintCountdown = t.contains("faint_in") ? t.getDouble("faint_in") : -1;
        m.poorAppetiteSeconds = t.getDouble("poor_appetite");
        CompoundTag cond = t.getCompound("conditions");
        for (int i = 0; i < Conditions.COUNT; i++) {
            CompoundTag c = cond.getCompound(Conditions.IDS[i]);
            m.condProgress[i] = c.getDouble("p");
            m.condRecover[i] = c.getDouble("r");
            m.condActive[i] = c.getBoolean("a");
        }
        m.overfillLimitHits = t.getInt("overfill_hits");
        m.overfillWindowSeconds = t.getDouble("overfill_window");
        m.appliedBodyTrait = t.getString("body_trait");
        CompoundTag iv = t.getCompound("iv");
        m.ivKcal = iv.getDouble("kcal");
        m.ivProtein = iv.getDouble("protein");
        m.ivFat = iv.getDouble("fat");
        m.ivCarbs = iv.getDouble("carbs");
        m.ivVitamins = iv.getDouble("vitamins");
        m.ivSeconds = iv.getDouble("seconds");
        if (m.initialized && m.leanKg <= 0) m.initialized = false;
    }
}
