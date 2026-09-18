package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDisplayRead;
import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyCounter.class)
public abstract class TerminalDisplayCounterMixin {

    @Inject(method = "add", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$saturateDisplay(AEKey key, long amount, CallbackInfo ci) {
        if (!TerminalDisplayRead.active()) return;
        KeyCounter counter = (KeyCounter) (Object) this;
        counter.set(key, NumberUtils.saturatedAdd(counter.get(key), amount));
        ci.cancel();
    }

    @Inject(method = "addAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$saturateDisplayMerge(KeyCounter other, CallbackInfo ci) {
        if (!TerminalDisplayRead.active()) return;
        KeyCounter counter = (KeyCounter) (Object) this;
        for (var entry : other) {
            counter.set(entry.getKey(), NumberUtils.saturatedAdd(counter.get(entry.getKey()), entry.getLongValue()));
        }
        ci.cancel();
    }
}
