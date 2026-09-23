package org.gtlcore.test.mixin;

import org.gtlcore.test.LegacyCalls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public class LegacyExecutorProbe {
    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void observe(CallbackInfoReturnable<Integer> cir) { LegacyCalls.executor.incrementAndGet(); }
}
