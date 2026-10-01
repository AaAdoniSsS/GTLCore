package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(targets = "com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine$MultipleComputationHandler", remap = false)
public abstract class ComputationSwitchHandlerMixin implements ComputationNode {

    @Unique
    private NetworkSwitchMachine gtlcore$owner() {
        return (NetworkSwitchMachine) ((NotifiableComputationContainer) (Object) this).getMachine();
    }

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        return List.of(gtlcore$owner());
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true)
    private void gtlcore$request(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen,
                                CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request(gtlcore$owner(), amount, simulate));
    }

    @Inject(method = { "getMaxCWUt", "getMaxCWUtForDisplay" }, at = @At("HEAD"), cancellable = true)
    private void gtlcore$capacity(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationMath.toInt(ComputationNetwork.capacity(List.of(gtlcore$owner()))));
    }

    @Inject(method = "canBridge", at = @At("HEAD"), cancellable = true)
    private void gtlcore$bridge(Collection<IOpticalComputationProvider> seen, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ComputationNetwork.canBridge(List.of(gtlcore$owner())));
    }
}
