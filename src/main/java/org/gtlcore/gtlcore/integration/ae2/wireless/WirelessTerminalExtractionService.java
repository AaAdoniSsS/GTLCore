package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

public final class WirelessTerminalExtractionService {

    private WirelessTerminalExtractionService() {}

    public static boolean extractToCursor(ServerPlayer player, AEKey key) {
        if (!WirelessAeKeyPacketCodec.supports(key) || !player.containerMenu.getCarried().isEmpty()) {
            return false;
        }

        WirelessTerminalGridResolver.Connection connection = WirelessTerminalGridResolver.findConnection(player, player.serverLevel());
        if (connection == null) {
            return false;
        }

        MEStorage storage = connection.host().getInventory();
        if (storage == null) {
            return false;
        }
        IActionSource source = IActionSource.ofPlayer(player);
        boolean extracted;
        if (key instanceof AEItemKey itemKey) {
            extracted = extractItem(player, connection, storage, source, itemKey);
        } else if (key instanceof AEFluidKey fluidKey) {
            extracted = extractFluid(player, connection, storage, source, fluidKey);
        } else {
            return false;
        }
        if (extracted) {
            player.containerMenu.broadcastChanges();
        }
        return extracted;
    }

    private static boolean extractItem(ServerPlayer player, WirelessTerminalGridResolver.Connection connection,
                                       MEStorage storage, IActionSource source, AEItemKey key) {
        long amount = StorageHelper.poweredExtraction(
                connection.host(), storage, key, key.getMaxStackSize(), source);
        if (amount <= 0) {
            return false;
        }
        player.containerMenu.setCarried(key.toStack((int) amount));
        connection.syncTerminalStack(player);
        return true;
    }

    private static boolean extractFluid(ServerPlayer player, WirelessTerminalGridResolver.Connection connection,
                                        MEStorage storage, IActionSource source, AEFluidKey key) {
        ItemStack filledBucket = FluidUtil.getFilledBucket(new FluidStack(
                key.getFluid(), FluidType.BUCKET_VOLUME, key.copyTag()));
        if (filledBucket.isEmpty()) {
            return false;
        }

        Inventory inventory = player.getInventory();
        int bucketSlot = findEmptyBucket(inventory);
        if (bucketSlot < 0) {
            return false;
        }

        long available = StorageHelper.poweredExtraction(
                connection.host(), storage, key, FluidType.BUCKET_VOLUME, source, Actionable.SIMULATE);
        if (available < FluidType.BUCKET_VOLUME) {
            return false;
        }

        long extracted = StorageHelper.poweredExtraction(
                connection.host(), storage, key, FluidType.BUCKET_VOLUME, source);
        if (extracted != FluidType.BUCKET_VOLUME) {
            if (extracted > 0) {
                storage.insert(key, extracted, Actionable.MODULATE, source);
            }
            connection.syncTerminalStack(player);
            return false;
        }

        inventory.getItem(bucketSlot).shrink(1);
        player.containerMenu.setCarried(filledBucket);
        connection.syncTerminalStack(player);
        return true;
    }

    private static int findEmptyBucket(Inventory inventory) {
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (inventory.getItem(slot).is(Items.BUCKET)) {
                return slot;
            }
        }
        return -1;
    }
}
