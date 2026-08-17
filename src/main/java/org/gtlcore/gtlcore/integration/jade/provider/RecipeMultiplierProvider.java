package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeMultiplierTracker;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.integration.jade.provider.CapabilityBlockProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public final class RecipeMultiplierProvider extends CapabilityBlockProvider<IRecipeLogicMachine> {

    private static final String ENERGY_MULTIPLIER_TAG = "energy_multiplier";
    private static final String DURATION_MULTIPLIER_TAG = "duration_multiplier";
    private static final int PERCENT_SCALE = 2;

    public RecipeMultiplierProvider() {
        super(GTLCore.id("recipe_multiplier_provider"));
    }

    @Nullable
    @Override
    protected IRecipeLogicMachine getCapability(Level level, BlockPos pos, @Nullable Direction side) {
        return MetaMachine.getMachine(level, pos) instanceof IRecipeLogicMachine machine ? machine : null;
    }

    @Override
    protected void write(CompoundTag data, IRecipeLogicMachine capability) {
        var recipeLogic = capability.getRecipeLogic();
        GTRecipe currentOriginRecipe = recipeLogic.getLastOriginRecipe();
        GTRecipe currentRecipe = recipeLogic.getLastRecipe();

        Optional<RecipeMultiplierTracker.Multipliers> multipliers;
        if (recipeLogic instanceof MultipleRecipesLogic multipleRecipesLogic) {
            var tracked = new RecipeMultiplierTracker.Multipliers(
                    multipleRecipesLogic.getReductionEUt(),
                    multipleRecipesLogic.getReductionDuration());
            multipliers = Optional.of(tracked);
        } else {
            Optional<RecipeMultiplierTracker.Multipliers> tracked = RecipeMultiplierTracker.get(capability.self());
            multipliers = tracked
                    .or(() -> currentRecipe == null ? Optional.empty() : Optional.of(RecipeMultiplierTracker.DEFAULT));
        }
        if (multipliers.isEmpty()) return;
        RecipeMultiplierTracker.Multipliers resolvedMultipliers = multipliers.get();

        double durationMultiplier = resolvedMultipliers.duration();
        if (capability instanceof IRecipeCapabilityMachine recipeCapabilityMachine) {
            var maintenanceMachine = recipeCapabilityMachine.getMaintenanceMachine();
            if (maintenanceMachine != null) {
                durationMultiplier *= maintenanceMachine.getDurationMultiplier();
            }
        }

        if (Double.isFinite(resolvedMultipliers.energy()) &&
                (currentOriginRecipe == null || RecipeHelper.getInputEUt(currentOriginRecipe) > 0)) {
            data.putDouble(ENERGY_MULTIPLIER_TAG, resolvedMultipliers.energy());
        }
        if (Double.isFinite(durationMultiplier)) {
            data.putDouble(DURATION_MULTIPLIER_TAG, durationMultiplier);
        }
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        if (capData.contains(ENERGY_MULTIPLIER_TAG, Tag.TAG_DOUBLE)) {
            tooltip.add(multiplierLine(
                    "tooltip.gtlcore.recipe_multiplier.energy",
                    capData.getDouble(ENERGY_MULTIPLIER_TAG)));
        }
        if (capData.contains(DURATION_MULTIPLIER_TAG, Tag.TAG_DOUBLE)) {
            tooltip.add(multiplierLine(
                    "tooltip.gtlcore.recipe_multiplier.duration",
                    capData.getDouble(DURATION_MULTIPLIER_TAG)));
        }
    }

    private static Component multiplierLine(String translationKey, double multiplier) {
        Component value = Component.literal(formatPercent(multiplier)).withStyle(ChatFormatting.GOLD);
        return Component.translatable(translationKey, value).withStyle(ChatFormatting.GRAY);
    }

    private static String formatPercent(double multiplier) {
        return BigDecimal.valueOf(multiplier)
                .movePointRight(2)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + '%';
    }
}
