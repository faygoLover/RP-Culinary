package faygolover.rpculinary.network;

import faygolover.rpculinary.core.Food;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Состав всей известной еды и её виды — для подсказок (шлётся при входе и перезагрузке датапаков). */
public record FoodTablePacket(Map<ResourceLocation, Food> foods, Map<ResourceLocation, Integer> categories,
                              Map<ResourceLocation, int[]> drinks) {
    /** Ключ ванильной бутылки воды (у зелий один id предмета). */
    public static final ResourceLocation WATER_POTION = new ResourceLocation("rpculinary", "water_potion");

    public static void encode(FoodTablePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.foods.size());
        p.foods.forEach((id, f) -> {
            buf.writeResourceLocation(id);
            buf.writeFloat((float) f.kcal());
            buf.writeFloat((float) f.protein());
            buf.writeFloat((float) f.fat());
            buf.writeFloat((float) f.carbs());
            buf.writeFloat((float) f.vitamins());
            buf.writeFloat((float) f.water());
        });
        buf.writeVarInt(p.categories.size());
        p.categories.forEach((id, c) -> {
            buf.writeResourceLocation(id);
            buf.writeByte(c);
        });
        buf.writeVarInt(p.drinks.size());
        p.drinks.forEach((id, d) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(d[0]);
            buf.writeVarInt(d[1]);
        });
    }

    public static FoodTablePacket decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Food> foods = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++)
            foods.put(buf.readResourceLocation(), new Food(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        Map<ResourceLocation, Integer> cats = new HashMap<>();
        int c = buf.readVarInt();
        for (int i = 0; i < c; i++) cats.put(buf.readResourceLocation(), (int) buf.readByte());
        Map<ResourceLocation, int[]> drinks = new HashMap<>();
        int dn = buf.readVarInt();
        for (int i = 0; i < dn; i++) drinks.put(buf.readResourceLocation(), new int[]{buf.readVarInt(), buf.readVarInt()});
        return new FoodTablePacket(foods, cats, drinks);
    }

    public static void handle(FoodTablePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> faygolover.rpculinary.client.ClientNutrition.setFoods(p.foods, p.categories, p.drinks));
        ctx.get().setPacketHandled(true);
    }
}
