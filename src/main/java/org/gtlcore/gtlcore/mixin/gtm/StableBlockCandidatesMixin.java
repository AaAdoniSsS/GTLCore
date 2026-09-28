package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PredicateBlocks.class, remap = false)
public abstract class StableBlockCandidatesMixin {

    @Inject(method = "buildPredicate", at = @At("RETURN"))
    private void gtlcore$fixedCandidates(CallbackInfoReturnable<SimplePredicate> cir) {
        var predicate = (PredicateBlocks) (Object) this;
        if (predicate.getClass() == PredicateBlocks.class) StableBlockCandidates.mark(predicate.candidates);
    }
}
