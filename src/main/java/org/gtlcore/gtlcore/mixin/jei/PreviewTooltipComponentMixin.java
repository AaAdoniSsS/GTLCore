package org.gtlcore.gtlcore.mixin.jei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.PreviewTooltipComponent", remap = false)
public abstract class PreviewTooltipComponentMixin {

    @Inject(method = "updateTransferError", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$hideTransferError(CallbackInfo ci) {
        ci.cancel();
    }
}
