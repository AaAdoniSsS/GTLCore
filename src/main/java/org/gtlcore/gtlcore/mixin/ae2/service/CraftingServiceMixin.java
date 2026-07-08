package org.gtlcore.gtlcore.mixin.ae2.service;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingPatternVersion;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IGridNode;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin implements ICraftingPatternVersion {

    @Unique
    private static final int CRAFT_MASK = NumberUtils.nearestPow2Lookup(ConfigHolder.INSTANCE.ae2CraftingServiceUpdateInterval) - 1;
    @Unique
    private long gTLCore$craftingPatternVersion;

    @Override
    @Unique
    public long gtlcore$getCraftingPatternVersion() {
        return this.gTLCore$craftingPatternVersion;
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"), cancellable = true, remap = false)
    public void onServerEndTick(CallbackInfo ci) {
        if ((TickHandler.instance().getCurrentTick() & CRAFT_MASK) != 0) {
            ci.cancel();
        }
    }

    @Inject(method = "addNode", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpPatternVersionOnAddNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        this.gTLCore$craftingPatternVersion++;
    }

    @Inject(method = "removeNode", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpPatternVersionOnRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        this.gTLCore$craftingPatternVersion++;
    }

    @Inject(method = "refreshNodeCraftingProvider", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpPatternVersionOnRefreshProvider(IGridNode gridNode, CallbackInfo ci) {
        this.gTLCore$craftingPatternVersion++;
    }
}
