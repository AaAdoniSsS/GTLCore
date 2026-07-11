package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Widget.class, remap = false)
public abstract class MEStorageConfiguratorTabBoundsMixin {

    @Inject(method = "isMouseOverElement", at = @At("RETURN"), cancellable = true)
    private void gtlcore$includeOverflowConfiguratorTabBounds(double mouseX, double mouseY,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) && gtlcore$isOverflowConfiguratorTab(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHoverElement", at = @At("RETURN"), cancellable = true)
    private void gtlcore$findOverflowConfiguratorTabHoverElement(double mouseX, double mouseY,
                                                                 CallbackInfoReturnable<Widget> cir) {
        if (cir.getReturnValue() == null && gtlcore$isOverflowConfiguratorTab(mouseX, mouseY)) {
            cir.setReturnValue((Widget) (Object) this);
        }
    }

    @Unique
    private boolean gtlcore$isOverflowConfiguratorTab(double mouseX, double mouseY) {
        Object widget = this;
        return widget instanceof ConfiguratorPanel panel &&
                MEStorageConfiguratorTabLayout.isMouseOverTab(panel, mouseX, mouseY);
    }
}
