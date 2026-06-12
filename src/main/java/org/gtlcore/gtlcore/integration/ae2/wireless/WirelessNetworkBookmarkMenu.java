package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WirelessNetworkBookmarkMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final UUID favoriteNetwork;
    private final List<Entry> networks;

    public WirelessNetworkBookmarkMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        super(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_MENU.get(), containerId);
        this.pos = data.readBlockPos();
        this.favoriteNetwork = readNullableUUID(data);
        int size = data.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(data.readUUID(), data.readUtf(32)));
        }
        this.networks = List.copyOf(entries);
    }

    public WirelessNetworkBookmarkMenu(int containerId, Inventory inventory, BlockPos pos,
                                       UUID favoriteNetwork, List<Entry> networks) {
        super(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_MENU.get(), containerId);
        this.pos = pos;
        this.favoriteNetwork = favoriteNetwork;
        this.networks = List.copyOf(networks);
    }

    public static void open(ServerPlayer player, WirelessNetworkBookmarkBlockEntity bookmark) {
        WirelessAeSavedData data = WirelessAeSavedData.get(player.serverLevel().getServer());
        BlockPos pos = bookmark.getBlockPos();
        UUID favoriteNetwork = data.getFavoriteNetwork();
        List<Entry> networks = data.getNetworkInfo().stream()
                .map(network -> new Entry(network.frequency(), network.name()))
                .toList();
        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.wireless_bookmark");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player menuPlayer) {
                        return new WirelessNetworkBookmarkMenu(containerId, inventory, pos, favoriteNetwork, networks);
                    }
                },
                buffer -> write(buffer, pos, favoriteNetwork, networks));
    }

    private static void write(FriendlyByteBuf buffer, BlockPos pos, UUID favoriteNetwork, List<Entry> networks) {
        buffer.writeBlockPos(pos);
        writeNullableUUID(buffer, favoriteNetwork);
        buffer.writeVarInt(networks.size());
        for (Entry network : networks) {
            buffer.writeUUID(network.frequency());
            buffer.writeUtf(network.name());
        }
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public UUID getFavoriteNetwork() {
        return this.favoriteNetwork;
    }

    public List<Entry> getNetworks() {
        return this.networks;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
        return blockEntity instanceof WirelessNetworkBookmarkBlockEntity && player.distanceToSqr(
                this.pos.getX() + 0.5D,
                this.pos.getY() + 0.5D,
                this.pos.getZ() + 0.5D) <= 64.0D;
    }

    private static UUID readNullableUUID(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static void writeNullableUUID(FriendlyByteBuf buffer, UUID frequency) {
        buffer.writeBoolean(frequency != null);
        if (frequency != null) {
            buffer.writeUUID(frequency);
        }
    }

    public record Entry(UUID frequency, String name) {}
}
