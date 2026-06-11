package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class WirelessAeTargetMenu extends AbstractContainerMenu {

    private final BlockPos targetPos;
    private final BlockPos originPos;
    private final Direction targetSide;
    private final List<Entry> networks;

    public WirelessAeTargetMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        super(GTLWirelessAeContent.WIRELESS_AE_TARGET_MENU.get(), containerId);
        this.targetPos = data.readBlockPos();
        this.originPos = data.readBlockPos();
        this.targetSide = readDirection(data);
        int size = data.readVarInt();
        this.networks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.networks.add(new Entry(
                    data.readUUID(),
                    data.readUtf(32),
                    data.readBoolean(),
                    data.readBoolean()));
        }
    }

    public WirelessAeTargetMenu(int containerId, Inventory inventory, BlockPos targetPos, BlockPos originPos,
                                Direction targetSide, List<Entry> networks) {
        super(GTLWirelessAeContent.WIRELESS_AE_TARGET_MENU.get(), containerId);
        this.targetPos = targetPos;
        this.originPos = originPos;
        this.targetSide = targetSide;
        this.networks = List.copyOf(networks);
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos originPos) {
        open(player, level, originPos, null, null);
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos originPos,
                            Direction targetSide, Vec3 hitLocation) {
        WirelessAeSavedData.MemberKey target = WirelessAeNetworkRuntime.resolveWirelessTarget(
                level,
                originPos,
                targetSide,
                hitLocation);
        BlockPos resolvedTargetPos = target.blockPos();
        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        UUID currentNetwork = data.getMemberNetwork(target);
        UUID wiredNetwork = WirelessAeNetworkRuntime.findWiredNetworkFrequency(level.getServer(), target);
        UUID connectedNetwork = wiredNetwork == null ? WirelessAeNetworkRuntime.findConnectedNetworkFrequency(level.getServer(), target) : wiredNetwork;
        List<Entry> entries = new ArrayList<>();
        for (WirelessAeSavedData.NetworkInfo network : data.getNetworkInfo()) {
            boolean connected = network.frequency().equals(connectedNetwork);
            boolean disconnectable = wiredNetwork == null && connected && network.frequency().equals(currentNetwork) && WirelessAeNetworkRuntime.hasWirelessConnection(network.frequency(), target);
            entries.add(new Entry(
                    network.frequency(),
                    network.name(),
                    connected,
                    disconnectable));
        }

        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.wireless_target");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player menuPlayer) {
                        return new WirelessAeTargetMenu(
                                containerId,
                                inventory,
                                resolvedTargetPos,
                                originPos,
                                target.side(),
                                entries);
                    }
                },
                buffer -> write(buffer, resolvedTargetPos, originPos, target.side(), entries));
    }

    private static void write(FriendlyByteBuf buffer, BlockPos targetPos, BlockPos originPos, Direction targetSide,
                              List<Entry> entries) {
        buffer.writeBlockPos(targetPos);
        buffer.writeBlockPos(originPos);
        writeDirection(buffer, targetSide);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.frequency());
            buffer.writeUtf(entry.name());
            buffer.writeBoolean(entry.connected());
            buffer.writeBoolean(entry.disconnectable());
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || player.distanceToSqr(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D) <= 64.0D;
    }

    public record Entry(UUID frequency, String name, boolean connected, boolean disconnectable) {}

    private static void writeDirection(FriendlyByteBuf buffer, Direction direction) {
        buffer.writeBoolean(direction != null);
        if (direction != null) {
            buffer.writeEnum(direction);
        }
    }

    private static Direction readDirection(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(Direction.class) : null;
    }
}
