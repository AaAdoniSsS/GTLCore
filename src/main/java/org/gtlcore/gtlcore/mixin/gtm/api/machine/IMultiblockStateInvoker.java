package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiblockState.class)
public interface IMultiblockStateInvoker {

    @Invoker(remap = false, value = "update")
    boolean updateState(BlockPos posIn, TraceabilityPredicate predicate);

    @Invoker(remap = false, value = "clean")
    void cleanState();
}
