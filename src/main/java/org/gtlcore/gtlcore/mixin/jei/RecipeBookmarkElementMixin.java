package org.gtlcore.gtlcore.mixin.jei;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.elements.RecipeBookmarkElement", remap = false)
public abstract class RecipeBookmarkElementMixin {

    @Inject(method = "createTransferComponents", at = @At("RETURN"), remap = false)
    private void gtlcore$hideTransferHints(CallbackInfoReturnable<ITooltipBuilder> cir) {
        cir.getReturnValue().clear();
    }
}
