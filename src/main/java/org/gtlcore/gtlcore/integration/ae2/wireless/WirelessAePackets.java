package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WirelessAePackets {
    private static final String PROTOCOL_VERSION = "1";
    private static int nextPacketId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLCore.MOD_ID, "wireless_ae"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private WirelessAePackets() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextPacketId++,
                RenameNetworkPacket.class,
                RenameNetworkPacket::encode,
                RenameNetworkPacket::decode,
                RenameNetworkPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ConnectTargetPacket.class,
                ConnectTargetPacket::encode,
                ConnectTargetPacket::decode,
                ConnectTargetPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenTargetMenuPacket.class,
                OpenTargetMenuPacket::encode,
                OpenTargetMenuPacket::decode,
                OpenTargetMenuPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenNormalTargetMenuPacket.class,
                OpenNormalTargetMenuPacket::encode,
                OpenNormalTargetMenuPacket::decode,
                OpenNormalTargetMenuPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                RequestTargetNetworksPacket.class,
                RequestTargetNetworksPacket::encode,
                RequestTargetNetworksPacket::decode,
                RequestTargetNetworksPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncTargetNetworksPacket.class,
                SyncTargetNetworksPacket::encode,
                SyncTargetNetworksPacket::decode,
                SyncTargetNetworksPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public record RenameNetworkPacket(BlockPos corePos, String name) {
        private static void encode(RenameNetworkPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.corePos);
            buffer.writeUtf(packet.name);
        }

        private static RenameNetworkPacket decode(FriendlyByteBuf buffer) {
            return new RenameNetworkPacket(buffer.readBlockPos(), buffer.readUtf(32));
        }

        private static void handle(RenameNetworkPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !isCloseEnough(player, packet.corePos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                BlockEntity blockEntity = level.getBlockEntity(packet.corePos);
                if (!(blockEntity instanceof WirelessNetworkCoreBlockEntity core)) {
                    return;
                }

                WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
                UUID frequency = core.getFrequency();
                data.setCore(frequency, GlobalPos.of(level.dimension(), packet.corePos));
                data.setNetworkName(frequency, packet.name);
                WirelessAeNetworkRuntime.requestReconnect(frequency);
                player.displayClientMessage(
                        Component.translatable("message.gtlcore.wireless_core.name_saved", data.getNetworkName(frequency)),
                        true
                );
            });
            context.setPacketHandled(true);
        }
    }

    public record ConnectTargetPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation,
                                      UUID frequency, boolean disconnect) {
        public ConnectTargetPacket(BlockPos targetPos, UUID frequency, boolean disconnect) {
            this(targetPos, null, null, frequency, disconnect);
        }

        private static void encode(ConnectTargetPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
            buffer.writeUUID(packet.frequency);
            buffer.writeBoolean(packet.disconnect);
        }

        private static ConnectTargetPacket decode(FriendlyByteBuf buffer) {
            return new ConnectTargetPacket(
                    buffer.readBlockPos(),
                    readDirection(buffer),
                    readVec3(buffer),
                    buffer.readUUID(),
                    buffer.readBoolean()
            );
        }

        private static void handle(ConnectTargetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !isCloseEnough(player, packet.targetPos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation
                );
                BlockPos targetPos = target.blockPos();
                GlobalPos targetGlobalPos = target.pos();
                WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
                for (UUID currentNetwork : data.removeMembersAt(targetGlobalPos)) {
                    WirelessAeNetworkRuntime.disconnectMembersAt(currentNetwork, targetGlobalPos);
                }

                if (packet.disconnect) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.disconnected"),
                            true
                    );
                    return;
                }

                if (!WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, targetPos, target.side())) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.invalid_target"),
                            true
                    );
                    return;
                }

                WirelessNetworkCoreBlockEntity core = WirelessAeNetworkRuntime.getLoadedCore(
                        level.getServer(),
                        packet.frequency
                );
                if (core == null) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.missing_core"),
                            true
                    );
                    return;
                }
                if (!core.isLinkedToAeNetwork()) {
                    player.displayClientMessage(
                            Component.translatable("message.gtlcore.wireless_target.core_not_connected"),
                            true
                    );
                    return;
                }

                WirelessAeNetworkRuntime.ConnectionResult result = WirelessAeNetworkRuntime.connectMemberNow(
                        level.getServer(),
                        packet.frequency,
                        target
                );
                data.addMember(packet.frequency, target);
                WirelessAeNetworkRuntime.requestReconnect(packet.frequency);

                boolean pending = result == WirelessAeNetworkRuntime.ConnectionResult.TARGET_MISSING
                        || result == WirelessAeNetworkRuntime.ConnectionResult.FAILED;
                player.displayClientMessage(
                        Component.translatable(
                                pending
                                        ? "message.gtlcore.wireless_target.linked_target_pending"
                                        : "message.gtlcore.wireless_target.linked_target",
                                data.getNetworkName(packet.frequency)
                        ),
                        true
                );
            });
            context.setPacketHandled(true);
        }
    }

    public record OpenTargetMenuPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation) {
        public OpenTargetMenuPacket(BlockPos targetPos) {
            this(targetPos, null, null);
        }

        private static void encode(OpenTargetMenuPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
        }

        private static OpenTargetMenuPacket decode(FriendlyByteBuf buffer) {
            return new OpenTargetMenuPacket(buffer.readBlockPos(), readDirection(buffer), readVec3(buffer));
        }

        private static void handle(OpenTargetMenuPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !isCloseEnough(player, packet.targetPos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation
                );
                if (WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, target.blockPos(), target.side())) {
                    WirelessAeTargetMenu.open(player, level, packet.targetPos, packet.targetSide, packet.hitLocation);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record OpenNormalTargetMenuPacket(BlockPos targetPos) {
        private static void encode(OpenNormalTargetMenuPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
        }

        private static OpenNormalTargetMenuPacket decode(FriendlyByteBuf buffer) {
            return new OpenNormalTargetMenuPacket(buffer.readBlockPos());
        }

        private static void handle(OpenNormalTargetMenuPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !isCloseEnough(player, packet.targetPos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                if (!level.hasChunkAt(packet.targetPos)) {
                    return;
                }

                BlockState state = level.getBlockState(packet.targetPos);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(packet.targetPos),
                        Direction.UP,
                        packet.targetPos,
                        false
                );
                InteractionResult result = state.use(level, player, InteractionHand.MAIN_HAND, hit);
                if (result.consumesAction()) {
                    return;
                }

                MenuProvider provider = state.getMenuProvider(level, packet.targetPos);
                if (provider != null) {
                    player.openMenu(provider);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record RequestTargetNetworksPacket(BlockPos targetPos, Direction targetSide, Vec3 hitLocation) {
        public RequestTargetNetworksPacket(BlockPos targetPos) {
            this(targetPos, null, null);
        }

        private static void encode(RequestTargetNetworksPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            writeDirection(buffer, packet.targetSide);
            writeVec3(buffer, packet.hitLocation);
        }

        private static RequestTargetNetworksPacket decode(FriendlyByteBuf buffer) {
            return new RequestTargetNetworksPacket(buffer.readBlockPos(), readDirection(buffer), readVec3(buffer));
        }

        private static void handle(RequestTargetNetworksPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !isCloseEnough(player, packet.targetPos)) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                        level,
                        packet.targetPos,
                        packet.targetSide,
                        packet.hitLocation
                );
                if (!WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, target.blockPos(), target.side())) {
                    return;
                }

                CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SyncTargetNetworksPacket(packet.targetPos, buildTargetEntries(level, target))
                );
            });
            context.setPacketHandled(true);
        }
    }

    public record SyncTargetNetworksPacket(BlockPos targetPos, List<TargetNetworkEntry> entries) {
        private static void encode(SyncTargetNetworksPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.targetPos);
            buffer.writeVarInt(packet.entries.size());
            for (TargetNetworkEntry entry : packet.entries) {
                buffer.writeUUID(entry.frequency());
                buffer.writeUtf(entry.name());
                buffer.writeBoolean(entry.connected());
            }
        }

        private static SyncTargetNetworksPacket decode(FriendlyByteBuf buffer) {
            BlockPos targetPos = buffer.readBlockPos();
            int size = buffer.readVarInt();
            List<TargetNetworkEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new TargetNetworkEntry(
                        buffer.readUUID(),
                        buffer.readUtf(32),
                        buffer.readBoolean()
                ));
            }
            return new SyncTargetNetworksPacket(targetPos, entries);
        }

        private static void handle(SyncTargetNetworksPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                try {
                    Class.forName("org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClientPacketHandler")
                            .getMethod("handleTargetNetworks", SyncTargetNetworksPacket.class)
                            .invoke(null, packet);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Client-only handler is not present on dedicated servers.
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record TargetNetworkEntry(UUID frequency, String name, boolean connected) {
    }

    private static List<TargetNetworkEntry> buildTargetEntries(ServerLevel level, WirelessAeSavedData.MemberKey target) {
        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        UUID currentNetwork = data.getMemberNetwork(target);
        List<TargetNetworkEntry> entries = new ArrayList<>();
        for (WirelessAeSavedData.NetworkInfo network : data.getNetworkInfo()) {
            entries.add(new TargetNetworkEntry(
                    network.frequency(),
                    network.name(),
                    network.frequency().equals(currentNetwork)
            ));
        }
        return entries;
    }

    private static void writeDirection(FriendlyByteBuf buffer, Direction direction) {
        buffer.writeBoolean(direction != null);
        if (direction != null) {
            buffer.writeEnum(direction);
        }
    }

    private static Direction readDirection(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(Direction.class) : null;
    }

    private static void writeVec3(FriendlyByteBuf buffer, Vec3 vec) {
        buffer.writeBoolean(vec != null);
        if (vec != null) {
            buffer.writeDouble(vec.x);
            buffer.writeDouble(vec.y);
            buffer.writeDouble(vec.z);
        }
    }

    private static Vec3 readVec3(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) : null;
    }

    private static boolean isCloseEnough(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
