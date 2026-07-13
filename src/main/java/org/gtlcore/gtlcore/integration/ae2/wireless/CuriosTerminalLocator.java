package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public record CuriosTerminalLocator(String identifier, int index) implements MenuLocator {

    public static void register() {
        MenuLocators.register(CuriosTerminalLocator.class, CuriosTerminalLocator::write, CuriosTerminalLocator::read);
    }

    @Override
    public @Nullable <T> T locate(Player player, Class<T> clazz) {
        ItemStack stack = CuriosCompat.locateItem(identifier, index, player);
        if (stack.isEmpty()) {
            return null;
        }

        ItemMenuHost host;
        if (stack.getItem() instanceof IUniversalWirelessTerminalItem) {
            CuriosTerminalLocator locator = new CuriosTerminalLocator(identifier, index);
            BiConsumer<Player, ISubMenu> returnToMainMenu = (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, locator);
            host = new CuriosAe2wtlibTerminalMenuHost(player, null, stack, identifier, index, returnToMainMenu);
        } else if (stack.getItem() instanceof WirelessTerminalItem) {
            // Use null slot so the host does not try to validate an inventory index.
            host = new CuriosWirelessTerminalMenuHost(
                    player,
                    stack,
                    identifier,
                    index,
                    (p, sub) -> MenuOpener.open(MEStorageMenu.WIRELESS_TYPE, p, this));
        } else if (stack.getItem() instanceof IMenuItem menuItem) {
            host = menuItem.getMenuHost(player, -1, stack, null);
        } else {
            return null;
        }

        if (host == null) {
            return null;
        }
        if (clazz.isInstance(host)) {
            return clazz.cast(host);
        }
        return null;
    }

    private static void write(CuriosTerminalLocator locator, FriendlyByteBuf buffer) {
        buffer.writeUtf(locator.identifier);
        buffer.writeInt(locator.index);
    }

    private static CuriosTerminalLocator read(FriendlyByteBuf buffer) {
        return new CuriosTerminalLocator(buffer.readUtf(), buffer.readInt());
    }
}
