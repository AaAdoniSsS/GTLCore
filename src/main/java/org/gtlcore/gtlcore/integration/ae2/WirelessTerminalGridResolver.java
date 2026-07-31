package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.integration.ae2.wireless.CuriosCompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class WirelessTerminalGridResolver {

    private static final int MAX_NESTED_HANDLER_DEPTH = 5;
    private static final int MAX_SCANNED_SLOTS = 256;
    public static final double MIN_TERMINAL_POWER = 0.5D;

    private WirelessTerminalGridResolver() {}

    public static @Nullable IGrid find(Player player, Level level) {
        IItemHandler handler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler != null) {
            IGrid grid = find(
                    player,
                    handler,
                    level,
                    Collections.newSetFromMap(new IdentityHashMap<>()),
                    new SearchBudget(MAX_NESTED_HANDLER_DEPTH, MAX_SCANNED_SLOTS),
                    0);
            if (grid != null) {
                return grid;
            }
        }

        CuriosCompat.TerminalSlot curiosSlot = CuriosCompat.findWirelessTerminal(player);
        if (curiosSlot != null && curiosSlot.stack().getItem() instanceof WirelessTerminalItem terminal) {
            return findUsableGrid(player, level, curiosSlot.stack(), terminal);
        }
        return null;
    }

    private static @Nullable IGrid find(Player player, IItemHandler handler, Level level, Set<IItemHandler> visited,
                                        SearchBudget budget, int depth) {
        if (handler == null || !budget.canEnter(depth) || !visited.add(handler)) {
            return null;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!budget.tryScanSlot()) {
                return null;
            }
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                IGrid grid = findUsableGrid(player, level, stack, terminal);
                if (grid != null) {
                    return grid;
                }
            }

            IItemHandler nested = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            IGrid nestedGrid = find(player, nested, level, visited, budget, depth + 1);
            if (nestedGrid != null) {
                return nestedGrid;
            }
        }
        return null;
    }

    private static @Nullable IGrid findUsableGrid(Player player, Level level, ItemStack stack,
                                                  WirelessTerminalItem terminal) {
        IGrid grid = terminal.getLinkedGrid(stack, level, null);
        if (grid == null || !terminal.hasPower(player, MIN_TERMINAL_POWER, stack)) {
            return null;
        }
        ItemMenuHost menuHost = terminal.getMenuHost(player, -1, stack, null);
        if (!(menuHost instanceof WirelessTerminalMenuHost wirelessHost) || !wirelessHost.rangeCheck()) {
            return null;
        }
        return grid;
    }

    static final class SearchBudget {

        private final int maxDepth;
        private int remainingSlots;

        SearchBudget(int maxDepth, int maxSlots) {
            if (maxDepth < 0 || maxSlots <= 0) {
                throw new IllegalArgumentException("Search depth must be non-negative and slot limit must be positive");
            }
            this.maxDepth = maxDepth;
            this.remainingSlots = maxSlots;
        }

        boolean canEnter(int depth) {
            return depth >= 0 && depth <= maxDepth;
        }

        boolean tryScanSlot() {
            if (remainingSlots <= 0) {
                return false;
            }
            remainingSlots--;
            return true;
        }
    }
}
