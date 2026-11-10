package faygolover.rpculinary.network;

import faygolover.rpculinary.RpCulinary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** Сеть мода: состояние питания игроку и таблица состава еды для подсказок. */
public final class Net {
    private Net() {}

    private static final String PROTOCOL = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(RpCulinary.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, FoodTablePacket.class, FoodTablePacket::encode, FoodTablePacket::decode, FoodTablePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CookbookPacket.class, CookbookPacket::encode, CookbookPacket::decode, CookbookPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, FeedPosePacket.class, FeedPosePacket::encode, FeedPosePacket::decode, FeedPosePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PortionDonePacket.class, PortionDonePacket::encode, PortionDonePacket::decode, PortionDonePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer sp, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg);
    }
}
