package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.CraftingStatusBulkActionControls;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReasonState;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingDispatchReasonView;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStatusReasons;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(CraftingCPUScreen.class)
public abstract class CraftingStatusScreenMixin implements ICraftingDispatchReasonView {

    @Unique
    private final Map<Long, Integer> gtlcore$dispatchReasonMasks = new HashMap<>();

    @Inject(method = "postUpdate", at = @At("HEAD"), remap = false)
    private void gtlcore$applyDispatchReasons(CraftingStatus status, CallbackInfo ci) {
        CraftingDispatchReasonState.applySerialUpdate(
                this.gtlcore$dispatchReasonMasks,
                status.isFullStatus(),
                ((ICraftingStatusReasons) status).gtlcore$getReasonMasks());
        for (var entry : status.getEntries()) {
            if (entry.isDeleted()) {
                this.gtlcore$dispatchReasonMasks.remove(entry.getSerial());
            }
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$refreshBulkActionButtons(CallbackInfo ci) {
        if ((Object) this instanceof CraftingStatusBulkActionControls controls) {
            controls.gtlcore$refreshBulkActionButtons();
        }
    }

    @Override
    @Unique
    public int gtlcore$getDispatchReasonMask(long serial) {
        return this.gtlcore$dispatchReasonMasks.getOrDefault(serial, 0);
    }
}
