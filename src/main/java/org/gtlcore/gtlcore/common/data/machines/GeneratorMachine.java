package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.client.renderer.machine.AnnihilateGeneratorRenderer;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.DysonSphereMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class GeneratorMachine {

    public static void init() {}

    public static final MultiblockMachineDefinition LARGE_SEMI_FLUID_GENERATOR = GTMachines.registerLargeCombustionEngine(
            "large_semi_fluid_generator", GTValues.EV,
            GTBlocks.CASING_TITANIUM_STABLE, GTBlocks.CASING_STEEL_GEARBOX, GTBlocks.CASING_ENGINE_INTAKE,
            GTCEu.id("block/casings/solid/machine_casing_stable_titanium"),
            GTCEu.id("block/multiblock/generator/large_combustion_engine"));

    public final static MultiblockMachineDefinition CHEMICAL_ENERGY_DEVOURER = REGISTRATE
            .multiblock("chemical_energy_devourer", ChemicalEnergyDevourerMachine::new)
            .rotationState(RotationState.ALL)
            .recipeTypes(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS, GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS,
                    GTRecipeTypes.GAS_TURBINE_FUELS, GTLRecipeTypes.ROCKET_ENGINE_FUELS)
            .generator(true)
            .tooltips(Component.translatable(
                    "gtceu.universal.tooltip.base_production_eut", 2 * GTValues.V[GTValues.ZPM]),
                    Component.translatable(
                            "gtceu.universal.tooltip.uses_per_hour_lubricant", 2000),
                    Component.literal(
                            "提供§f120mB/s§7的液态氧，并消耗§f双倍§7燃料以产生高达§f" + (2 * GTValues.V[GTValues.UV]) + "§7EU/t的功率。"),
                    Component.literal(
                            "再额外提供§f80mB/s§7的四氧化二氮，并消耗§f四倍§7燃料以产生高达§f" + (2 * GTValues.V[GTValues.UHV]) + "§7EU/t的功率。"))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(ChemicalEnergyDevourerMachine::recipeModifier, true)
            .appearanceBlock(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("BBBBBBB", "BBBBBBB", "BBPBPBB", "BBBBBBB", "BBPBPBB", "BBBBBBB", "BBBBBBB")
                    .aisle("BBBBBBB", "BDDDDDB", "BEHHHEB", "BGHEHGB", "BEHHHEB", "BDDDDDB", "BBBBBBB")
                    .aisle("BBBBBBB", "BDDDDDB", "BEHHHEB", "CGHEHGC", "BEHHHEB", "BDDDDDB", "BBBBBBB")
                    .aisle("BBBBBBB", "FDDDDDF", "BEHHHEB", "CGHEHGC", "BEHHHEB", "FDDDDDF", "BBIBIBB")
                    .aisle("BBBBBBB", "FDDDDDF", "BEHHHEB", "CGHEHGC", "BEHHHEB", "FDDDDDF", "BBIBIBB")
                    .aisle("BBBBBBB", "FDDDDDF", "BEHHHEB", "CGHEHGC", "BEHHHEB", "FDDDDDF", "BBIBIBB")
                    .aisle("BBBBBBB", "FDDDDDF", "BEHHHEB", "CGHEHGC", "BEHHHEB", "FDDDDDF", "BBIBIBB")
                    .aisle("BBBBBBB", "BDDDDDB", "BEHHHEB", "CGHEHGC", "BEHHHEB", "BDDDDDB", "BBIBIBB")
                    .aisle("BBBBBBB", "BDDDDDB", "BEHHHEB", "BGHEHGB", "BEHHHEB", "BDDDDDB", "BBBBBBB")
                    .aisle("AAAAAAA", "AAAAAAA", "AABBBAA", "AABSBAA", "AABBBAA", "AAAAAAA", "AAAAAAA")
                    .where("S", controller(blocks(definition.get())))
                    .where("A", blocks(GTBlocks.CASING_EXTREME_ENGINE_INTAKE.get()))
                    .where("B", blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get()))
                    .where("F", blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get())
                            .or(abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(4)))
                    .where("C", blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where("G", blocks(GCyMBlocks.ELECTROLYTIC_CELL.get()))
                    .where("D", blocks(GTBlocks.FIREBOX_TITANIUM.get()))
                    .where("E", blocks(GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX.get()))
                    .where("H", blocks(GTBlocks.CASING_TITANIUM_GEARBOX.get()))
                    .where("P", abilities(PartAbility.OUTPUT_ENERGY))
                    .where("I", abilities(PartAbility.MUFFLER))
                    .build())
            .recoveryItems(
                    () -> new ItemLike[] { GTItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get() })
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                    GTCEu.id("block/multiblock/generator/extreme_combustion_engine"), false)
            .register();

    public static MultiblockMachineDefinition registerMegaTurbine(String name, int tier, int value, GTRecipeType recipeType,
                                                                  Supplier<Block> casing, Supplier<Block> gear, ResourceLocation baseCasing,
                                                                  ResourceLocation overlayModel) {
        return REGISTRATE.multiblock(name, holder -> new MegaTurbineMachine(holder, tier, value))
                .rotationState(RotationState.ALL)
                .recipeType(recipeType)
                .generator(true)
                .tooltips(Component.translatable("tooltip.gtlcore.can_use_transformer_hatch"))
                .tooltips(Component.translatable("gtceu.universal.tooltip.base_production_eut", FormattingUtil.formatNumbers(GTValues.V[tier] * value)))
                .tooltips(Component.translatable("gtceu.multiblock.turbine.efficiency_tooltip", GTValues.VNF[tier]))
                .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
                .tooltipBuilder(GTLMachines.GTL_ADD)
                .recipeModifier(MegaTurbineMachine::recipeModifier)
                .appearanceBlock(casing)
                .pattern(definition -> FactoryBlockPattern.start()
                        .aisle("AAAAAAA", "AAAAAAA", "AAEAEAA", "AAAAAAA", "AAEAEAA", "AAAAAAA", "AAAAAAA")
                        .aisle("AAAAAAA", "BDDDDDB", "AAAAAAA", "AAAAAAA", "AAAAAAA", "BDDDDDB", "AAAAAAA")
                        .aisle("AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA")
                        .aisle("AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA")
                        .aisle("AAAAAAA", "BDDDDDB", "AAAAAAA", "AAAAAAA", "AAAAAAA", "BDDDDDB", "AAAMAAA")
                        .aisle("AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA")
                        .aisle("AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA")
                        .aisle("AAAAAAA", "BDDDDDB", "AAAAAAA", "AAAAAAA", "AAAAAAA", "BDDDDDB", "AAAAAAA")
                        .aisle("AAAAAAA", "AAAAAAA", "AAACAAA", "AACSCAA", "AAACAAA", "AAAAAAA", "AAAAAAA")
                        .where("S", Predicates.controller(Predicates.blocks(definition.get())))
                        .where("A", Predicates.blocks(casing.get())
                                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(8))
                                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(2)))
                        .where("B", GTLPredicates.RotorBlock())
                        .where("D", Predicates.blocks(gear.get()))
                        .where("C", Predicates.blocks(casing.get()).or(Predicates.blocks(GTLMachines.ROTOR_HATCH.getBlock()).setMaxGlobalLimited(1)))
                        .where("E", Predicates.abilities(PartAbility.OUTPUT_ENERGY).or(Predicates.abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY)))
                        .where("M", Predicates.abilities(PartAbility.MUFFLER))
                        .build())
                .workableCasingRenderer(baseCasing, overlayModel)
                .register();
    }

    public final static MultiblockMachineDefinition ROCKET_LARGE_TURBINE = GTMachines.registerLargeTurbine("rocket_large_turbine", GTValues.EV,
            GTLRecipeTypes.ROCKET_ENGINE_FUELS,
            GTBlocks.CASING_TITANIUM_TURBINE, GTBlocks.CASING_TITANIUM_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_titanium"),
            GTCEu.id("block/multiblock/generator/large_gas_turbine"));

    public final static MultiblockMachineDefinition SUPERCRITICAL_STEAM_TURBINE = GTMachines.registerLargeTurbine("supercritical_steam_turbine", GTValues.IV,
            GTLRecipeTypes.SUPERCRITICAL_STEAM_TURBINE_FUELS,
            GTLBlocks.CASING_SUPERCRITICAL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTLCore.id("block/supercritical_turbine_casing"),
            GTCEu.id("block/multiblock/generator/large_plasma_turbine"));

    public final static MultiblockMachineDefinition STEAM_MEGA_TURBINE = registerMegaTurbine("steam_mega_turbine", GTValues.EV, 32, GTRecipeTypes.STEAM_TURBINE_FUELS, GTBlocks.CASING_STEEL_TURBINE, GTBlocks.CASING_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_steel"), GTCEu.id("block/multiblock/generator/large_steam_turbine"));
    public final static MultiblockMachineDefinition GAS_MEGA_TURBINE = registerMegaTurbine("gas_mega_turbine", GTValues.IV, 32, GTRecipeTypes.GAS_TURBINE_FUELS, GTBlocks.CASING_STAINLESS_TURBINE, GTBlocks.CASING_STAINLESS_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_stainless_steel"), GTCEu.id("block/multiblock/generator/large_gas_turbine"));
    public final static MultiblockMachineDefinition ROCKET_MEGA_TURBINE = registerMegaTurbine("rocket_mega_turbine", GTValues.IV, 48, GTLRecipeTypes.ROCKET_ENGINE_FUELS, GTBlocks.CASING_TITANIUM_TURBINE, GTBlocks.CASING_STAINLESS_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_titanium"), GTCEu.id("block/multiblock/generator/large_gas_turbine"));
    public final static MultiblockMachineDefinition PLASMA_MEGA_TURBINE = registerMegaTurbine("plasma_mega_turbine", GTValues.LuV, 64, GTRecipeTypes.PLASMA_GENERATOR_FUELS, GTBlocks.CASING_TUNGSTENSTEEL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_tungstensteel"), GTCEu.id("block/multiblock/generator/large_plasma_turbine"));
    public final static MultiblockMachineDefinition SUPERCRITICAL_MEGA_STEAM_TURBINE = registerMegaTurbine("supercritical_mega_steam_turbine", GTValues.LuV, 128, GTLRecipeTypes.SUPERCRITICAL_STEAM_TURBINE_FUELS, GTLBlocks.CASING_SUPERCRITICAL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTLCore.id("block/supercritical_turbine_casing"), GTCEu.id("block/multiblock/generator/large_plasma_turbine"));

    public final static MultiblockMachineDefinition DYSON_SPHERE = REGISTRATE.multiblock("dyson_sphere", DysonSphereMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.DYSON_SPHERE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.8"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.7"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.9"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.6"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.dyson_sphere")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier((machine, recipe, params, result) -> DysonSphereMachine.recipeModifier(machine, recipe))
            .appearanceBlock(() -> Registries.getBlock("kubejs:dyson_receiver_casing"))
            .pattern((definition) -> FactoryBlockPattern.start()
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B            B     JJJJJJJJJ   ", "B            B     JJJJJJJJJ   ", "B            B     JJJJJJJJJ   ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B CCCCCCCCCC B     JJJJJJJJJ   ", "B CCCCCCCCCC B     JJJJJJJJJ   ", "B CCCCCCCCCC B     JJJNNNJJJ   ", "BBBBBBBBBBBBBB        NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                      NNN      ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C        C B     JJJJJJJJJ   ", "B C        C B     JJJPQPJJJ   ", "B C        C B     JJNPQPNJJ   ", "BBBBBBBBBBBBBB       NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "                      RPR      ", "                      RPR      ", "                      RPR      ", "                      RPR      ", "                      RPR      ", "                      RPR      ", "                      RRR      ", "                       R       ", "                       R       ", "                       R       ", "                       R       ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C        C B     JJJJJJJJJ   ", "B C        C B     JJJQTQJJJ   ", "B C        C B     JJNQ QNJJ   ", "BBBBBBBBBBBBBB       NQ QN     ", "    DDDDD            NQ QN     ", "                     NQ QN     ", "                     NQ QN     ", "    EEEEE            NQ QN     ", "                     NQ QN     ", "    EEEEE            NQ QN     ", "                     NQ QN     ", "    EEEEE            NQ QN     ", "                     NQ QN     ", "    EEEEE            NQ QN     ", "                      P P      ", "                      P P      ", "                      P P      ", "    EEEEE             P P      ", "    EEEEE             P P      ", "    EEEEE             P P      ", "    EEEEE             R R      ", "                      R R      ", "                      R R      ", "                      R R      ", "                      R R      ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C FFFFF  C B     JJJJJJJJJ   ", "B C FFFFF  C B     JJJPQPJJJ   ", "B C FFFFF  C B     JJNPQPNJJ   ", "BBBBBBBBBBBBBB       NPQPN     ", "   DDDDDDD           NPQPN     ", "                     NPQPN     ", "                     NPQPN     ", "   E  D  E           NPQPN     ", "                     NPQPN     ", "   E  D  E           NPQPN     ", "                     NPQPN     ", "   E  D  E           NPQPN     ", "                     NPQPN     ", "   E  D  E           NPQPN     ", "                      RPR      ", "                      RPR      ", "    EEEEE             RPR      ", "   EEEEEEE            RPR      ", "   EEEEEEE            RPR      ", "   EEEEEEE            RPR      ", "   EEEEEEE            RRR      ", "    EEEEE              R       ", "                       R       ", "                       R       ", "                       R       ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C F   F  C B     JJJJJJJJJ   ", "B C F   F  C B     JJJJJJJJJ   ", "B C F   F  C B     JJJNNNJJJ   ", "BBBBBDDDBBBBBB        NNN      ", "   DD D DD            NNN      ", "      D               NNN      ", "      D               NNN      ", "   E DDD E            NNN      ", "      D               NNN      ", "   E DDD E            NNN      ", "      D               NNN      ", "   E DDD E            NNN      ", "      D               NNN      ", "   E DDD E            NNN      ", "      D                        ", "      D                        ", "    EEDEE                      ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "    EEEEE                      ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C F G F  C B     JJJJJJJJJ   ", "B C F G F  C B     JJJJJJJJJ   ", "B C F G F  C B     JJJJJJJJJ   ", "BBBBBDGDBBBBBB                 ", "   DDDGDDD                     ", "     DGD                       ", "     DGD                       ", "   EDDGDDE                     ", "     DGD                       ", "   EDDGDDE                     ", "     DGD                       ", "   EDDGDDE                     ", "     DGD                       ", "   EDDGDDE                     ", "     DGD                       ", "     DGD                       ", "    EDGDE                      ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "    EEEEE                      ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB     JJJJJJJJJ   ", "B C F   F  C B     JJJJJJJJJ   ", "B C F   F  C B     JJJJJJJJJ   ", "B C F   F  C B     JJJJJJJJJ   ", "BBBBBDDDBBBBBB                 ", "   DD D DD                     ", "      D                        ", "      D                        ", "   E DDD E                     ", "      D                        ", "   E DDD E                     ", "      D                        ", "   E DDD E                     ", "      D                        ", "   E DDD E                     ", "      D                        ", "      D                        ", "    EEDEE                      ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "    EEEEE                      ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "B C FFFFF  C B                 ", "B C FFFFF  C B                 ", "B C FFFFF  C B                 ", "BBBBBBBBBBBBBB                 ", "   DDDDDDD                     ", "                               ", "                               ", "   E  D  E                     ", "                               ", "   E  D  E                     ", "                               ", "   E  D  E                     ", "                               ", "   E  D  E                     ", "                               ", "                               ", "    EEEEE                      ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "   EEEEEEE                     ", "    EEEEE                      ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "B C        C B                 ", "B C        C B                 ", "B C        C B                 ", "BBBBBBBBBBBBBB                 ", "    DDDDD                      ", "                               ", "                               ", "    EEEEE                      ", "                               ", "    EEEEE                      ", "                               ", "    EEEEE                      ", "                               ", "    EEEEE                      ", "                               ", "                               ", "                               ", "    EEEEE                      ", "    EEEEE                      ", "    EEEEE                      ", "    EEEEE                      ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "B C        C B                 ", "B C        C B                 ", "B C        C B                 ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "B CCCCCCCCCC B                 ", "B CCCCCCCCCC B                 ", "B CCCCCCCCCC B                 ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "B            B                 ", "B            B                 ", "B            B                 ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBB                 ", "BBBBBBBBBBBBBB                 ", "BBBBBBBBBBBBBB                 ", "BBBBBBBBBBBBBB                 ", "BBBBBBBBBBBBBB                 ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                  HHHHHHHHH    ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                   HHHHHHH     ", "                 HHHHHHHHHHH   ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                    HHHHH      ", "                  HHHHHHHHH    ", "                HH         HH  ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                    HHHHH      ", "                   HHHHHHH     ", "                 HH       HH   ", "               HH           HH ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                               ", "                               ", "                               ", "                               ", "                               ", "                   HHHHHHH     ", "                  HH     HH    ", "                HH         HH  ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IKKKKKKKI    ", "                  IKKKKKKKI    ", "                  IKKKKKKKI    ", "                  IIIIIIIII    ", "                    L   L      ", "                    L   L      ", "                    L   L      ", "                    L   L      ", "                    LHHHL      ", "                  HHHHHHHHH    ", "                 HH       HH   ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IK     KI    ", "                  IK     KI    ", "                  IK     KI    ", "                  IIIIIIIII    ", "                   L     L     ", "                   L     L     ", "                   L     L     ", "                   L     L     ", "                   LHHHHHL     ", "                 HHHH   HHHH   ", "                HH         HH  ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IK  M  KI    ", "                  IK  M  KI    ", "                  IK  M  KI    ", "                  IIIIIIIII    ", "                     L L       ", "                     L L       ", "                     L L       ", "                     L L       ", "                   HHHHHHH     ", "                 HHH     HHH   ", "                HH         HH  ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IK MIM KI    ", "                  IK MIM KI    ", "                  IK MIM KI    ", "                  IIIIIIIII    ", "                      I        ", "                      I        ", "                      I        ", "                      I        ", "                   HHHIHHH     ", "                 HHH  I  HHH   ", "                HH    I    HH  ", "               HH     I     HH ", "              HH      I      HH", "                      I        ", "                      S        ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IK  M  KI    ", "                  IK  M  KI    ", "                  IK  M  KI    ", "                  IIIIIIIII    ", "                     L L       ", "                     L L       ", "                     L L       ", "                     L L       ", "                   HHHHHHH     ", "                 HHH     HHH   ", "                HH         HH  ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IK     KI    ", "                  IK     KI    ", "                  IK     KI    ", "                  IIIIIIIII    ", "                   L     L     ", "                   L     L     ", "                   L     L     ", "                   L     L     ", "                   LHHHHHL     ", "                 HHHH   HHHH   ", "                HH         HH  ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIIIIII    ", "                  IKKKKKKKI    ", "                  IKKKKKKKI    ", "                  IKKKKKKKI    ", "                  IIIIIIIII    ", "                    L   L      ", "                    L   L      ", "                    L   L      ", "                    L   L      ", "                    LHHHL      ", "                  HHHHHHHHH    ", "                 HH       HH   ", "               HH           HH ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                  IIIIOIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                  IIIIIIIII    ", "                               ", "                               ", "                               ", "                               ", "                               ", "                   HHHHHHH     ", "                  HH     HH    ", "                HH         HH  ", "              HH             HH", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                    HHHHH      ", "                   HHHHHHH     ", "                 HH       HH   ", "               HH           HH ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                    HHHHH      ", "                  HHHHHHHHH    ", "                HH         HH  ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                   HHHHHHH     ", "                 HHHHHHHHHHH   ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .aisle("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                  HHHHHHHHH    ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ", "                               ")
                    .where("O", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("I", Predicates.blocks(Registries.getBlock("kubejs:dyson_receiver_casing"))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where("A", Predicates.blocks(Registries.getBlock("kubejs:high_strength_concrete")))
                    .where("B", Predicates.blocks(Registries.getBlock("kubejs:dyson_control_casing")))
                    .where("C", Predicates.blocks(GCyMBlocks.HEAT_VENT.get()))
                    .where("D", Predicates.blocks(GTBlocks.CASING_PALLADIUM_SUBSTATION.get()))
                    .where("E", Predicates.blocks(Registries.getBlock("kubejs:dyson_control_toroid")))
                    .where("F", Predicates.blocks(Registries.getBlock("kubejs:spacetime_assembly_line_unit")))
                    .where("G", Predicates.blocks(Registries.getBlock("kubejs:hollow_casing")))
                    .where("H", Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where("J", Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_casing")))
                    .where("K", Predicates.blocks(Registries.getBlock("kubejs:adamantine_coil_block")))
                    .where("L", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTLMaterials.Vibranium)))
                    .where("M", Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where("N", Predicates.blocks(GTLBlocks.ECHO_CASING.get()))
                    .where("P", Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_magnet")))
                    .where("Q", Predicates.blocks(GTLBlocks.POWER_MODULE_5.get()))
                    .where("R", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTLMaterials.Adamantium)))
                    .where("S", Predicates.blocks(Registries.getBlock("kubejs:containment_field_generator")))
                    .where("T", Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_core")))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(new ResourceLocation("kubejs:block/dyson_receiver_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition LARGE_NAQUADAH_REACTOR = REGISTRATE.multiblock("large_naquadah_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.LARGE_NAQUADAH_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.large_naquadah_reactor")))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifier((machine, recipe, params, result) -> GTLRecipeModifiers.standardOverclocking((WorkableElectricMultiblockMachine) machine, recipe))
            .appearanceBlock(GTLBlocks.HYPER_MECHANICAL_CASING)
            .pattern((definition) -> FactoryBlockPattern.start()
                    .aisle(" bbb ", " bdb ", " bbb ", "  b  ", "  b  ", " bbb ", " bdb ", " bbb ")
                    .aisle("bbbbb", "bc cb", "bf fb", " c c ", " c c ", "bf fb", "bc cb", " bbb ")
                    .aisle("bbbbb", "d e d", "b e b", "b e b", "b e b", "b e b", "d e d", " bbb ")
                    .aisle("bbbbb", "bc cb", "bf fb", " c c ", " c c ", "bf fb", "bc cb", " bbb ")
                    .aisle(" bbb ", " bab ", " bbb ", "  b  ", "  b  ", " bbb ", " bdb ", " bbb ")
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where("c", Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                    .where("d", Predicates.blocks(GCyMBlocks.HEAT_VENT.get()))
                    .where("e", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Naquadria)))
                    .where("f", Predicates.blocks(Registries.getBlock("kubejs:neutronium_gearbox")))
                    .where(" ", Predicates.any())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition ADVANCED_HYPER_REACTOR = REGISTRATE.multiblock("advanced_hyper_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.ADVANCED_HYPER_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.advanced_hyper_reactor.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.advanced_hyper_reactor.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.advanced_hyper_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifier((machine, recipe, params, result) -> {
                if (machine instanceof WorkableElectricMultiblockMachine workableElectricMultiblockMachine) {
                    int p = 1;
                    if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.Starmetal.getFluid(FluidStorageKeys.PLASMA, 1))) {
                        p = 8;
                    }
                    if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.DenseNeutron.getFluid(FluidStorageKeys.PLASMA, 1))) {
                        p = 16;
                    }
                    return GTLRecipeModifiers.standardOverclocking(workableElectricMultiblockMachine, GTRecipeModifiers.fastParallel(machine, recipe, p, false).getFirst());
                }
                return recipe;
            })
            .appearanceBlock(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING)
            .pattern((definition) -> FactoryBlockPattern.start()
                    .aisle("           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "    bbb    ", "   bbbbb   ", "   bbbbb   ", "   bbbbb   ", "    bbb    ", "           ", "           ", "           ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "           ", "           ", "   bbbbb   ", "  bb---bb  ", "  b-----b  ", "  b-----b  ", "  b-----b  ", "  bb---bb  ", "   bbbbb   ", "           ", "           ")
                    .aisle("  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  ebbbbbe  ", "  b-----b  ", " b-------b ", " b-------b ", " b-------b ", " b-------b ", " b-------b ", "  b-----b  ", "   bbbbb   ", "           ")
                    .aisle("   e   e   ", "   e   e   ", "   e   e   ", "   e   e   ", "   e   e   ", "   ebbbe   ", "  bb---bb  ", " b-------b ", " b-------b ", "b---------b", "b----c----b", "b---------b", " b-------b ", " b-------b ", "  bb---bb  ", "    bbb    ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "   bbbbb   ", "  b-----b  ", " b-------b ", "b---------b", "b----c----b", "b---ccc---b", "b----c----b", "b---------b", " b-------b ", "  b-----b  ", "   bbbbb   ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "   bbbbb   ", "  b-----b  ", " b-------b ", "b----c----b", "b---ccc---b", "b--ccccc--b", "b---ccc---b", "b----c----b", " b-------b ", "  b-----b  ", "   bbbbb   ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "   bbbbb   ", "  b-----b  ", " b-------b ", "b---------b", "b----c----b", "b---ccc---b", "b----c----b", "b---------b", " b-------b ", "  b-----b  ", "   bbbbb   ")
                    .aisle("   e   e   ", "   e   e   ", "   e   e   ", "   e   e   ", "   e   e   ", "   ebbbe   ", "  bb---bb  ", " b-------b ", " b-------b ", "b---------b", "b----c----b", "b---------b", " b-------b ", " b-------b ", "  bb---bb  ", "    bbb    ")
                    .aisle("  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  e     e  ", "  ebbbbbe  ", "  b-----b  ", " b-------b ", " b-------b ", " b-------b ", " b-------b ", " b-------b ", "  b-----b  ", "   bbbbb   ", "           ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "           ", "           ", "   bbbbb   ", "  bb---bb  ", "  b-----b  ", "  b-----b  ", "  b-----b  ", "  bb---bb  ", "   bbbbb   ", "           ", "           ")
                    .aisle("           ", "           ", "           ", "           ", "           ", "           ", "           ", "           ", "    bbb    ", "   bbbbb   ", "   bbabb   ", "   bbbbb   ", "    bbb    ", "           ", "           ", "           ")
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where("c", Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where("e", Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Naquadria)))
                    .where(" ", Predicates.any())
                    .where("-", Predicates.air())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/enhance_hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MultiblockMachineDefinition HYPER_REACTOR = REGISTRATE.multiblock("hyper_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.HYPER_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.hyper_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifier((machine, recipe, params, result) -> {
                if (machine instanceof WorkableElectricMultiblockMachine workableElectricMultiblockMachine) {
                    int p = 1;
                    long outputEUt = RecipeHelper.getOutputEUt(recipe);
                    if (outputEUt == GTValues.V[GTValues.UEV]) {
                        if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.Orichalcum.getFluid(FluidStorageKeys.PLASMA, 1))) {
                            p = 16;
                        }
                    } else if (outputEUt == GTValues.V[GTValues.UIV]) {
                        if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.Enderium.getFluid(FluidStorageKeys.PLASMA, 1))) {
                            p = 16;
                        }
                    } else if (outputEUt == GTValues.V[GTValues.UXV]) {
                        if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.Infuscolium.getFluid(FluidStorageKeys.PLASMA, 1))) {
                            p = 16;
                        }
                    } else if (outputEUt == GTValues.V[GTValues.OpV]) {
                        if (MachineIO.inputFluid(workableElectricMultiblockMachine, GTLMaterials.MetastableHassium.getFluid(FluidStorageKeys.PLASMA, 1))) {
                            p = 16;
                        }
                    }
                    return GTLRecipeModifiers.standardOverclocking(workableElectricMultiblockMachine, GTRecipeModifiers.fastParallel(machine, recipe, p, false).getFirst());
                }
                return recipe;
            })
            .appearanceBlock(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING)
            .pattern((definition) -> FactoryBlockPattern.start()
                    .aisle("bbbbb", "bdddb", "bdddb", "bdddb", "bbbbb")
                    .aisle("bbbbb", "d   d", "d c d", "d   d", "bbbbb")
                    .aisle("bbbbb", "d c d", "dcccd", "d c d", "bbbbb")
                    .aisle("bbbbb", "d   d", "d c d", "d   d", "bbbbb")
                    .aisle("bbabb", "bdddb", "bdddb", "bdddb", "bbbbb")
                    .where("a", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("b", Predicates.blocks(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where("c", Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where("d", Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                    .where(" ", Predicates.air())
                    .build())
            .workableCasingRenderer(GTLCore.id("block/casings/enhance_hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public static final MultiblockMachineDefinition GENERATOR_ARRAY = REGISTRATE.multiblock("generator_array", GeneratorArrayMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .generator(true)
            .tooltips(Component.translatable("gtceu.machine.generator_array.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.generator_array.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_6.tooltip",
                    Component.translatable("gtceu.steam_turbine"),
                    Component.translatable("gtceu.combustion_generator"),
                    Component.translatable("gtceu.gas_turbine"),
                    Component.translatable("gtceu.semi_fluid_generator"),
                    Component.translatable("gtceu.rocket_engine"),
                    Component.translatable("gtceu.naquadah_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(GeneratorArrayMachine::recipeModifier, true)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("XXX", "CCC", "XXX")
                    .aisle("XXX", "C#C", "XXX")
                    .aisle("XSX", "CCC", "XXX")
                    .where('S', Predicates.controller(blocks(definition.getBlock())))
                    .where('X', blocks(GTBlocks.CASING_STEEL_SOLID.get())
                            .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1)))
                    .where('C', blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                    .where('#', Predicates.air())
                    .build())
            .workableCasingRenderer(GTCEu.id("block/casings/solid/machine_casing_solid_steel"), GTCEu.id("block/multiblock/processing_array"))
            .register();

    public final static MultiblockMachineDefinition ANNIHILATE_GENERATOR = REGISTRATE.multiblock("annihilate_generator", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.ANNIHILATE_GENERATOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.annihilate_generator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifier((machine, recipe, params, result) -> GTLRecipeModifiers.standardOverclocking((WorkableElectricMultiblockMachine) machine, recipe))
            .appearanceBlock(GTBlocks.HIGH_POWER_CASING)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle(AnnihilateGeneratorB.A_1)
                    .aisle(AnnihilateGeneratorB.A_2)
                    .aisle(AnnihilateGeneratorB.A_3)
                    .aisle(AnnihilateGeneratorB.A_4)
                    .aisle(AnnihilateGeneratorB.A_5)
                    .aisle(AnnihilateGeneratorB.A_6)
                    .aisle(AnnihilateGeneratorB.A_7)
                    .aisle(AnnihilateGeneratorB.A_8)
                    .aisle(AnnihilateGeneratorB.A_9)
                    .aisle(AnnihilateGeneratorB.A_10)
                    .aisle(AnnihilateGeneratorB.A_11)
                    .aisle(AnnihilateGeneratorB.A_12)
                    .aisle(AnnihilateGeneratorB.A_13)
                    .aisle(AnnihilateGeneratorB.A_14)
                    .aisle(AnnihilateGeneratorB.A_15)
                    .aisle(AnnihilateGeneratorB.A_16)
                    .aisle(AnnihilateGeneratorB.A_17)
                    .aisle(AnnihilateGeneratorB.A_18)
                    .aisle(AnnihilateGeneratorB.A_19)
                    .aisle(AnnihilateGeneratorB.A_20)
                    .aisle(AnnihilateGeneratorB.A_21)
                    .aisle(AnnihilateGeneratorB.A_22)
                    .aisle(AnnihilateGeneratorB.A_23)
                    .aisle(AnnihilateGeneratorB.A_24)
                    .aisle(AnnihilateGeneratorB.A_25)
                    .aisle(AnnihilateGeneratorB.A_26)
                    .aisle(AnnihilateGeneratorB.A_27)
                    .aisle(AnnihilateGeneratorB.A_28)
                    .aisle(AnnihilateGeneratorB.A_29)
                    .aisle(AnnihilateGeneratorB.A_30)
                    .aisle(AnnihilateGeneratorB.A_31)
                    .aisle(AnnihilateGeneratorB.A_32)
                    .aisle(AnnihilateGeneratorB.A_33)
                    .aisle(AnnihilateGeneratorB.A_34)
                    .aisle(AnnihilateGeneratorB.A_35)
                    .aisle(AnnihilateGeneratorB.A_36)
                    .aisle(AnnihilateGeneratorB.A_37)
                    .aisle(AnnihilateGeneratorB.A_38)
                    .aisle(AnnihilateGeneratorB.A_39)
                    .aisle(AnnihilateGeneratorB.A_40)
                    .aisle(AnnihilateGeneratorB.A_41)
                    .aisle(AnnihilateGeneratorB.A_42)
                    .aisle(AnnihilateGeneratorB.A_43)
                    .aisle(AnnihilateGeneratorB.A_44)
                    .aisle(AnnihilateGeneratorB.A_45)
                    .aisle(AnnihilateGeneratorB.A_46)
                    .aisle(AnnihilateGeneratorB.A_47)
                    .aisle(AnnihilateGeneratorB.A_48)
                    .aisle(AnnihilateGeneratorB.A_49)
                    .aisle(AnnihilateGeneratorB.A_50)
                    .aisle(AnnihilateGeneratorB.A_51)
                    .aisle(AnnihilateGeneratorB.A_52)
                    .aisle(AnnihilateGeneratorB.A_53)
                    .aisle(AnnihilateGeneratorA.A_54)
                    .aisle(AnnihilateGeneratorA.A_55)
                    .aisle(AnnihilateGeneratorA.A_56)
                    .aisle(AnnihilateGeneratorA.A_57)
                    .aisle(AnnihilateGeneratorA.A_58)
                    .aisle(AnnihilateGeneratorA.A_59)
                    .aisle(AnnihilateGeneratorA.A_60)
                    .aisle(AnnihilateGeneratorA.A_61)
                    .aisle(AnnihilateGeneratorA.A_62)
                    .aisle(AnnihilateGeneratorA.A_63)
                    .aisle(AnnihilateGeneratorA.A_64)
                    .aisle(AnnihilateGeneratorA.A_65)
                    .aisle(AnnihilateGeneratorA.A_66)
                    .aisle(AnnihilateGeneratorA.A_67)
                    .aisle(AnnihilateGeneratorA.A_68)
                    .aisle(AnnihilateGeneratorA.A_69)
                    .aisle(AnnihilateGeneratorA.A_70)
                    .aisle(AnnihilateGeneratorA.A_71)
                    .aisle(AnnihilateGeneratorA.A_72)
                    .aisle(AnnihilateGeneratorA.A_73)
                    .aisle(AnnihilateGeneratorA.A_74)
                    .aisle(AnnihilateGeneratorA.A_75)
                    .aisle(AnnihilateGeneratorA.A_76)
                    .aisle(AnnihilateGeneratorA.A_77)
                    .aisle(AnnihilateGeneratorA.A_78)
                    .aisle(AnnihilateGeneratorA.A_79)
                    .aisle(AnnihilateGeneratorA.A_80)
                    .aisle(AnnihilateGeneratorA.A_81)
                    .aisle(AnnihilateGeneratorA.A_82)
                    .aisle(AnnihilateGeneratorA.A_83)
                    .aisle(AnnihilateGeneratorA.A_84)
                    .aisle(AnnihilateGeneratorA.A_85)
                    .aisle(AnnihilateGeneratorA.A_86)
                    .aisle(AnnihilateGeneratorA.A_87)
                    .aisle(AnnihilateGeneratorA.A_88)
                    .aisle(AnnihilateGeneratorA.A_89)
                    .aisle(AnnihilateGeneratorA.A_90)
                    .aisle(AnnihilateGeneratorA.A_91)
                    .aisle(AnnihilateGeneratorA.A_92)
                    .aisle(AnnihilateGeneratorA.A_93)
                    .aisle(AnnihilateGeneratorA.A_94)
                    .aisle(AnnihilateGeneratorA.A_95)
                    .aisle(AnnihilateGeneratorA.A_96)
                    .aisle(AnnihilateGeneratorA.A_97)
                    .aisle(AnnihilateGeneratorA.A_98)
                    .aisle(AnnihilateGeneratorA.A_99)
                    .aisle(AnnihilateGeneratorA.A_100)
                    .aisle(AnnihilateGeneratorA.A_101)
                    .aisle(AnnihilateGeneratorA.A_102)
                    .aisle(AnnihilateGeneratorA.A_103)
                    .aisle(AnnihilateGeneratorA.A_104)
                    .aisle(AnnihilateGeneratorA.A_105)
                    .aisle(AnnihilateGeneratorA.A_106)
                    .aisle(AnnihilateGeneratorA.A_107)
                    .aisle(AnnihilateGeneratorA.A_108)
                    .aisle(AnnihilateGeneratorA.A_109)
                    .where("~", Predicates.controller(Predicates.blocks(definition.get())))
                    .where("A", Predicates.blocks(GTLBlocks.GRAVITON_FIELD_CONSTRAINT_CASING.get()))
                    .where("B", Predicates.blocks(Registries.getBlock("kubejs:annihilate_core")))
                    .where("C", Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where("D", Predicates.blocks(Registries.getBlock("kubejs:hollow_casing")))
                    .where("E", Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get()))
                    .where("F", Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                    .where("G", Predicates.blocks(Registries.getBlock("kubejs:dyson_control_toroid")))
                    .where("H", Predicates.blocks(GTLBlocks.RHENIUM_REINFORCED_ENERGY_GLASS.get()))
                    .where("P", Predicates.blocks(Registries.getBlock("kubejs:dyson_control_casing")))
                    .where("S", Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get())
                            .or(Predicates.abilities(PartAbility.OUTPUT_LASER))
                            .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .or(Predicates.abilities(PartAbility.EXPORT_ITEMS)))
                    .where("T", Predicates.blocks(GTLBlocks.DEGENERATE_RHENIUM_CONSTRAINED_CASING.get()))
                    .where("R", Predicates.blocks(Registries.getBlock("kubejs:dyson_receiver_casing")))
                    .where(" ", Predicates.any())
                    .build())
            .renderer(AnnihilateGeneratorRenderer::new)
            .hasTESR(true)
            .register();
}
