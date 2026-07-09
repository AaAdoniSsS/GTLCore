package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEInputBusPartMachine.class)
public interface MEInputBusPartMachineAccessor {

    @Accessor(value = "aeItemHandler", remap = false)
    ExportOnlyAEItemList gtlcore$getAeItemHandler();
}
