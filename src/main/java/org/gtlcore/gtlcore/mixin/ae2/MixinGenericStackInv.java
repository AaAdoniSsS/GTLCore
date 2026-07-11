package org.gtlcore.gtlcore.mixin.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.helpers.externalstorage.GenericStackInv;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GenericStackInv.class)
public abstract class MixinGenericStackInv {

    @Shadow(remap = false)
    public abstract long getCapacity(AEKeyType space);

    @Inject(method = "getMaxAmount", at = @At("HEAD"), cancellable = true, remap = false)
    private void getMaxAmountInj(AEKey key, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(gtceu$getMaxAmount(key, (GenericStackInv) (Object) this));
    }

    @Unique
    private long gtceu$getMaxAmount(AEKey key, @NotNull GenericStackInv inv) {
        if (key instanceof AEItemKey) {
            return Long.MAX_VALUE;
        } else {
            return inv.getCapacity(key.getType());
        }
    }
}
