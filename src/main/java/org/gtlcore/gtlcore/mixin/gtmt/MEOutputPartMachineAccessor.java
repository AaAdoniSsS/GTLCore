package org.gtlcore.gtlcore.mixin.gtmt;

import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEOutputPartMachine.class)
public interface MEOutputPartMachineAccessor {

    @Accessor(value = "internalBuffer", remap = false)
    KeyStorage gtlcore$getInternalBuffer();

    @Accessor(value = "internalTankBuffer", remap = false)
    KeyStorage gtlcore$getInternalTankBuffer();
}
