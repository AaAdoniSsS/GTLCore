package org.gtlcore.gtlcore.integration.ae2;

import net.minecraft.server.level.ServerPlayer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public final class MeInventoryAmountService {

    private MeInventoryAmountService() {}

    public static Result query(ServerPlayer player, AEKey key) {
        IGrid grid = WirelessTerminalGridResolver.find(player, player.serverLevel());
        return query(grid, IActionSource.ofPlayer(player), key);
    }

    private static Result query(@Nullable IGrid grid, IActionSource source, AEKey key) {
        if (grid == null || !(key instanceof AEItemKey || key instanceof AEFluidKey)) {
            return Result.unavailable();
        }
        try {
            long amount = grid.getStorageService().getInventory()
                    .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
            return Result.available(amount);
        } catch (RuntimeException ignored) {
            return Result.unavailable();
        }
    }

    public record Result(boolean available, long amount) {

        public Result {
            amount = available ? Math.max(0, amount) : 0;
        }

        public static Result unavailable() {
            return new Result(false, 0);
        }

        public static Result available(long amount) {
            return new Result(true, amount);
        }
    }
}
