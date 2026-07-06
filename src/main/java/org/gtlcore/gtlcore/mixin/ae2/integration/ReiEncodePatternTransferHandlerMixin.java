package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.client.gui.PatterEncodingTermMenuModify;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import appeng.menu.me.items.PatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.integration.modules.rei.transfer.EncodePatternTransferHandler", remap = false)
public class ReiEncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Lnet/minecraft/world/item/crafting/Recipe;Lme/shedaniel/rei/api/common/display/Display;Z)Lme/shedaniel/rei/api/client/registry/transfer/TransferHandler$Result;",
            at = @At("HEAD"),
            remap = false)
    private void rememberRecipeTypeForQuickUpload(PatternEncodingTermMenu menu, Recipe<?> recipe, Object display,
                                                  boolean doTransfer, CallbackInfoReturnable<?> cir) {
        if (!doTransfer || !(menu instanceof PatterEncodingTermMenuModify menuModify)) {
            return;
        }
        menuModify.gTLCore$setQuickUploadRecipeType(gtlcore$getRecipeTypeId(recipe));
    }

    private static ResourceLocation gtlcore$getRecipeTypeId(Recipe<?> recipeBase) {
        if (recipeBase instanceof GTRecipe gtRecipe && gtRecipe.recipeType != null) {
            return gtRecipe.recipeType.registryName;
        }
        return BuiltInRegistries.RECIPE_TYPE.getKey(recipeBase.getType());
    }
}
