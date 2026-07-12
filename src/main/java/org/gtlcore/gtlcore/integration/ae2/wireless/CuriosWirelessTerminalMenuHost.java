package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;

import java.util.function.BiConsumer;

/**
 * Curios 饰品栏中的无线终端菜单宿主。
 * 在 onBroadcastChanges 时把当前 ItemStack 写回 Curios 槽位，
 * 以持久化耗电等 NBT 变化。
 */
public class CuriosWirelessTerminalMenuHost extends WirelessTerminalMenuHost {

    private final String curiosIdentifier;
    private final int curiosIndex;

    public CuriosWirelessTerminalMenuHost(Player player,
                                          ItemStack itemStack,
                                          String curiosIdentifier,
                                          int curiosIndex,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, null, itemStack, returnToMainMenu);
        this.curiosIdentifier = curiosIdentifier;
        this.curiosIndex = curiosIndex;
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        CuriosCompat.writeStackBack(curiosIdentifier, curiosIndex, getPlayer(), getItemStack());
        return super.onBroadcastChanges(menu);
    }
}
