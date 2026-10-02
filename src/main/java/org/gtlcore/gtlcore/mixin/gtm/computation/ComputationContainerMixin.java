package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.ComputationBuffer;
import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(NotifiableComputationContainer.class)
public abstract class ComputationContainerMixin implements ComputationNode {

    @Unique
    private ComputationBuffer gtlcore$output;

    @Unique
    private ComputationBuffer gtlcore$output() {
        if (gtlcore$output == null) gtlcore$output = new ComputationBuffer(((NotifiableComputationContainer) (Object) this).getMachine());
        return gtlcore$output;
    }

    @Override
    public List<ComputationSource> gtlcore$localComputationSources() {
        return ((NotifiableComputationContainer) (Object) this).getHandlerIO().support(IO.OUT) ? List.of(gtlcore$output()) : List.of();
    }

    @Override
    public boolean gtlcore$computationOnline() {
        return ComputationConnections.loaded(((NotifiableComputationContainer) (Object) this).getMachine());
    }

    @Override
    public List<IOpticalComputationProvider> gtlcore$computationLinks() {
        var self = (NotifiableComputationContainer) (Object) this;
        if (!self.getHandlerIO().support(IO.IN)) return List.of();
        return self.isTransmitter() ? ComputationConnections.controllers(self.getMachine()) :
                ComputationConnections.wired(self.getMachine());
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
        if (io == IO.OUT && ((NotifiableComputationContainer) (Object) this).getHandlerIO().support(IO.OUT)) {
            try {
                long amount = 0;
                for (Object value : left) amount = Math.addExact(amount, ComputationAmounts.read(value));
                cir.setReturnValue(gtlcore$output().produce(amount, simulate) ? null : left);
            } catch (IllegalArgumentException | ArithmeticException e) {
                cir.setReturnValue(left);
            }
            return;
        }
        cir.setReturnValue(ComputationConnections.legacyInput((IOpticalComputationProvider) this, io, recipe, left, simulate));
    }

    @Inject(method = "getContents", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$outputContents(CallbackInfoReturnable<List<Object>> cir) {
        if (((NotifiableComputationContainer) (Object) this).getHandlerIO().support(IO.OUT))
            cir.setReturnValue(List.of(ComputationAmounts.box(gtlcore$output().gtlcore$availableComputation())));
    }

    @Inject(method = "getTotalContentAmount", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$outputAmount(CallbackInfoReturnable<Double> cir) {
        if (((NotifiableComputationContainer) (Object) this).getHandlerIO().support(IO.OUT))
            cir.setReturnValue((double) gtlcore$output().gtlcore$availableComputation());
    }
}
