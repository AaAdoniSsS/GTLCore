package org.gtlcore.gtlcore.mixin.ae2;

import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.stacks.AEKeyType;
import appeng.util.CowMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericSlotCapacities.class)
public abstract class MixinGenericSlotCapacities {

    @Final
    @Shadow(remap = false)
    private static CowMap<AEKeyType, Long> map;

    @Inject(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lappeng/api/behaviors/GenericSlotCapacities;register(Lappeng/api/stacks/AEKeyType;Ljava/lang/Long;)V"),
            remap = false)
    private static void clinitInj(CallbackInfo ci) {
        map.putIfAbsent(AEKeyType.items(), Long.MAX_VALUE);
        map.putIfAbsent(AEKeyType.fluids(), Long.MAX_VALUE);
    }
}
