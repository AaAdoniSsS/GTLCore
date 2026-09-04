package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLPartAbility;
import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.data.machines.structure.AdditionalMultiBlockMachineStructure;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.common.data.GTRecipeModifiers.ELECTRIC_OVERCLOCK;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class AdditionalMultiBlockMachine {

    public static void init() {}

    public final static MultiblockMachineDefinition ADVANCED_RARE_EARTH_CENTRIFUGAL = REGISTRATE.multiblock("advanced_rare_earth_centrifugal", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTLRecipeTypes.RARE_EARTH_CENTRIFUGAL_RECIPES)
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.rare_earth_centrifugal")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK))
            .appearanceBlock(GTLBlocks.SPS_CASING)
            .pattern(definition -> AdditionalMultiBlockMachineStructure.ADVANCED_RARE_EARTH_CENTRIFUGAL
                    .where("J", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.SPS_CASING.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1)))
                    .where("B", Predicates.blocks(GTLBlocks.DEGENERATE_RHENIUM_CONSTRAINED_CASING.get()))
                    .where("C", Predicates.blocks(GTLBlocks.ADVANCED_FUSION_COIL.get()))
                    .where("D", Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where("E", Predicates.blocks(GTBlocks.CASING_HSSE_STURDY.get()))
                    .where("F", Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where("G", Predicates.blocks(Registries.getBlock("kubejs:neutronium_gearbox")))
                    .where("H", Predicates.blocks(Registries.getBlock("kubejs:neutronium_pipe_casing")))
                    .where("I", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.HastelloyX)))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/sps_casing"), GTCEu.id("block/multiblock/gcym/large_centrifuge"))
            .register();

    public final static MultiblockMachineDefinition ADVANCED_VACUUM_DRYING_FURNACE = REGISTRATE.multiblock("advanced_vacuum_drying_furnace", CoilWorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTLRecipeTypes.VACUUM_DRYING_RECIPES)
            .recipeType(GTLRecipeTypes.DEHYDRATOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.duration_multiplier.tooltip", 0.5))
            .tooltips(Component.translatable("gtceu.machine.vacuum_drying_furnace.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.coil_parallel"))
            .tooltips(Component.translatable("gtceu.machine.vacuum_drying_furnace.tooltip.1"))
            .tooltips(Component.translatable("gtceu.multiblock.coil_parallel"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_2.tooltip",
                    Component.translatable("gtceu.vacuum_drying"), Component.translatable("gtceu.dehydrator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .appearanceBlock(GTBlocks.HIGH_POWER_CASING)
            .recipeModifiers((machine, recipe, params, result) -> {
                if (machine instanceof CoilWorkableElectricMultiblockMachine coilMachine) {
                    // 0.5 耗时倍率在并行之先应用，与其他带固定耗时倍率的机器保持一致
                    recipe.duration = (int) Math.max(1, recipe.duration * 0.5);

                    int parallel = (int) Math.min(2147483647,
                            Math.pow(2, coilMachine.getCoilType().getCoilTemperature() / 900D));
                    GTRecipe recipe1 = GTRecipeModifiers.accurateParallel(coilMachine, recipe, parallel, false).getFirst();
                    if (recipe1 == null) return null;

                    if (recipe1.data.contains("ebf_temp")) {
                        return GTRecipeModifiers.ebfOverclock(coilMachine, recipe1, params, result);
                    } else {
                        return RecipeHelper.applyOverclock(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK, recipe1,
                                coilMachine.getOverclockVoltage(), params, result);
                    }
                }
                return null;
            })
            .pattern(definition -> AdditionalMultiBlockMachineStructure.ADVANCED_VACUUM_DRYING_FURNACE
                    .where("N", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_TURBINE.get()))
                    .where("B", Predicates.blocks(GTBlocks.CASING_STAINLESS_TURBINE.get()))
                    .where("C", Predicates.blocks(GTLBlocks.IRIDIUM_CASING.get()))
                    .where("D", Predicates.blocks(GTBlocks.CASING_GRATE.get()))
                    .where("E", Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()))
                    .where("F", Predicates.blocks(Registries.getBlock("kubejs:red_steel_casing")))
                    .where("G", Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()))
                    .where("H", Predicates.heatingCoils())
                    .where("I", Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_PIPE.get()))
                    .where("J", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Tungsten)))
                    .where("K", Predicates.blocks(GTBlocks.COMPUTER_CASING.get()))
                    .where("L", Predicates.blocks(GTBlocks.COMPUTER_HEAT_VENT.get()))
                    .where("M", Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("O", Predicates.blocks(GTBlocks.HERMETIC_CASING_LuV.get()))
                    .where("P", Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX.get()))
                    .where("Q", Predicates.blocks(GTBlocks.FILTER_CASING.get()))
                    .build())
            .additionalDisplay((controller, components) -> {
                if (controller instanceof CoilWorkableElectricMultiblockMachine coilMachine && controller.isFormed()) {
                    components.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(FormattingUtil.formatNumbers(Math.min(2147483647, (int) Math.pow(2, ((double) coilMachine.getCoilType().getCoilTemperature() / 900))))).withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
                    components.add(Component.translatable("gtceu.multiblock.blast_furnace.max_temperature",
                            Component.translatable(FormattingUtil.formatNumbers(coilMachine.getCoilType().getCoilTemperature() + 100L * Math.max(0, coilMachine.getTier() - GTValues.MV)) + "K")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED))));
                }
            })
            .workableCasingRenderer(GTCEu.id("block/casings/hpca/high_power_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition HUGE_INCUBATOR = REGISTRATE.multiblock("huge_incubator", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.INCUBATOR_RECIPES)
            .recipeType(GTRecipeTypes.BREWING_RECIPES)
            .recipeType(GTRecipeTypes.FERMENTING_RECIPES)
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_3.tooltip",
                    Component.translatable("gtceu.incubator"), Component.translatable("gtceu.brewery"), Component.translatable("gtceu.fermenter")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK))
            .appearanceBlock(GTBlocks.CASING_PTFE_INERT)
            .pattern(definition -> AdditionalMultiBlockMachineStructure.HUGE_INCUBATOR
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.IRIDIUM_CASING.get()))
                    .where("B", Predicates.blocks(Registries.getBlock("gtceu:sterilizing_filter_casing")))
                    .where("C", Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.autoAbilities(true, false, true))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1)))
                    .where("D", Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get()))
                    .where("E", Predicates.blocks(GTBlocks.CASING_HSSE_STURDY.get()))
                    .where("F", Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                    .where("G", Predicates.blocks(GTLBlocks.MOLECULAR_CASING.get()))
                    .where("H", Predicates.blocks(GTBlocks.CLEANROOM_GLASS.get()))
                    .where("I", Predicates.blocks(Registries.getBlock("kubejs:containment_field_generator")))
                    .where("J", Predicates.blocks(GTBlocks.CASING_STAINLESS_TURBINE.get()))
                    .where("K", Predicates.blocks(GTLBlocks.DEGENERATE_RHENIUM_CONSTRAINED_CASING.get()))
                    .where("L", Predicates.blocks(GTBlocks.PLASTCRETE.get()))
                    .where("M", Predicates.blocks(Blocks.SPONGE))
                    .where("N", Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where("O", Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_TURBINE.get()))
                    .where("P", Predicates.frames(GTMaterials.Naquadria))
                    .where("R", Predicates.blocks(Registries.getBlock("kubejs:containment_field_generator")))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"), GTCEu.id("block/multiblock/gcym/large_maceration_tower"))
            .register();

    public static final MultiblockMachineDefinition ADVANCED_NEUTRON_ACTIVATOR = REGISTRATE
            .multiblock("advanced_neutron_activator", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.advanced_neutron_activator.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.advanced_neutron_activator.tooltip.2"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.neutron_activator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeTypes(GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES)
            .recipeModifiers(((machine, recipe, params, result) -> {
                long eu = recipe.data.getInt("evt") * 2000L;
                recipe.tickInputs.put(EURecipeCapability.CAP,
                        List.of(new Content(eu, 10000, 10000, 0, null, null)));
                ((IGTRecipe) recipe).setHasTick(true);
                int parallel = GTLRecipeModifiers.getHatchParallel(machine);
                result.init(eu, recipe.duration, parallel, params.getOcAmount());
                return recipe;
            }))
            .appearanceBlock(GTLBlocks.SPS_CASING)
            .pattern(definition -> AdditionalMultiBlockMachineStructure.HUGE_INCUBATOR_2
                    .where('K', Predicates.controller(Predicates.blocks(definition.get())))
                    .where('A', Predicates.blocks(GTLBlocks.SPS_CASING.get()))
                    .where('B', Predicates.blocks(GTLBlocks.DIMENSIONALLY_TRANSCENDENT_CASING.get()))
                    .where('C', Predicates.frames(GTLMaterials.Quantanium))
                    .where('D', Predicates.frames(GTLMaterials.Vibranium))
                    .where('E', Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where('F', Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                    .where('G', Predicates.blocks(Registries.getBlock("kubejs:speeding_pipe")))
                    .where('H', Predicates.blocks(GTLBlocks.SPS_CASING.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER).setMinGlobalLimited(0).setMaxGlobalLimited(2))
                            .or(Predicates.autoAbilities(true, false, true)))
                    .where('I', Predicates.blocks(GTLBlocks.HSSS_REINFORCED_BOROSILICATE_GLASS.get()))
                    .where('J', Predicates.blocks(Registries.getBlock("kubejs:neutronium_pipe_casing")))
                    .where(' ', Predicates.any())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/sps_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition MOLECULAR_ASSEMBLER_MATRIX = REGISTRATE.multiblock("molecular_assembler_matrix", MolecularAssemblerMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.MOLECULAR_ASSEMBLER)
            .tooltips(Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.0"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.1"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.2"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.3"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.4"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.5"),
                    Component.translatable("gtceu.machine.molecular_assembler_matrix.tooltip.6"))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .appearanceBlock(GCyMBlocks.CASING_LARGE_SCALE_ASSEMBLING)
            .pattern(definition -> AdditionalMultiBlockMachineStructure.MOLECULAR_ASSEMBLER_MATRIX
                    .where("R", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("H", Predicates.blocks(GTLBlocks.MOLECULAR_CASING.get()))
                    .where("Q", abilities(GTLPartAbility.MOLECULAR_ASSEMBLER_MATRIX)
                            .or(Predicates.blocks(GTLBlocks.MOLECULAR_CASING.get())))
                    .where("D", Predicates.blocks(Registries.getBlock("gtceu:high_power_casing")))
                    .where("N", Predicates.blocks(Registries.getBlock("gtceu:large_scale_assembler_casing"))
                            .or(blocks(GTLMachines.GTAEMachines.ME_MOLECULAR_ASSEMBLER_IO.get()).setExactLimit(1)))
                    .where("K", Predicates.blocks(Registries.getBlock("gtceu:naquadah_alloy_frame")))
                    .where("M", Predicates.blocks(Registries.getBlock("gtceu:hsse_frame")))
                    .where("O", Predicates.blocks(Registries.getBlock("gtceu:large_scale_assembler_casing")))
                    .where("I", Predicates.blocks(Registries.getBlock("gtceu:assembly_line_grating")))
                    .where("P", Predicates.blocks(Registries.getBlock("gtceu:europium_frame")))
                    .where("L", Predicates.blocks(Registries.getBlock("gtlcore:hyper_mechanical_casing")))
                    .where("B", Predicates.blocks(Registries.getBlock("gtceu:fusion_glass")))
                    .where("C", Predicates.blocks(Registries.getBlock("gtlcore:advanced_assembly_line_unit")))
                    .where("J", Predicates.blocks(Registries.getBlock("gtceu:assembly_line_casing")))
                    .where("A", Predicates.blocks(Registries.getBlock("gtlcore:iridium_casing")))
                    .where("F", Predicates.blocks(Registries.getBlock("gtceu:superconducting_coil")))
                    .where("E", Predicates.blocks(Registries.getBlock("gtceu:advanced_computer_casing")))
                    .where("G", Predicates.blocks(Registries.getBlock("gtceu:trinium_frame")))
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/gcym/large_scale_assembling_casing"), GTCEu.id("block/multiblock/research_station"))
            .register();

    public static final MultiblockMachineDefinition TRANSFINITE_COMPUTATION_ARRAY = REGISTRATE
            .multiblock("transfinite_computation_array", TransfiniteComputationArrayMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .appearanceBlock(GTLBlocks.IRIDIUM_CASING)
            .tooltips(Component.translatable("gtlcore.machine.transfinite_computation_array.tooltip.0"))
            .tooltips(Component.translatable("gtlcore.machine.transfinite_computation_array.tooltip.1"))
            .tooltips(Component.translatable("gtlcore.machine.transfinite_computation_array.tooltip.2"))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .pattern(definition -> AdditionalMultiBlockMachineStructure.MOLECULAR_ASSEMBLER_MATRIX_2
                    .where("A", Predicates.blocks(GTLBlocks.IRIDIUM_CASING.get()))
                    .where("B", Predicates.blocks(GTBlocks.CASING_PALLADIUM_SUBSTATION.get()))
                    .where("C", Predicates.blocks(Registries.getBlock("mae2:256x_crafting_accelerator")))
                    .where("D", Predicates.blocks(GTLBlocks.OXIDATION_RESISTANT_HASTELLOY_N_MECHANICAL_CASING.get()))
                    .where("E", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Naquadria)))
                    .where("F", Predicates.blocks(Registries.getBlock("ae2:smooth_sky_stone_wall")))
                    .where("G", Predicates.blocks(GTBlocks.CLEANROOM_GLASS.get()))
                    .where("H", Predicates.blocks(GTBlocks.ADVANCED_COMPUTER_CASING.get()))
                    .where("J", Predicates.blocks(GTBlocks.COMPUTER_HEAT_VENT.get()))
                    .where("K", Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get()))
                    .where("L", Predicates.blocks(GCyMBlocks.HEAT_VENT.get()))
                    .where("M", Predicates.blocks(GTLBlocks.CRAFTING_STORAGE_MAX.get()).setExactLimit(1))
                    .where("N", Predicates.blocks(GCyMBlocks.ELECTROLYTIC_CELL.get()))
                    .where("O", Predicates.blocks(GTLBlocks.IMPROVED_SUPERCONDUCTOR_COIL.get()))
                    .where("P", Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                    .where("Q", Predicates.blocks(Registries.getBlock("gtceu:sterilizing_filter_casing")))
                    .where("R", Predicates.blocks(Registries.getBlock("ad_astra:glowing_iron_pillar")))
                    .where("S", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("T", Predicates.blocks(GTBlocks.CASING_EXTREME_ENGINE_INTAKE.get()))
                    .where("U", Predicates.blocks(GTLBlocks.IRIDIUM_CASING.get())
                            .or(abilities(GTLPartAbility.ME_CRAFTING_CPU_INTERFACE).setExactLimit(1)))
                    .where("W", Predicates.blocks(Blocks.WATER))
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/iridium_casing"),
                    GTCEu.id("block/multiblock/research_station"))
            .register();
}
