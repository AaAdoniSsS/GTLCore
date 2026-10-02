package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.CloudComputationBridge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Pseudo
@Mixin(targets = "com.gtladd.gtladditions.common.machine.CloudOpticalComputationMonitorMachine", remap = false)
public abstract class CloudComputationMonitorMixin {

    @Inject(method = "getMaxCWU", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$capacity(UUID owner, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ComputationNetwork.capacity(CloudComputationBridge.providers(owner)));
    }

    @Inject(method = "getRemainingCWU", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$remaining(UUID owner, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ComputationNetwork.available(owner, CloudComputationBridge.providers(owner)));
    }

    @Inject(method = "requestCWU", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$request(UUID owner, long amount, boolean simulate, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ComputationNetwork.request(CloudComputationBridge.providers(owner), amount, simulate));
    }

    @Inject(method = "markCacheDirty", at = @At("TAIL"))
    private static void gtlcore$cloudTopology(CallbackInfo ci) {
        ComputationNetwork.invalidate();
    }
}
