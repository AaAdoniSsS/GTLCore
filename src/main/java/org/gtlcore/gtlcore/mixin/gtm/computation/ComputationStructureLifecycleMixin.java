package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiblockControllerMachine.class)
public abstract class ComputationStructureLifecycleMixin {

    @Inject(method = { "onStructureFormed", "onStructureInvalid" }, at = @At("RETURN"), remap = false)
    private void gtlcore$structureChanged(CallbackInfo ci) {
        ComputationNetwork.forget(this);
        if (ComputationConnections.participates((MultiblockControllerMachine) (Object) this)) ComputationNetwork.invalidate();
    }
}
