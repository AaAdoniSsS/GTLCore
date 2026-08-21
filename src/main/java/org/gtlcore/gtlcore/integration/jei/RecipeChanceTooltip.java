package org.gtlcore.gtlcore.integration.jei;

import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class RecipeChanceTooltip {

    private RecipeChanceTooltip() {}

    public static void add(Content content, ChanceLogic logic, List<Component> tooltips, IO io) {
        boolean formulaDisplay = content.tierChanceBoost != 0 &&
                content instanceof IRecipeChanceDisplay;
        if (!formulaDisplay && content.chance >= ChanceLogic.getMaxChancedValue()) return;
        int chance = content instanceof IRecipeChanceDisplay display ? display.gtlcore$getDisplayChance() :
                content.chance;

        if (formulaDisplay) {
            float chancePercent = 100 * (float) chance / content.maxChance;
            addChanceFormula(content, (IRecipeChanceDisplay) content, chancePercent, tooltips, io);
            return;
        }
        if (chance == 0) {
            tooltips.add(Component.translatable("gtceu.gui.content.chance_0"));
            return;
        }

        float chancePercent = 100 * (float) chance / content.maxChance;
        if (logic != ChanceLogic.NONE && logic != ChanceLogic.OR) {
            tooltips.add(Component.translatable(
                    io == IO.IN ? "gtceu.gui.content.chance_1_logic_in" : "gtceu.gui.content.chance_1_logic",
                    FormattingUtil.formatNumber2Places(chancePercent), logic.getTranslation())
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltips.add(FormattingUtil.formatPercentage2Places(
                    io == IO.IN ? "gtceu.gui.content.chance_1_in" : "gtceu.gui.content.chance_1",
                    chancePercent));
        }

        if (content.tierChanceBoost != 0) {
            float tierBoostPercent = content.tierChanceBoost / 100.0f;
            String formattedTierBoost = FormattingUtil.formatNumber2Places(tierBoostPercent);
            if (tierBoostPercent > 0) formattedTierBoost = "+" + formattedTierBoost;
            tooltips.add(Component.translatable("gtceu.gui.content.tier_boost_fix", formattedTierBoost)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void addChanceFormula(Content content, IRecipeChanceDisplay display, float finalChancePercent,
                                         List<Component> tooltips, IO io) {
        float initialChancePercent = 100 * (float) content.chance / content.maxChance;
        float tierBoostPercent = 100 * (float) content.tierChanceBoost / content.maxChance;
        int boostTierCount = display.gtlcore$getChanceBoostTierCount();
        tooltips.add(Component.translatable(io == IO.IN ? "gtceu.gui.content.chance_final_in" :
                "gtceu.gui.content.chance_final_out",
                FormattingUtil.formatNumber2Places(finalChancePercent)).withStyle(ChatFormatting.YELLOW));
        tooltips.add(Component.translatable("gtceu.gui.content.chance_calculation",
                FormattingUtil.formatNumber2Places(initialChancePercent),
                tierBoostPercent < 0 ? "-" : "+",
                boostTierCount,
                FormattingUtil.formatNumber2Places(Math.abs(tierBoostPercent)))
                .withStyle(ChatFormatting.YELLOW));
    }
}
