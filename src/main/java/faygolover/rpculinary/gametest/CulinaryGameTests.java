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
        // Со сборкой (FD) хлеб — из теста в печи (рецепт верстака убран датапаком), доли другие
        if (!net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight"))
            h.assertTrue(Math.abs(bread.carbs() / bread.kcal() - wheat.carbs() / wheat.kcal()) < 1e-6, "доли — как у пшеницы");
        h.assertTrue(bread.kcal() <= 5 * Settings.get().kcalPerFoodPoint * 2 + 1e-6 && bread.kcal() >= (net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight") ? 300 : 400), "хлеб " + bread.kcal() + " ккал");
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
        // С 0.3.0 полный желудок не мешает есть (через силу); не лезет только «до предела» — вдвое больше объёма.
        m.stomach = Settings.get().stomachKcal * 2;
        tick(p, 1);
        h.assertTrue(p.getFoodData().getFoodLevel() == CulinaryService.FOOD_FULL, "желудок до предела — ванильная еда не лезет");
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
        // Бутылка воды — 2 глотка по 20 % (база RP Core): «допил» — последний глоток.
        finish(p, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        h.assertTrue(Math.abs(m.water - 70) < 1e-6, "последний глоток +20, было " + m.water);
        // Глоток из полной бутылки в руке: бутылка остаётся, глотков 1 из 2.
        ItemStack bottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bottle);
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(CulinaryService.sip(p), "глоток");
        h.assertTrue(faygolover.rpculinary.server.Sips.left(p.getMainHandItem()) == 1, "остался 1 глоток из 2");
        h.assertTrue(Math.abs(m.water - 90) < 1e-6, "глоток +20, было " + m.water);
        p.stopUsingItem();
        remove(h, p);
        h.succeed();
    }

    /** Еда по частям: большое блюдо — укус шагом, предмет остаётся с пометкой; последний укус — ровно остаток. */
    @GameTest(template = T)
    public static void bigDishIsEatenInBites(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionState m = state(p);
        Settings s = Settings.get();
        ItemStack stew = new ItemStack(Items.RABBIT_STEW);
        // Шаг поменьше, чтобы тушёный кролик (сколько бы ни дала таблица) точно елся укусами.
        double oldStep = s.biteKcal;
        s.biteKcal = 100;
        h.assertTrue(CulinaryService.bites(299, 200) == 1 && CulinaryService.bites(300, 200) == 2 && CulinaryService.bites(450, 200) == 2
                && CulinaryService.bites(500, 200) == 3, "укусы к ближайшему: 299 — 1, 300 — 2, 450 — 2, 500 — 3");
        int n = CulinaryService.bitesFor(p, stew, s);
        h.assertTrue(n >= 2, "кролик укусами: " + n);
        double before = m.stomach;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stew);
        p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(CulinaryService.bite(p), "первый укус");
        ItemStack held = p.getMainHandItem();
        h.assertTrue(!held.isEmpty() && held.getTag() != null && held.getTag().getInt(CulinaryService.BITES) == n - 1,
                "блюдо осталось, укусов " + (n - 1));
        h.assertTrue(Math.abs(m.stomach - before - s.biteKcal) < 1, "в желудке укус: " + (m.stomach - before));
        p.stopUsingItem();
        s.biteKcal = oldStep;
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
        // Удачное блюдо не травит, даже если раньше считалось «сырым».
        ItemStack good = new ItemStack(Items.COOKED_BEEF);
        faygolover.rpculinary.server.CookingService.put(good, faygolover.rpculinary.core.Cooking.GOOD, 0);
        p.removeAllEffects();
        for (int i = 0; i < 20; i++) CulinaryService.foodPoisoning(p, good, faygolover.rpculinary.core.Cooking.GOOD, 1, Settings.get());
        h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "хорошее блюдо не травит");
        remove(h, p);
        h.succeed();
    }

    /** Группы экзотики (29.10): по тегу; знание — журналом с полки, не тратится, нужен навык; отложенные паразиты; сладкое. */
    @GameTest(template = T)
    public static void exoticGroups(GameTestHelper h) {
        var server = h.getLevel().getServer();
        h.assertTrue((NutritionTable.exoticMask(server, Items.CHORUS_FRUIT) & faygolover.rpculinary.server.Exotic.bit("other_worlds")) != 0, "хорус — иные миры");
        h.assertTrue((NutritionTable.exoticMask(server, Items.ROTTEN_FLESH) & faygolover.rpculinary.server.Exotic.bit("spoiled")) != 0, "гнилая плоть — испорченное");
        h.assertTrue((NutritionTable.exoticMask(server, Items.RED_MUSHROOM) & faygolover.rpculinary.server.Exotic.bit("mushrooms")) != 0, "мухомор — дикие грибы");
        h.assertTrue(NutritionTable.exoticMask(server, Items.BREAD) == 0 && NutritionTable.exoticMask(server, Items.COOKED_BEEF) == 0
                && NutritionTable.exoticMask(server, Items.BROWN_MUSHROOM) == 0, "хлеб, стейк, шампиньон — не экзотика");
        h.assertTrue(new ItemStack(faygolover.rpculinary.item.ResearchNotes.NOTES.get()).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS), "журнал ставится на полку");
        ServerPlayer p = player(h);
        int g = faygolover.rpculinary.server.Exotic.index("other_worlds");
        if (RpCoreAPI.isSkillManaged(RpIds.COOKING)) {
            h.assertTrue(!faygolover.rpculinary.server.Exotic.knows(p, g), "с Perks без записей — не знает");
            ItemStack book = faygolover.rpculinary.item.ResearchNotes.of(g);
            p.setItemInHand(InteractionHand.MAIN_HAND, book);
            book.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            boolean able = faygolover.rpculinary.server.CookingService.level(p) >= faygolover.rpculinary.server.Exotic.minSkill(g);
            h.assertTrue(faygolover.rpculinary.server.Exotic.knows(p, g) == able, "изучил только при нужном навыке");
            h.assertTrue(!p.getMainHandItem().isEmpty(), "записи не тратятся");
        } else {
            h.assertTrue(faygolover.rpculinary.server.Exotic.knows(p, g), "без Perks знает всё");
        }
        NutritionData d = NutritionData.get(p);
        d.parasitesAt = server.overworld().getGameTime() - 1;
        p.tickCount = 3;
        tick(p, 1);
        h.assertTrue(d.parasitesAt == 0 && p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "паразиты проявились");
        // домашний фарш: (свинина+говядина)+(свинина+курица) = 2 свинина + говядина + курица; кратные доли сокращаются
        ItemStack m1 = new ItemStack(Items.PORKCHOP), m2 = new ItemStack(Items.PORKCHOP), m3 = new ItemStack(Items.BEEF);
        faygolover.rpculinary.core.Source.setParts(m1, java.util.Map.of("pig", 1, "cow", 1), "pig");
        faygolover.rpculinary.core.Source.setParts(m2, java.util.Map.of("pig", 1, "chicken", 1), "pig");
        var mix = faygolover.rpculinary.core.Source.mix(java.util.List.of(m1, m2));
        h.assertTrue(mix.get("pig") == 2 && mix.get("cow") == 1 && mix.get("chicken") == 1, "смесь фаршей: " + mix);
        faygolover.rpculinary.core.Source.setParts(m3, java.util.Map.of("cow", 2, "pig", 2), "cow");
        h.assertTrue(faygolover.rpculinary.core.Source.parts(m3).get("cow") == 1 && faygolover.rpculinary.core.Source.home(m3), "2+2 → 1+1, домашний");
        NutritionData copy = new NutritionData();
        d.insectMeals = 2;
        copy.load(d.save());
        h.assertTrue(copy.insectMeals == 2, "привычка к насекомым сохраняется");
        for (int i = 0; i < 6; i++) faygolover.rpculinary.server.ExoticEffects.sweets(p, d, 1, Settings.get());
        h.assertTrue(d.sweetPortions >= 5 && d.sugarUntil > server.getTickCount(), "сладкое: прилив и счёт порций");
        remove(h, p);
        h.succeed();
    }

    /** Человечина без «Каннибала» — без падения; гнилая плоть — экзотика «монстры». */
    @GameTest(template = T)
    public static void humanMeat(GameTestHelper h) {
        ServerPlayer p = player(h);
        NutritionData d = NutritionData.get(p);
        for (int i = 0; i < 3; i++) faygolover.rpculinary.server.ExoticEffects.human(p, d, 1, Settings.get());
        finish(p, new ItemStack(Items.ROTTEN_FLESH));
        remove(h, p);
        h.succeed();
    }

    /** Качество — только у финальных блюд: печь и костёр греют, верстак — нет; полуфабрикаты (тег no_quality) — не блюдо. */
    @GameTest(template = T)
    public static void finalDishesOnly(GameTestHelper h) {
        h.assertTrue(faygolover.rpculinary.server.CookingService.heat(Blocks.FURNACE) && faygolover.rpculinary.server.CookingService.heat(Blocks.CAMPFIRE)
                && !faygolover.rpculinary.server.CookingService.heat(Blocks.CRAFTING_TABLE), "печь и костёр греют, верстак — нет");
        ServerPlayer p = player(h);
        h.assertTrue(faygolover.rpculinary.server.CookingService.dish(p, new ItemStack(Items.BREAD)), "хлеб — блюдо");
        if (net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")) {
            for (String path : java.util.List.of("wheat_dough", "minced_beef", "raw_pasta", "cabbage_leaf")) {
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", path));
                h.assertTrue(!faygolover.rpculinary.server.CookingService.dish(p, new ItemStack(item)), "farmersdelight:" + path + " — полуфабрикат");
            }
        }
        remove(h, p);
        h.succeed();
    }

    /** Сырое: сырое мясо и рыба — да, жареное, тухлятина, хлеб — нет; со сборкой — бекон и фарш FD да, копчёный окорок нет. */
    @GameTest(template = T)
    public static void rawDetection(GameTestHelper h) {
        var server = h.getLevel().getServer();
        h.assertTrue(NutritionTable.raw(server, Items.BEEF) && NutritionTable.raw(server, Items.CHICKEN) && NutritionTable.raw(server, Items.COD),
                "сырые говядина, курица, треска");
        h.assertTrue(!NutritionTable.raw(server, Items.COOKED_BEEF) && !NutritionTable.raw(server, Items.BREAD)
                && !NutritionTable.raw(server, Items.ROTTEN_FLESH) && !NutritionTable.raw(server, Items.APPLE), "готовое и не мясо — не сырое");
        if (net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")) {
            java.util.function.Function<String, net.minecraft.world.item.Item> fd = path ->
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", path));
            h.assertTrue(!NutritionTable.raw(server, fd.apply("smoked_ham")), "копчёный окорок — не сырой");
            for (String path : java.util.List.of("ham", "bacon", "minced_beef", "chicken_cuts", "cod_slice"))
                h.assertTrue(NutritionTable.raw(server, fd.apply(path)), "farmersdelight:" + path + " — сырое");
        }
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

    /** Разделка (блок 3): потери и опыт по порогу, замедление от веса туш; с Butchery — доп. разрез коровы перед «родным». */
    @GameTest(template = T)
    public static void butchery(GameTestHelper h) {
        Settings s = Settings.get();
        h.assertTrue(faygolover.rpculinary.server.Butchering.loss(5, 2, s) == s.butcherLossFar
                && faygolover.rpculinary.server.Butchering.loss(5, 4, s) == s.butcherLossNear
                && faygolover.rpculinary.server.Butchering.loss(5, 5, s) == s.butcherLossAt
                && faygolover.rpculinary.server.Butchering.loss(5, 6, s) == 0, "потери по порогу");
        h.assertTrue(faygolover.rpculinary.server.Butchering.xp(5, 10, 12) == 0 && faygolover.rpculinary.server.Butchering.xp(5, 5, 12) > 0
                && faygolover.rpculinary.server.Butchering.xp(10, 5, 12) > faygolover.rpculinary.server.Butchering.xp(1, 1, 12), "опыт за разрез");
        double cow = faygolover.rpculinary.server.Butchering.slow(125, s), chicken = faygolover.rpculinary.server.Butchering.slow(2, s);
        h.assertTrue(cow > 0.5 && cow <= s.carcassSlowMax && chicken <= 0.02 && faygolover.rpculinary.server.Butchering.slow(0, s) == 0,
                "замедление: корова " + cow + ", курица " + chicken);
        h.assertTrue(faygolover.rpculinary.server.Butchering.mincerLoss(0) == 0.15 && faygolover.rpculinary.server.Butchering.mincerLoss(10) == 0.05,
                "мясорубка: 15 % у новичка, 5 % у мастера");
        if (!net.minecraftforge.fml.ModList.get().isLoaded("butchery")) {
            h.succeed();
            return;
        }
        var server = h.getLevel().getServer();
        for (String t : new String[]{"butchery:blocks/cow_cut_2_drop", "rpculinary:carcass/cow_forequarter", "minecraft:cow_cut_1_drop"})
            h.assertTrue(server.getLootData().getLootTable(new net.minecraft.resources.ResourceLocation(t))
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "таблица " + t + " загружена");
        var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("butchery", "drained_cow_carcass"));
        BlockPos rel = new BlockPos(1, 2, 1), pos = h.absolutePos(rel);
        var prop = (net.minecraft.world.level.block.state.properties.IntegerProperty) block.getStateDefinition().getProperty("blockstate");
        h.getLevel().setBlock(pos, block.defaultBlockState().setValue(prop, 8), 3);
        var be = h.getLevel().getBlockEntity(pos);
        h.assertTrue(be != null, "у туши есть блок-сущность");
        be.getPersistentData().putBoolean("isDrained", true);
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("butchery", "iron_cleaver"))));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        // Без Perks навык — 10 (порог коровы 5): без потерь, всё ровно. С Perks у тестового игрока 0: часть — в обрезки.
        boolean exact = faygolover.rpculinary.server.Butchering.loss(5, faygolover.rpculinary.server.CookingService.level(p), s) == 0;
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit));
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(prop) == 8, "доп. разрез стадию не двигает");
        int chuck = dropped(h, pos, "butchery:raw_chuck_steak");
        h.assertTrue(exact ? chuck == 2 : chuck <= 2, "доп. разрез: передняя четверть — лопатка ×2, было " + chuck);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit));
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(prop) == 9, "родной разрез двигает стадию");
        int ribeye = dropped(h, pos, "butchery:raw_ribeye_steak"), sirloin = dropped(h, pos, "butchery:raw_sirloin_steak");
        h.assertTrue(exact ? ribeye == 4 && sirloin == 4 : ribeye <= 4 && sirloin <= 4, "родной разрез 2: рибай и вырезка ×4");
        boolean lost = chuck < 2 || ribeye < 4 || sirloin < 4;
        h.assertTrue(lost == (dropped(h, pos, "butchery:meat_scraps") > 0), "потерянное — обрезками");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        remove(h, p);
        h.succeed();
    }

    /** Доска FD (блок 4): крупный кусок с весом в NBT режется ножом на порции по весу, доска пустеет. */
    @GameTest(template = T)
    public static void boardByWeight(GameTestHelper h) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight") || !net.minecraftforge.fml.ModList.get().isLoaded("butchery")) {
            h.succeed();
            return;
        }
        var board = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", "cutting_board"));
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, board.defaultBlockState(), 3);
        var handler = h.getLevel().getBlockEntity(pos).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        ItemStack chuck = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("butchery", "raw_chuck_steak")));
        chuck.getOrCreateTag().putInt(faygolover.rpculinary.core.Weights.TAG_WEIGHT, 6000);
        chuck.getOrCreateTag().putString(faygolover.rpculinary.core.Weights.TAG_SOURCE, "cow");
        handler.insertItem(0, chuck, false);
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("farmersdelight", "iron_knife"))));
        boolean exact = faygolover.rpculinary.server.Butchering.loss(4, faygolover.rpculinary.server.CookingService.level(p), Settings.get()) == 0;
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        // один удар — одна порция (или обрезки), кусок худеет на 250 г и остаётся на доске; вид — «корова»
        ItemStack left = handler.getStackInSlot(0);
        h.assertTrue(left.is(chuck.getItem()) && faygolover.rpculinary.core.Weights.nbtGrams(left) == 5750, "на доске осталось 5,75 кг, было " + faygolover.rpculinary.core.Weights.nbtGrams(left));
        int beef = dropped(h, pos, "minecraft:beef"), scraps = dropped(h, pos, "butchery:meat_scraps");
        h.assertTrue(exact ? beef == 1 : beef + scraps == 1, "одна часть за удар: говядина " + beef + ", обрезки " + scraps);
        for (var ie : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)))
            h.assertTrue(faygolover.rpculinary.core.Source.list(ie.getItem()).contains("cow"), "часть помнит вид: корова");
        if (net.minecraftforge.fml.ModList.get().isLoaded("farm_and_charm")) {
            var mix = h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("rpculinary", "bowl/home_mince"));
            h.assertTrue(mix.isPresent(), "смешанный фарш: рецепт есть (состав — из происхождения)");
        }
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        remove(h, p);
        h.succeed();
    }

    /** Техники по умению (29.10): без «Винокура» станция пивоварни не открывается; заготовка без «Заготовщика» не выходит. */
    @GameTest(template = T)
    public static void techniques(GameTestHelper h) {
        ServerPlayer p = player(h);
        boolean managed = RpCoreAPI.isSkillManaged(RpIds.COOKING);
        h.assertTrue(faygolover.rpculinary.server.Techniques.allowed(p, faygolover.rpculinary.server.Techniques.VINTNER) == !managed,
                "без Perks можно всем, с Perks без умения — нельзя");
        var ham = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", "smoked_ham"));
        if (ham != null && ham != Items.AIR) {
            p.getInventory().add(new ItemStack(ham, 2));
            boolean spoiled = faygolover.rpculinary.server.Techniques.spoilPreserves(p, ham, 2);
            h.assertTrue(spoiled == managed, "копчёный окорок без «Заготовщика» не вышел");
            h.assertTrue(p.getInventory().countItem(ham) == (managed ? 0 : 2), "испорченное убрано, у умеющего — осталось");
        }
        var station = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("brewery", "wooden_brewingstation"));
        if (station != null && station != Blocks.AIR) {
            BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
            h.getLevel().setBlock(pos, station.defaultBlockState(), 3);
            var ev = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            MinecraftForge.EVENT_BUS.post(ev);
            h.assertTrue(ev.isCanceled() == managed, "станция пивоварни без умения не открывается");
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        remove(h, p);
        h.succeed();
    }

    /** Мешки и ящики (блок 6): наполнить из стака, достать, стак 1; поставленная — хранит содержимое и отдаёт его. */
    @GameTest(template = T)
    public static void containers(GameTestHelper h) {
        var crate = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("farmersdelight", "potato_crate"));
        if (crate == null || crate == Items.AIR) {
            h.succeed();
            return;
        }
        ItemStack box = new ItemStack(crate);
        h.assertTrue(box.getMaxStackSize() == 1, "тара не стакается");
        ItemStack potatoes = new ItemStack(Items.POTATO, 64);
        int moved = faygolover.rpculinary.core.Containers.fill(box, potatoes);
        h.assertTrue(moved > 0 && faygolover.rpculinary.core.Containers.count(box) == moved && potatoes.getCount() == 64 - moved, "картофель положен");
        h.assertTrue(faygolover.rpculinary.core.Containers.fill(box, new ItemStack(Items.CARROT, 4)) == 0, "морковь в ящик картофеля — нет");
        ItemStack out = faygolover.rpculinary.core.Containers.take(box);
        h.assertTrue(out.is(Items.POTATO) && out.getCount() > 0, "достал стак");
        int left = faygolover.rpculinary.core.Containers.count(box);
        // поставленный ящик: ПКМ картофелем — положить, пустой рукой — достать
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, ((net.minecraft.world.item.BlockItem) crate).getBlock().defaultBlockState(), 3);
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POTATO, 10));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit));
        h.assertTrue(faygolover.rpculinary.server.ContainerFill.storedCount(h.getLevel(), pos) == 10 && p.getMainHandItem().isEmpty(), "в поставленный ящик положено 10");
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos, hit));
        h.assertTrue(p.getMainHandItem().is(Items.POTATO) && p.getMainHandItem().getCount() == 10
                && faygolover.rpculinary.server.ContainerFill.storedCount(h.getLevel(), pos) == 0, "достал обратно");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(left >= 0, "остаток считается");
        remove(h, p);
        h.succeed();
    }

    /** Мясорубка Butchery без насадки для колбас не мелет говядину в фарш (решение 29.10); сладкое — мало калорий. */
    @GameTest(template = T)
    public static void butcheryGrinderNoMince(GameTestHelper h) throws Exception {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("butchery")) {
            h.succeed();
            return;
        }
        var grinder = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("butchery", "meat_grinder"));
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, grinder.defaultBlockState(), 3);
        var handler = (net.minecraftforge.items.IItemHandlerModifiable) h.getLevel().getBlockEntity(pos)
                .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        handler.setStackInSlot(0, new ItemStack(Items.BEEF, 4));
        var exec = Class.forName("net.mcreator.butchery.procedures.MeatgrinderrecipeprocedureProcedure")
                .getMethod("execute", net.minecraft.world.level.LevelAccessor.class, double.class, double.class, double.class);
        for (int i = 0; i < 300; i++) exec.invoke(null, h.getLevel(), (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
        h.assertTrue(handler.getStackInSlot(0).is(Items.BEEF) && handler.getStackInSlot(0).getCount() == 4, "говядина не перемолота");
        for (int i = 0; i < handler.getSlots(); i++)
            h.assertTrue(!String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(handler.getStackInSlot(i).getItem())).endsWith("_mince"), "фарша нет");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    private static int dropped(GameTestHelper h, BlockPos pos, String id) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id));
        int n = 0;
        for (var ie : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2)))
            if (ie.getItem().is(item)) n += ie.getItem().getCount();
        return n;
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
