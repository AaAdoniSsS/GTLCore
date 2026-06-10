package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class WirelessNetworkCoreMenu extends AbstractContainerMenu {

    @Getter
    private final BlockPos pos;
    @Getter
    private final UUID frequency;
    @Getter
    private final String networkName;
    private int linkedToAeNetwork;

    public WirelessNetworkCoreMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(
                containerId,
                inventory,
                data.readBlockPos(),
                data.readUUID(),
                data.readUtf(32),
                data.readBoolean());
    }

    public WirelessNetworkCoreMenu(int containerId, Inventory inventory, BlockPos pos, UUID frequency,
                                   String networkName, boolean linkedToAeNetwork) {
        super(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_MENU.get(), containerId);
        this.pos = pos;
        this.frequency = frequency;
        this.networkName = networkName;
        this.linkedToAeNetwork = linkedToAeNetwork ? 1 : 0;
        this.addDataSlot(new DataSlot() {

            @Override
            public int get() {
                if (!inventory.player.level().isClientSide && inventory.player.level().getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
                    return core.isLinkedToAeNetwork() ? 1 : 0;
                }
                return WirelessNetworkCoreMenu.this.linkedToAeNetwork;
            }

            @Override
            public void set(int value) {
                WirelessNetworkCoreMenu.this.linkedToAeNetwork = value;
            }
        });
    }

    public static void open(ServerPlayer player, WirelessNetworkCoreBlockEntity core) {
        WirelessAeSavedData data = WirelessAeSavedData.get(player.serverLevel().getServer());
        UUID frequency = core.getFrequency();
        String networkName = data.getNetworkName(frequency);
        boolean linked = core.isLinkedToAeNetwork();
        BlockPos pos = core.getBlockPos();
        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.wireless_core");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player menuPlayer) {
                        return new WirelessNetworkCoreMenu(containerId, inventory, pos, frequency, networkName, linked);
                    }
                },
                buffer -> write(buffer, pos, frequency, networkName, linked));
    }

    private static void write(FriendlyByteBuf buffer, BlockPos pos, UUID frequency, String networkName,
                              boolean linked) {
        buffer.writeBlockPos(pos);
        buffer.writeUUID(frequency);
        buffer.writeUtf(networkName);
        buffer.writeBoolean(linked);
    }

    public boolean isLinkedToAeNetwork() {
        return this.linkedToAeNetwork != 0;
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
        return blockEntity instanceof WirelessNetworkCoreBlockEntity && player.distanceToSqr(
                this.pos.getX() + 0.5D,
                this.pos.getY() + 0.5D,
                this.pos.getZ() + 0.5D) <= 64.0D;
    }
}
