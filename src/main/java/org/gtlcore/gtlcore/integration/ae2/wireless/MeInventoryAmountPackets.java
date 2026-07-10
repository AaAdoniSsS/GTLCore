package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.MeInventoryAmountService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class MeInventoryAmountPackets {

    private static final int ITEM_KEY_TYPE = 0;
    private static final int FLUID_KEY_TYPE = 1;
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
            writeKey(buffer, packet.key);
        }

        private static Request decode(FriendlyByteBuf buffer) {
            return new Request(readKey(buffer));
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
            writeKey(buffer, packet.key);
            buffer.writeBoolean(packet.available);
            if (packet.available) {
                buffer.writeVarLong(packet.amount);
            }
        }

        private static Response decode(FriendlyByteBuf buffer) {
            AEKey key = readKey(buffer);
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

    private static void writeKey(FriendlyByteBuf buffer, AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            buffer.writeByte(ITEM_KEY_TYPE);
            itemKey.writeToPacket(buffer);
        } else if (key instanceof AEFluidKey fluidKey) {
            buffer.writeByte(FLUID_KEY_TYPE);
            fluidKey.writeToPacket(buffer);
        } else {
            throw new IllegalArgumentException("Unsupported ME inventory key: " + key);
        }
    }

    private static AEKey readKey(FriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case ITEM_KEY_TYPE -> AEItemKey.fromPacket(buffer);
            case FLUID_KEY_TYPE -> AEFluidKey.fromPacket(buffer);
            default -> throw new IllegalArgumentException("Unsupported ME inventory key type");
        };
    }
}
