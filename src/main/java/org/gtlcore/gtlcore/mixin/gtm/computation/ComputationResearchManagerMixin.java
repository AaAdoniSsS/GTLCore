package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationResearchEntries;
import org.gtlcore.gtlcore.api.machine.computation.LongComputationRecipeBuilder;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ResearchManager.class)
public abstract class ComputationResearchManagerMixin {

    @Inject(method = "createDefaultResearchRecipe(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;IIILjava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtlcore$longResearch(GTRecipeType type, String researchId, ItemStack researchItem,
                                            ItemStack dataItem, int duration, int eu, int cwu,
                                            Consumer<FinishedRecipe> provider, CallbackInfo ci) {
        var amounts = ComputationResearchEntries.get(dataItem);
        if (amounts == null) return;
        ci.cancel();
        if (!ConfigHolder.INSTANCE.machines.enableResearch) return;
        ResearchManager.writeResearchToNBT(dataItem.getOrCreateTag(), researchId, type);
        var builder = GTRecipeTypes.RESEARCH_STATION_RECIPES.recipeBuilder(FormattingUtil.toLowerCaseUnderscore(researchId))
                .inputItems(dataItem.getItem()).inputItems(researchItem).outputItems(dataItem).EUt(eu);
        var longBuilder = (LongComputationRecipeBuilder<?>) builder;
        longBuilder.CWUt(amounts.rate());
        longBuilder.totalCWU(amounts.total());
        builder.save(provider);
    }
}
