package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.integration.GTRecipeWidget;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

import static org.gtlcore.gtlcore.utils.NumberUtils.*;
import static org.gtlcore.gtlcore.utils.TextUtil.GTL_CORE$VC;

@Mixin(GTRecipeWidget.class)
public abstract class GTRecipeWidgetMixin {

    @Shadow(remap = false)
    @Final
    private GTRecipe recipe;
    @Shadow(remap = false)
    private int tier;

    @Shadow(remap = false)
    private int getMinTier() {
        throw new AssertionError();
    }

    @Inject(method = "setTier", at = @At("TAIL"), remap = false)
    private void gtlcore$updateDisplayedChances(int tier, CallbackInfo ci) {
        int recipeTier = getMinTier();
        gtlcore$updateDisplayedChances(recipe.inputs, recipeTier);
        gtlcore$updateDisplayedChances(recipe.outputs, recipeTier);
        gtlcore$updateDisplayedChances(recipe.tickInputs, recipeTier);
        gtlcore$updateDisplayedChances(recipe.tickOutputs, recipeTier);
    }

    @Unique
    private void gtlcore$updateDisplayedChances(Map<?, List<Content>> contents, int recipeTier) {
        for (List<Content> capabilityContents : contents.values()) {
            for (Content content : capabilityContents) {
                ((IRecipeChanceDisplay) (Object) content).gtlcore$setChanceDisplayTiers(recipeTier, this.tier);
            }
        }
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    private static @NotNull List<Component> getRecipeParaText(GTRecipe recipe, int duration, long inputEUt, long outputEUt) {
        List<Component> texts = new ArrayList<>();
        if (!recipe.data.getBoolean("hide_duration")) {
            texts.add(Component.translatable("gtceu.recipe.duration", FormattingUtil.formatNumbers(duration / 20f)));
        }
        var EUt = inputEUt;
        boolean isOutput = false;
        if (EUt == 0) {
            EUt = outputEUt;
            isOutput = true;
        }
        if (EUt > 0) {
            long euTotal = EUt * duration;
            // sadly we still need a custom override here, since computation uses duration and EU/t very differently
            if (recipe.data.getBoolean("duration_is_total_cwu") &&
                    recipe.tickInputs.containsKey(CWURecipeCapability.CAP)) {
                int minimumCWUt = Math.max(recipe.tickInputs.get(CWURecipeCapability.CAP).stream()
                        .map(Content::getContent).mapToInt(CWURecipeCapability.CAP::of).sum(), 1);
                texts.add(Component.translatable("gtceu.recipe.max_eu",
                        FormattingUtil.formatNumbers(euTotal / minimumCWUt)));
            } else texts.add(Component.translatable("gtceu.recipe.total", formatLong(euTotal)));
            long absEUt = Math.abs(EUt);
            var tier = GTUtil.getTierByVoltage(absEUt);
            var component = Component.translatable(!isOutput ? "gtceu.recipe.eu" : "gtceu.recipe.eu_inverted",
                    formatLong(EUt));
            if (!isOutput) component.append(
                    Component.literal(" (").withStyle(ChatFormatting.GREEN)
                            .append(Component
                                    .literal(formatDouble((double) absEUt / GTValues.V[tier]) + "A")
                                    .withStyle(style -> style.withColor(GTL_CORE$VC[tier])))
                            .append(Component.literal(")").withStyle(ChatFormatting.GREEN)));
            texts.add(component);
        }

        return texts;
    }
}
