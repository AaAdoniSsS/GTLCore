package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Curios 槽位专用的 ae2wtlib 终端宿主。
 * 复用 ae2wtlib 的量子桥卡跨维/跨距逻辑，同时把 NBT 变化回写到 Curios 槽位。
 */
public class CuriosAe2wtlibTerminalMenuHost extends WTMenuHost {

    private final String curiosIdentifier;
    private final int curiosIndex;

    public CuriosAe2wtlibTerminalMenuHost(Player player,
                                          @Nullable Integer inventorySlot,
                                          ItemStack stack,
                                          String curiosIdentifier,
                                          int curiosIndex,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        this.curiosIdentifier = curiosIdentifier;
        this.curiosIndex = curiosIndex;
        // 初始化奇点槽等内部库存，确保量子桥判定能读取到频率 NBT。
        try {
            super.readFromNbt();
        } catch (Throwable ignored) {}
    }

    @Override
    protected boolean ensureItemStillInSlot() {
        return !CuriosCompat.locateItem(curiosIdentifier, curiosIndex, getPlayer()).isEmpty();
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        CuriosCompat.writeStackBack(curiosIdentifier, curiosIndex, getPlayer(), getItemStack());
        return super.onBroadcastChanges(menu);
    }
}
