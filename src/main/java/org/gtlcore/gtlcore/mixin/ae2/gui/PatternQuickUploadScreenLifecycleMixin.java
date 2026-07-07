package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.wireless.PatternQuickUploadSelectionOverlay;

import net.minecraft.client.gui.screens.Screen;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class PatternQuickUploadScreenLifecycleMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handlePatternQuickUploadSelectionKey(int keyCode, int scanCode, int modifiers,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$isPatternEncodingTermScreen() && PatternQuickUploadSelectionOverlay.keyPressed(keyCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void gtlcore$closePatternQuickUploadSelection(CallbackInfo ci) {
        if (gtlcore$isPatternEncodingTermScreen()) {
            PatternQuickUploadSelectionOverlay.close();
        }
    }

    @Unique
    private boolean gtlcore$isPatternEncodingTermScreen() {
        return (Object) this instanceof PatternEncodingTermScreen<?>;
    }
}
