package org.gtlcore.test.mixin;

import org.gtlcore.test.LegacyCalls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor", remap = false)
public class MaxFastProbe {
    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private void observe(CallbackInfo ci) { LegacyCalls.maxFast.incrementAndGet(); }
}
