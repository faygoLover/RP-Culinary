package faygolover.rpculinary.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер: игрок начал или закончил кормить другого (рука вытянута). */
public record FeedPosePacket(int entity, boolean on) {

    public static void encode(FeedPosePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeBoolean(p.on);
    }

    public static FeedPosePacket decode(FriendlyByteBuf buf) {
        return new FeedPosePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(FeedPosePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> faygolover.rpculinary.client.FeedPose.set(p.entity, p.on));
        ctx.get().setPacketHandled(true);
    }
}
