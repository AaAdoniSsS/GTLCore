package org.gtlcore.gtlcore.mixin.ae2.crafting;

import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChildCraftingSimulationState.class)
public interface ChildCraftingSimulationStateAccessor {

    @Accessor(value = "parent", remap = false)
    ICraftingInventory gtlcore$getParent();
}
