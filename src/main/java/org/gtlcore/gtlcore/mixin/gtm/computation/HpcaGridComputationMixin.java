package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationGrid;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationUsage;

import com.gregtechceu.gtceu.api.capability.IHPCAComputationProvider;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.HPCAMachine;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(HPCAMachine.HPCAGridHandler.class)
public abstract class HpcaGridComputationMixin implements ComputationGrid {

    @Shadow(remap = false)
    @Final
    private HPCAMachine controller;

    @Shadow(remap = false)
    @Final
    private Set<IHPCAComputationProvider> computationProviders;
    @Shadow(remap = false)
    private int allocatedCWUt;
    @Unique
    private long gtlcore$thermalUsage;

    @Override
    public long gtlcore$maximumComputation() {
        long capacity = 0;
        for (var provider : computationProviders) capacity = ComputationMath.add(capacity, Math.max(0, provider.getCWUPerTick()));
        return capacity;
    }

    @Override
    public void gtlcore$setThermalUsage(long amount) {
        gtlcore$thermalUsage = amount;
        allocatedCWUt = ComputationMath.toInt(amount);
    }

    @Inject(method = "getMaxCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$capacity(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationMath.toInt(gtlcore$maximumComputation()));
    }

    @Inject(method = "getCurrentEUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$longEnergy(CallbackInfoReturnable<Long> cir) {
        if (controller == null) return;
        var self = (HPCAMachine.HPCAGridHandler) (Object) this;
        long upkeep = Math.max(0, self.getUpkeepEUt());
        long maximum = Math.max(upkeep, self.getMaxEUt());
        cir.setReturnValue(ComputationMath.add(upkeep, ComputationMath.multiplyDivide(maximum - upkeep,
                gtlcore$thermalUsage, Math.max(1, gtlcore$maximumComputation()))));
    }

    @ModifyArg(method = "calculateTemperatureChange", at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(D)J", ordinal = 0), index = 0, remap = false)
    private double gtlcore$longHeatFraction(double original) {
        if (controller == null) return original;
        return (double) ((HPCAMachine.HPCAGridHandler) (Object) this).getMaxCoolingDemand() *
                gtlcore$thermalUsage / Math.max(1, gtlcore$maximumComputation());
    }

    @Inject(method = { "tick", "clearComputationCache", "reset" }, at = @At("RETURN"), remap = false)
    private void gtlcore$clearThermalUsage(CallbackInfo ci) {
        gtlcore$thermalUsage = 0;
    }

    @Inject(method = "allocateCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$allocate(int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        if (controller != null) cir.setReturnValue(ComputationNetwork.request(controller, amount, simulate));
        else if (amount <= 0) cir.setReturnValue(0);
    }

    @Inject(method = "getAllocatedCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$used(CallbackInfoReturnable<Integer> cir) {
        if (controller instanceof ComputationUsage usage)
            cir.setReturnValue(ComputationMath.toInt(usage.gtlcore$usedComputation()));
    }
}
