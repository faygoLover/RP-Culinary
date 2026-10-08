package faygolover.rpculinary.gametest;

import com.mojang.authlib.GameProfile;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.event.FoodEatenEvent;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.server.CulinaryService;
import faygolover.rpculinary.server.NutritionTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Проверка в настоящем сервере: еда из руки и с блока, вода, замок ванильной сытости, API для соседей. */
@GameTestHolder(RpCulinary.MODID)
@PrefixGameTestTemplate(false)
public final class CulinaryGameTests {
    private CulinaryGameTests() {}

    static final String T = "platform";
    private static int counter;

    static ServerPlayer player(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "rpc-test-" + (counter++)));
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return p;
    }

    static void remove(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().getServer().getPlayerList().remove(p);
    }

    /** Тик игрока: у фальшивого игрока сервер его не вызывает (нет сетевого соединения) — зовём сами. */
    static void tick(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    static NutritionState state(ServerPlayer p) {
        return CulinaryService.state(p);
    }

    static void finish(ServerPlayer p, ItemStack stack) {
        MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p, stack, 0, stack.copy()));
    }

    /** Мод питания зарегистрирован в Core и ведёт голод, воду, вес. */
    @GameTest(template = T)
    public static void providerRegistered(GameTestHelper h) {
        var n = RpCoreAPI.nutrition();
        h.assertTrue(n != null, "питание зарегистрировано в RP Core");
        h.assertTrue(RpCoreAPI.nutritionHandles(RpIds.FOOD_HUNGER) && RpCoreAPI.nutritionHandles(RpIds.FOOD_WEIGHT), "голод и вес — Кулинарии");
        ServerPlayer p = player(h);
        h.assertTrue(Math.abs(n.satiety(p) - Settings.get().startSatiety) < 1e-6, "новый игрок сыт на " + n.satiety(p));
        h.assertTrue(Math.abs(n.weightKg(p) - 70) < 1e-6 && Math.abs(n.bodyFatPercent(p) - 20) < 1e-6, "вес 70 кг, жир 20 %");
        n.setBody(p, 90, 30);
        h.assertTrue(Math.abs(n.weightKg(p) - 90) < 1e-6 && Math.abs(n.bodyFatPercent(p) - 30) < 1e-6, "ГМ выставил 90 кг, 30 %");
        remove(h, p);
        h.succeed();
    }

    /** Состав блюд — по рецептам: хлеб из пшеницы, стейк как сырое мясо. */
    @GameTest(template = T)
    public static void foodTableFromRecipes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        Food wheat = NutritionTable.get(server, Items.WHEAT);
        Food bread = NutritionTable.get(server, Items.BREAD);
        Food beef = NutritionTable.get(server, Items.BEEF);
        Food steak = NutritionTable.get(server, Items.COOKED_BEEF);
        h.assertTrue(wheat != null && bread != null, "пшеница из датапака, хлеб из рецепта");
        // Хлеб — три пшеницы по составу, но калории не больше двух ванильных оценок (5 ед. × 90 × 2).
        h.assertTrue(Math.abs(bread.carbs() / bread.kcal() - wheat.carbs() / wheat.kcal()) < 1e-6, "доли — как у пшеницы");
        h.assertTrue(bread.kcal() <= 5 * Settings.get().kcalPerFoodPoint * 2 + 1e-6 && bread.kcal() >= 400, "хлеб " + bread.kcal() + " ккал");
        h.assertTrue(beef != null && steak != null && Math.abs(steak.kcal() - beef.kcal()) < 1e-6, "жарка состав сохраняет");
        h.assertTrue(NutritionTable.get(server, Items.STONE) == null, "камень — не еда");
        h.assertTrue(NutritionTable.category(server, Items.BREAD) == NutritionState.category("grain"), "хлеб — зерно");
        h.succeed();
    }

    /** Съел хлеб: калории в желудок, событие соседям; полный желудок — ванильная сытость-замок 20. */
    @GameTest(template = T)
    public static void eatingFillsStomachAndLocks(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionState m = state(p);
        Food bread = NutritionTable.get(h.getLevel().getServer(), Items.BREAD);
        AtomicReference<Double> heard = new AtomicReference<>(0.0);
        Consumer<FoodEatenEvent> listener = e -> {
            if (e.getEntity() == p) heard.set(heard.get() + e.kcal());
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, FoodEatenEvent.class, listener);
        double before = m.stomach;
        finish(p, new ItemStack(Items.BREAD));
        MinecraftForge.EVENT_BUS.unregister(listener);
        h.assertTrue(Math.abs(m.stomach - before - bread.kcal()) < 1e-6, "хлеб в желудке: " + m.stomach);
        h.assertTrue(Math.abs(heard.get() - bread.kcal()) < 1e-6, "Medicine и другие услышали «съел»: " + heard.get());
        tick(p, 1);
        h.assertTrue(p.getFoodData().getFoodLevel() == CulinaryService.FOOD_ROOM, "есть место — замок " + CulinaryService.FOOD_ROOM);
        h.assertTrue(p.getFoodData().getSaturationLevel() == 0, "насыщения нет");
        m.stomach = Settings.get().stomachKcal;
        tick(p, 1);
        h.assertTrue(p.getFoodData().getFoodLevel() == CulinaryService.FOOD_FULL, "желудок полон — ванильная еда не лезет");
        h.assertFalse(p.canEat(false), "есть нельзя");
        h.assertTrue(p.canEat(true), "золотое яблоко — можно");
        remove(h, p);
        h.succeed();
    }

    /** Торт — еда с блока: прирост ванильной сытости после клика превращается в калории торта. */
    @GameTest(template = T)
    public static void eatingFromBlockCounts(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionState m = state(p);
        BlockPos rel = new BlockPos(2, 1, 3);
        h.setBlock(rel, Blocks.CAKE);
        BlockPos pos = h.absolutePos(rel);
        tick(p, 1);
        double before = m.stomach;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit));
        h.getLevel().getBlockState(pos).use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        tick(p, 1);
        double got = m.stomach - before;
        h.assertTrue(Math.abs(got - 2 * Settings.get().kcalPerFoodPoint) < 1e-6, "кусок торта = 2 ед. × ккал, было " + got);
        h.assertTrue(p.getFoodData().getFoodLevel() == CulinaryService.FOOD_ROOM, "замок вернулся");
        // Без клика прирост ванильной сытости (подкормка на «Мирной») — не еда.
        double s2 = m.stomach;
        p.getFoodData().setFoodLevel(CulinaryService.FOOD_ROOM + 3);
        NutritionData.get(p).clickedTick = -100;
        tick(p, 1);
        h.assertTrue(m.stomach == s2, "подкормка «Мирной» не считается едой");
        remove(h, p);
        h.succeed();
    }

    /** Бутылка воды поит; зелье скорости — нет. */
    @GameTest(template = T)
    public static void drinkingWater(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionState m = state(p);
        if (CulinaryService.thirstMode(p, Settings.get()) != 1) {
            // С LSO вода — его; своя не тратится и не пополняется.
            finish(p, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
            h.assertTrue(RpCoreAPI.nutrition().hydration(p) >= 0, "вода LSO видна через Core");
            remove(h, p);
            h.succeed();
            return;
        }
        m.water = 50;
        finish(p, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS));
        h.assertTrue(m.water == 50, "зелье скорости — не вода");
        finish(p, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        h.assertTrue(Math.abs(m.water - 85) < 1e-6, "вода +35, было " + m.water);
        remove(h, p);
        h.succeed();
    }

    /** Голод не убивает: голодающий с ванильной сытостью-замком не получает голодного урона. */
    @GameTest(template = T)
    public static void starvationDoesNotKill(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionState m = state(p);
        m.energy = 0;
        m.stomach = 0;
        tick(p, 400);
        h.assertTrue(Metabolism.hungerStage(m, Settings.get()) == 3, "голодает");
        h.assertTrue(p.getFoodData().getFoodLevel() >= CulinaryService.FOOD_ROOM, "ванильная сытость не ниже замка");
        h.assertTrue(p.getHealth() == p.getMaxHealth(), "голодного урона нет");
        h.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(
                java.util.UUID.fromString("0f1e7a46-3c2b-4f1a-9c0e-7a6d2b5e8c11")) != null, "нет сил — медленнее");
        remove(h, p);
        h.succeed();
    }

    /** Смерть: тело то же, желудок пуст, но не на грани. */
    @GameTest(template = T)
    public static void persistsAcrossSave(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionData d = NutritionData.get(p);
        NutritionState m = d.state;
        m.setBody(85, 28, Settings.get());
        m.nutrients[NutritionState.VITAMINS] = 7;
        m.water = 33;
        NutritionData copy = new NutritionData();
        copy.load(d.save());
        h.assertTrue(Math.abs(copy.state.weightKg() - 85) < 1e-6 && Math.abs(copy.state.fatPercent() - 28) < 1e-6, "вес и жир сохраняются");
        h.assertTrue(copy.state.nutrients[NutritionState.VITAMINS] == 7 && copy.state.water == 33, "нутриенты и вода сохраняются");
        remove(h, p);
        h.succeed();
    }

    /** Готовка: стейк из печи помечается качеством; пустой опыт без RP Perks (уровень 10 — жарка без опыта). */
    @GameTest(template = T)
    public static void cookingMarksQuality(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 3));
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent(p, new ItemStack(Items.COOKED_BEEF, 3)));
        p.tickCount = 10;
        tick(p, 1);
        int marked = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(Items.COOKED_BEEF) && faygolover.rpculinary.server.CookingService.quality(st) >= 0) marked += st.getCount();
        }
        h.assertTrue(marked == 3, "три стейка с качеством, было " + marked);
        // Качество при еде: хорошее — витамины сохраннее, подгоревшее — меньше калорий.
        ItemStack burnt = new ItemStack(Items.COOKED_BEEF);
        CompoundTag q = new CompoundTag();
        q.putInt(faygolover.rpculinary.server.CookingService.Q, faygolover.rpculinary.core.Cooking.BURNT);
        burnt.getOrCreateTag().put(faygolover.rpculinary.server.CookingService.TAG, q);
        NutritionState m = state(p);
        double before = m.stomach;
        finish(p, burnt);
        double kcal = NutritionTable.get(h.getLevel().getServer(), Items.COOKED_BEEF).kcal();
        h.assertTrue(Math.abs(m.stomach - before - kcal * 0.85) < 1e-6, "подгорело — 85 % калорий, было " + (m.stomach - before));
        remove(h, p);
        h.succeed();
    }

    /**
     * Моды готовки сборки (запуск с -Pwith_pack=true): состав блюд Kaleidoscope и Farmer's Delight считается по их
     * рецептам. В лог — сколько блюд каждого мода известно по датапаку, по рецептам и только по оценке.
     */
    @GameTest(template = T)
    public static void packDishesFromRecipes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (String ns : java.util.List.of("minecraft", "kaleidoscope_cookery", "kaleidoscope_tavern", "farmersdelight", "farm_and_charm",
                "brewery", "vinery", "candlelight", "herbalbrews")) {
            if (!ns.equals("minecraft") && !net.minecraftforge.fml.ModList.get().isLoaded(ns)) continue;
            java.util.EnumMap<NutritionTable.Source, Integer> counts = new java.util.EnumMap<>(NutritionTable.Source.class);
            java.util.List<String> estimated = new java.util.ArrayList<>();
            for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
                if (id == null || !id.getNamespace().equals(ns)) continue;
                var src = NutritionTable.source(server, item);
                if (src == null) continue;
                counts.merge(src, 1, Integer::sum);
                if (src == NutritionTable.Source.ESTIMATE) estimated.add(id.getPath());
            }
            RpCulinary.LOGGER.info("RP Culinary audit {}: {} ; estimated: {}", ns, counts, estimated);
        }
        dish(h, "kaleidoscope_cookery", "borscht");
        kaleidoscopeQualityShift(h);
        dish(h, "farmersdelight", "beef_stew");
        h.succeed();
    }

    /** Kaleidoscope: мастер (без RP Perks — уровень 10) поднимает их качество на ступень: обычное → отличное. */
    private static void kaleidoscopeQualityShift(GameTestHelper h) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("kaleidoscope_cookery") || RpCoreAPI.isSkillManaged(RpIds.COOKING)) return;
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("kaleidoscope_cookery", "borscht"));
        ServerPlayer p = player(h);
        ItemStack bowl = new ItemStack(item, 2);
        bowl.getOrCreateTag().putInt("kaleidoscope_cookery:quality", 2);
        p.getInventory().add(bowl);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent(p, new ItemStack(item, 2), p.getInventory()));
        p.tickCount = 10;
        tick(p, 1);
        int better = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(item) && st.getTag() != null && st.getTag().getInt("kaleidoscope_cookery:quality") == 1) better += st.getCount();
        }
        h.assertTrue(better == 2, "борщ мастера — отличный, было " + better);
        remove(h, p);
    }

    private static void dish(GameTestHelper h, String ns, String path) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded(ns)) return;
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(ns, path));
        h.assertTrue(item != null && item != Items.AIR, ns + ":" + path + " есть");
        var server = h.getLevel().getServer();
        Food f = NutritionTable.get(server, item);
        h.assertTrue(f != null && f.kcal() > 0 && NutritionTable.source(server, item) == NutritionTable.Source.RECIPE,
                ns + ":" + path + " — по рецепту: " + f + " / " + NutritionTable.source(server, item));
        RpCulinary.LOGGER.info("RP Culinary dish {}:{} = {} ({})", ns, path, f, NutritionState.CATEGORIES[Math.max(0,
                NutritionTable.category(server, item))]);
    }
}
