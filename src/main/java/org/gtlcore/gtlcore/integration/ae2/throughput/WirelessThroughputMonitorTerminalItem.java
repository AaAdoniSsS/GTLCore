package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.terminal.ItemWT;

public final class WirelessThroughputMonitorTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "throughput_monitor";
    public static final String HOTKEY_NAME = "wireless_throughput_monitor_terminal";

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU.get();
    }
}
