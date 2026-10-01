package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDenseAccess;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDenseCounter;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalDisplayRead;
import org.gtlcore.gtlcore.integration.ae2.storage.TerminalVariantAccess;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyCounter.class)
public abstract class TerminalDisplayCounterMixin implements TerminalDenseAccess {

    @org.spongepowered.asm.mixin.Unique
    private TerminalDenseCounter gtlcore$dense;

    @Override
    public TerminalDenseCounter gtlcore$dense() {
        return gtlcore$dense;
    }

    @Override
    public void gtlcore$dense(TerminalDenseCounter counter) {
        gtlcore$dense = counter;
    }

    @WrapOperation(method = "addAll",
                   remap = false,
                   at = @At(value = "INVOKE",
                            target = "Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object gtlcore$checkMergedGroup(Reference2ObjectMap<?, ?> groups, Object primaryKey, Operation<Object> original) {
        Object target = original.call(groups, primaryKey);
        if (target != null) {
            TerminalDisplayRead.beforeGroupMerge((KeyCounter) (Object) this, groups, primaryKey,
                    (TerminalVariantAccess) target);
        }
        return target;
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtlcore$createDisplayCounter(CallbackInfo ci) {
        TerminalDisplayRead.created((KeyCounter) (Object) this);
    }

    @Inject(method = "add", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$addDisplay(AEKey key, long amount, CallbackInfo ci) {
        KeyCounter counter = (KeyCounter) (Object) this;
        if (!TerminalDisplayRead.tracks(counter)) {
            TerminalDisplayRead.materialize(counter);
            return;
        }
        TerminalDisplayRead.add(counter, key, amount);
        ci.cancel();
    }

    @Inject(method = "addAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$mergeDisplay(KeyCounter other, CallbackInfo ci) {
        KeyCounter counter = (KeyCounter) (Object) this;
        if (!TerminalDisplayRead.tracks(counter)) {
            TerminalDisplayRead.materialize(counter);
            TerminalDisplayRead.materialize(other);
            return;
        }
        TerminalDisplayRead.merge(counter, other, false);
        ci.cancel();
    }

    @Inject(method = "set", at = @At("HEAD"), remap = false)
    private void gtlcore$setDisplay(AEKey key, long amount, CallbackInfo ci) {
        KeyCounter counter = (KeyCounter) (Object) this;
        TerminalDisplayRead.materialize(counter);
        if (TerminalDisplayRead.tracks(counter)) TerminalDisplayRead.forget(counter, key);
    }

    @Inject(method = { "clear", "reset" }, at = @At("HEAD"), remap = false)
    private void gtlcore$clearDisplay(CallbackInfo ci) {
        KeyCounter counter = (KeyCounter) (Object) this;
        TerminalDisplayRead.materialize(counter);
        if (TerminalDisplayRead.tracks(counter)) TerminalDisplayRead.clear(counter);
    }

    @Inject(method = "remove(Lappeng/api/stacks/AEKey;)J", at = @At("HEAD"), remap = false)
    private void gtlcore$removeDisplay(AEKey key, CallbackInfoReturnable<Long> cir) {
        KeyCounter counter = (KeyCounter) (Object) this;
        TerminalDisplayRead.materialize(counter);
        if (TerminalDisplayRead.tracks(counter)) TerminalDisplayRead.forget(counter, key);
    }

    @Inject(method = "removeAll", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$subtractDisplay(KeyCounter other, CallbackInfo ci) {
        KeyCounter counter = (KeyCounter) (Object) this;
        if (!TerminalDisplayRead.tracks(counter)) {
            TerminalDisplayRead.materialize(counter);
            TerminalDisplayRead.materialize(other);
            return;
        }
        TerminalDisplayRead.merge(counter, other, true);
        ci.cancel();
    }

    @Inject(method = "get", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$getDense(AEKey key, CallbackInfoReturnable<Long> cir) {
        if (gtlcore$dense != null) cir.setReturnValue(gtlcore$dense.get(key));
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$isDenseEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$dense != null) cir.setReturnValue(gtlcore$dense.size() == 0);
    }

    @Inject(method = "size", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$denseSize(CallbackInfoReturnable<Integer> cir) {
        if (gtlcore$dense != null) cir.setReturnValue(gtlcore$dense.size());
    }

    @Inject(method = { "iterator", "keySet", "findFuzzy", "getFirstEntry", "getFirstKey" },
            at = @At("HEAD"),
            remap = false)
    private void gtlcore$nativeRead(CallbackInfoReturnable<?> cir) {
        TerminalDisplayRead.materialize((KeyCounter) (Object) this);
    }

    @Inject(method = { "removeZeros", "removeEmptySubmaps", "remove(Lappeng/api/stacks/AEKey;J)V" },
            at = @At("HEAD"),
            remap = false)
    private void gtlcore$nativeMutation(CallbackInfo ci) {
        TerminalDisplayRead.materialize((KeyCounter) (Object) this);
    }
}
