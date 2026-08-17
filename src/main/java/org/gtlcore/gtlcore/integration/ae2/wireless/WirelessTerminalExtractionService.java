package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;
import org.gtlcore.gtlcore.utils.FluidBucketUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

public final class WirelessTerminalExtractionService {

    private static final int MAX_FLUID_EXTRACTION_ATTEMPTS = 16;

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
        ItemStack filledBucket = FluidBucketUtil.getFilledBucket(new FluidStack(
                key.getFluid(), AEFluidKey.AMOUNT_BUCKET, key.copyTag()));
        if (filledBucket.isEmpty()) {
            return false;
        }

        AEItemKey bucketKey = AEItemKey.of(filledBucket);
        if (bucketKey != null && extractItem(player, connection, storage, source, bucketKey)) {
            return true;
        }

        double requiredPower = (double) AEFluidKey.AMOUNT_BUCKET / Math.max(1, key.getAmountPerOperation());
        double availablePower = connection.host()
                .extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (availablePower < requiredPower) {
            return false;
        }

        long extracted = extractFluidAmount(storage, source, key, AEFluidKey.AMOUNT_BUCKET);
        if (extracted != AEFluidKey.AMOUNT_BUCKET) {
            if (extracted > 0) {
                storage.insert(key, extracted, Actionable.MODULATE, source);
            }
            connection.syncTerminalStack(player);
            return false;
        }
        connection.host().extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);

        player.containerMenu.setCarried(filledBucket);
        connection.syncTerminalStack(player);
        return true;
    }

    private static long extractFluidAmount(MEStorage storage, IActionSource source, AEFluidKey key, long requested) {
        long extracted = 0;
        for (int attempt = 0; attempt < MAX_FLUID_EXTRACTION_ATTEMPTS && extracted < requested; attempt++) {
            long current = storage.extract(key, requested - extracted, Actionable.MODULATE, source);
            if (current <= 0) {
                break;
            }
            extracted += current;
        }
        return extracted;
    }
}
