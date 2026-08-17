package org.gtlcore.gtlcore.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.common.config.ClientConfig", remap = false)
public abstract class ClientConfigMixin {

    // FTB Quests exposes filled buckets as item focuses, so JEI must also inspect their contained fluid.
    @Inject(method = "isLookupFluidContentsEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$enableFluidContainerRecipeLookup(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
