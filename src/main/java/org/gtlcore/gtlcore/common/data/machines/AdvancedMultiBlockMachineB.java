package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLPartAbility;
import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.client.renderer.machine.SpaceElevatorRenderer;
import org.gtlcore.gtlcore.common.block.BlockMap;
import org.gtlcore.gtlcore.common.block.GTLFusionCasingBlock;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.data.machines.structure.AdvancedMultiBlockMachineStructureA;
import org.gtlcore.gtlcore.common.data.machines.structure.AdvancedMultiBlockMachineStructureB;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.*;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.HeatExchangerMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.client.renderer.machine.FusionReactorRenderer;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;

import com.hepdd.gtmthings.data.CustomMachines;

import java.util.*;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.DUMMY_RECIPES;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class AdvancedMultiBlockMachineB {

    public static void init() {}

    public final static MultiblockMachineDefinition PROCESSING_PLANT = REGISTRATE.multiblock("processing_plant", (holder) -> new StorageMachine(holder, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.BENDER_RECIPES)
            .recipeType(GTRecipeTypes.COMPRESSOR_RECIPES)
            .recipeType(GTRecipeTypes.FORGE_HAMMER_RECIPES)
            .recipeType(GTRecipeTypes.CUTTER_RECIPES)
            .recipeType(GTRecipeTypes.EXTRUDER_RECIPES)
            .recipeType(GTRecipeTypes.LATHE_RECIPES)
            .recipeType(GTRecipeTypes.WIREMILL_RECIPES)
            .recipeType(GTRecipeTypes.FORMING_PRESS_RECIPES)
            .recipeType(GTRecipeTypes.POLARIZER_RECIPES)
            .recipeType(GTRecipeTypes.LASER_ENGRAVER_RECIPES)
            .recipeType(GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.eut_multiplier.tooltip", 0.9))
            .tooltips(Component.translatable("gtceu.machine.duration_multiplier.tooltip", 0.6))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.1"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_11.tooltip",
                    Component.translatable("gtceu.bender"),
                    Component.translatable("gtceu.compressor"),
                    Component.translatable("gtceu.forge_hammer"),
                    Component.translatable("gtceu.cutter"),
                    Component.translatable("gtceu.extruder"),
                    Component.translatable("gtceu.lathe"),
                    Component.translatable("gtceu.wiremill"),
                    Component.translatable("gtceu.forming_press"),
                    Component.translatable("gtceu.polarizer"),
                    Component.translatable("gtceu.laser_engraver"),
                    Component.translatable("gtceu.fluid_solidifier")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTLRecipeModifiers::processingPlantOverclock)
            .appearanceBlock(GTLBlocks.MULTI_FUNCTIONAL_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureA.PROCESSING_PLANT
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.MULTI_FUNCTIONAL_CASING.get())
                            .setMinGlobalLimited(14)
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("c", Predicates.blocks(GTBlocks.CASING_BRONZE_GEARBOX.get()))
                    .build())
            .beforeWorking((machine, recipe) -> {
                boolean isrecipe = false;
                if (machine instanceof StorageMachine storageMachine) {
                    int tier = storageMachine.getTier();
                    GTRecipeType recipeType = storageMachine.getRecipeType();
                    if (storageMachine.getMachineStorageItem().isEmpty()) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_NO_INPUT);
                        return false;
                    }
                    if (recipeType.equals(GTRecipeTypes.BENDER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_bender");
                    } else if (recipeType.equals(GTRecipeTypes.COMPRESSOR_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_compressor");
                    } else if (recipeType.equals(GTRecipeTypes.FORGE_HAMMER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_forge_hammer");
                    } else if (recipeType.equals(GTRecipeTypes.CUTTER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_cutter");
                    } else if (recipeType.equals(GTRecipeTypes.EXTRUDER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_extruder");
                    } else if (recipeType.equals(GTRecipeTypes.LATHE_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_lathe");
                    } else if (recipeType.equals(GTRecipeTypes.WIREMILL_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_wiremill");
                    } else if (recipeType.equals(GTRecipeTypes.FORMING_PRESS_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_forming_press");
                    } else if (recipeType.equals(GTRecipeTypes.POLARIZER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_polarizer");
                    } else if (recipeType.equals(GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_fluid_solidifier");
                    } else if (recipeType.equals(GTRecipeTypes.LASER_ENGRAVER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_laser_engraver");
                    }
                    if (!isrecipe) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_WRONG_INPUT);
                        machine.getRecipeLogic().interruptRecipe();
                    }
                }
                return isrecipe;
            })
            .additionalDisplay(GTLMachines.PROCESSING_PLANT_PARALLEL)
            .workableCasingRenderer(GTLCore.id("block/multi_functional_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition ASSEMBLE_PLANT = REGISTRATE.multiblock("assemble_plant", (holder) -> new StorageMachine(holder, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.ASSEMBLER_RECIPES)
            .recipeType(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.eut_multiplier.tooltip", 0.9))
            .tooltips(Component.translatable("gtceu.machine.duration_multiplier.tooltip", 0.6))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.1"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_2.tooltip",
                    Component.translatable("gtceu.assembler"), Component.translatable("gtceu.circuit_assembler")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTLRecipeModifiers::processingPlantOverclock)
            .appearanceBlock(GTLBlocks.MULTI_FUNCTIONAL_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureA.ASSEMBLE_PLANT
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.MULTI_FUNCTIONAL_CASING.get())
                            .setMinGlobalLimited(14)
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("c", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.StainlessSteel)))
                    .build())
            .beforeWorking((machine, recipe) -> {
                boolean isrecipe = false;
                if (machine instanceof StorageMachine storageMachine) {
                    int tier = storageMachine.getTier();
                    GTRecipeType recipeType = storageMachine.getRecipeType();
                    if (storageMachine.getMachineStorageItem().isEmpty()) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_NO_INPUT);
                        return false;
                    }
                    if (recipeType.equals(GTRecipeTypes.ASSEMBLER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_assembler");
                    } else if (recipeType.equals(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_circuit_assembler");
                    }
                    if (!isrecipe) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_WRONG_INPUT);
                        machine.getRecipeLogic().interruptRecipe();
                    }
                }
                return isrecipe;
            })
            .additionalDisplay(GTLMachines.PROCESSING_PLANT_PARALLEL)
            .workableCasingRenderer(GTLCore.id("block/multi_functional_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition SEPARATED_PLANT = REGISTRATE.multiblock("separated_plant", (holder) -> new StorageMachine(holder, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CENTRIFUGE_RECIPES)
            .recipeType(GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES)
            .recipeType(GTRecipeTypes.ELECTROLYZER_RECIPES)
            .recipeType(GTRecipeTypes.SIFTER_RECIPES)
            .recipeType(GTRecipeTypes.MACERATOR_RECIPES)
            .recipeType(GTRecipeTypes.EXTRACTOR_RECIPES)
            .recipeType(GTLRecipeTypes.DEHYDRATOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.eut_multiplier.tooltip", 0.9))
            .tooltips(Component.translatable("gtceu.machine.duration_multiplier.tooltip", 0.6))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.1"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_7.tooltip",
                    Component.translatable("gtceu.centrifuge"),
                    Component.translatable("gtceu.thermal_centrifuge"),
                    Component.translatable("gtceu.electrolyzer"),
                    Component.translatable("gtceu.sifter"),
                    Component.translatable("gtceu.macerator"),
                    Component.translatable("gtceu.extractor"),
                    Component.translatable("gtceu.dehydrator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTLRecipeModifiers::processingPlantOverclock)
            .appearanceBlock(GTLBlocks.MULTI_FUNCTIONAL_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.SEPARATED_PLANT
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.MULTI_FUNCTIONAL_CASING.get())
                            .setMinGlobalLimited(14)
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("c", Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))
                    .build())
            .beforeWorking((machine, recipe) -> {
                boolean isrecipe = false;
                if (machine instanceof StorageMachine storageMachine) {
                    int tier = storageMachine.getTier();
                    GTRecipeType recipeType = storageMachine.getRecipeType();
                    if (storageMachine.getMachineStorageItem().isEmpty()) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_NO_INPUT);
                        return false;
                    }
                    if (recipeType.equals(GTRecipeTypes.CENTRIFUGE_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_centrifuge");
                    } else if (recipeType.equals(GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_thermal_centrifuge");
                    } else if (recipeType.equals(GTRecipeTypes.ELECTROLYZER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_electrolyzer");
                    } else if (recipeType.equals(GTRecipeTypes.SIFTER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_sifter");
                    } else if (recipeType.equals(GTRecipeTypes.MACERATOR_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_macerator");
                    } else if (recipeType.equals(GTRecipeTypes.EXTRACTOR_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_extractor");
                    } else if (recipeType.equals(GTLRecipeTypes.DEHYDRATOR_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_dehydrator");
                    }
                    if (!isrecipe) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_WRONG_INPUT);
                        machine.getRecipeLogic().interruptRecipe();
                    }
                }
                return isrecipe;
            })
            .additionalDisplay(GTLMachines.PROCESSING_PLANT_PARALLEL)
            .workableCasingRenderer(GTLCore.id("block/multi_functional_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition MIXED_PLANT = REGISTRATE.multiblock("mixed_plant", (holder) -> new StorageMachine(holder, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .recipeType(GTRecipeTypes.MIXER_RECIPES)
            .recipeType(GTRecipeTypes.CHEMICAL_BATH_RECIPES)
            .recipeType(GTRecipeTypes.ORE_WASHER_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.eut_multiplier.tooltip", 0.9))
            .tooltips(Component.translatable("gtceu.machine.duration_multiplier.tooltip", 0.6))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.processing_plant.tooltip.1"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_4.tooltip",
                    Component.translatable("gtceu.chemical_reactor"),
                    Component.translatable("gtceu.mixer"),
                    Component.translatable("gtceu.chemical_bath"),
                    Component.translatable("gtceu.ore_washer")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTLRecipeModifiers::processingPlantOverclock)
            .appearanceBlock(GTLBlocks.MULTI_FUNCTIONAL_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.MIXED_PLANT
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.MULTI_FUNCTIONAL_CASING.get())
                            .setMinGlobalLimited(14)
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("c", Predicates.blocks(GTBlocks.CASING_STEEL_PIPE.get()))
                    .build())
            .beforeWorking((machine, recipe) -> {
                boolean isrecipe = false;
                if (machine instanceof StorageMachine storageMachine) {
                    int tier = storageMachine.getTier();
                    GTRecipeType recipeType = storageMachine.getRecipeType();
                    if (storageMachine.getMachineStorageItem().isEmpty()) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_NO_INPUT);
                        return false;
                    }
                    if (recipeType.equals(GTRecipeTypes.CHEMICAL_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_chemical_reactor");
                    } else if (recipeType.equals(GTRecipeTypes.MIXER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_mixer");
                    } else if (recipeType.equals(GTRecipeTypes.CHEMICAL_BATH_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_chemical_bath");
                    } else if (recipeType.equals(GTRecipeTypes.ORE_WASHER_RECIPES)) {
                        isrecipe = Objects.equals(Registries.getItemId(storageMachine.getMachineStorageItem()), "gtceu:" + GTValues.VN[tier].toLowerCase() + "_ore_washer");
                    }
                    if (!isrecipe) {
                        RecipeResult.of(machine, RecipeResult.FAIL_PROCESSING_PLANT_WRONG_INPUT);
                        machine.getRecipeLogic().interruptRecipe();
                    }
                }
                return isrecipe;
            })
            .additionalDisplay(GTLMachines.PROCESSING_PLANT_PARALLEL)
            .workableCasingRenderer(GTLCore.id("block/multi_functional_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition WEATHER_CONTROL = REGISTRATE.multiblock("weather_control", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.WEATHER_CONTROL_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.weather_control.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.weather_control.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.weather_control.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.weather_control")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(new OverclockingLogic(1, 1, false)))
            .appearanceBlock(GTBlocks.STEEL_HULL)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.WEATHER_CONTROL
                    .where("A", Predicates.blocks(GTBlocks.STEEL_HULL.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(1)))
                    .where("B", Predicates.blocks(Blocks.IRON_BLOCK))
                    .where("C", Predicates.blocks(Blocks.LIGHTNING_ROD))
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where(" ", Predicates.any())
                    .build())
            .shapeInfo(controller -> MultiblockShapeInfo.builder()
                    .aisle("AAA", " A ", " A ", "   ", "   ", "   ", "   ")
                    .aisle("AAA", "ABA", "ABA", " B ", " B ", " B ", " C ")
                    .aisle("A~A", " A ", " A ", "   ", "   ", "   ", "   ")
                    .where('~', controller, Direction.SOUTH)
                    .where('A', GTBlocks.STEEL_HULL.get())
                    .where('B', Blocks.IRON_BLOCK)
                    .where('C', Blocks.LIGHTNING_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP))
                    .where(' ', Blocks.AIR)
                    .build())
            .afterWorking(machine -> {
                Level level = machine.self().getLevel();
                if (level instanceof ServerLevel serverLevel) {
                    if (MachineIO.notConsumableCircuit((WorkableMultiblockMachine) machine, 1)) {
                        int duration = 6000 + serverLevel.random.nextInt(6000);
                        serverLevel.setWeatherParameters(duration, 0, false, false);
                    } else if (MachineIO.notConsumableCircuit((WorkableMultiblockMachine) machine, 2)) {
                        int duration = 6000 + serverLevel.random.nextInt(12000);
                        serverLevel.setWeatherParameters(0, duration, true, false);
                    } else if (MachineIO.notConsumableCircuit((WorkableMultiblockMachine) machine, 3)) {
                        int duration = 6000 + serverLevel.random.nextInt(12000);
                        serverLevel.setWeatherParameters(0, duration, true, true);
                    }
                }
            })
            .workableCasingRenderer(GTCEu.id("block/casings/steam/steel/side"), GTCEu.id("block/multiblock/gcym/large_maceration_tower"))
            .register();

    public final static MultiblockMachineDefinition NANO_FORGE_1 = REGISTRATE.multiblock("nano_forge_1", (holder) -> new StorageMachine(holder, 64))
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.NANO_FORGE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.nano_forge.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_1.tooltip.0"))
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.nano_forge")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers((machine, recipe, params, result) -> GTLRecipeModifiers.nanoForgeOverclock(machine, recipe, params, result, 1))
            .appearanceBlock(GTLBlocks.NAQUADAH_ALLOY_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.NANO_FORGE_1
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("B", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get()))
                    .where("A", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER)))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Ruridit)))
                    .where(" ", Predicates.any())
                    .build())
            .additionalDisplay((controller, components) -> {
                if (controller.isFormed() && controller instanceof StorageMachine machine) {
                    if (Objects.equals(Registries.getItemId(machine.getMachineStorageItem()), "gtceu:carbon_nanoswarm")) {
                        components.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(String.valueOf(Math.min((machine.getMachineStorageItem().getCount()), 64))).withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
                    } else {
                        components.add(Component.translatable("message.gtlcore.need_carbon_nano_swarm_red").withStyle(ChatFormatting.RED));
                    }
                }
            })
            .workableCasingRenderer(GTLCore.id("block/casings/hyper_mechanical_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition NANO_FORGE_2 = REGISTRATE.multiblock("nano_forge_2", (holder) -> new StorageMachine(holder, 64))
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.NANO_FORGE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.nano_forge.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_2.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_2.tooltip.1"))
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.nano_forge")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers((machine, recipe, params, result) -> GTLRecipeModifiers.nanoForgeOverclock(machine, recipe, params, result, 2))
            .appearanceBlock(GTLBlocks.NAQUADAH_ALLOY_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.NANO_FORGE_2
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get()))
                    .where("C", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER)))
                    .where("B", Predicates.blocks(GTBlocks.CASING_ASSEMBLY_LINE.get()))
                    .where("D", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Ruridit)))
                    .where(" ", Predicates.any())
                    .build())
            .additionalDisplay((controller, components) -> {
                if (controller.isFormed() && controller instanceof StorageMachine machine) {
                    if (Objects.equals(Registries.getItemId(machine.getMachineStorageItem()), "gtceu:neutronium_nanoswarm")) {
                        components.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(String.valueOf(Math.min((machine.getMachineStorageItem().getCount()), 64))).withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
                    } else {
                        components.add(Component.translatable("message.gtlcore.need_neutronium_nano_swarm_red").withStyle(ChatFormatting.RED));
                    }
                }
            })
            .workableCasingRenderer(GTLCore.id("block/casings/hyper_mechanical_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition NANO_FORGE_3 = REGISTRATE.multiblock("nano_forge_3", (holder) -> new StorageMachine(holder, 64))
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.NANO_FORGE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.nano_forge.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_3.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_3.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.nano_forge_3.tooltip.2"))
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.nano_forge")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers((machine, recipe, params, result) -> GTLRecipeModifiers.nanoForgeOverclock(machine, recipe, params, result, 3))
            .appearanceBlock(GTLBlocks.NAQUADAH_ALLOY_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.NANO_FORGE_3
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get()))
                    .where("D", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER)))
                    .where("B", Predicates.blocks(GTLBlocks.ADVANCED_ASSEMBLY_LINE_UNIT.get()))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Ruridit)))
                    .where(" ", Predicates.any())
                    .build())
            .additionalDisplay((controller, components) -> {
                if (controller.isFormed() && controller instanceof StorageMachine machine) {
                    if (Objects.equals(Registries.getItemId(machine.getMachineStorageItem()), "gtceu:draconium_nanoswarm")) {
                        components.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(String.valueOf(Math.min((machine.getMachineStorageItem().getCount()), 64))).withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
                    } else {
                        components.add(Component.translatable("message.gtlcore.need_dragon_nano_swarm_red").withStyle(ChatFormatting.RED));
                    }
                }
            })
            .workableCasingRenderer(GTLCore.id("block/casings/hyper_mechanical_casing"), GTCEu.id("block/multiblock/gcym/large_assembler"))
            .register();

    public final static MultiblockMachineDefinition ISA_MILL = REGISTRATE.multiblock("isa_mill", (holder) -> new StorageMachine(holder, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.ISA_MILL_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.isa_mill.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.isa_mill")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier((machine, recipe, params, result) -> {
                if (machine instanceof StorageMachine storageMachine) {
                    ItemStack item = storageMachine.getMachineStorageItem();
                    int tier = switch (Registries.getItemId(item)) {
                        case "kubejs:grindball_aluminium" -> 2;
                        case "kubejs:grindball_soapstone" -> 1;
                        default -> 0;
                    };
                    if (tier == recipe.data.getInt("grindball")) {
                        int damage = item.getDamageValue();
                        if (damage < item.getMaxDamage()) {
                            item.setDamageValue(damage + 1);
                            storageMachine.setMachineStorageItem(item);
                        } else {
                            storageMachine.setMachineStorageItem(new ItemStack(Items.AIR));
                        }
                        return RecipeHelper.applyOverclock(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK, recipe, storageMachine.getOverclockVoltage(), params, result);
                    }
                }
                return null;
            })
            .appearanceBlock(() -> Registries.getBlock("kubejs:inconel_625_casing"))
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.ISA_MILL
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("B", Predicates.blocks(Registries.getBlock("kubejs:inconel_625_casing"))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(4))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.MUFFLER).setExactLimit(1)))
                    .where("C", Predicates.blocks(Registries.getBlock("kubejs:inconel_625_gearbox")))
                    .where("A", Predicates.blocks(Registries.getBlock("kubejs:inconel_625_pipe")))
                    .build())
            .workableCasingRenderer(new ResourceLocation("kubejs:block/inconel_625_casing"), GTCEu.id("block/multiblock/gcym/large_maceration_tower"))
            .register();

    public static final MultiblockMachineDefinition NEUTRON_ACTIVATOR = REGISTRATE
            .multiblock("neutron_activator", NeutronActivatorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.6"))
            .tooltips(Component.translatable("gtceu.machine.neutron_activator.tooltip.7"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.neutron_activator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeTypes(GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES)
            .recipeModifiers(((machine, recipe, params, result) -> NeutronActivatorMachine.recipeModifier(machine, recipe)))
            .appearanceBlock(GTBlocks.CASING_STAINLESS_CLEAN)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.ISA_MILL_2
                    .where('G', controller(blocks(definition.getBlock())))
                    .where('A', blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())
                            .or(blocks(GTLMachines.NEUTRON_SENSOR.get()).setMaxGlobalLimited(1))
                            .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(2))
                            .or(abilities(GTLPartAbility.NEUTRON_ACCELERATOR).setMaxGlobalLimited(2))
                            .or(abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
                            .or(abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where('B', frames(GTMaterials.Tungsten))
                    .where('C', blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())
                            .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(2)))
                    .where('D', blocks(GTLBlocks.PROCESS_MACHINE_CASING.get()))
                    .where('E', blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where('F', GTLPredicates.countBlock("SpeedPipe",
                            Registries.getBlock("kubejs:speeding_pipe")))
                    .where(' ', any())
                    .build())
            .workableCasingRenderer(
                    GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"),
                    GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition HEAT_EXCHANGER = REGISTRATE
            .multiblock("heat_exchanger", HeatExchangerMachine::new)
            .langValue("Heat Exchanger")
            .tooltips(Component.translatable("gtceu.machine.heat_exchanger.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.heat_exchanger.tooltip.1"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.heat_exchanger")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.HEAT_EXCHANGER_RECIPES)
            .recipeModifiers((machine, recipe, params, result) -> HeatExchangerMachine.recipeModifier(machine, recipe))
            .appearanceBlock(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.HEAT_EXCHANGER
                    .where('S', controller(blocks(definition.get())))
                    .where('A',
                            blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get()).setMinGlobalLimited(98)
                                    .or(autoAbilities(definition.getRecipeTypes()))
                                    .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where('C', blocks(GTBlocks.CASING_TUNGSTENSTEEL_PIPE.get()))
                    .where('B', blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where('D', blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.HSSG)))
                    .where(' ', any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                    GTCEu.id("block/multiblock/implosion_compressor"))
            .register();

    public static final MultiblockMachineDefinition[] FLUID_DRILLING_RIG = GTMachines.registerTieredMultis(
            "fluid_drilling_rig", INFFluidDrillMachine::new, (tier, builder) -> builder
                    .rotationState(RotationState.ALL)
                    .langValue("%s Fluid Drilling Rig %s".formatted(GTValues.VLVH[tier], GTValues.VLVT[tier]))
                    .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                    .tooltips(
                            Component.translatable("gtceu.machine.fluid_drilling_rig.description"),
                            Component.translatable("gtceu.machine.fluid_drilling_rig.depletion", 0),
                            Component.translatable("gtceu.universal.tooltip.energy_tier_range", GTValues.VNF[tier],
                                    GTValues.VNF[tier + 1]),
                            Component.translatable("gtceu.machine.fluid_drilling_rig.production",
                                    INFFluidDrillMachine.getRigMultiplier(tier),
                                    FormattingUtil.formatNumbers(INFFluidDrillMachine.getRigMultiplier(tier) * 1.5)))
                    .tooltipBuilder(GTLMachines.GTL_ADD)
                    .appearanceBlock(() -> INFFluidDrillMachine.getCasingState(tier))
                    .pattern((definition) -> AdvancedMultiBlockMachineStructureB.HEAT_EXCHANGER_2
                            .where('S', controller(blocks(definition.get())))
                            .where('X', blocks(INFFluidDrillMachine.getCasingState(tier)).setMinGlobalLimited(3)
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1)))
                            .where('C', blocks(INFFluidDrillMachine.getCasingState(tier)))
                            .where('F', blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Ruridit)))
                            .where('#', any())
                            .build())
                    .workableCasingRenderer(GTLCore.id("block/casings/iridium_casing"),
                            GTCEu.id("block/multiblock/fluid_drilling_rig"))
                    .register(),
            GTValues.ZPM);

    public final static MultiblockMachineDefinition ADVANCED_ASSEMBLY_LINE = REGISTRATE
            .multiblock("advanced_assembly_line", AdvancedAssemblyLineMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.ASSEMBLY_LINE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.advanced_assembly_line.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.advanced_assembly_line.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.assembly_line.tooltip.0"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.assembly_line")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(AdvancedAssemblyLineMachine::recipeModifier)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.ADVANCED_ASSEMBLY_LINE
                    .where("S", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("F", Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(4))
                            .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1)))
                    .where("O", Predicates.abilities(PartAbility.EXPORT_ITEMS).addTooltips(Component.translatable("gtceu.multiblock.pattern.location_end")))
                    .where("Y", Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2)))
                    .where("I", Predicates.blocks(GTMachines.ITEM_IMPORT_BUS[0].get()).or(Predicates.blocks(CustomMachines.HUGE_ITEM_IMPORT_BUS[0].get())))
                    .where("G", Predicates.blocks(GTBlocks.CASING_GRATE.get()))
                    .where("D", Predicates.blocks(GTBlocks.CASING_GRATE.get())
                            .or(Predicates.abilities(PartAbility.OPTICAL_DATA_RECEPTION).setExactLimit(1)))
                    .where("A", Predicates.blocks(GTBlocks.CASING_ASSEMBLY_CONTROL.get()))
                    .where("R", Predicates.blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where("T", GTLPredicates.countBlock("Unit", GTLBlocks.ADVANCED_ASSEMBLY_LINE_UNIT.get()))
                    .where("#", Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"), GTCEu.id("block/multiblock/assembly_line"))
            .register();

    public final static MultiblockMachineDefinition FISSION_REACTOR = REGISTRATE.multiblock("fission_reactor", FissionReactorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.FISSION_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.6"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.7"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.8"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.9"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.10"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.11"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.12"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.13"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.14"))
            .tooltips(Component.translatable("gtceu.machine.fission_reactor.tooltip.15"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.fission_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier((machine, recipe, params, result) -> FissionReactorMachine.recipeModifier(machine, recipe))
            .appearanceBlock(GTLBlocks.FISSION_REACTOR_CASING)
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.FISSION_REACTOR
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.FISSION_REACTOR_CASING.get())
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1)))
                    .where("B", Predicates.blocks(GTBlocks.CASING_LAMINATED_GLASS.get()).or(Predicates.blocks(GTLBlocks.FISSION_REACTOR_CASING.get())))
                    .where("C", Predicates.air().or(GTLPredicates.countBlock("FuelAssembly", GTLBlocks.FISSION_FUEL_ASSEMBLY.get()))
                            .or(GTLPredicates.countBlock("Cooler", GTLBlocks.COOLER.get())))
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/fission_reactor_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition SPACE_ELEVATOR = REGISTRATE.multiblock("space_elevator", SpaceElevatorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.SPACE_ELEVATOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.space_elevator.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.space_elevator.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.space_elevator.tooltip.2"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.space_elevator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(new OverclockingLogic(1, 4, false)))
            .appearanceBlock(GTLBlocks.SPACE_ELEVATOR_MECHANICAL_CASING)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.SPACE_ELEVATOR
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("X", Predicates.blocks(GTLBlocks.SPACE_ELEVATOR_MECHANICAL_CASING.get())
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("E", Predicates.blocks(GTLBlocks.SPACE_ELEVATOR_SUPPORT.get()))
                    .where("H", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Neutronium)))
                    .where("F", Predicates.blocks(Registries.getBlock("kubejs:space_elevator_internal_support")))
                    .where("C", GTLPredicates.tierCasings(BlockMap.sepmMap, "SEPMTier"))
                    .where("A", Predicates.blocks(Registries.getBlock("kubejs:high_strength_concrete")))
                    .where("D", Predicates.blocks(GTLBlocks.SPACE_ELEVATOR_MECHANICAL_CASING.get()))
                    .where("M", Predicates.blocks(GTLBlocks.POWER_CORE.get()))
                    .where("G", Predicates.blocks(Registries.getBlock("kubejs:module_base")))
                    .where("V", Predicates.any().or(Predicates.blocks(Registries.getBlock("kubejs:module_connector")).setPreviewCount(1)))
                    .where("-", Predicates.air())
                    .where(" ", Predicates.any())
                    .build())
            .renderer(SpaceElevatorRenderer::new)
            .hasTESR(true)
            .register();

    public final static MultiblockMachineDefinition SLAUGHTERHOUSE = REGISTRATE.multiblock("slaughterhouse", SlaughterhouseMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(new OverclockingLogic(1, 4, false)))
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .recipeType(GTLRecipeTypes.SLAUGHTERHOUSE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.slaughterhouse.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.slaughterhouse")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.SLAUGHTERHOUSE
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(4))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("B", Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                    .where("C", Predicates.blocks(GTBlocks.CASING_STEEL_GEARBOX.get()))
                    .where("D", Predicates.blocks(Blocks.IRON_BARS))
                    .where("E", Predicates.blocks(GTBlocks.FIREBOX_STEEL.get()))
                    .where(" ", Predicates.air())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"), GTCEu.id("block/multiblock/gcym/large_cutter"))
            .register();

    public static final MultiblockMachineDefinition[] FUSION_REACTOR = GTMachines.registerTieredMultis("fusion_reactor",
            FusionReactorMachine::new, (tier, builder) -> builder
                    .rotationState(RotationState.ALL)
                    .langValue("Fusion Reactor Computer MK %s".formatted(FormattingUtil.toRomanNumeral(tier - 5)))
                    .recipeType(GTRecipeTypes.FUSION_RECIPES)
                    .recipeModifiers(GTRecipeModifiers.DEFAULT_ENVIRONMENT_REQUIREMENT,
                            FusionReactorMachine::recipeModifier)
                    .tooltips(
                            Component.translatable("gtceu.machine.fusion_reactor.capacity",
                                    FusionReactorMachine.calculateEnergyStorageFactor(tier, 16) / 1000000L),
                            Component.translatable("gtceu.machine.fusion_reactor.overclocking"),
                            Component.translatable("gtceu.multiblock.%s_fusion_reactor.description"
                                    .formatted(GTValues.VN[tier].toLowerCase(Locale.ROOT))))
                    .tooltipBuilder(GTLMachines.GTL_ADD)
                    .appearanceBlock(() -> GTLFusionCasingBlock.getCasingState(tier))
                    .pattern((definition) -> {
                        var casing = blocks(GTLFusionCasingBlock.getCasingState(tier));
                        return AdvancedMultiBlockMachineStructureB.SLAUGHTERHOUSE_2
                                .where('S', controller(blocks(definition.get())))
                                .where('G', blocks(GTBlocks.FUSION_GLASS.get()).or(casing))
                                .where('E', casing.or(
                                        blocks(PartAbility.INPUT_ENERGY.getBlockRange(tier, GTValues.UEV).toArray(Block[]::new))
                                                .setMinGlobalLimited(1).setPreviewCount(16)))
                                .where('C', casing)
                                .where('K', blocks(GTLFusionCasingBlock.getCoilState(tier)))
                                .where('O', casing.or(abilities(PartAbility.EXPORT_FLUIDS)))
                                .where('A', air())
                                .where('I', casing.or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(2)))
                                .where('#', any())
                                .build();
                    })
                    .shapeInfos((controller) -> {
                        List<MultiblockShapeInfo> shapeInfos = new ArrayList<>();

                        MultiblockShapeInfo.ShapeInfoBuilder baseBuilder = MultiblockShapeInfo.builder()
                                .aisle("###############", "######NMN######", "###############")
                                .aisle("######DCD######", "####GG###GG####", "######UCU######")
                                .aisle("####CC###CC####", "###w##SGS##e###", "####CC###CC####")
                                .aisle("###C#######C###", "##nKsG###GsKn##", "###C#######C###")
                                .aisle("##C#########C##", "#G#e#######w#G#", "##C#########C##")
                                .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                                .aisle("#D###########D#", "W#E#########W#E", "#U###########U#")
                                .aisle("#C###########C#", "G#G#########G#G", "#C###########C#")
                                .aisle("#D###########D#", "W#E#########W#E", "#U###########U#")
                                .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                                .aisle("##C#########C##", "#G#e#######w#G#", "##C#########C##")
                                .aisle("###C#######C###", "##sKnG###GnKs##", "###C#######C###")
                                .aisle("####CC###CC####", "###w##NGN##e###", "####CC###CC####")
                                .aisle("######DCD######", "####GG###GG####", "######UCU######")
                                .aisle("###############", "######SGS######", "###############")
                                .where('M', controller, Direction.NORTH)
                                .where('C', GTLFusionCasingBlock.getCasingState(tier))
                                .where('G', GTBlocks.FUSION_GLASS.get())
                                .where('K', GTLFusionCasingBlock.getCoilState(tier))
                                .where('W', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.WEST)
                                .where('E', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.EAST)
                                .where('S', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.SOUTH)
                                .where('N', GTMachines.FLUID_EXPORT_HATCH[tier], Direction.NORTH)
                                .where('w', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.WEST)
                                .where('e', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.EAST)
                                .where('s', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.SOUTH)
                                .where('n', GTMachines.ENERGY_INPUT_HATCH[tier], Direction.NORTH)
                                .where('U', GTMachines.FLUID_IMPORT_HATCH[tier], Direction.UP)
                                .where('D', GTMachines.FLUID_IMPORT_HATCH[tier], Direction.DOWN)
                                .where('#', Blocks.AIR.defaultBlockState());

                        shapeInfos.add(baseBuilder.shallowCopy()
                                .where('G', GTLFusionCasingBlock.getCasingState(tier))
                                .build());
                        shapeInfos.add(baseBuilder.build());
                        return shapeInfos;
                    })
                    .renderer(() -> new FusionReactorRenderer(GTLFusionCasingBlock.getCasingType(tier).getTexture(),
                            GTCEu.id("block/multiblock/fusion_reactor")))
                    .hasTESR(true)
                    .register(),
            GTValues.UHV, GTValues.UEV);

    public static final MultiblockMachineDefinition[] COMPRESSED_FUSION_REACTOR = GTMachines.registerTieredMultis("compressed_fusion_reactor",
            (holder, tier) -> new FusionReactorMachine(holder, tier) {

                @Override
                public long getMaxVoltage() {
                    return super.getOverclockVoltage();
                }
            }, (tier, builder) -> builder
                    .rotationState(RotationState.ALL)
                    .langValue("Fusion Reactor Computer MK %s".formatted(FormattingUtil.toRomanNumeral(tier - 5)))
                    .recipeType(GTRecipeTypes.FUSION_RECIPES)
                    .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, FusionReactorMachine::recipeModifier)
                    .tooltips(
                            Component.translatable("gtceu.machine.fusion_reactor.capacity",
                                    FusionReactorMachine.calculateEnergyStorageFactor(tier, 16) / 1000000L),
                            Component.translatable("gtceu.machine.fusion_reactor.overclocking"))
                    .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
                    .tooltips(Component.translatable("gtceu.multiblock.fusion_reactor_energy.limit", GTValues.VN[tier]))
                    .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
                    .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
                    .tooltipBuilder(GTLMachines.GTL_ADD)
                    .appearanceBlock(() -> GTLFusionCasingBlock.getCasingState(tier))
                    .pattern((definition) -> {
                        var casing = blocks(GTLFusionCasingBlock.getCasingState(tier));
                        return AdvancedMultiBlockMachineStructureB.SLAUGHTERHOUSE_3
                                .where('S', controller(blocks(definition.get())))
                                .where('B', blocks(GTBlocks.FUSION_GLASS.get()))
                                .where('C', casing)
                                .where('P', casing.or(abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1)))
                                .where('I', casing.or(abilities(PartAbility.IMPORT_FLUIDS))
                                        .or(abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(16)))
                                .where('F', blocks(GTLFusionCasingBlock.getFrameState(tier)))
                                .where('H', blocks(GTLFusionCasingBlock.getCompressedCoilState(tier)))
                                .where('E', casing.or(blocks(PartAbility.INPUT_ENERGY.getBlockRange(tier, 14).toArray(Block[]::new)))
                                        .or(blocks(PartAbility.INPUT_LASER.getBlockRange(tier, 14).toArray(Block[]::new)).setPreviewCount(16)))
                                .where('#', air())
                                .where(' ', any())
                                .build();
                    })
                    .workableCasingRenderer(GTLFusionCasingBlock.getCasingType(tier).getTexture(), GTCEu.id("block/multiblock/fusion_reactor"))
                    .register(),
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV, GTValues.UEV);

    public final static MultiblockMachineDefinition SUPER_COMPUTATION = REGISTRATE.multiblock("super_computation", (holder) -> new ComputationProviderMachine(holder, false))
            .rotationState(RotationState.NONE)
            .allowExtendedFacing(false)
            .allowFlip(false)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.super_computation.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.super_computation")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .appearanceBlock(GTBlocks.COMPUTER_CASING)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK))
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.SUPER_COMPUTATION
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("a", Predicates.blocks(GTBlocks.COMPUTER_CASING.get())
                            .or(Predicates.abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("b", Predicates.blocks(GTBlocks.COMPUTER_HEAT_VENT.get()))
                    .where("c", Predicates.fluids(Registries.getFluid("kubejs:gelid_cryotheum")))
                    .where("d", Predicates.blocks(GTLBlocks.SUPER_COOLER_COMPONENT.get()))
                    .where("e", Predicates.blocks(GTLBlocks.SUPER_COMPUTATION_COMPONENT.get()))
                    .where("f", Predicates.blocks(GTBlocks.ADVANCED_COMPUTER_CASING.get()))
                    .where("g", Predicates.blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/hpca/computer_casing/back"), GTCEu.id("block/multiblock/super_computation"))
            .register();

    public final static MultiblockMachineDefinition CREATE_COMPUTATION = REGISTRATE.multiblock("create_computation", (holder) -> new ComputationProviderMachine(holder, true))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.create_computation.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.super_computation")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .appearanceBlock(GTBlocks.ADVANCED_COMPUTER_CASING)
            .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK))
            .pattern((definition) -> AdvancedMultiBlockMachineStructureB.CREATE_COMPUTATION
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTBlocks.ADVANCED_COMPUTER_CASING.get())
                            .or(Predicates.abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(1)))
                    .where("a", Predicates.blocks(GTBlocks.ADVANCED_COMPUTER_CASING.get()))
                    .where("c", Predicates.blocks(Registries.getBlock("kubejs:create_hpca_component")))
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/hpca/advanced_computer_casing/back"), GTCEu.id("block/multiblock/hpca"))
            .register();

    public final static MultiblockMachineDefinition ADVANCED_INFINITE_DRILLER = REGISTRATE.multiblock("advanced_infinite_driller", AdvancedInfiniteDrillMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(DUMMY_RECIPES)
            .appearanceBlock(GTLBlocks.HYPER_MECHANICAL_CASING)
            .tooltips(Component.translatable("gtceu.machine.advanced_infinite_driller.drilled_fluid.tooltip.0"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltipBuilder((item, list) -> {
                for (int i = 1; i <= 18; i++) {
                    list.add(Component.translatable("gtceu.machine.advanced_infinite_driller.drilled_fluid.tooltip." + i));
                }
            })
            .pattern(definition -> AdvancedMultiBlockMachineStructureB.ADVANCED_INFINITE_DRILLER
                    .where("#", controller(blocks(definition.get())))
                    .where("I", blocks(Registries.getBlock("gtceu:filter_casing")))
                    .where("G", blocks(Registries.getBlock("kubejs:dimensional_bridge_casing")))
                    .where("N", Predicates.heatingCoils())
                    .where("M", blocks(Registries.getBlock("kubejs:neutronium_gearbox")))
                    .where("L", blocks(Registries.getBlock("gtceu:vanadium_block")))
                    .where("B", blocks(Registries.getBlock("gtlcore:hyper_core")))
                    .where("D", blocks(Registries.getBlock("minecraft:oak_log")))
                    .where("F", blocks(Registries.getBlock("kubejs:restraint_device")))
                    .where("H", blocks(Registries.getBlock("gtceu:heat_vent")))
                    .where("K", blocks(Registries.getBlock("kubejs:machine_casing_grinding_head")))
                    .where("E", blocks(Registries.getBlock("gtceu:neutronium_frame")))
                    .where("J", blocks(Registries.getBlock("gtceu:ptfe_pipe_casing")))
                    .where("C", blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where("A", blocks(Registries.getBlock("gtlcore:iridium_casing"))
                            .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                            .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(16))
                            .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2).setMinGlobalLimited(1))
                            .or(abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1)))
                    .where("Q", blocks(GTLBlocks.HYPER_CORE.get())
                            .or(blocks(GTLMachines.HEAT_SENSOR.get())))
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/iridium_casing"),
                    GTCEu.id("block/multiblock/fluid_drilling_rig"))
            .register();
}
