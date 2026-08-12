package org.gtlcore.gtlcore.api.machine.trait;

import net.minecraft.world.item.ItemStack;

public interface IDirectItemStackTransfer {

    void gtlcore$setStackWithoutNotify(int slot, ItemStack stack);
}
