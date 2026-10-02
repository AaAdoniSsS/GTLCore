package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipes;
import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(GTRecipe.class)
public abstract class ComputationRecipeMixin {

    @Inject(method = "copy(Lcom/gregtechceu/gtceu/api/recipe/content/ContentModifier;Z)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;",
            at = @At("RETURN"), remap = false)
    private void gtlcore$copyResearchTotal(ContentModifier modifier, boolean modifyDuration, CallbackInfoReturnable<GTRecipe> cir) {
        var self = (GTRecipe) (Object) this;
        if (!modifyDuration || !self.data.getBoolean("duration_is_total_cwu")) return;
        long total = Math.max(1, ComputationAmounts.modify(ComputationAmounts.total(self), modifier));
        var copy = cir.getReturnValue();
        copy.data = self.data.copy();
        copy.data.putLong(ComputationAmounts.TOTAL_CWU, total);
        copy.duration = ComputationAmounts.duration(total);
    }

    @WrapMethod(method = "matchRecipeContents", remap = false)
    private GTRecipe.ActionResult gtlcore$matchComputation(IO io, IRecipeCapabilityHolder holder,
                                                          Map<RecipeCapability<?>, List<Content>> contents,
                                                          boolean isTick, Operation<GTRecipe.ActionResult> original) {
        if (io != IO.IN || !isTick || !ComputationRecipes.aggregate(contents.get(CWURecipeCapability.CAP)))
            return original.call(io, holder, contents, isTick);
        var rest = new HashMap<>(contents);
        var computation = rest.remove(CWURecipeCapability.CAP);
        var result = original.call(io, holder, rest, isTick);
        if (!result.isSuccess()) {
            ComputationNetwork.forget(holder);
            return result;
        }
        return ComputationRecipes.matches(holder, (GTRecipe) (Object) this, computation) ? result :
                ComputationRecipes.unavailable();
    }

    @WrapMethod(method = "handleRecipe", remap = false)
    private boolean gtlcore$consumeComputation(IO io, IRecipeCapabilityHolder holder, boolean isTick,
                                               Map<RecipeCapability<?>, List<Content>> contents,
                                               Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches,
                                               Operation<Boolean> original) {
        if (io != IO.IN || !isTick || !ComputationRecipes.aggregate(contents.get(CWURecipeCapability.CAP)))
            return original.call(io, holder, isTick, contents, chanceCaches);
        var rest = new HashMap<>(contents);
        var computation = rest.remove(CWURecipeCapability.CAP);
        long required = ComputationRecipes.required(computation);
        if (required < 0) return false;
        if (required == 0) return original.call(io, holder, isTick, rest, chanceCaches);
        var recipe = (GTRecipe) (Object) this;
        var transaction = ComputationRecipes.reserve(holder, recipe, computation);
        if (transaction == null) return false;
        boolean accepted = false;
        try {
            if (!original.call(io, holder, isTick, rest, chanceCaches)) return false;
            ComputationRecipes.accepted(holder, recipe, transaction);
            accepted = true;
            return true;
        } finally {
            if (!accepted) transaction.close();
        }
    }
}
