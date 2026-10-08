package faygolover.rpculinary.capability;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.NutritionState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Питание игрока (сохраняется) и служебное состояние сервера (не сохраняется). */
public final class NutritionData {
    public static final Capability<NutritionData> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(RpCulinary.MODID, "nutrition");

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
    /** Нагрузка с прошлого шага: выносливость RP Stamina, тики бега без неё. */
    public double exertionStamina;
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
    @Nullable
    public java.util.Map<net.minecraft.world.item.Item, Integer> cookBaseline;
    public final java.util.Map<net.minecraft.world.item.Item, Integer> cookPending = new java.util.HashMap<>();

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
        t.putDouble("recent_kcal", m.recentKcal);
        t.putDouble("fat_kg", m.fatKg);
        t.putDouble("lean_kg", m.leanKg);
        t.putDouble("starving", m.starvingSeconds);
        t.putDouble("dehydrated", m.dehydratedSeconds);
        t.putDouble("faint_in", m.faintCountdown);
        t.putDouble("poor_appetite", m.poorAppetiteSeconds);
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
        return t;
    }

    public void load(CompoundTag t) {
        NutritionState m = state;
        m.initialized = t.getBoolean("init");
        m.energy = t.getDouble("energy");
        m.stomach = t.getDouble("stomach");
        m.water = t.contains("water") ? t.getDouble("water") : 100;
        for (int i = 0; i < NutritionState.COUNT; i++)
            if (t.contains(NutritionState.NUTRIENTS[i])) m.nutrients[i] = t.getDouble(NutritionState.NUTRIENTS[i]);
        for (int i = 0; i < NutritionState.CATEGORIES.length; i++) m.monotony[i] = t.getDouble("mono_" + NutritionState.CATEGORIES[i]);
        m.recentKcal = t.getDouble("recent_kcal");
        m.fatKg = t.getDouble("fat_kg");
        m.leanKg = t.getDouble("lean_kg");
        m.starvingSeconds = t.getDouble("starving");
        m.dehydratedSeconds = t.getDouble("dehydrated");
        m.faintCountdown = t.contains("faint_in") ? t.getDouble("faint_in") : -1;
        m.poorAppetiteSeconds = t.getDouble("poor_appetite");
        CompoundTag iv = t.getCompound("iv");
        m.ivKcal = iv.getDouble("kcal");
        m.ivProtein = iv.getDouble("protein");
        m.ivFat = iv.getDouble("fat");
        m.ivCarbs = iv.getDouble("carbs");
        m.ivVitamins = iv.getDouble("vitamins");
        m.ivSeconds = iv.getDouble("seconds");
        if (m.initialized && m.leanKg <= 0) m.initialized = false;
    }

    /** Провайдер для AttachCapabilitiesEvent. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final NutritionData data = new NutritionData();
        private final LazyOptional<NutritionData> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAP.orEmpty(cap, opt);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.load(nbt);
        }
    }
}
