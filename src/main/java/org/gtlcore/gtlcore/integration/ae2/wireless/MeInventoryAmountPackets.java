package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.MeInventoryAmountService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import appeng.api.stacks.AEKey;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class MeInventoryAmountPackets {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long REQUEST_WINDOW_TICKS = 20;
    private static final MeInventoryRequestLimiter<ServerPlayer> REQUEST_LIMITER = new MeInventoryRequestLimiter<>(MAX_REQUESTS_PER_WINDOW, REQUEST_WINDOW_TICKS);

    private MeInventoryAmountPackets() {}

    public static void register(SimpleChannel channel, IntSupplier packetIds) {
        channel.registerMessage(
                packetIds.getAsInt(),
                Request.class,
                Request::encode,
                Request::decode,
                Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(
                packetIds.getAsInt(),
                Response.class,
                Response::encode,
                Response::decode,
                Response::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendRequest(AEKey key) {
        WirelessAePackets.CHANNEL.sendToServer(new Request(key));
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
                if (player == null) {
                    return;
                }
                if (!REQUEST_LIMITER.tryAcquire(player, player.serverLevel().getGameTime())) {
                    return;
                }
                MeInventoryAmountService.Result result = MeInventoryAmountService.query(player, packet.key);
                WirelessAePackets.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new Response(packet.key, result.available(), result.amount()));
            });
            context.setPacketHandled(true);
        }
    }

    public record Response(AEKey key, boolean available, long amount) {

        public Response {
            amount = available ? Math.max(0, amount) : 0;
        }

        private static void encode(Response packet, FriendlyByteBuf buffer) {
            WirelessAeKeyPacketCodec.write(buffer, packet.key);
            buffer.writeBoolean(packet.available);
            if (packet.available) {
                buffer.writeVarLong(packet.amount);
            }
        }

        private static Response decode(FriendlyByteBuf buffer) {
            AEKey key = WirelessAeKeyPacketCodec.read(buffer);
            boolean available = buffer.readBoolean();
            long amount = available ? buffer.readVarLong() : 0;
            return new Response(key, available, amount);
        }

        private static void handle(Response packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                try {
                    Class.forName("org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler")
                            .getMethod("handleMeInventoryAmount", Response.class)
                            .invoke(null, packet);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Client-only handler is not present on dedicated servers.
                }
            });
            context.setPacketHandled(true);
        }
    }
}
