package faygolover.rpculinary;

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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

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
    private void creativeTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != RpCoreAPI.MISC_TAB) return;
        for (int g = 0; g < faygolover.rpculinary.server.Exotic.GROUPS.size(); g++)
            if (faygolover.rpculinary.server.Exotic.learnable(g)) e.accept(faygolover.rpculinary.item.ResearchNotes.of(g));
    }

    public RpCulinary() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CulinaryConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCaps);
        modBus.addListener(this::onConfig);
        modBus.addListener(this::onConfigReload);
        faygolover.rpculinary.item.ResearchNotes.ITEMS.register(modBus);
        modBus.addListener(this::creativeTab);
        faygolover.rpculinary.pack.FoodPack.register(modBus);
        faygolover.rpculinary.server.SkillYield.TYPES.register(modBus);

        var bus = MinecraftForge.EVENT_BUS;
        bus.addGenericListener(Entity.class, this::attachCaps);
        bus.addListener(this::reloadListeners);
        bus.addListener(CulinaryCommands::register);
        bus.addListener(CulinaryService::onPlayerTick);
        bus.addListener(CulinaryService::onUseTick);
        bus.addListener(CulinaryService::onUseFinish);
        bus.addListener(faygolover.rpculinary.server.Sips::onStackedOnOther);
        bus.addListener(faygolover.rpculinary.server.Sips::onRightClick);
        bus.addListener(CulinaryService::onRightClickBlock);
        bus.addListener(CulinaryService::onStaminaRestored);
        bus.addListener(CulinaryService::onAttack);
        bus.addListener(CulinaryService::onClone);
        bus.addListener(CulinaryService::onLogin);
        bus.addListener(CulinaryService::onRespawn);
        bus.addListener(CulinaryService::onDatapackSync);
        bus.addListener(faygolover.rpculinary.server.CookingService::onRightClickBlock);
        bus.addListener(faygolover.rpculinary.server.CookingService::onContainerOpen);
        bus.addListener(faygolover.rpculinary.server.CookingService::onContainerClose);
        bus.addListener(faygolover.rpculinary.server.CookingService::onCrafted);
        bus.addListener(faygolover.rpculinary.server.CookingService::onSmelted);
        bus.addListener(faygolover.rpculinary.server.CookingService::onPlayerTick);
        bus.addListener(faygolover.rpculinary.server.CookingService::forget);
        bus.addListener(faygolover.rpculinary.server.CookingService::onServerStopped);
        bus.addListener(CulinaryCommands::onTimeSkip);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, faygolover.rpculinary.server.Butchering::onRightClick);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true, faygolover.rpculinary.server.Butchering::afterRightClick);
        bus.addListener(faygolover.rpculinary.server.Butchering::onPlayerTick);
        bus.addListener(faygolover.rpculinary.server.ExoticEffects::onPlayerTick);
        bus.addListener(faygolover.rpculinary.server.Butchering::onItemJoin);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> faygolover.rpculinary.client.ClientSetup.init(modBus));
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        Net.register();
        e.enqueueWork(() -> {
            RpCoreAPI.registerModule(MODID, "питание: сытость в калориях, вода, нутриенты, вес");
            RpCoreAPI.registerNutrition(new CulinaryProvider());
            CulinaryCommands.describe();
        });
    }

    private void registerCaps(RegisterCapabilitiesEvent e) {
        e.register(NutritionData.class);
    }

    private void attachCaps(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) e.addCapability(NutritionData.ID, new NutritionData.Provider());
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
