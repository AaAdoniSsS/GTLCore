package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricParallelHatchMultipleRecipesMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.PrimitiveOreMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class MultiBlockMachineB {

    public static void init() {}

    public final static MultiblockMachineDefinition GRAVITATION_SHOCKBURST = REGISTRATE.multiblock("gravitation_shockburst", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.GRAVITATION_SHOCKBURST_RECIPES)
            .recipeType(GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES)
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_2.tooltip",
                    Component.translatable("gtceu.gravitation_shockburst"), Component.translatable("gtceu.electric_implosion_compressor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK))
            .appearanceBlock(GTLBlocks.CREATE_CASING)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("aaaaaaaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " bcccccb ", " bcc ccb ", " bc   cb ", " bcc ccb ", " bcccccb ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
                    .aisle("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
                    .aisle("aaaa~aaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa")
                    .where("a", Predicates.blocks(GTLBlocks.CREATE_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(3))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(3))
                            .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1)))
                    .where("b", Predicates.blocks(GTLBlocks.INFINITY_GLASS.get()))
                    .where("c", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.block, GTLMaterials.Infinity)))
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/create_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition DISSOLVING_TANK = REGISTRATE.multiblock("dissolving_tank", WorkableElectricMultiblockMachine::new)
            .langValue("Dissolving Tank")
            .tooltips(Component.translatable("gtceu.multiblock.dissolving_tank.tooltip.0"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.dissolution_treatment")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .rotationState(RotationState.ALL)
            .recipeTypes(GTLRecipeTypes.DISSOLUTION_TREATMENT)
            .recipeModifier(GTLRecipeModifiers::dissolvingTankOverclock)
            .appearanceBlock(GTBlocks.CASING_STAINLESS_CLEAN)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("X###X", "OOOOO", "XGGGX", "XGGGX", "#XXX#")
                    .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
                    .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
                    .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
                    .aisle("X###X", "OOSOO", "XGGGX", "XGGGX", "#XXX#")
                    .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                    .where('X', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()))
                    .where('K', Predicates.blocks(GTBlocks.CASING_INVAR_HEATPROOF.get()))
                    .where('O', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.autoAbilities(true, false, true)))
                    .where('G', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                    .where('A', Predicates.air())
                    .where('#', Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"), GTCEu.id("block/multiblock/generator/large_gas_turbine"))
            .register();

    public final static MultiblockMachineDefinition DIGESTION_TANK = REGISTRATE.multiblock("digestion_tank", CoilWorkableElectricMultiblockMachine::new)
            .langValue("Digestion Tank")
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.digestion_treatment")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .rotationState(RotationState.ALL)
            .recipeTypes(GTLRecipeTypes.DIGESTION_TREATMENT)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK))
            .appearanceBlock(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("#OOOOO#", "#YMMMY#", "##YYY##", "#######")
                    .aisle("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
                    .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
                    .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
                    .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
                    .aisle("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
                    .aisle("#OOSOO#", "#YMMMY#", "##YYY##", "#######")
                    .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                    .where('X', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()))
                    .where('K', Predicates.blocks(GTBlocks.CASING_INVAR_HEATPROOF.get()))
                    .where('Y', Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get()))
                    .where('M', Predicates.heatingCoils())
                    .where('O', Predicates.blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get())
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.autoAbilities(true, false, true)))
                    .where('A', Predicates.air())
                    .where('#', Predicates.any())
                    .build())
            .beforeWorking((machine, recipe) -> machine instanceof CoilWorkableElectricMultiblockMachine coilMachine && coilMachine.getCoilType().getCoilTemperature() >= recipe.data.getInt("ebf_temp"))
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"), GTCEu.id("block/multiblock/gcym/large_maceration_tower"))
            .register();

    public final static MultiblockMachineDefinition WOOD_DISTILLATION = REGISTRATE.multiblock("wood_distillation", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.WOOD_DISTILLATION_RECIPES)
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.wood_distillation")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
            .appearanceBlock(GTBlocks.CASING_INVAR_HEATPROOF)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("ABBBA IIIII IIIII ABBBA", "AAAAA IIIII IIIII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
                    .aisle("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKKKI IKKKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DJD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
                    .aisle("BBBBB IKKKI IKKKI BBBBB", "AAGAA IKGKI IKGKI AAGAA", "ADGDA IJGJI IJGJI ADGDA", " DGD   JGJ   JGJ  CDGDC", "CDGDC  JGJ   JGJC A G A", "A G A  JGJ  CJGJC  EGE ", " EGE  CJGJC J   J A G A", "A G A J   J J   J  EGE ", " EGE  J   J J   J A G A", "A G A J   J B   B  EGE ", " EGE  B   B  DGD  B   B", "A G A CDGDC CDGDC D   D", " EEE   DGD  D   GGD   D", "B   B CEEEC D   D D   D", "D   GGG   D D   D CD DC", "D   D D   D CD DC  DDD ", "CD DC D   D  DDD       ", " DDD  D   D            ", "      CD DC            ", "       DDD             ")
                    .aisle("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKGKI IKGKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DGD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
                    .aisle("ABBBA IKKKI IKKKI ABBBA", "AAAAA IIGII IIGII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
                    .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("C   C  IIIIIIIII       ", "CCCCC  IGIIIIIGI       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIEIIIEII       ", " DDD    IEI IEI        ", "        IEI IEI        ", "        IHI IHI        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("       IILLLLLII       ", "CDDDC  IILLMLLII       ", "D   D  IILLLLLII       ", "D   D  IIIIIIIII       ", "D   D  IIIIIIIII       ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("                       ", "CDDDC                  ", "D   D                  ", "D   D                  ", "D   D                  ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .aisle("C   C                  ", "CCCCC                  ", "CDNDC                  ", "CDNDC                  ", "CDDDC                  ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
                    .where("M", controller(blocks(definition.get())))
                    .where("I", blocks(GTBlocks.CASING_INVAR_HEATPROOF.get()))
                    .where("N", blocks(GTBlocks.HERMETIC_CASING_HV.get()))
                    .where("J", blocks(GTBlocks.CASING_STAINLESS_EVAPORATION.get()))
                    .where("E", blocks(GTBlocks.FILTER_CASING.get()))
                    .where("L", blocks(GTBlocks.CASING_INVAR_HEATPROOF.get())
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(2).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(6).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(2).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("B", Predicates.blocks(GCyMBlocks.HEAT_VENT.get()))
                    .where("H", Predicates.abilities(PartAbility.MUFFLER))
                    .where("D", blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()))
                    .where("G", blocks(GTBlocks.CASING_STEEL_PIPE.get()))
                    .where("K", blocks(GTBlocks.FIREBOX_STEEL.get()))
                    .where("C", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.StainlessSteel)))
                    .where("A", blocks(GTBlocks.CASING_ALUMINIUM_FROSTPROOF.get()))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_heatproof"), GTCEu.id("block/multiblock/electric_blast_furnace"))
            .register();

    public final static MultiblockMachineDefinition DESULFURIZER = REGISTRATE.multiblock("desulfurizer", WorkableElectricParallelHatchMultipleRecipesMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.DESULFURIZER_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.multiple_recipes.tooltip"))
            .tooltips(Component.translatable("gtceu.multiblock.parallelizable.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.desulfurizer")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .appearanceBlock(GTBlocks.CASING_STAINLESS_CLEAN)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("CCCCCIIIIIII", "CCCCCILILILI", "CCCCCILILILI", "            ", "            ", "            ")
                    .aisle("CCCCCIIIIIII", "CGCCCXXXXXXI", "CCCCCILILILI", "  CCCILILILI", "  CCCIIIIIII", "            ")
                    .aisle("CCCCCIIIIIII", "CGGGGILILILI", "CCCCCILILILI", "  CXXXXXXXXI", "  CCCIIIIIII", "            ")
                    .aisle("CCDDDIIIIIII", "CGDPDXXXXXXI", "CCDDDILILILI", "  DDDILILILI", "  DDDIIIIIII", "  DDD       ")
                    .aisle("CCCCCIIIIIII", "CCDPDILILILI", "CCDPDILILILI", "  DPD       ", "  DPD       ", "  DDD       ")
                    .aisle("  DDD       ", "  D~D       ", "  DDD       ", "  DDD       ", "  DDD       ", "  DDD       ")
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("I", blocks(GTBlocks.CASING_INVAR_HEATPROOF.get()))
                    .where("X", Predicates.blocks(GTBlocks.CASING_STEEL_PIPE.get()))
                    .where("P", Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))
                    .where("G", Predicates.blocks(GTBlocks.HERMETIC_CASING_MV.get()))
                    .where("D", Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())
                            .setMinGlobalLimited(36)
                            .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                            .or(Predicates.autoAbilities(true, false, true)))
                    .where("L", Predicates.blocks(GTBlocks.COIL_KANTHAL.get()))
                    .where("C", Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"), GTCEu.id("block/multiblock/large_chemical_reactor"))
            .register();

    public final static MultiblockMachineDefinition PRIMITIVE_VOID_ORE = ConfigHolder.INSTANCE.enablePrimitiveVoidOre ?
            REGISTRATE.multiblock("primitive_void_ore", PrimitiveOreMachine::new)
                    .langValue("Primitive Void Ore")
                    .tooltips(Component.translatable("tooltip.gtlcore.primitive_void_ore_random_output"))
                    .tooltips(Component.translatable("tooltip.gtlcore.supports_dimensions"))
                    .tooltipBuilder(GTLMachines.GTL_ADD)
                    .rotationState(RotationState.ALL)
                    .recipeType(GTLRecipeTypes.PRIMITIVE_VOID_ORE_RECIPES)
                    .appearanceBlock(() -> Blocks.DIRT)
                    .pattern(definition -> FactoryBlockPattern.start()
                            .aisle("XXX", "XXX", "XXX")
                            .aisle("XXX", "XAX", "XXX")
                            .aisle("XXX", "XSX", "XXX")
                            .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                            .where('X',
                                    Predicates.blocks(Blocks.DIRT)
                                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS)))
                            .where('A', Predicates.air())
                            .build())
                    .workableCasingRenderer(new ResourceLocation("minecraft:block/dirt"),
                            GTCEu.id("block/multiblock/gcym/large_extractor"))
                    .register() :
            null;

    public final static MultiblockMachineDefinition LARGE_FRAGMENT_WORLD_COLLECTION_MACHINE = ConfigHolder.INSTANCE.enableSkyBlokeMode ?
            REGISTRATE.multiblock("large_fragment_world_collection_machine", WorkableElectricMultiblockMachine::new)
                    .langValue("Large fragment world collection machine")
                    .tooltips(Component.translatable("gtlcore.machine.large_fragment_world_collection.sky_block_mode"))
                    .tooltips(Component.translatable("gtlcore.machine.large_fragment_world_collection.energy_multiplier"))
                    .tooltips(Component.translatable("gtlcore.machine.large_fragment_world_collection.duration_multiplier"))
                    .tooltips(Component.translatable("gtlcore.machine.large_fragment_world_collection.max_parallel"))
                    .tooltipBuilder(GTLMachines.GTL_ADD)
                    .recipeModifiers(
                            (machine, recipe, params, result) -> GTLRecipeModifiers.reduction(machine, recipe, 256, 0.25),
                            (machine, recipe, params, result) -> GTRecipeModifiers.accurateParallel(machine, recipe, 64, false).getFirst())
                    .rotationState(RotationState.ALL)
                    .recipeType(GTLRecipeTypes.FRAGMENT_WORLD_COLLECTION)
                    .appearanceBlock(GTBlocks.CASING_TITANIUM_STABLE)
                    .pattern(definition -> FactoryBlockPattern.start()
                            .aisle("AAA", "AXA", "XXX", "XXX", "XXX", "AXA", "AAA")
                            .aisle("AOA", "XXX", "XXX", "XXX", "XXX", "XXX", "AIA")
                            .aisle("AAA", "AXA", "XXX", "XSX", "XXX", "AXA", "AAA")
                            .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                            .where('X', Predicates.blocks(GTBlocks.CASING_TITANIUM_STABLE.get())
                                    .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setExactLimit(1)))
                            .where('I', Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .where('O', Predicates.abilities(PartAbility.EXPORT_ITEMS))
                            .where('A', Predicates.any())
                            .build())
                    .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_stable_titanium"),
                            GTCEu.id("block/multiblock/gcym/large_extractor"))
                    .register() :
            null;
}
