package org.gtlcore.gtlcore.mixin.ae2.logic;

import com.gregtechceu.gtceu.common.data.GTItems;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * @author EasterFG on 2024/10/13
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    @Shadow(remap = false)
    @Final
    private Set<AEKey> patternInputs;

    @Inject(method = "updatePatterns", at = @At("TAIL"), remap = false)
    public void updatePatternsHook(CallbackInfo ci) {
        patternInputs.remove(AEItemKey.of(GTItems.INTEGRATED_CIRCUIT.get()));
    }

    @Inject(method = "adapterAcceptsAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$requireFullTargetCapacity(PatternProviderTarget target, KeyCounter[] inputHolder,
                                                   CallbackInfoReturnable<Boolean> cir) {
        var combinedInputs = new KeyCounter();
        for (var inputList : inputHolder) {
            for (var input : inputList) {
                combinedInputs.add(input.getKey(), input.getLongValue());
            }
        }

        for (var input : combinedInputs) {
            long amount = input.getLongValue();
            if (target.insert(input.getKey(), amount, Actionable.SIMULATE) != amount) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }
}
