package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;

import net.minecraft.world.InteractionResult;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WirelessOpticalComputationHatchMachine.class)
public abstract class WirelessComputationBindingMixin {

    @Inject(method = { "setTransmitterPos", "setReceiverPos" }, at = @At("RETURN"), remap = false)
    private void gtlcore$bindingChanged(CallbackInfo ci) {
        ComputationNetwork.invalidate();
    }

    @Inject(method = "onUse", at = @At("RETURN"), remap = false)
    private void gtlcore$bindingUsed(CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.SUCCESS) ComputationNetwork.invalidate();
    }
}
