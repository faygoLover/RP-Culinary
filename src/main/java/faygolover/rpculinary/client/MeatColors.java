package faygolover.rpculinary.client;

import faygolover.rpculinary.core.MeatFood;
import faygolover.rpculinary.core.Source;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Подкраска форм мяса в цвет вида (решения Alex 29.10, галерея q:tint, q:mince): у вида нет своего фарша, обрезков, котлеты,
 * колбасок — берём один из трёх фаршей (или общий предмет) и красим. Цвет — множитель «вид / вид-основа спрайта»
 * ({@link MeatFood.Form#nativeSpecies}), поэтому основа не меняется, а кенгуру темнеет. Домашнее — в цвет первого фарша смеси.
 */
public final class MeatColors {
    private MeatColors() {}

    /** Цвет сырого мяса вида (RGB). */
    private static final Map<String, Integer> COLOR = Map.ofEntries(
            Map.entry("cow", 0x8B2E2A), Map.entry("sheep", 0x9E3B36), Map.entry("goat", 0x96392F), Map.entry("pig", 0xD98C86),
            Map.entry("chicken", 0xE8B9A0), Map.entry("rabbit", 0xD9A08F), Map.entry("horse", 0x7A2424), Map.entry("donkey", 0x7E2B28),
            Map.entry("mule", 0x802D29), Map.entry("llama", 0x8C3A34), Map.entry("camel", 0x86352F), Map.entry("kangaroo", 0x6E1F1A),
            Map.entry("moose", 0x6B2320), Map.entry("polar_bear", 0x70302A), Map.entry("panda", 0x80342E), Map.entry("wolf", 0x7D3A33),
            Map.entry("fox", 0x8A4A3E), Map.entry("cat", 0xA8605A), Map.entry("ocelot", 0xA0584F), Map.entry("turtle", 0x9B7B5A),
            Map.entry("dolphin", 0x5A2830), Map.entry("frog", 0xE0C8B8), Map.entry("bat", 0x7A4B48), Map.entry("axolotl", 0xF0B8C0),
            Map.entry("sniffer", 0x6D3B2D), Map.entry("hoglin", 0xA2483C), Map.entry("strider", 0x9A3C3C), Map.entry("dinosaur", 0x7B3B2B),
            Map.entry("fish", 0xF0D7C8), Map.entry("squid", 0xF2E6DE), Map.entry("bee", 0xC8A050), Map.entry("spider", 0x5B4A5E),
            Map.entry("cave_spider", 0x3F5A6A), Map.entry("silverfish", 0x9A9AA0), Map.entry("endermite", 0x5C3F6E),
            Map.entry("enderman", 0x3A2B45), Map.entry("shulker", 0x9D7BA8), Map.entry("guardian", 0x6E8F7E),
            Map.entry("elder_guardian", 0x8A9A86), Map.entry("warden", 0x1F3A4A), Map.entry("creeper", 0x5E8A4E),
            Map.entry("human", 0xC87870), Map.entry("villager", 0xC07468), Map.entry("pillager", 0xB46E66), Map.entry("vindicator", 0xB46E66),
            Map.entry("evoker", 0xB46E66), Map.entry("witch", 0xA06A60), Map.entry("piglin", 0xD08880), Map.entry("zombie", 0x6E7E5A),
            Map.entry("phantom", 0x5A6A88), Map.entry("slime", 0x7EB86A), Map.entry("ender_dragon", 0x3B2A40));
    private static final int OFFAL = 0x5A2620;

    public static void register(RegisterColorHandlersEvent.Item e) {
        for (var en : MeatFood.FORMS.entrySet()) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(en.getKey()));
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            String nativeSp = en.getValue().nativeSpecies();
            e.register((stack, tint) -> tint == 0 ? tint(stack, nativeSp) : -1, item);
        }
    }

    /** Множитель цвета (0xRRGGBB) или -1 — не красить. */
    static int tint(ItemStack st, String nativeSp) {
        boolean offal = st.getTag() != null && st.getTag().getBoolean(MeatFood.OFFAL_FORM);
        String look = Source.look(st);
        if (!offal && (look == null || look.equals(nativeSp))) return -1;
        Integer c = offal ? Integer.valueOf(OFFAL) : COLOR.get(look);
        Integer n = COLOR.get(nativeSp);
        if (c == null || n == null) return -1;
        int r = ch(c >> 16, n >> 16), g = ch(c >> 8, n >> 8), b = ch(c, n);
        return r << 16 | g << 8 | b;
    }

    private static int ch(int c, int n) {
        c &= 0xFF;
        n &= 0xFF;
        return Math.min(255, Math.round(c * 255f / Math.max(1, n)));
    }
}
