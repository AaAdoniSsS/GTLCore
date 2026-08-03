package org.gtlcore.gtlcore.integration.ae2.emitter;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.terminal.ItemWT;

public final class WirelessEmitterManagerTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "emitter_manager";
    public static final String HOTKEY_NAME = "wireless_emitter_manager_terminal";

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return GTLWirelessAeContent.WIRELESS_EMITTER_MANAGER_TERMINAL_MENU.get();
    }
}
