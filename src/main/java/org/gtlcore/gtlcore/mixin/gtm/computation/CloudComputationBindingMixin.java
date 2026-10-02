package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.CloudComputationBridge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.gtladd.gtladditions.common.machine.hatch.CloudOpticalComputationHatchMachine", remap = false)
public abstract class CloudComputationBindingMixin {

    @Inject(method = "setUUID", at = @At("TAIL"))
    private void gtlcore$ownerChanged(CallbackInfo ci) {
        CloudComputationBridge.invalidate();
    }
}
