package org.gtlcore.gtlcore.mixin.ae2.service;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin {

    @Unique
    private static final int CRAFT_MASK = NumberUtils.nearestPow2Lookup(ConfigHolder.INSTANCE.ae2CraftingServiceUpdateInterval) - 1;

    @Inject(method = "onServerEndTick", at = @At("HEAD"), cancellable = true, remap = false)
    public void onServerEndTick(CallbackInfo ci) {
        if ((TickHandler.instance().getCurrentTick() & CRAFT_MASK) != 0) {
            ci.cancel();
        }
    }
}
