package faygolover.rpculinary.client;

import com.mojang.blaze3d.platform.InputConstants;
import faygolover.rpcore.client.hud.HudRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Клиент: полоски в общем HUD, скрыть ванильные окорочка (и капли LSO), клавиша окна «Питание», подсказки. */
public final class ClientSetup {
    private ClientSetup() {}

    public static final KeyMapping NUTRITION = new KeyMapping("key.rpculinary.nutrition", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.rpculinary");
    private static final ResourceLocation LSO_THIRST = new ResourceLocation("legendarysurvivaloverhaul", "thirst");
    private static final ResourceLocation LSO_COLD_HUNGER = new ResourceLocation("legendarysurvivaloverhaul", "cold_hunger");

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::setup);
        modBus.addListener(ClientSetup::keys);
        modBus.addListener(PortionBars::register);
        modBus.addListener(MeatColors::register);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onOverlay);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLogout);
        MinecraftForge.EVENT_BUS.addListener(FoodTooltips::onTooltip);
    }

    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            // обложка журнала — по группе (D2): модель research_notes.json выбирает по этому значению
            net.minecraft.client.renderer.item.ItemProperties.register(faygolover.rpculinary.item.ResearchNotes.NOTES.get(),
                    new ResourceLocation("rpculinary", "group"),
                    (st, level, entity, seed) -> (faygolover.rpculinary.item.ResearchNotes.group(st) + 1) / 100f);
            net.minecraft.client.renderer.item.ItemProperties.register(faygolover.rpculinary.item.CookbookItems.COOKBOOK.get(),
                    new ResourceLocation("rpculinary", "section"),
                    (st, level, entity, seed) -> (faygolover.rpculinary.core.Cookbook.SECTIONS.indexOf(faygolover.rpculinary.core.Cookbook.section(st)) + 1) / 100f);
            HudRegistry.register(new CulinaryHud.Satiety());
            HudRegistry.register(new CulinaryHud.Water());
        });
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        e.register(NUTRITION);
    }

    /**
     * Вместо окорочков — наша полоска (рисует общий HUD). Место над ней держим, как держали окорочка, — иначе
     * пузырьки воздуха лягут на полоску.
     */
    private static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        ResourceLocation id = e.getOverlay().id();
        boolean hunger = ClientNutrition.hunger();
        boolean lsoThirst = ClientNutrition.thirstMode() == 2;
        if (hunger && id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            e.setCanceled(true);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui instanceof ForgeGui fg && mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator())
                fg.rightHeight += ClientNutrition.thirstMode() != 0 ? 19 : 10;
        } else if (hunger && id.equals(LSO_COLD_HUNGER)) {
            e.setCanceled(true);
        } else if (lsoThirst && id.equals(LSO_THIRST)) {
            e.setCanceled(true);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (NUTRITION.consumeClick()) {
            if (mc.player != null && mc.screen == null && ClientNutrition.known) mc.setScreen(new NutritionScreen());
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientNutrition.clear();
    }
}
