package org.gtlcore.gtlcore.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.OperationMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.stats.AeStats;
import appeng.util.ConfigManager;
import com.google.common.primitives.Ints;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IOPortBlockEntity.class)
public abstract class IOPortBlockEntityMixin {

    @Shadow(remap = false)
    @Final
    private ConfigManager manager;

    @Shadow(remap = false)
    @Final
    private IActionSource mySrc;

    private static final int MAX_TRANSFER_LOOPS = 8192;
    @Unique
    private static final double GTLCORE$POWER_EPSILON = 1.0E-9D;

    /**
     * @author Dragons
     * @reason 防止单tick循环过多卡死
     */
    @Overwrite(remap = false)
    private long transferContents(IGrid grid, StorageCell cellInv, long itemsToMove) {
        var networkInv = grid.getStorageService().getInventory();

        KeyCounter srcList;
        MEStorage src, destination;
        if (this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY) {
            src = cellInv;
            srcList = cellInv.getAvailableStacks();
            destination = networkInv;
        } else {
            src = networkInv;
            srcList = grid.getStorageService().getCachedInventory();
            destination = cellInv;
        }

        var energy = grid.getEnergyService();
        long linearCostBudget = Math.min(itemsToMove, gtlcore$getAffordableLinearCost(energy, itemsToMove));
        if (linearCostBudget <= 0) {
            return itemsToMove;
        }

        long movedLinearCost = 0;
        boolean didStuff;
        int loopBudget = MAX_TRANSFER_LOOPS;

        do {
            if (loopBudget-- <= 0) {
                break;
            }

            didStuff = false;

            for (var srcEntry : srcList) {
                var totalStackSize = srcEntry.getLongValue();
                if (totalStackSize > 0) {
                    var what = srcEntry.getKey();
                    var possible = destination.insert(what, totalStackSize, Actionable.SIMULATE, this.mySrc);

                    if (possible > 0) {
                        possible = Math.min(possible,
                                gtlcore$saturatedMultiply(linearCostBudget, what.getAmountPerOperation()));

                        possible = src.extract(what, possible, Actionable.MODULATE, this.mySrc);
                        if (possible > 0) {
                            var inserted = destination.insert(what, possible, Actionable.MODULATE, this.mySrc);

                            if (inserted < possible) {
                                src.insert(what, possible - inserted, Actionable.MODULATE, this.mySrc);
                            }

                            if (inserted > 0) {
                                long linearCost = Math.min(linearCostBudget, gtlcore$getLinearCost(what, inserted));
                                linearCostBudget -= linearCost;
                                movedLinearCost += linearCost;
                                didStuff = true;
                                if (what instanceof AEItemKey) {
                                    this.mySrc.player().ifPresent(player -> AeStats.ItemsInserted.addToPlayer(player,
                                            Ints.saturatedCast(inserted)));
                                }
                            }

                            break;
                        }
                    }
                }
            }
        } while (linearCostBudget > 0 && didStuff);

        if (movedLinearCost > 0) {
            energy.extractAEPower(gtlcore$getLogTotalCost(movedLinearCost), Actionable.MODULATE, PowerMultiplier.CONFIG);
        }

        return itemsToMove - movedLinearCost;
    }

    @Unique
    private static long gtlcore$getAffordableLinearCost(IEnergySource energy, long requestedLinearCost) {
        double requestedCost = gtlcore$getLogTotalCost(requestedLinearCost);
        double affordableCost = energy.extractAEPower(requestedCost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (affordableCost + GTLCORE$POWER_EPSILON >= requestedCost) {
            return requestedLinearCost;
        }
        return gtlcore$getLinearCostForPower(affordableCost);
    }

    @Unique
    private static long gtlcore$getLinearCost(AEKey what, long amount) {
        return Math.max(1, amount / Math.max(1, what.getAmountPerOperation()));
    }

    @Unique
    private static double gtlcore$getLogTotalCost(long linearCost) {
        return Math.log1p(Math.max(0, linearCost));
    }

    @Unique
    private static long gtlcore$getLinearCostForPower(double cost) {
        if (cost <= GTLCORE$POWER_EPSILON) {
            return 0;
        }

        double linearCost = Math.expm1(cost);
        if (linearCost >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(linearCost + GTLCORE$POWER_EPSILON);
    }

    @Unique
    private static long gtlcore$saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
