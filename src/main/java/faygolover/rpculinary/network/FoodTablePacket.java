package faygolover.rpculinary.network;

import faygolover.rpculinary.core.Food;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Состав всей известной еды и её виды — для подсказок (шлётся при входе и перезагрузке датапаков). */
public record FoodTablePacket(Map<ResourceLocation, Food> foods, Map<ResourceLocation, Integer> categories) {
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
    }

    public static FoodTablePacket decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Food> foods = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++)
            foods.put(buf.readResourceLocation(), new Food(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        Map<ResourceLocation, Integer> cats = new HashMap<>();
        int c = buf.readVarInt();
        for (int i = 0; i < c; i++) cats.put(buf.readResourceLocation(), (int) buf.readByte());
        return new FoodTablePacket(foods, cats);
    }

    public static void handle(FoodTablePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> faygolover.rpculinary.client.ClientNutrition.setFoods(p.foods, p.categories));
        ctx.get().setPacketHandled(true);
    }
}
