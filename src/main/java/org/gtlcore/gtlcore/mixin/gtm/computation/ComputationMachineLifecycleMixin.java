package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MetaMachine.class)
public abstract class ComputationMachineLifecycleMixin {

    @Inject(method = "onUnload", at = @At("HEAD"), remap = false)
    private void gtlcore$unload(CallbackInfo ci) {
        ComputationNetwork.forget(this);
        if (ComputationConnections.participates((MetaMachine) (Object) this)) ComputationNetwork.invalidate();
    }
}
