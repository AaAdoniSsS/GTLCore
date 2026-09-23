package org.gtlcore.test.mixin;

import org.gtlcore.test.LegacyCalls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.crafting.CraftingCalculation", remap = false)
public class LegacyPlannerProbe {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void observe(CallbackInfo ci) { LegacyCalls.planner.incrementAndGet(); }
}
