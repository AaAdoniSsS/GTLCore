package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.terminal.ItemWT;

public final class WirelessMEChamberManagerTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "me_chamber_manager";
    public static final String HOTKEY_NAME = "wireless_me_chamber_manager_terminal";

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return GTLWirelessAeContent.WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU.get();
    }
}
