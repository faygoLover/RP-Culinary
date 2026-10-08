package faygolover.rpculinary.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Своё питание — игроку: полоски HUD и окно «Питание».
 *
 * @param thirstMode 0 — жажды нет, 1 — своя, 2 — LSO
 * @param stomach    ещё в желудке, в долях полной сытости
 */
public record SyncPacket(boolean hunger, int thirstMode, float satiety, float stomach, float hydration, int hungerStage, int thirstStage,
                         boolean poorAppetite, boolean room, float[] nutrients, int fedUpMask, float weightKg, float fatPercent,
                         float recentKcal, boolean nutrientsOn) {

    public static void encode(SyncPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.hunger);
        buf.writeByte(p.thirstMode);
        buf.writeFloat(p.satiety);
        buf.writeFloat(p.stomach);
        buf.writeFloat(p.hydration);
        buf.writeByte(p.hungerStage);
        buf.writeByte(p.thirstStage);
        buf.writeBoolean(p.poorAppetite);
        buf.writeBoolean(p.room);
        buf.writeByte(p.nutrients.length);
        for (float v : p.nutrients) buf.writeFloat(v);
        buf.writeVarInt(p.fedUpMask);
        buf.writeFloat(p.weightKg);
        buf.writeFloat(p.fatPercent);
        buf.writeFloat(p.recentKcal);
        buf.writeBoolean(p.nutrientsOn);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        boolean hunger = buf.readBoolean();
        int thirst = buf.readByte();
        float sat = buf.readFloat(), stomach = buf.readFloat(), hyd = buf.readFloat();
        int hs = buf.readByte(), ts = buf.readByte();
        boolean poor = buf.readBoolean(), room = buf.readBoolean();
        float[] nu = new float[buf.readByte()];
        for (int i = 0; i < nu.length; i++) nu[i] = buf.readFloat();
        return new SyncPacket(hunger, thirst, sat, stomach, hyd, hs, ts, poor, room, nu, buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readBoolean());
    }

    public static void handle(SyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> faygolover.rpculinary.client.ClientNutrition.set(p));
        ctx.get().setPacketHandled(true);
    }
}
