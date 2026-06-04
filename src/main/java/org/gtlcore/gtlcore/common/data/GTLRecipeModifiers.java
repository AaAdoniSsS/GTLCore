package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.steam.LargeSteamParallelMultiblockMachine;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;

import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class GTLRecipeModifiers {

    public static final RecipeModifier GCYM_REDUCTION = (machine, recipe, params, result) -> GTLRecipeModifiers
            .reduction(machine, recipe, 0.8, 0.6);

    public static final RecipeModifier LARGE_STEAM_OC = (machine, recipe, params, result) -> LargeSteamParallelMultiblockMachine.recipeModifier(machine, recipe, 0.5);

    public static final RecipeModifier STEAM_OC = (machine, recipe, params, result) -> LargeSteamParallelMultiblockMachine.recipeModifier(machine, recipe, 1);

    public static final RecipeModifier COIL_PARALLEL = (machine, recipe, params, result) -> GTRecipeModifiers.accurateParallel(machine, recipe, Math.min(2147483647, (int) Math.pow(2, ((double) ((CoilWorkableElectricMultiblockMachine) machine).getCoilType().getCoilTemperature() / 900))), false).getFirst();

    public static GTRecipe chemicalPlantOverclock(MetaMachine machine, @NotNull GTRecipe recipe, @NotNull OCParams params,
                                                  @NotNull OCResult result) {
        if (machine instanceof CoilWorkableElectricMultiblockMachine coilMachine) {
            GTRecipe recipe1 = reduction(machine, recipe, (1.0 - coilMachine.getCoilTier() * 0.05) * 0.8, (1.0 - coilMachine.getCoilTier() * 0.05) * 0.6);
            if (recipe1 != null) {
                recipe1 = GTRecipeModifiers.hatchParallel(machine, recipe1, false, params, result);
                if (recipe1 != null)
                    return RecipeHelper.applyOverclock(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK, recipe1, coilMachine.getOverclockVoltage(), params, result);
            }
        }
        return null;
    }

    public static GTRecipe processingPlantOverclock(MetaMachine machine, @NotNull GTRecipe recipe, @NotNull OCParams params,
                                                    @NotNull OCResult result) {
        if (machine instanceof WorkableElectricMultiblockMachine workableElectricMultiblockMachine) {
            GTRecipe recipe1 = reduction(machine, recipe, 0.9, 0.6);
            if (recipe1 != null) {
                recipe1 = GTRecipeModifiers.accurateParallel(machine, recipe1, 4 * (workableElectricMultiblockMachine).getTier() - 1, false).getFirst();
                if (recipe1 != null)
                    return RecipeHelper.applyOverclock(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK, recipe1,
                            workableElectricMultiblockMachine.getOverclockVoltage(), params, result);
            }
        }
        return null;
    }

    public static GTRecipe nanoForgeOverclock(MetaMachine machine, @NotNull GTRecipe recipe, @NotNull OCParams params,
                                              @NotNull OCResult result, int tier) {
        if (machine instanceof StorageMachine storageMachine) {
            int t = recipe.data.getInt("nano_forge_tier");
            if (t > tier) {
                RecipeResult.of((IRecipeLogicMachine) machine,
                        RecipeResult.fail(Component.translatable("gtceu.recipe.fail.nano_forge.tier")));
                return null;
            }
            if (tier == 1) {
                if (!Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:carbon_nanoswarm")) {
                    RecipeResult.of((IRecipeLogicMachine) machine,
                            RecipeResult.fail(Component.translatable("message.gtlcore.need_carbon_nano_swarm")));
                    return null;
                }
            } else if (tier == 2) {
                if (!Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:neutronium_nanoswarm")) {
                    RecipeResult.of((IRecipeLogicMachine) machine,
                            RecipeResult.fail(Component.translatable("message.gtlcore.need_neutronium_nano_swarm")));
                    return null;
                }
            } else if (tier == 3) {
                if (!Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:draconium_nanoswarm")) {
                    RecipeResult.of((IRecipeLogicMachine) machine,
                            RecipeResult.fail(Component.translatable("message.gtlcore.need_dragon_nano_swarm")));
                    return null;
                }
            }
            GTRecipe recipe1 = GTRecipeModifiers.accurateParallel(machine, recipe, (int) (Math.min((storageMachine.getMachineStorageItem().getCount()), 64) * Math.pow(2, tier - t)), false).getFirst();
            return RecipeHelper.applyOverclock(new OverclockingLogic(1 / Math.pow(2, 1 + tier - t), 4, false), recipe1, storageMachine.getOverclockVoltage(), params, result);
        }
        return null;
    }

    public static GTRecipe dissolvingTankOverclock(MetaMachine machine, @NotNull GTRecipe recipe, @NotNull OCParams params,
                                                   @NotNull OCResult result) {
        if (machine instanceof WorkableElectricMultiblockMachine workmachine) {
            List<Content> fluidList = recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, null);
            FluidStack fluidStack1 = FluidRecipeCapability.CAP.of(fluidList.get(0).getContent()).getStacks()[0];
            FluidStack fluidStack2 = FluidRecipeCapability.CAP.of(fluidList.get(1).getContent()).getStacks()[0];

            FluidAmounts amounts = countFluidAmounts(workmachine, recipe, fluidStack1.getFluid(), fluidStack2.getFluid());
            long a = amounts.first(), b = amounts.second();
            if (a == 0) return null;
            GTRecipe hatchedParallel = GTRecipeModifiers.hatchParallel(machine, recipe, false, params, result);
            if (hatchedParallel == null) return null;
            GTRecipe recipe1 = RecipeHelper.applyOverclock(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK, hatchedParallel, workmachine.getOverclockVoltage(), params, result);
            if (b / a != fluidStack2.getAmount() / fluidStack1.getAmount()) {
                RecipeResult.ofWorking((IRecipeLogicMachine) machine, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.ratio")));
                recipe1.outputs.clear();
            } else RecipeResult.ofWorking((IRecipeLogicMachine) machine, RecipeResult.SUCCESS);
            return recipe1;
        }
        return null;
    }

    public static GTRecipe reduction(MetaMachine machine, @NotNull GTRecipe recipe,
                                     double reductionEUt, double reductionDuration) {
        if (machine instanceof IOverclockMachine overclockMachine) {
            if (RecipeHelper.getRecipeEUtTier(recipe) > overclockMachine.getMaxOverclockTier()) {
                return null;
            }
            GTRecipe recipe1 = recipe.copy();
            if (reductionEUt != 1) {
                recipe1.tickInputs.put(EURecipeCapability.CAP,
                        List.of(new Content((long) Math.max(1, RecipeHelper.getInputEUt(recipe) * reductionEUt),
                                ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(),
                                0, null, null)));
            }
            if (reductionDuration != 1) {
                recipe1.duration = (int) Math.max(1, recipe.duration * reductionDuration);
            }
            return recipe1;
        }
        return recipe;
    }

    public static int getHatchParallel(MetaMachine machine) {
        if (machine instanceof IMultiController controller && controller.isFormed() && controller instanceof IRecipeCapabilityMachine recipeCapabilityMachine) {
            final var parallelHatch = recipeCapabilityMachine.getParallelHatch();
            if (parallelHatch != null) return parallelHatch.getCurrentParallel();
        }
        return 1;
    }

    public static GTRecipe standardOverclocking(WorkableElectricMultiblockMachine machine, @NotNull GTRecipe recipe) {
        double resultDuration = recipe.duration;
        double resultVoltage = RecipeHelper.getOutputEUt(recipe);
        long maxVoltage = machine.getOverclockVoltage();

        for (int numberOfOCs = 16; numberOfOCs > 0; numberOfOCs--) {
            double potentialVoltage = resultVoltage * 4;

            if (potentialVoltage > maxVoltage) break;

            double potentialDuration = resultDuration / 4;

            if (potentialDuration < 1) break;

            resultDuration = potentialDuration;

            resultVoltage = potentialVoltage;
        }
        GTRecipe recipe1 = recipe.copy();
        recipe1.duration = (int) resultDuration;
        recipe1.tickOutputs.put(EURecipeCapability.CAP,
                List.of(new Content((long) resultVoltage, ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0, null, null)));
        return recipe1;
    }

    private static FluidAmounts countFluidAmounts(WorkableElectricMultiblockMachine workmachine, GTRecipe recipe, Fluid fluid1, Fluid fluid2) {
        long a = 0, b = 0;

        if (workmachine instanceof IRecipeCapabilityMachine rcm) {
            var handlePart = rcm.getActiveRecipeHandle(recipe);
            if (handlePart != null) {
                if (handlePart instanceof RecipeHandlePart rhp) {
                    FluidAmounts amounts = countFluidInRecipeHandlePart(rhp, fluid1, fluid2);
                    a += amounts.first();
                    b += amounts.second();
                } else if (handlePart instanceof MEPatternRecipeHandlePart meRhp) {
                    FluidAmounts amounts = countFluidInMERecipeHandlePart(meRhp, recipe, fluid1, fluid2);
                    a += amounts.first();
                    b += amounts.second();
                }
            } else {
                FluidAmounts amounts = countFluidInParts(workmachine, fluid1, fluid2);
                a += amounts.first();
                b += amounts.second();
            }
        }

        return new FluidAmounts(a, b);
    }

    private static FluidAmounts countFluidInRecipeHandlePart(RecipeHandlePart rhp, Fluid fluid1, Fluid fluid2) {
        long a = 0, b = 0;
        for (var p : rhp.getCapability(FluidRecipeCapability.CAP)) {
            for (var contents : p.getContents()) {
                if (contents instanceof FluidStack fluidStack) {
                    if (fluidStack.getFluid() == fluid1) a += fluidStack.getAmount();
                    if (fluidStack.getFluid() == fluid2) b += fluidStack.getAmount();
                }
            }
        }
        return new FluidAmounts(a, b);
    }

    private static FluidAmounts countFluidInMERecipeHandlePart(MEPatternRecipeHandlePart meRhp, GTRecipe recipe, Fluid fluid1, Fluid fluid2) {
        long a = 0, b = 0;
        for (var it = Object2LongMaps.fastIterator(meRhp.getMEContent(FluidRecipeCapability.CAP, recipe)); it.hasNext();) {
            var entry = it.next();
            if (fluid1 == entry.getKey().getFluid()) a += entry.getLongValue();
            if (fluid2 == entry.getKey().getFluid()) b += entry.getLongValue();
        }
        return new FluidAmounts(a, b);
    }

    private static FluidAmounts countFluidInParts(WorkableElectricMultiblockMachine workMachine, Fluid fluid1, Fluid fluid2) {
        long a = 0, b = 0;
        for (var part : workMachine.getParts()) {
            for (var handler : part.getRecipeHandlers()) {
                if (handler.getHandlerIO() == IO.IN) {
                    for (var contents : handler.getContents()) {
                        if (contents instanceof FluidStack fluidStack) {
                            if (fluidStack.getFluid() == fluid1) a += fluidStack.getAmount();
                            if (fluidStack.getFluid() == fluid2) b += fluidStack.getAmount();
                        }
                    }
                }
            }
        }
        return new FluidAmounts(a, b);
    }

    private record FluidAmounts(long first, long second) {}
}
