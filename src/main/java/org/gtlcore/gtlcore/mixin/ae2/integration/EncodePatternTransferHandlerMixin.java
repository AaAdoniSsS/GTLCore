package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.client.gui.PatterEncodingTermMenuModify;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

import static org.gtlcore.gtlcore.config.ConfigHolder.INSTANCE;

@Mixin(EncodePatternTransferHandler.class)
@SuppressWarnings("all")
public class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            remap = false)
    private void rememberRecipeTypeForQuickUpload(PatternEncodingTermMenu menu, Object recipeBase,
                                                  IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer,
                                                  boolean doTransfer,
                                                  CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!doTransfer || !(menu instanceof PatterEncodingTermMenuModify menuModify)) {
            return;
        }
        menuModify.gTLCore$setQuickUploadRecipeType(gtlcore$getRecipeTypeId(recipeBase));
    }

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("RETURN"),
            remap = false)
    private void clearRecipeTypeAfterFailedTransfer(PatternEncodingTermMenu menu, Object recipeBase,
                                                    IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer,
                                                    boolean doTransfer,
                                                    CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!doTransfer || cir.getReturnValue() == null || !(menu instanceof PatterEncodingTermMenuModify menuModify)) {
            return;
        }
        menuModify.gTLCore$setQuickUploadRecipeType(null);
    }

    @ModifyArg(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
               at = @At(value = "INVOKE",
                        target = "Lappeng/integration/modules/jeirei/EncodingHelper;encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/util/List;Ljava/util/List;)V"),
               index = 1,
               remap = false)
    public List<List<GenericStack>> multiBlockInputFilter(List<List<GenericStack>> genericIngredients, @Local(name = "recipeBase") Object recipeBase) {
        if (!(recipeBase instanceof MultiblockInfoWrapper) || INSTANCE.filterHatch.length == 0) return genericIngredients;
        var newList = new ObjectArrayList<List<GenericStack>>();
        for (var l : genericIngredients) {
            var list = l.stream().filter(g -> Arrays.stream(INSTANCE.filterHatch).noneMatch(s -> g.what().getId().toString().contains(s))).toList();
            if (!list.isEmpty()) newList.add(l);
        }
        return newList;
    }

    @ModifyArg(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
               at = @At(value = "INVOKE",
                        target = "Lappeng/integration/modules/jeirei/EncodingHelper;encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/util/List;Ljava/util/List;)V"),
               index = 2,
               remap = false)
    public List<GenericStack> multiBlockOutputImport(List<GenericStack> genericIngredients, @Local(name = "recipeBase") Object recipeBase) {
        if (!(recipeBase instanceof MultiblockInfoWrapper miw)) return genericIngredients;
        var g = GenericStack.fromItemStack(Items.WRITTEN_BOOK.getDefaultInstance().kjs$withName(Component.translatable(miw.definition.getId().toLanguageKey("block")).withStyle(style -> style.withColor(16536828))));
        return g == null ? List.of() : List.of(g);
    }

    private static ResourceLocation gtlcore$getRecipeTypeId(Object recipeBase) {
        if (recipeBase instanceof GTRecipe gtRecipe) {
            return gtRecipe.recipeType == null ? null : gtRecipe.recipeType.registryName;
        } else if (recipeBase instanceof GTRecipeWrapper wrapper) {
            GTRecipe recipe = wrapper.recipe;
            return recipe == null || recipe.recipeType == null ? null : recipe.recipeType.registryName;
        } else if (recipeBase instanceof Recipe<?> recipe) {
            return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        }
        return null;
    }
}
