package org.gtlcore.gtlcore.common.machine.trait;

import net.minecraft.world.item.ItemStack;

public interface QuantumChestLongStorage {

    ItemStack gtlcore$getStoredStack();

    long gtlcore$getStoredAmount();

    long gtlcore$getStorageCapacity();

    void gtlcore$setStoredAmount(long amount);

    void gtlcore$changeStoredAmount(long amount);

    void gtlcore$markStorageChanged();
}
