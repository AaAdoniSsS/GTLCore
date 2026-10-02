package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationLedger;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.common.machine.storage.CreativeComputationProviderMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(CreativeComputationProviderMachine.class)
public abstract class CreativeComputationMixin implements ComputationSource {

    @Shadow(remap = false)
    private int maxCWUt;
    @Shadow(remap = false)
    private boolean active;
    @Shadow(remap = false)
    private int lastRequestedCWUt;
    @Shadow(remap = false)
    private int requestedCWUPerSec;
    @Unique
    private final ComputationLedger gtlcore$ledger = new ComputationLedger();
    @Unique
    private long gtlcore$perSecond;

    @Unique
    private CreativeComputationProviderMachine gtlcore$self() {
        return (CreativeComputationProviderMachine) (Object) this;
    }

    @Override
    public long gtlcore$computationCapacity() {
        return active && ComputationConnections.loaded(gtlcore$self()) ? Math.max(0, maxCWUt) : 0;
    }

    @Override
    public long gtlcore$availableComputation() {
        gtlcore$ledger.advance(ComputationNetwork.tick(gtlcore$self().getLevel()));
        return gtlcore$ledger.remaining(gtlcore$computationCapacity());
    }

    @Override
    public boolean gtlcore$canBridgeComputation() {
        return true;
    }

    @Override
    public Receipt gtlcore$withdrawComputation(long amount) {
        if (amount <= 0 || amount > gtlcore$availableComputation()) return null;
        gtlcore$ledger.debit(amount, 0);
        gtlcore$perSecond = ComputationMath.add(gtlcore$perSecond, amount);
        return new Receipt(amount, () -> {
            gtlcore$ledger.refund(amount, 0);
            gtlcore$perSecond -= amount;
        });
    }

    @Inject(method = "requestCWUt", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$request(int amount, boolean simulate, Collection<IOpticalComputationProvider> seen,
                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ComputationNetwork.request(gtlcore$self(), amount, simulate));
    }

    @Inject(method = "updateComputationTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$usage(CallbackInfo ci) {
        ci.cancel();
        if (gtlcore$self().getOffsetTimer() % 20 == 0) {
            lastRequestedCWUt = ComputationMath.toInt(gtlcore$perSecond / 20);
            requestedCWUPerSec = 0;
            gtlcore$perSecond = 0;
        }
    }
}
