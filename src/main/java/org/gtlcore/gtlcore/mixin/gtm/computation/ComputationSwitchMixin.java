package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(NetworkSwitchMachine.class)
public abstract class ComputationSwitchMixin implements ComputationNode {

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        return ComputationConnections.switchInputs((NetworkSwitchMachine) (Object) this);
    }

    @Override
    public boolean gtlcore$computationOnline() {
        var self = (NetworkSwitchMachine) (Object) this;
        return ComputationConnections.loaded(self) && self.isFormed() && self.isActive() &&
                !self.getRecipeLogic().isWaiting() && self.isWorkingEnabled();
    }

    @Override
    public boolean gtlcore$requiresComputationBridge() {
        return true;
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$request(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen,
                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request((IOpticalComputationProvider) this, amount, simulate));
    }

    @Inject(method = "getMaxCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$capacity(Collection<IOpticalComputationProvider> seen, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationMath.toInt(ComputationNetwork.capacity(List.of((IOpticalComputationProvider) this))));
    }

    @Inject(method = "canBridge", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$bridge(Collection<IOpticalComputationProvider> seen, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ComputationNetwork.canBridge(List.of((IOpticalComputationProvider) this)));
    }
}
