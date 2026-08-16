package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.gui.widget.IShiftAmountOperations;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.EnergyLevelEmitterScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.implementations.EnergyLevelEmitterMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnergyLevelEmitterScreen.class)
public abstract class EnergyLevelEmitterScreenMixin {

    @Shadow(remap = false)
    @Final
    private NumberEntryWidget level;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$enableShiftAmountOperations(EnergyLevelEmitterMenu menu, Inventory inventory,
                                                     Component title, ScreenStyle style, CallbackInfo ci) {
        ((NumberEntryWidgetAccessor) this.level).getTextField()
                .setMaxLength(Long.toString(Long.MAX_VALUE).length());
        ((IShiftAmountOperations) this.level).gtlcore$enableShiftAmountOperations();
    }
}
