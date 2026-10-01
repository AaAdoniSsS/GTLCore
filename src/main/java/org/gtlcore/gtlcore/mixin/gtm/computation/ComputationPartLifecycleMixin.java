package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;

import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiblockPartMachine.class)
public abstract class ComputationPartLifecycleMixin {

    @Inject(method = { "addedToController", "removedFromController" }, at = @At("RETURN"), remap = false)
    private void gtlcore$controllerChanged(CallbackInfo ci) {
        if (ComputationConnections.participates((MultiblockPartMachine) (Object) this)) ComputationNetwork.invalidate();
    }
}
