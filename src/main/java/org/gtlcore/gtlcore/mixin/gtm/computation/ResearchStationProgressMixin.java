package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationProgress;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Research stations override setupRecipe instead of invoking the base implementation. */
@Mixin(targets = "com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine$ResearchStationRecipeLogic")
public abstract class ResearchStationProgressMixin {

    @Inject(method = "setupRecipe", at = @At("RETURN"), remap = false)
    private void gtlcore$newResearch(GTRecipe recipe, CallbackInfo ci) {
        ((ComputationProgress) this).gtlcore$resetComputation(recipe);
    }
}
