package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GTRecipeModifiers.class)
public abstract class GTRecipeModifiersMixin {

    @Unique
    private static final String EBF_TEMPERATURE_KEY = "ebf_temp";
    @Unique
    private static final int EBF_TEMPERATURE_PER_VOLTAGE_TIER = 100;
    @Unique
    private static final double MIN_EBF_DURATION_MULTIPLIER = 0.5;

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static GTRecipe ebfOverclock(MetaMachine machine, @NotNull GTRecipe recipe, @NotNull OCParams params, @NotNull OCResult result) {
        if (machine instanceof CoilWorkableElectricMultiblockMachine coilMachine) {
            int blastFurnaceTemperature = coilMachine.getCoilType().getCoilTemperature() +
                    EBF_TEMPERATURE_PER_VOLTAGE_TIER * Math.max(0, coilMachine.getTier() - GTValues.MV);
            if (recipe.data.contains(EBF_TEMPERATURE_KEY)) {
                int requiredTemperature = recipe.data.getInt(EBF_TEMPERATURE_KEY);
                if (requiredTemperature <= blastFurnaceTemperature) {
                    if (RecipeHelper.getRecipeEUtTier(recipe) > coilMachine.getTier()) return null;

                    GTRecipe modifiedRecipe = recipe.copy();
                    double durationMultiplier = Math.max(MIN_EBF_DURATION_MULTIPLIER,
                            (double) requiredTemperature / blastFurnaceTemperature);
                    modifiedRecipe.duration = Math.max(1, (int) (recipe.duration * durationMultiplier));
                    GTRecipe overclockedRecipe = RecipeHelper.applyOverclock(new OverclockingLogic((p, r, maxVoltage) -> OverclockingLogic.heatingCoilOC(
                            params, result, maxVoltage, blastFurnaceTemperature, requiredTemperature)),
                            modifiedRecipe, coilMachine.getOverclockVoltage(), params, result);
                    int energyDiscounts = Math.max(0, (blastFurnaceTemperature - requiredTemperature) /
                            OverclockingLogic.COIL_EUT_DISCOUNT_TEMPERATURE);
                    double energyMultiplier = NumberUtils.pow95(energyDiscounts);
                    result.setEut(gtlcore$applyEnergyMultiplier(result.getEut(), energyMultiplier));
                    result.setParallelEUt(gtlcore$applyEnergyMultiplier(result.getParallelEUt(), energyMultiplier));
                    return overclockedRecipe;
                } else {
                    RecipeResult.of((IRecipeLogicMachine) machine, RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE);
                    return null;
                }
            } else return null;
        } else return null;
    }

    @Unique
    private static long gtlcore$applyEnergyMultiplier(long eut, double multiplier) {
        if (eut > 0) return Math.max(1, (long) (eut * multiplier));
        if (eut < 0) return Math.min(-1, (long) (eut * multiplier));
        return 0;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static GTRecipe hatchParallel(MetaMachine machine, @NotNull GTRecipe recipe, boolean modifyDuration,
                                         @NotNull OCParams params, @NotNull OCResult result) {
        if (machine instanceof IMultiController controller && controller instanceof IRecipeCapabilityMachine) {
            if (controller.isFormed()) {
                var hatch = ((IRecipeCapabilityMachine) controller).getParallelHatch();
                if (hatch != null) {
                    long recipeEU = RecipeHelper.getInputEUt(recipe);
                    var parallelRecipe = ParallelLogic.applyParallel(machine, recipe, hatch.getCurrentParallel(), modifyDuration);
                    if (parallelRecipe.getSecond() == 0) return null;
                    result.init(recipeEU, recipe.duration, parallelRecipe.getSecond(), params.getOcAmount());
                    return parallelRecipe.getFirst();
                }
            }
        }
        return recipe;
    }

    /**
     * @author Dragons
     * @reason 适配me增广输出
     */
    @Overwrite(remap = false)
    public static Pair<GTRecipe, Integer> fastParallel(MetaMachine machine, @NotNull GTRecipe recipe, int maxParallel,
                                                       boolean modifyDuration) {
        if (machine instanceof IRecipeCapabilityHolder holder) {
            while (maxParallel > 0) {
                var copied = recipe.copy(ContentModifier.multiplier(maxParallel), modifyDuration);
                if (RecipeRunnerHelper.matchRecipe(holder, copied) && copied.matchTickRecipe(holder).isSuccess()) {
                    return Pair.of(copied, maxParallel);
                }
                maxParallel /= 2;
            }
        }
        return Pair.of(recipe, 1);
    }
}
