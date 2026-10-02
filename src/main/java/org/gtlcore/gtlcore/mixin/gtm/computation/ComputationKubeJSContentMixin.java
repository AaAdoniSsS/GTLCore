package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipeComponent;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.integration.kjs.recipe.components.ContentJS;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContentJS.class)
public abstract class ComputationKubeJSContentMixin {

    @Shadow(remap = false)
    @Final
    @Mutable
    private RecipeComponent<?> baseComponent;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtlcore$longSchema(RecipeComponent<?> component, RecipeCapability<?> capability, boolean output, CallbackInfo ci) {
        if (capability == CWURecipeCapability.CAP) baseComponent = ComputationRecipeComponent.INSTANCE;
    }
}
