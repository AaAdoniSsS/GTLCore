package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import appeng.api.stacks.AEKey;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class JeiWirelessTerminalOrderPackets {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long REQUEST_WINDOW_TICKS = 20;
    private static final MeInventoryRequestLimiter<ServerPlayer> REQUEST_LIMITER = new MeInventoryRequestLimiter<>(
            MAX_REQUESTS_PER_WINDOW,
            REQUEST_WINDOW_TICKS);

    private JeiWirelessTerminalOrderPackets() {}

    public static void register(SimpleChannel channel, IntSupplier packetIds) {
        channel.registerMessage(
                packetIds.getAsInt(),
                Request.class,
                Request::encode,
                Request::decode,
                Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendRequest(AEKey key) {
        if (WirelessAeKeyPacketCodec.supports(key)) {
            WirelessAePackets.CHANNEL.sendToServer(new Request(key));
        }
    }

    public record Request(AEKey key) {

        private static void encode(Request packet, FriendlyByteBuf buffer) {
            WirelessAeKeyPacketCodec.write(buffer, packet.key);
        }

        private static Request decode(FriendlyByteBuf buffer) {
            return new Request(WirelessAeKeyPacketCodec.read(buffer));
        }

        private static void handle(Request packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) {
                    WirelessTerminalCraftingMenuService.open(player, packet.key);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
