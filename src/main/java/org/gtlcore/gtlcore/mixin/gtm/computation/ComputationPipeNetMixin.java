package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationVisualState;

import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeNet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpticalPipeNet.class)
public abstract class ComputationPipeNetMixin implements ComputationVisualState {

    @Unique
    private long gtlcore$lastLightTick = Long.MIN_VALUE;

    @Override
    public boolean gtlcore$refreshComputationLight(long tick) {
        if (gtlcore$lastLightTick != Long.MIN_VALUE && tick >= gtlcore$lastLightTick && tick - gtlcore$lastLightTick < 80)
            return false;
        gtlcore$lastLightTick = tick;
        return true;
    }

    @Inject(method = { "onNeighbourUpdate", "onPipeConnectionsUpdate", "transferNodeData" }, at = @At("HEAD"), remap = false)
    private void gtlcore$invalidateRoutes(CallbackInfo ci) {
        ComputationNetwork.invalidate();
        gtlcore$lastLightTick = Long.MIN_VALUE;
    }
}
