package org.gtlcore.test.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import net.minecraft.core.Direction;
import org.gtlcore.test.GraphParallelProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only in the isolated test mod; observes real assembler accepts without changing behavior. */
@Mixin(value = MolecularAssemblerBlockEntity.class, remap = false)
public class AssemblerDispatchProbe {
    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void accepted(IPatternDetails pattern, KeyCounter[] inputs, Direction from, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) GraphParallelProbe.accepted(this, pattern);
    }
}
