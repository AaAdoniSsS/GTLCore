package org.gtlcore.gtlcore.integration.ae2.emitter;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public final class WirelessEmitterManagerMenuHost extends WTMenuHost {

    public WirelessEmitterManagerMenuHost(Player player, @Nullable Integer inventorySlot, ItemStack stack,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        readFromNbt();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(GTLWirelessAeContent.WIRELESS_EMITTER_MANAGER_TERMINAL.get());
    }
}
