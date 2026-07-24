package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public final class WirelessThroughputMonitorMenuHost extends WTMenuHost implements IViewCellStorage {

    public WirelessThroughputMonitorMenuHost(Player player, @Nullable Integer inventorySlot, ItemStack stack,
                                             BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        readFromNbt();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL.get());
    }
}
