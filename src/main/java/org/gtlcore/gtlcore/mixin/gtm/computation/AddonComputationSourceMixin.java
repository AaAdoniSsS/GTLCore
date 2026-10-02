package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.ComputationProviderMachine;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.HPCAMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Additions adds these methods to the providers. They are optional when Additions is absent. */
@Mixin(value = { HPCAMachine.class, ComputationProviderMachine.class }, priority = 800)
public abstract class AddonComputationSourceMixin {

    @Inject(method = "requestCWU(JZ)J", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtlcore$request(long amount, boolean simulate, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(ComputationNetwork.request(List.of((IOpticalComputationProvider) this), amount, simulate));
    }

    @Inject(method = "getMaxCWU()J", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtlcore$capacity(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(((ComputationSource) this).gtlcore$computationCapacity());
    }

    @Inject(method = "remainCWU()J", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtlcore$remaining(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(((ComputationSource) this).gtlcore$availableComputation());
    }
}
