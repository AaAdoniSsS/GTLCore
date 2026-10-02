package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.hepdd.gtmthings.common.block.machine.trait.WirelessNotifiableComputationContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(WirelessNotifiableComputationContainer.class)
public abstract class WirelessComputationContainerMixin implements ComputationNode {

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        var self = (WirelessNotifiableComputationContainer) (Object) this;
        if (self.getHandlerIO() != IO.IN) return List.of();
        return self.isTransmitter() ? ComputationConnections.controllers(self.getMachine()) :
                ComputationConnections.wireless(self.getMachine());
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

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$legacyInput(IO io, GTRecipe recipe, List<?> left, String slot, boolean simulate,
                                     CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(ComputationConnections.legacyInput((IOpticalComputationProvider) this, io, recipe, left, simulate));
    }
}
