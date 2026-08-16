package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeMultiplierTracker;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.integration.jade.provider.CapabilityBlockProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Optional;

public final class RecipeMultiplierProvider extends CapabilityBlockProvider<IRecipeLogicMachine> {

    private static final String ENERGY_MULTIPLIER_TAG = "energy_multiplier";
    private static final String DURATION_MULTIPLIER_TAG = "duration_multiplier";
    private static final String ENERGY_MULTIPLIER_DISPLAY = "gtceu.machine.eut_multiplier.tooltip";
    private static final String DURATION_MULTIPLIER_DISPLAY = "gtceu.machine.duration_multiplier.tooltip";
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
        GTRecipe originalRecipe = recipeLogic.getLastOriginRecipe();
        GTRecipe modifiedRecipe = recipeLogic.getLastRecipe();

        Optional<RecipeMultiplierTracker.Multipliers> multipliers;
        if (recipeLogic instanceof MultipleRecipesLogic multipleRecipesLogic) {
            multipliers = Optional.of(new RecipeMultiplierTracker.Multipliers(
                    multipleRecipesLogic.getReductionEUt(),
                    multipleRecipesLogic.getReductionDuration()));
        } else {
            Optional<RecipeMultiplierTracker.Multipliers> tracked = RecipeMultiplierTracker.get(capability.self());
            Optional<RecipeMultiplierTracker.Multipliers> displayed = modifiedRecipe == null || tracked.isEmpty() ?
                    readDisplayedMultipliers(capability.self()) : Optional.empty();
            multipliers = modifiedRecipe == null ? displayed.or(() -> tracked) : tracked.or(() -> displayed);
            if (multipliers.isEmpty() && modifiedRecipe != null) {
                multipliers = Optional.of(RecipeMultiplierTracker.DEFAULT);
            }
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
                (originalRecipe == null || RecipeHelper.getInputEUt(originalRecipe) > 0)) {
            data.putDouble(ENERGY_MULTIPLIER_TAG, resolvedMultipliers.energy());
        }
        if (Double.isFinite(durationMultiplier)) {
            data.putDouble(DURATION_MULTIPLIER_TAG, durationMultiplier);
        }
    }

    private static Optional<RecipeMultiplierTracker.Multipliers> readDisplayedMultipliers(MetaMachine machine) {
        if (!(machine instanceof IMultiController controller) || !controller.isFormed() ||
                !(machine.getDefinition() instanceof MultiblockMachineDefinition definition)) {
            return Optional.empty();
        }

        var displayText = new ArrayList<Component>();
        definition.getAdditionalDisplay().accept(controller, displayText);
        Optional<Double> energy = findDisplayedMultiplier(displayText, ENERGY_MULTIPLIER_DISPLAY);
        Optional<Double> duration = findDisplayedMultiplier(displayText, DURATION_MULTIPLIER_DISPLAY);
        if (energy.isEmpty() && duration.isEmpty()) return Optional.empty();
        return Optional.of(new RecipeMultiplierTracker.Multipliers(energy.orElse(1D), duration.orElse(1D)));
    }

    private static Optional<Double> findDisplayedMultiplier(Iterable<Component> components, String translationKey) {
        for (Component component : components) {
            Optional<Double> value = findDisplayedMultiplier(component, translationKey);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static Optional<Double> findDisplayedMultiplier(Component component, String translationKey) {
        if (component.getContents() instanceof TranslatableContents contents &&
                translationKey.equals(contents.getKey()) && contents.getArgs().length > 0) {
            return parseMultiplier(contents.getArgs()[0]);
        }
        return findDisplayedMultiplier(component.getSiblings(), translationKey);
    }

    private static Optional<Double> parseMultiplier(Object argument) {
        if (argument instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        String value = argument instanceof Component component ? component.getString() : argument.toString();
        try {
            return Optional.of(Double.parseDouble(value.replace(",", "")));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
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
