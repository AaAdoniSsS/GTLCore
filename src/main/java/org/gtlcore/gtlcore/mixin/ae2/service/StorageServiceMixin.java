package org.gtlcore.gtlcore.mixin.ae2.service;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStorageVersion;
import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.storage.IStorageProvider;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.InterestManager;
import appeng.me.helpers.StackWatcher;
import appeng.me.service.StorageService;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

@Mixin(StorageService.class)
public abstract class StorageServiceMixin implements ICraftingStorageVersion {

    @Shadow(remap = false)
    @Final
    @Mutable
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager;
    @Shadow(remap = false)
    private boolean cachedStacksNeedUpdate;

    @Shadow(remap = false)
    protected abstract void updateCachedStacks();

    @Unique
    private static final int STORAGE_MASK = NumberUtils.nearestPow2Lookup(ConfigHolder.INSTANCE.ae2StorageServiceUpdateInterval) - 1;
    @Unique
    private long gTLCore$storageVersion;

    public StorageServiceMixin(InterestManager<StackWatcher<IStorageWatcherNode>> interestManager) {
        this.interestManager = interestManager;
    }

    @Override
    @Unique
    public long gtlcore$getStorageVersion() {
        return this.gTLCore$storageVersion;
    }

    @Inject(method = "invalidateCache", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpVersionOnInvalidate(CallbackInfo ci) {
        this.gTLCore$storageVersion++;
    }

    @Inject(method = "addGlobalStorageProvider", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpVersionOnAddGlobalProvider(IStorageProvider provider, CallbackInfo ci) {
        this.gTLCore$storageVersion++;
    }

    @Inject(method = "removeGlobalStorageProvider", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpVersionOnRemoveGlobalProvider(IStorageProvider provider, CallbackInfo ci) {
        this.gTLCore$storageVersion++;
    }

    @Inject(method = "refreshNodeStorageProvider", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpVersionOnRefreshNodeProvider(IGridNode node, CallbackInfo ci) {
        this.gTLCore$storageVersion++;
    }

    @Inject(method = "refreshGlobalStorageProvider", at = @At("HEAD"), remap = false)
    private void gTLCore$bumpVersionOnRefreshGlobalProvider(IStorageProvider provider, CallbackInfo ci) {
        this.gTLCore$storageVersion++;
    }

    /**
     * @author .
     * @reason 减少更新频率
     */
    @Overwrite(remap = false)
    public void onServerEndTick() {
        if (this.interestManager.isEmpty()) {
            this.cachedStacksNeedUpdate = true;
        } else {
            if ((TickHandler.instance().getCurrentTick() & STORAGE_MASK) == 0) this.updateCachedStacks();
        }
    }
}
