package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipes;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeLogic.class)
public abstract class ComputationRecipeLogicMixin {

    @Shadow(remap = false)
    @Final
    protected IRecipeLogicMachine machine;

    @WrapMethod(method = "handleRecipeWorking", remap = false)
    private void gtlcore$computationWork(Operation<Void> original) {
        var logic = (RecipeLogic) (Object) this;
        var recipe = logic.getLastRecipe();
        if (recipe == null || !recipe.tickInputs.containsKey(CWURecipeCapability.CAP) && !recipe.tickOutputs.containsKey(CWURecipeCapability.CAP)) {
            original.call();
            return;
        }
        // onWorking may still reject the tick after all recipe IO succeeded. Keep receipts until then.
        long previousTicks = logic.getTotalContinuousRunningTime();
        try (var scope = ComputationRecipes.begin(machine, recipe)) {
            original.call();
            if (!scope.failed() && logic.getTotalContinuousRunningTime() == previousTicks + 1) scope.commit(true);
        }
    }

    @WrapMethod(method = "handleTickRecipe", remap = false)
    private GTRecipe.ActionResult gtlcore$computationTick(GTRecipe recipe, Operation<GTRecipe.ActionResult> original) {
        if (!recipe.tickInputs.containsKey(CWURecipeCapability.CAP) && !recipe.tickOutputs.containsKey(CWURecipeCapability.CAP))
            return original.call(recipe);
        if (ComputationRecipes.active(machine, recipe)) {
            var result = original.call(recipe);
            return result.isSuccess() && ComputationRecipes.commitFailed() ? ComputationRecipes.unavailable() : result;
        }
        try (var scope = ComputationRecipes.begin(machine, recipe)) {
            var result = original.call(recipe);
            if (!result.isSuccess()) return result;
            if (scope.failed()) return ComputationRecipes.unavailable();
            scope.commit(false);
            return result;
        }
    }

    @WrapOperation(method = "handleTickRecipe",
                   at = @At(value = "INVOKE",
                            target = "Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;handleTickRecipeIO(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)Z"),
                   remap = false)
    private boolean gtlcore$checkComputationCommit(RecipeLogic logic, GTRecipe recipe, IO io, Operation<Boolean> original) {
        if (ComputationRecipes.commitFailed()) return false;
        boolean success = original.call(logic, recipe, io);
        if (!success) ComputationRecipes.failedCommit();
        return success;
    }

    @Inject(method = { "onRecipeFinish", "interruptRecipe", "resetRecipeLogic" }, at = @At("HEAD"), remap = false, require = 0)
    private void gtlcore$releaseComputationDemand(CallbackInfo ci) {
        ComputationNetwork.forget(machine);
    }

    @Inject(method = "setWorkingEnabled", at = @At("HEAD"), remap = false)
    private void gtlcore$disable(boolean enabled, CallbackInfo ci) {
        if (!enabled) ComputationNetwork.forget(machine);
    }
}
