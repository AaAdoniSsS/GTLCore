package org.gtlcore.gtlcore.mixin.gtm;

import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.integration.jei.GTJEIPlugin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GTJEIPlugin.class)
public abstract class GTJEIPluginMixin {

    @Redirect(
              method = "registerCategories",
              at = @At(
                       value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/recipe/ui/GTRecipeTypeUI;isXEIVisible()Z"),
              remap = false)
    private boolean gtlcore$hideRecipeTypesWithoutUi(@Nullable GTRecipeTypeUI recipeUI) {
        return recipeUI != null && recipeUI.isXEIVisible();
    }
}
