package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.common.data.GTLMachines;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;

import net.minecraft.network.chat.Component;

public class TootipsModify {

    public static void init() {
        MachineDefinition hpca_computation_component = GTResearchMachines.HPCA_COMPUTATION_COMPONENT;
        hpca_computation_component.setTooltipBuilder(hpca_computation_component.getTooltipBuilder().andThen((itemStack, components) -> components.set(3, Component.translatable("gtceu.machine.hpca.component_type.computation_cwut", 8))));
        hpca_computation_component.setTooltipBuilder(hpca_computation_component.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition hpca_advanced_computation_component = GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT;
        hpca_advanced_computation_component.setTooltipBuilder(hpca_advanced_computation_component.getTooltipBuilder().andThen((itemStack, components) -> components.set(3, Component.translatable("gtceu.machine.hpca.component_type.computation_cwut", 32))));
        hpca_advanced_computation_component.setTooltipBuilder(hpca_advanced_computation_component.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition electric_blast_furnace = GTMachines.ELECTRIC_BLAST_FURNACE;
        electric_blast_furnace.setTooltipBuilder(electric_blast_furnace.getTooltipBuilder().andThen((itemStack, components) -> {
            components.add(1, Component.translatable("gtceu.machine.electric_blast_furnace.tooltip.a"));
            components.set(3, Component.translatable("gtceu.machine.perfect_oc"));
        }));
        electric_blast_furnace.setTooltipBuilder(electric_blast_furnace.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition steamExportBus = GTMachines.STEAM_EXPORT_BUS;
        steamExportBus.setTooltipBuilder(steamExportBus.getTooltipBuilder().andThen((itemStack, components) -> {
            components.add(1, Component.translatable("gtceu.machine.item_bus.export.tooltip"));
            components.add(2, Component.translatable("gtceu.universal.tooltip.item_storage_capacity", 16));
        }));
        steamExportBus.setTooltipBuilder(steamExportBus.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition steamImportBus = GTMachines.STEAM_IMPORT_BUS;
        steamImportBus.setTooltipBuilder(steamImportBus.getTooltipBuilder().andThen((itemStack, components) -> {
            components.add(1, Component.translatable("gtceu.machine.item_bus.import.tooltip"));
            components.add(2, Component.translatable("gtceu.universal.tooltip.item_storage_capacity", 16));
        }));
        steamImportBus.setTooltipBuilder(steamImportBus.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition steamOven = GTMachines.STEAM_OVEN;
        steamOven.setTooltipBuilder(steamOven.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition bronzeLargeBoiler = GTMachines.LARGE_BOILER_BRONZE;
        bronzeLargeBoiler.setTooltipBuilder(bronzeLargeBoiler.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition steelBoiler = GTMachines.LARGE_BOILER_STEEL;
        steelBoiler.setTooltipBuilder(steelBoiler.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition titaniumBoiler = GTMachines.LARGE_BOILER_TITANIUM;
        titaniumBoiler.setTooltipBuilder(titaniumBoiler.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition tungstensteelLargeBoiler = GTMachines.LARGE_BOILER_TUNGSTENSTEEL;
        tungstensteelLargeBoiler.setTooltipBuilder(tungstensteelLargeBoiler.getTooltipBuilder().andThen(GTLMachines.GTL_MODIFY));

        MachineDefinition largeChemicalReactor = GTMachines.LARGE_CHEMICAL_REACTOR;
        largeChemicalReactor.setTooltipBuilder(largeChemicalReactor.getTooltipBuilder().andThen((itemStack, components) -> components.add(1, Component.translatable("gtceu.machine.perfect_oc"))));

        MachineDefinition researchStation = GTResearchMachines.RESEARCH_STATION;
        researchStation.setTooltipBuilder(researchStation.getTooltipBuilder().andThen((itemStack, components) -> {
            components.add(1, Component.translatable("gtceu.machine.research_station.tooltip.0"));
            components.add(2, Component.translatable("gtceu.machine.research_station.tooltip.1"));
            components.add(3, Component.translatable("gtceu.machine.research_station.tooltip.2"));
        }));
    }
}
