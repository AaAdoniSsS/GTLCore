package org.gtlcore.gtlcore.mixin.gtlcore.machine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualInputHatchPartMachine;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEDualInputHatchPartMachine.class)
public interface MEDualInputHatchPartMachineAccessor {

    @Accessor(value = "aeFluidHandler", remap = false)
    ExportOnlyAEFluidList gtlcore$getAeFluidHandler();
}
