package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.ComputationRecipes;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Mixin(GTRecipeWidget.class)
public abstract class ComputationRecipeWidgetMixin {

    @Inject(method = "getRecipeParaText", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtlcore$researchEnergy(GTRecipe recipe, int duration, long inputEUt, long outputEUt,
                                               CallbackInfoReturnable<List<Component>> cir) {
        if (!recipe.data.getBoolean("duration_is_total_cwu") || !recipe.tickInputs.containsKey(CWURecipeCapability.CAP)) return;
        List<Component> texts = new ArrayList<>();
        if (!recipe.data.getBoolean("hide_duration"))
            texts.add(Component.translatable("gtceu.recipe.duration", FormattingUtil.formatNumbers(duration / 20.0f)));
        long eu = inputEUt > 0 ? inputEUt : outputEUt;
        if (eu > 0) {
            long required = Math.max(1, ComputationRecipes.required(recipe.tickInputs.get(CWURecipeCapability.CAP)));
            long total = ComputationAmounts.total(recipe);
            // Research can draw the last partial tick, whose energy must still be counted.
            long ticks = total / required + (total % required == 0 ? 0 : 1);
            String energy = String.format("%,d", BigInteger.valueOf(eu).multiply(BigInteger.valueOf(ticks)));
            texts.add(Component.translatable("gtceu.recipe.max_eu", energy));
            texts.add(Component.translatable(inputEUt > 0 ? "gtceu.recipe.eu" : "gtceu.recipe.eu_inverted", FormattingUtil.formatNumbers(eu)));
        }
        cir.setReturnValue(texts);
    }
}
