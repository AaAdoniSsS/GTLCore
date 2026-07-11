package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEInputHatchPartMachine.class)
public interface MEInputHatchPartMachineAccessor {

    @Accessor(value = "aeFluidHandler", remap = false)
    ExportOnlyAEFluidList gtlcore$getAeFluidHandler();
}
