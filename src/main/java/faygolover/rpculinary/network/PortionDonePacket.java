package faygolover.rpculinary.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер: глоток или укус сделан, предмет остался в руке. Клиент снимает «использую» (у своего игрока оно не
 * синхронизируется само), и если ПКМ всё ещё зажата, ваниль сама начинает следующий глоток или укус.
 */
public record PortionDonePacket() {

    public static void encode(PortionDonePacket p, FriendlyByteBuf buf) {}

    public static PortionDonePacket decode(FriendlyByteBuf buf) {
        return new PortionDonePacket();
    }

    public static void handle(PortionDonePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(faygolover.rpculinary.client.ClientNutrition::portionDone);
        ctx.get().setPacketHandled(true);
    }
}
