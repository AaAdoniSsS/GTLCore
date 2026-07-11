package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.JeiTerminalSearchTarget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.integration.abstraction.ItemListMod;
import appeng.menu.me.common.MEStorageMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MEStorageScreen.class)
public abstract class MEStorageScreenMixin<C extends MEStorageMenu> extends AEBaseScreen<C>
                                          implements JeiTerminalSearchTarget {

    @Shadow(remap = false)
    @Final
    private AETextField searchField;

    private MEStorageScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void gtlcore$setJeiSearchText(String searchText) {
        this.searchField.setValue(searchText);
        if (this.config.isUseExternalSearch()) {
            ItemListMod.setSearchText(searchText);
        }
    }
}
