package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationGrid;
import org.gtlcore.gtlcore.api.machine.computation.ComputationLedger;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;
import org.gtlcore.gtlcore.api.machine.computation.ComputationUsage;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.HPCAMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HPCAMachine.class)
public abstract class HpcaComputationMixin implements ComputationSource, ComputationUsage {

    @Shadow(remap = false)
    private IEnergyContainer energyContainer;
    @Shadow(remap = false)
    private IMaintenanceMachine maintenance;
    @Shadow(remap = false)
    private IFluidTransfer coolantHandler;
    @Shadow(remap = false)
    @Final
    private HPCAMachine.HPCAGridHandler hpcaHandler;
    @Shadow(remap = false)
    private boolean hasNotEnoughEnergy;
    @Shadow(remap = false)
    private double temperature;
    @Unique
    private final ComputationLedger gtlcore$ledger = new ComputationLedger();
    @Unique
    private long gtlcore$thermalTick = Long.MIN_VALUE;
    @Unique
    private long gtlcore$energyTick = Long.MIN_VALUE;
    @Unique
    private boolean gtlcore$upkeepPaid;

    @Unique
    private HPCAMachine gtlcore$self() {
        return (HPCAMachine) (Object) this;
    }

    @Unique
    private void gtlcore$advance() {
        long tick = ComputationNetwork.tick(gtlcore$self().getLevel());
        if (tick != gtlcore$energyTick) {
            gtlcore$energyTick = tick;
            gtlcore$upkeepPaid = false;
        }
        gtlcore$ledger.advance(tick);
    }

    @Override
    public long gtlcore$computationCapacity() {
        var self = gtlcore$self();
        return ComputationConnections.loaded(self) && self.isFormed() && self.isWorkingEnabled() ?
                ((ComputationGrid) hpcaHandler).gtlcore$maximumComputation() : 0;
    }

    @Unique
    private long gtlcore$energyCost(long usage) {
        long upkeep = Math.max(0, hpcaHandler.getUpkeepEUt());
        long maximum = Math.max(upkeep, hpcaHandler.getMaxEUt());
        long cost = ComputationMath.add(upkeep,
                ComputationMath.multiplyDivide(maximum - upkeep, usage, Math.max(1, ((ComputationGrid) hpcaHandler).gtlcore$maximumComputation())));
        if (ConfigHolder.INSTANCE.machines.enableMaintenance && maintenance != null)
            cost = ComputationMath.add(cost, ComputationMath.multiplyDivide(cost, maintenance.getNumMaintenanceProblems(), 10));
        return cost;
    }

    @Override
    public long gtlcore$availableComputation() {
        gtlcore$advance();
        long limit = gtlcore$ledger.remaining(gtlcore$computationCapacity());
        if (limit == 0 || energyContainer == null) return 0;
        long energy = Math.max(0, energyContainer.getEnergyStored());
        long low = 0;
        long high = limit;
        while (low < high) {
            long middle = low + (high - low) / 2 + (high - low) % 2;
            long cost = Math.max(0, gtlcore$energyCost(gtlcore$ledger.used() + middle) - gtlcore$ledger.paidEnergy());
            if (cost <= energy) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    @Override
    public boolean gtlcore$canBridgeComputation() {
        return gtlcore$self().isFormed() && hpcaHandler.hasHPCABridge();
    }

    @Override
    public Receipt gtlcore$withdrawComputation(long amount) {
        if (amount <= 0 || amount > gtlcore$availableComputation()) return null;
        long cost = Math.max(0, gtlcore$energyCost(gtlcore$ledger.used() + amount) - gtlcore$ledger.paidEnergy());
        long removed = cost == 0 ? 0 : energyContainer.removeEnergy(cost);
        if (removed != cost) {
            if (removed > 0) energyContainer.addEnergy(removed);
            return null;
        }
        gtlcore$ledger.debit(amount, cost);
        hasNotEnoughEnergy = false;
        return new Receipt(amount, () -> {
            // A later request may already have committed. Refund only the cost no longer needed by
            // the remaining work, keeping upkeep if the provider itself has ticked this epoch.
            long remaining = gtlcore$ledger.used() - amount;
            long retainedCost = remaining == 0 && !gtlcore$upkeepPaid ? 0 : gtlcore$energyCost(remaining);
            long refund = Math.max(0, gtlcore$ledger.paidEnergy() - retainedCost);
            gtlcore$ledger.refund(amount, refund);
            if (refund > 0) energyContainer.addEnergy(refund);
        });
    }

    @Override
    public long gtlcore$usedComputation() {
        gtlcore$advance();
        return gtlcore$ledger.used();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$tick(CallbackInfo ci) {
        ci.cancel();
        var self = gtlcore$self();
        long tick = ComputationNetwork.tick(self.getLevel());
        if (tick == gtlcore$thermalTick) return;
        gtlcore$thermalTick = tick;
        gtlcore$advance();
        boolean working = self.isFormed() && self.isWorkingEnabled();
        if (working) {
            long needed = Math.max(0, gtlcore$energyCost(gtlcore$ledger.used()) - gtlcore$ledger.paidEnergy());
            long removed = energyContainer.getEnergyStored() >= needed ? energyContainer.removeEnergy(needed) : 0;
            hasNotEnoughEnergy = removed != needed;
            if (hasNotEnoughEnergy) {
                if (removed > 0) energyContainer.addEnergy(removed);
            } else {
                gtlcore$ledger.debit(0, needed);
                gtlcore$upkeepPaid = true;
            }
            self.getRecipeLogic().setStatus(hasNotEnoughEnergy ? RecipeLogic.Status.WAITING : RecipeLogic.Status.WORKING);
        }
        ((ComputationGrid) hpcaHandler).gtlcore$setThermalUsage(gtlcore$ledger.previousUsed());
        if (working && !hasNotEnoughEnergy || gtlcore$ledger.previousUsed() > 0) {
            temperature = Math.max(200.0, temperature + hpcaHandler.calculateTemperatureChange(coolantHandler, temperature >= 400.0) / 2.0);
            if (temperature >= 1000.0) hpcaHandler.attemptDamageHPCA();
            hpcaHandler.tick();
        } else {
            temperature = Math.max(200.0, temperature - 0.25);
            hpcaHandler.tick();
        }
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$request(int amount, boolean simulate,
                                 java.util.Collection<com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider> seen,
                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request(gtlcore$self(), amount, simulate));
    }

    @Inject(method = "getMaxCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$capacity(java.util.Collection<com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider> seen,
                                  CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationMath.toInt(gtlcore$computationCapacity()));
    }

    @Inject(method = "onStructureInvalid", at = @At("HEAD"), remap = false)
    private void gtlcore$invalidate(CallbackInfo ci) {
        // Breaking and re-forming a structure is not a new tick. Preserve paid work, upkeep
        // and the thermal update guard so repeated structure changes cannot reset them.
        ComputationNetwork.invalidate();
    }
}
