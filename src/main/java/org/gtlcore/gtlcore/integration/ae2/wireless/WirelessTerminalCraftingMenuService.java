package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
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
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) {
                continue;
            }

            IGrid grid = terminal.getLinkedGrid(stack, player.serverLevel(), null);
            if (grid == null || !terminal.hasPower(player, WirelessTerminalGridResolver.MIN_TERMINAL_POWER, stack)) {
                continue;
            }

            var host = new WirelessTerminalMenuHost(player, slot, stack, (ignoredPlayer, ignoredMenu) -> {});
            if (!host.rangeCheck() || !grid.getCraftingService().isCraftable(key)) {
                continue;
            }

            CraftAmountMenu.open(player, MenuLocators.forInventorySlot(slot), key, INITIAL_CRAFT_AMOUNT);
            return player.containerMenu instanceof CraftAmountMenu;
        }
        return false;
    }
}
