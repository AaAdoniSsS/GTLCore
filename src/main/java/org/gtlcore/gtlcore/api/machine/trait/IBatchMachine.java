package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.gui.GuiTextures;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface IBatchMachine {

    boolean isBatchEnabled();

    void setBatchEnabled(boolean enabled);

    default boolean supportsBatchProcessing() {
        return true;
    }

    default boolean canConfigureBatchProcessing() {
        return supportsBatchProcessing();
    }

    static void attachBatchConfigurator(ConfiguratorPanel configuratorPanel,
                                        WorkableElectricMultiblockMachine machine) {
        if (!((Object) machine instanceof IBatchMachine batchMachine) ||
                !batchMachine.canConfigureBatchProcessing())
            return;

        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                GuiTextures.BATCH_PROCESSING_DISABLED,
                GuiTextures.BATCH_PROCESSING_ENABLED,
                () -> batchMachine.supportsBatchProcessing() && batchMachine.isBatchEnabled(),
                (clickData, pressed) -> batchMachine.setBatchEnabled(pressed))
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(
                        !batchMachine.supportsBatchProcessing() ? "gui.gtlcore.batch_processing.unsupported_mode" :
                                pressed ? "gui.gtlcore.batch_processing.enabled" :
                                        "gui.gtlcore.batch_processing.disabled"))));
    }
}
