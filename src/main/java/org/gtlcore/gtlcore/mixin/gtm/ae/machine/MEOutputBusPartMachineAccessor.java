package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEOutputBusPartMachine.class)
public interface MEOutputBusPartMachineAccessor {

    @Accessor(value = "internalBuffer", remap = false)
    KeyStorage gtlcore$getInternalBuffer();
}
