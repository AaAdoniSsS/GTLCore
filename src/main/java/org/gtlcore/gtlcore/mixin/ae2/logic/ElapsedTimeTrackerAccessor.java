package org.gtlcore.gtlcore.mixin.ae2.logic;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ElapsedTimeTracker.class)
public interface ElapsedTimeTrackerAccessor {

    @Invoker(remap = false)
    void invokeAddMaxItems(long itemDiff, AEKeyType keyType);

    @Invoker(remap = false)
    void invokeDecrementItems(long itemDiff, AEKeyType keyType);
}
