package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuantumChestMachine.class)
public interface QuantumChestMachineAccessor {

    @Accessor(value = "itemsStoredInside", remap = false)
    int gtlcore$getItemsStoredInside();

    @Accessor(value = "itemsStoredInside", remap = false)
    void gtlcore$setItemsStoredInside(int amount);

    @Accessor(value = "storedAmount", remap = false)
    int gtlcore$getStoredAmount();

    @Accessor(value = "storedAmount", remap = false)
    void gtlcore$setStoredAmount(int amount);

    @Accessor(value = "stored", remap = false)
    void gtlcore$setStored(ItemStack stack);
}
