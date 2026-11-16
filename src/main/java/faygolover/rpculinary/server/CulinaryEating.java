package faygolover.rpculinary.server;

import static faygolover.rpculinary.server.CulinaryService.*;
import static faygolover.rpculinary.server.CulinaryPlayerEvents.*;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.event.FoodEatenEvent;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.core.Food;
import faygolover.rpculinary.core.Metabolism;
import faygolover.rpculinary.core.NutritionState;
import faygolover.rpculinary.core.Nutrients;
import faygolover.rpculinary.core.Settings;
import faygolover.rpculinary.data.DrinkRules;
import faygolover.rpculinary.data.NutritionRules;
import faygolover.rpculinary.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import faygolover.rpcore.api.Consumables;
import faygolover.rpcore.api.FoodTaste;
import faygolover.rpcore.api.event.RpConsumeEvent;
import faygolover.rpcore.api.event.RpMoodEvent;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.client.ClientNutrition;
import faygolover.rpculinary.core.Boredom;
import faygolover.rpculinary.core.Cooking;
import faygolover.rpculinary.core.MeatFood;
import faygolover.rpculinary.core.Weights;
import faygolover.rpculinary.network.PortionDonePacket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.Container;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;

/**
 * Еда и питьё: укусы и глотки по ходу использования предмета, порция, еда из блока, отравление, вкусы и «приелось»,
 * сколько воды в напитке, что предмет даёт телу. Выделено из {@link CulinaryService}: там остались доступ к данным,
 * шаг раз в секунду, эффекты и отправка клиенту.
 */
public final class CulinaryEating {
    private CulinaryEating() {}

    // ------------------------------------------------------------------ еда

    /**
     * Приелось — ест без аппетита, дольше. Длительность не меняем (анимация руки считает её от «родной»
     * длительности предмета), а откатываем часть тиков — одинаково на клиенте и сервере (как в RP Medicine).
     */
    public static void onUseTick(LivingEntityUseItemEvent.Tick e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // Еда по частям и глотки: вместо «доел» откусил или отпил, предмет остаётся (сервер; последний — onUseFinish).
        // Глотки — и у питья, которое не «еда» (бутылка воды, напитки SA): раньше проверка шла после isEdible, и такая
        // бутылка выпивалась целиком (30.10).
        if (e.getDuration() <= 1 && p instanceof ServerPlayer sp && (sip(sp) || e.getItem().isEdible() && active(sp) && bite(sp))) {
            e.setDuration(0);
            sp.stopUsingItem();
            // Клиент сам «использую» не снимет — зажатая ПКМ иначе так и стоит в конце анимации.
            Net.send(sp, new PortionDonePacket());
            return;
        }
        if (!e.getItem().isEdible()) return;
        Settings s = Settings.get();
        boolean fedUp;
        if (p.level().isClientSide) {
            fedUp = ClientNutrition.fedUp(e.getItem());
        } else {
            NutritionState m = state(p);
            fedUp = m != null && p instanceof ServerPlayer sp && Nutrients.fedUp(m, NutritionTable.category(sp.server, e.getItem().getItem()), s);
        }
        // Через силу: желудок полон (или воды уже больше 100 % и это питьё) — медленнее.
        boolean drink = e.getItem().getUseAnimation() == UseAnim.DRINK;
        boolean forced;
        if (p.level().isClientSide) {
            forced = ClientNutrition.forced(drink);
        } else {
            NutritionState m = state(p);
            forced = m != null && (drink ? thirstMode((ServerPlayer) p, s) == 1 && m.water >= 100
                    : s.hungerEnabled && !Metabolism.hasRoom(m, s, stomachMult(p, m, s)));
        }
        // «Уголь» почти несъедобен — жуётся долго (метка в NBT видна и клиенту).
        boolean charcoal = CookingService.quality(e.getItem()) == Cooking.CHARCOAL;
        double factor = (fedUp ? s.monotonyEatSlow : 1) * (forced ? s.forcedEatSlow : 1) * (dislikedKind(p, e.getItem()) ? s.dislikedEatSlow : 1)
                * (charcoal ? s.charcoalEatSlow : 1);
        if (factor > 1 && slowTick(p.tickCount, factor) && e.getDuration() < e.getItem().getUseDuration())
            e.setDuration(e.getDuration() + 1);
    }

    /** Откатить ли этот тик: доля откатов 1 − 1/factor, равномерно по времени. */
    static boolean slowTick(int tick, double factor) {
        if (factor <= 1.0) return false;
        double k = 1 - 1 / Math.min(factor, 10);
        return Math.floor(tick * k) != Math.floor((tick - 1) * k);
    }

    /** Без рабочих рук сам не ест и не пьёт: кормить может другой ({@link Feeding}). */
    public static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !Feeding.feedable(e.getItem()) || RpCoreAPI.handsFree(sp)) return;
        e.setCanceled(true);
        sp.displayClientMessage(Component.translatable("rpcore.msg.no_hands"), true);
        Net.send(sp, new PortionDonePacket());
    }

    /** Доля предмета, которую скормил другой: вещества и питание — как при обычной еде. */
    static void feedPortion(ServerPlayer target, ItemStack used, double fraction) {
        MinecraftForge.EVENT_BUS.post(new RpConsumeEvent(target, used.copy(), fraction));
        if (!active(target)) return;
        NutritionData d = NutritionData.get(target);
        if (d == null) return;
        ensureInit(target, d);
        eatPortion(target, d, used, fraction);
    }

    /** Доел или допил предмет (у большого блюда — последний укус: ровно остаток). */
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer sp) ate(sp, e.getItem());
    }

    /** Кто только что кликнул по блоку Candlelight: еду с тарелки мод снимает сразу за кликом. */
    @Nullable
    static ServerPlayer plateEater;

    /** Еду с тарелки Candlelight считаем как порцию, а прирост сытости от неё — не считаем. */
    public static void ateFromPlate(ItemStack food) {
        ServerPlayer sp = plateEater;
        if (sp == null) return;
        plateEater = null;
        NutritionData d = NutritionData.get(sp);
        if (d != null) d.clickedTick = -1;
        ate(sp, food);
    }

    static void ate(ServerPlayer sp, ItemStack used) {
        double fraction = 1;
        int sips = Sips.total(used);
        int n = used.hasTag() ? used.getTag().getInt(BITES_N) : 0;
        if (sips > 1) {
            fraction = 1.0 / sips;
        } else if (n > 1) {
            double kcal = dishKcal(sp, used, Settings.get());
            if (kcal > 0) fraction = Math.max(0.05, 1 - (n - 1) * Settings.get().biteKcal / kcal);
        }
        // Вещества, бодрость, кофеин — по доле (RP Core), и в творческом тоже.
        MinecraftForge.EVENT_BUS.post(new RpConsumeEvent(sp, used.copy(), fraction));
        if (!active(sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        ensureInit(sp, d);
        eatPortion(sp, d, used, fraction);
    }

    /**
     * Глоток напитка в руке (пьётся глотками — база RP Core): вода и вещества — долей, предмет остаётся, отпитый
     * лежит отдельно (со свежими не складывается). false — не глоток (один глоток или последний — onUseFinish).
     */
    public static boolean sip(ServerPlayer sp) {
        ItemStack held = sp.getItemInHand(sp.getUsedItemHand());
        int n = Sips.total(held);
        int left = Sips.left(held);
        if (n <= 1 || left <= 1) return false;
        ItemStack piece = held.getCount() > 1 ? held.split(1) : held;
        ItemStack before = piece.copy();
        Sips.set(piece, left - 1, n);
        if (piece != held && !sp.getInventory().add(piece)) sp.drop(piece, false);
        MinecraftForge.EVENT_BUS.post(new RpConsumeEvent(sp, before, 1.0 / n));
        NutritionData d = NutritionData.get(sp);
        if (d != null && active(sp)) {
            ensureInit(sp, d);
            eatPortion(sp, d, before, 1.0 / n);
        }
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5f,
                0.9f + sp.getRandom().nextFloat() * 0.2f);
        return true;
    }

    /** Сколько укусов у целого блюда: к ближайшему по {@link Settings#biteKcal} (299 — 1, 300 — 2), последний — остаток. */
    public static int bitesFor(ServerPlayer sp, ItemStack st, Settings s) {
        if (!s.bitesEnabled || s.biteKcal <= 0 || st.getUseAnimation() != UseAnim.EAT) return 1;
        return bites(dishKcal(sp, st, s), s.biteKcal);
    }

    public static int bites(double kcal, double bite) {
        return bite <= 0 ? 1 : Math.max(1, (int) Math.round(kcal / bite));
    }

    /** Калории целого блюда с качеством готовки. */
    static double dishKcal(ServerPlayer sp, ItemStack st, Settings s) {
        Food f = itemFood(sp, st);
        if (f == null) return 0;
        int q = CookingService.quality(st);
        if (q >= 0) f = Cooking.apply(f, q, s);
        return f.kcal();
    }

    /**
     * Укус большого блюда в руке: съесть шаг, записать, сколько осталось. Из стопки откусывается один предмет —
     * надкусанный кладётся отдельно (со свежими не складывается). false — это не укус (обычная еда или последний).
     */
    public static boolean bite(ServerPlayer sp) {
        Settings s = Settings.get();
        NutritionData d = NutritionData.get(sp);
        if (d == null) return false;
        ItemStack held = sp.getItemInHand(sp.getUsedItemHand());
        if (held.isEmpty() || !held.isEdible()) return false;
        int n = held.hasTag() && held.getTag().contains(BITES_N) ? held.getTag().getInt(BITES_N) : bitesFor(sp, held, s);
        int left = held.hasTag() && held.getTag().contains(BITES) ? held.getTag().getInt(BITES) : n;
        if (n <= 1 || left <= 1) return false;
        double kcal = dishKcal(sp, held, s);
        if (kcal <= 0) return false;
        ensureInit(sp, d);
        ItemStack piece = held.getCount() > 1 ? held.split(1) : held;
        MinecraftForge.EVENT_BUS.post(new RpConsumeEvent(sp, piece.copy(), s.biteKcal / kcal));
        eatPortion(sp, d, piece.copy(), s.biteKcal / kcal);
        piece.getOrCreateTag().putInt(BITES_N, n);
        piece.getTag().putInt(BITES, left - 1);
        if (piece != held && !sp.getInventory().add(piece)) sp.drop(piece, false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5f,
                0.9f + sp.getRandom().nextFloat() * 0.2f);
        return true;
    }

    /** Нелюбимое по виду еды (то, что знают и сервер, и клиент: одинаково замедляем еду). */
    static boolean dislikedKind(Player p, ItemStack st) {
        String dis = RpCoreAPI.taste(p).disliked();
        if (!dis.startsWith("kind:")) return false;
        int cat;
        if (p.level().isClientSide) {
            cat = ClientNutrition.CATEGORIES.getOrDefault(ForgeRegistries.ITEMS.getKey(st.getItem()), -1);
        } else {
            cat = NutritionTable.category(((ServerPlayer) p).server, st.getItem());
        }
        return cat >= 0 && dis.equals("kind:" + NutritionState.CATEGORIES[cat]);
    }

    /** Съесть долю {@code fraction} предмета {@code used}: вода, еда, «приелось», вкусы, «Сыт и доволен». */
    static void eatPortion(ServerPlayer sp, NutritionData d, ItemStack used, double fraction) {
        Settings s = Settings.get();
        double water = (thirstMode(sp, s) == 1 ? drinkWater(used, s) : 0) * fraction;
        Food food = itemFood(sp, used);
        if (food == null && water == 0) return;
        if (used.isEdible()) d.ateItemTick = sp.server.getTickCount();
        if (food == null) food = Food.ZERO;
        int quality = CookingService.quality(used);
        boolean gourmet = RpCoreAPI.hasTrait(sp, RpIds.GOURMET);
        if (quality >= 0) food = Cooking.apply(food, quality, s);
        // Гурман: хорошее радует сильнее (витамины), однообразное надоедает быстрее.
        if (gourmet && Cooking.goodOrBetter(quality))
            food = new Food(food.kcal(), food.protein(), food.fat(), food.carbs(), food.vitamins() * 1.1, food.water());
        food = food.scale(fraction);
        double monotony = quality >= 0 ? Cooking.monotonyWeight(quality) : 1;
        if (gourmet) monotony *= Cooking.goodOrBetter(quality) ? 0.5 : s.gourmetMonotony;
        if (water != 0) food = food.plus(new Food(0, 0, 0, 0, 0, water));
        int cat = NutritionTable.category(sp.server, used.getItem());
        int method = FoodMethods.of(sp.server, used);
        ResourceLocation dishId = ForgeRegistries.ITEMS.getKey(used.getItem());
        // «Приелось» копится калориями: шаг порции — единица (укус большого блюда — его доля).
        double portions = food.kcal() > 0 && s.portionStepKcal > 0 ? food.kcal() / s.portionStepKcal * monotony : 0;
        FoodTaste taste = RpCoreAPI.taste(sp);
        boolean favourite = tasteMatches(taste.favourite(), cat, method);
        boolean disliked = !favourite && tasteMatches(taste.disliked(), cat, method);
        var meal = new Boredom.Meal(cat, method, dishId != null ? dishId.toString() : "",
                portions, favourite, disliked);
        boolean wasFull = s.hungerEnabled && !Metabolism.hasRoom(d.state, s, stomachMult(sp, d.state, s));
        consume(sp, d, food, meal, used);
        BodyEffects.afterEating(sp, d, s, food, wasFull);
        if (food.kcal() > 0) {
            double strength = Math.min(1.5, portions);
            if (favourite) MinecraftForge.EVENT_BUS.post(new RpMoodEvent(sp,
                    RpMoodEvent.Kind.FAVOURITE_FOOD, strength));
            if (disliked) MinecraftForge.EVENT_BUS.post(new RpMoodEvent(sp,
                    RpMoodEvent.Kind.DISLIKED_FOOD, strength));
            variety(sp, d.state, cat, s);
        }
        foodPoisoning(sp, used, quality, fraction, s);
        ExoticEffects.afterEating(sp, d, used, fraction, s);
    }

    /**
     * Пищевое отравление (решения Alex 27.10.2026): сырое, съеденное сырым, и неудачные стадии блюда (недожарено,
     * подгорело, пережарено, уголь). Удачное блюдо и непомеченная готовая еда не травят. Шанс — по стадии, на
     * желудок едока и на чистоплотность (ест Чистюля или готовил Чистюля); укус — меньше.
     */
    public static void foodPoisoning(ServerPlayer sp, ItemStack used, int quality, double fraction, Settings s) {
        String msg;
        double chance;
        if (quality >= 0) {
            chance = Cooking.poisonChance(quality, s);
            msg = quality == Cooking.UNDERCOOKED ? "undercooked_sick" : "burnt_sick";
            // Экзотику готовили без знания — сверху свой высокий шанс.
            if (CookingService.wildCook(used)) {
                if (Cooking.success(quality)) msg = "exotic_sick";
                chance = 1 - (1 - chance) * (1 - s.exoticUnknownPoison);
            }
        } else if (NutritionTable.raw(sp.server, used.getItem())) {
            chance = s.rawMeatPoisonChance;
            msg = "raw_meat";
        } else {
            return;
        }
        if (chance <= 0) return;
        chance *= poisonFactor(sp, used, s);
        if (sp.getRandom().nextDouble() >= chance * Math.min(1, fraction * 2)) return;
        int ticks = quality == Cooking.CHARCOAL ? 300 : 200;
        sp.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, 0));
        sp.displayClientMessage(Component.translatable("rpculinary.msg." + msg).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), true);
    }

    /** Множитель шанса отравления: желудок едока и чистоплотность (своя или повара — один раз). */
    static double poisonFactor(ServerPlayer sp, ItemStack used, Settings s) {
        double k = BodyEffects.poisonFactor(sp, s);
        if (RpCoreAPI.hasTrait(sp, RpIds.CLEAN) || CookingService.cleanCook(used)) k *= s.cleanPoisonFactor;
        return k;
    }

    /** Вкус совпал: «kind:вид» или «method:способ». */
    static boolean tasteMatches(String taste, int cat, int method) {
        if (taste == null || taste.isEmpty()) return false;
        if (cat >= 0 && taste.equals("kind:" + NutritionState.CATEGORIES[cat])) return true;
        return method >= 0 && taste.equals("method:" + NutritionState.METHODS[method]);
    }

    /**
     * «Сыт и доволен»: за RP-сутки съел не меньше {@link Settings#varietyKinds} разных видов и нет нехватки
     * нутриентов — раз в сутки событие психике (стресс −), строка ощущения и чуть быстрее отдых до конца суток.
     */
    static void variety(ServerPlayer sp, NutritionState m, int cat, Settings s) {
        long day = rpDay(sp);
        if (m.varietyDay != day) {
            m.varietyDay = day;
            m.varietyMask = 0;
            m.contentGiven = false;
        }
        if (cat >= 0) m.varietyMask |= 1 << cat;
        if (m.contentGiven || Integer.bitCount(m.varietyMask) < s.varietyKinds || Nutrients.anyLow(m, s)) return;
        m.contentGiven = true;
        MinecraftForge.EVENT_BUS.post(new RpMoodEvent(sp, RpMoodEvent.Kind.FED_CONTENT, 1));
        sp.displayClientMessage(Component.translatable("rpculinary.msg.content").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), true);
    }

    /** Предметы, о которых уже написано «неизвестный напиток» (раз на предмет за запуск). */
    static final Set<Item> UNKNOWN_DRINKS = ConcurrentHashMap.newKeySet();

    /**
     * Сколько поит выпитое (проценты; минус — сушит): общая база RP Core, затем свой датапак {@code drinks},
     * затем суп по названию. Пьётся, но нигде не записано — немного воды и строка в лог, чтобы дописать базу.
     */
    static double drinkWater(ItemStack used, Settings s) {
        Consumables.Entry c = Consumables.forItem(used);
        if (c != null && c.hasWater()) return c.water();
        double w = DrinkRules.water(used);
        if (w > 0) return w;
        if (soup(used)) return s.soupWater;
        // Зелья — не напитки (вода в бутылке записана в базе отдельно).
        if (c == null && used.getUseAnimation() == UseAnim.DRINK && !(used.getItem() instanceof PotionItem)) {
            if (UNKNOWN_DRINKS.add(used.getItem()))
                RpCulinary.LOGGER.warn("RP Culinary: неизвестный напиток {} — даёт {} % воды; допишите в rpcore/consumables",
                        ForgeRegistries.ITEMS.getKey(used.getItem()), s.unknownDrinkWater);
            return s.unknownDrinkWater;
        }
        return 0;
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
        // Вода в бутылке и т. п. (общая база RP Core, "calories": false) — не еда, сытости не даёт.
        Consumables.Entry c = Consumables.forItem(stack);
        if (c != null && !c.calories()) return null;
        FoodProperties fp = stack.getFoodProperties(sp);
        if (fp == null && NutritionRules.base(item) == null) return null;
        Food base = NutritionTable.get(sp.server, item);
        if (base == null) return null;
        FoodProperties def = item.getFoodProperties();
        if (fp != null && def != null && def.getNutrition() > 0 && fp.getNutrition() != def.getNutrition())
            base = base.scale(Math.max(0.3, Math.min(2, fp.getNutrition() / (double) def.getNutrition())));
        // Крупный кусок с весом в NBT: питание по весу (6 кг лопатки = 24 порции).
        base = base.scale(Weights.nutritionFactor(stack));
        // Состав мяса: фарш, котлета, колбаски — по виду; блюдо — мясная часть рецепта по составу (решение 29.10).
        return MeatFood.adjust(stack, base, NutritionTable.meatPart(sp.server, item));
    }

    /** Кликнул по блоку: если в этот тик прибавится ванильная сытость — значит, съел с него. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        NutritionData d = NutritionData.get(sp);
        if (d == null) return;
        d.clickedBlock = sp.level().getBlockState(e.getPos()).getBlock();
        d.clickedTick = sp.server.getTickCount();
        String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(d.clickedBlock));
        plateEater = id.startsWith("candlelight:") ? sp : null;
        d.clickedFood = "refurbished_furniture:plate".equals(id) && sp.level().getBlockEntity(e.getPos()) instanceof Container c
                ? c.getItem(0).copy() : ItemStack.EMPTY;
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
        consume(sp, d, food, new Boredom.Meal(cat, -1, "", monotonyWeight, false, false), stack);
    }

    /** То же с блюдом: вид, способ, само блюдо, вес в порциях, любимое или нелюбимое. */
    public static void consume(ServerPlayer sp, NutritionData d, Food food, Boredom.Meal meal, ItemStack stack) {
        Settings s = Settings.get();
        NutritionState m = d.state;
        var axis = Boredom.axis(m, meal, s);
        boolean fed = Metabolism.eat(m, food, meal, s);
        // Ощущения, без цифр: что приелось — блюдо, вид или способ; любимое и нелюбимое.
        String key = null;
        if (fed) key = switch (axis) {
            case DISH -> "rpculinary.monotony.dish";
            case CATEGORY -> meal.cat() >= 0 ? "rpculinary.monotony." + NutritionState.CATEGORIES[meal.cat()] : "rpculinary.monotony.dish";
            case METHOD -> "rpculinary.monotony.method";
            case NONE -> null;
        };
        else if (meal.favourite() && food.kcal() > 0) key = "rpculinary.taste.favourite";
        else if (meal.disliked() && food.kcal() > 0) key = "rpculinary.taste.disliked";
        if (key != null) sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
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
}
