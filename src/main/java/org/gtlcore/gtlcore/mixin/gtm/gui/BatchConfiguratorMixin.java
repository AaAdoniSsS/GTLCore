package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FancyMachineUIWidget.class, remap = false)
public abstract class BatchConfiguratorMixin {

    @Shadow
    @Final
    protected ConfiguratorPanel configuratorPanel;

    @Inject(
            method = "setupFancyUI(Lcom/gregtechceu/gtceu/api/gui/fancy/IFancyUIProvider;Z)V",
            at = @At(
                     value = "INVOKE",
                     target = "Lcom/gregtechceu/gtceu/api/gui/fancy/IFancyUIProvider;attachConfigurators(Lcom/gregtechceu/gtceu/api/gui/fancy/ConfiguratorPanel;)V",
                     shift = At.Shift.AFTER))
    private void gtlcore$attachBatchConfigurator(IFancyUIProvider page, boolean hasPlayerInventory,
                                                 CallbackInfo ci) {
        if (page instanceof WorkableElectricMultiblockMachine machine) {
            IBatchMachine.attachBatchConfigurator(configuratorPanel, machine);
        }
    }
}
