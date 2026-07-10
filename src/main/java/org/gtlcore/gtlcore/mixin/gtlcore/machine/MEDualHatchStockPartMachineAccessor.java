package org.gtlcore.gtlcore.mixin.gtlcore.machine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEDualHatchStockPartMachine.class)
public interface MEDualHatchStockPartMachineAccessor {

    @Accessor(value = "aeItemHandler", remap = false)
    ExportOnlyAEItemList gtlcore$getAeItemHandler();

    @Accessor(value = "aeFluidHandler", remap = false)
    ExportOnlyAEFluidList gtlcore$getAeFluidHandler();
}
