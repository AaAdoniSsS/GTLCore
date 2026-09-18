package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.integration.ae2.storage.PreciseInventoryDisplayService;

import net.minecraft.server.level.ServerPlayer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

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
            BigInteger exact = BigInteger.valueOf(amount);
            if (amount == Long.MAX_VALUE) {
                exact = PreciseInventoryDisplayService.query(grid.getStorageService().getInventory(), List.of(key), source)
                        .getOrDefault(key, exact);
            }
            return new Result(true, exact);
        } catch (RuntimeException ignored) {
            return Result.unavailable();
        }
    }

    public record Result(boolean available, BigInteger amount) {

        public Result {
            amount = available ? amount.max(BigInteger.ZERO) : BigInteger.ZERO;
        }

        public static Result unavailable() {
            return new Result(false, BigInteger.ZERO);
        }

        public static Result available(long amount) {
            return new Result(true, BigInteger.valueOf(amount));
        }
    }
}
