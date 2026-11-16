package faygolover.rpculinary;

import faygolover.rpculinary.server.CulinaryPlayerEvents;
import faygolover.rpculinary.server.CulinaryEating;
import com.mojang.logging.LogUtils;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpculinary.capability.NutritionData;
import faygolover.rpculinary.config.CulinaryConfig;
import faygolover.rpculinary.data.DrinkRules;
import faygolover.rpculinary.data.NutritionRules;
import faygolover.rpculinary.network.Net;
import faygolover.rpculinary.server.CulinaryCommands;
import faygolover.rpculinary.server.CulinaryProvider;
import faygolover.rpculinary.server.CulinaryService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import faygolover.rpcore.api.StatusLines;
import faygolover.rpcore.api.event.RpCharacterWipeEvent;
import faygolover.rpculinary.client.ClientSetup;
import faygolover.rpculinary.core.Cookbook;
import faygolover.rpculinary.item.CookbookItems;
import faygolover.rpculinary.item.ResearchNotes;
import faygolover.rpculinary.pack.FoodPack;
import faygolover.rpculinary.server.Butchering;
import faygolover.rpculinary.server.ContainerFill;
import faygolover.rpculinary.server.CookingService;
import faygolover.rpculinary.server.CulinaryStatus;
import faygolover.rpculinary.server.Exotic;
import faygolover.rpculinary.server.ExoticEffects;
import faygolover.rpculinary.server.Feeding;
import faygolover.rpculinary.server.Processing;
import faygolover.rpculinary.server.Provenance;
import faygolover.rpculinary.server.Sips;
import faygolover.rpculinary.server.SkillYield;
import faygolover.rpculinary.server.Techniques;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * RP Culinary — питание для семейства RP-модов (дизайн — {@code docs/01_design.md}): сытость в калориях вместо
 * окорочков, вода, нутриенты, вес и жир от еды. Требует только RP Core; RP Medicine, RP Stamina и RP Perks берут
 * отсюда голод, воду и вес через {@link faygolover.rpcore.api.NutritionProvider}.
 */
@Mod(RpCulinary.MODID)
public class RpCulinary {
    public static final String MODID = "rpculinary";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Книги «Записи исследований» — во вкладку ГМа «RP: разное» (RP Core), по одной на группу с книгой. */
    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != RpCoreAPI.MISC_TAB) return;
        for (int g = 0; g < Exotic.GROUPS.size(); g++)
            if (Exotic.learnable(g)) e.accept(ResearchNotes.of(g));
        for (String s : Cookbook.SECTIONS) e.accept(CookbookItems.book(s));
        e.accept(new ItemStack(CookbookItems.NOTEBOOK.get()));
    }

    public RpCulinary() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CulinaryConfig.SPEC);
        modBus.addListener(this::commonSetup);
        NutritionData.register();
        modBus.addListener(this::onConfig);
        modBus.addListener(this::onConfigReload);
        CookbookItems.init();
        ResearchNotes.ITEMS.register(modBus);
        modBus.addListener(this::creativeTab);
        FoodPack.register(modBus);
        SkillYield.TYPES.register(modBus);

        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(this::reloadListeners);
        bus.addListener(CulinaryCommands::register);
        bus.addListener(CulinaryService::onPlayerTick);
        bus.addListener(CulinaryEating::onUseTick);
        bus.addListener(CulinaryEating::onUseStart);
        bus.addListener(Feeding::onEntityInteract);
        bus.addListener(Feeding::onServerTick);
        bus.addListener(Feeding::onLogin);
        bus.addListener(EventPriority.LOW, false, RpCharacterWipeEvent.class, CulinaryPlayerEvents::onWipe);
        bus.addListener(EventPriority.HIGHEST, CookingService::onHandsCheck);
        bus.addListener(CulinaryEating::onUseFinish);
        bus.addListener(Sips::onStackedOnOther);
        bus.addListener(Sips::onRightClick);
        bus.addListener(CulinaryEating::onRightClickBlock);
        bus.addListener(CulinaryPlayerEvents::onStaminaRestored);
        bus.addListener(CulinaryPlayerEvents::onAttack);
        bus.addListener(CulinaryPlayerEvents::onDatapackSync);
        bus.addListener(CookingService::onRightClickBlock);
        bus.addListener(CookingService::onContainerOpen);
        bus.addListener(CookingService::onContainerClose);
        bus.addListener(CookingService::onCrafted);
        bus.addListener(CookingService::onSmelted);
        bus.addListener(CookingService::onPlayerTick);
        bus.addListener(CookingService::forget);
        bus.addListener(CookingService::onServerStopped);
        bus.addListener(CulinaryCommands::onTimeSkip);
        bus.addListener(EventPriority.HIGHEST, Butchering::onRightClick);
        bus.addListener(EventPriority.LOWEST, true, Butchering::afterRightClick);
        bus.addListener(Butchering::onPlayerTick);
        bus.addListener(ExoticEffects::onPlayerTick);
        bus.addListener(EventPriority.HIGH, Techniques::onRightClickBlock);
        bus.addListener(EventPriority.HIGHEST, Processing::onMincer);
        bus.addListener(Provenance::onPlayerTick);
        bus.addListener(Processing::onBowlClick);
        bus.addListener(Processing::onServerTick);
        bus.addListener(ContainerFill::onStackedOnOther);
        bus.addListener(ContainerFill::onRightClickBlock);
        bus.addListener(ContainerFill::onPlace);
        bus.addListener(ContainerFill::onBreak);
        bus.addListener(ContainerFill::onItemJoin);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        Net.register();
        e.enqueueWork(() -> {
            RpCoreAPI.registerModule(MODID, "питание: сытость в калориях, вода, нутриенты, вес");
            // Необязательные интеграции — в общий отчёт RP Core (/rpcore compat).
            faygolover.rpcore.api.RpCoreAPI.reportCompat(MODID, "legendarysurvivaloverhaul");
            faygolover.rpcore.api.RpCoreAPI.reportCompat(MODID, "butchery");
            faygolover.rpcore.api.RpCoreAPI.reportCompat(MODID, "farm_and_charm");
            faygolover.rpcore.api.RpCoreAPI.reportCompat(MODID, "mekanism");
            RpCoreAPI.registerNutrition(new CulinaryProvider());
            StatusLines.register(CulinaryStatus.INSTANCE);
            CulinaryCommands.describe();
        });
    }

    private void reloadListeners(AddReloadListenerEvent e) {
        e.addListener(NutritionRules.LOADER);
        e.addListener(DrinkRules.LOADER);
    }

    private void onConfig(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == CulinaryConfig.SPEC) CulinaryConfig.bake();
    }

    private void onConfigReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == CulinaryConfig.SPEC) CulinaryConfig.bake();
    }
}
