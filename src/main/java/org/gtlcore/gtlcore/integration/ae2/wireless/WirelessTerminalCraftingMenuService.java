package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;

public final class WirelessTerminalCraftingMenuService {

    private static final int INITIAL_CRAFT_AMOUNT = 1;

    private WirelessTerminalCraftingMenuService() {}

    public static boolean open(ServerPlayer player, AEKey key) {
        if (!WirelessAeKeyPacketCodec.supports(key)) {
            return false;
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            MenuLocator locator = MenuLocators.forInventorySlot(slot);
            if (tryOpen(player, stack, locator, slot, key)) {
                return true;
            }
        }

        CuriosCompat.TerminalSlot curiosSlot = CuriosCompat.findWirelessTerminal(player);
        if (curiosSlot != null) {
            MenuLocator locator = new CuriosTerminalLocator(curiosSlot.identifier(), curiosSlot.index());
            if (tryOpen(player, curiosSlot.stack(), locator, null, key)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryOpen(ServerPlayer player, ItemStack stack, MenuLocator locator, Integer hostSlot,
                                   AEKey key) {
        if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
            return false;
        }

        IGrid grid = terminal.getLinkedGrid(stack, player.serverLevel(), null);
        if (grid == null || !terminal.hasPower(player, WirelessTerminalGridResolver.MIN_TERMINAL_POWER, stack)) {
            return false;
        }

        ItemMenuHost menuHost = terminal.getMenuHost(player, hostSlot != null ? hostSlot : -1, stack, null);
        if (!(menuHost instanceof WirelessTerminalMenuHost host) || !host.rangeCheck()) {
            return false;
        }

        if (!grid.getCraftingService().isCraftable(key)) {
            return false;
        }

        CraftAmountMenu.open(player, locator, key, INITIAL_CRAFT_AMOUNT);
        return player.containerMenu instanceof CraftAmountMenu;
    }
}
