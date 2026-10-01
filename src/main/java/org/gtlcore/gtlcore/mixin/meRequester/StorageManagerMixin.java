package org.gtlcore.gtlcore.mixin.meRequester;

import org.gtlcore.gtlcore.integration.ae2.InventorySnapshotVersion;
import org.gtlcore.gtlcore.integration.ae2.requester.RequesterStorageAccounting;
import org.gtlcore.gtlcore.integration.ae2.requester.RequesterStorageSync;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageService;
import com.almostreliable.merequester.requester.RequesterBlockEntity;
import com.almostreliable.merequester.requester.StorageManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StorageManager.class, remap = false)
public abstract class StorageManagerMixin implements RequesterStorageSync {

    @Shadow
    @Final
    private RequesterBlockEntity host;
    @Shadow
    @Final
    private StorageManager.Storage[] storages;

    @Shadow
    public abstract StorageManager.Storage get(int slot);

    @Shadow
    private void resetWatcher() {}

    @Unique
    private IStorageService gtlcore$sampledService;
    @Unique
    private long gtlcore$sampledVersion;
    @Unique
    private boolean gtlcore$needsBaseline = true;

    @Override
    public void gtlcore$synchronizeInventory() {
        var service = host.getMainNodeGrid().getStorageService();
        // Use the shared AE sample. Never rescan the entire network per requester or per slot.
        var inventory = service.getCachedInventory();
        long version = ((InventorySnapshotVersion) service).gtlcore$inventorySnapshotVersion();
        if (!gtlcore$needsBaseline && service == gtlcore$sampledService && version == gtlcore$sampledVersion) return;
        var requests = host.getRequests();
        for (int i = 0; i < storages.length; i++) {
            var key = requests.getKey(i);
            if (key == null && storages[i] == null) continue;
            ((RequesterStorageAccounting) get(i)).gtlcore$updateSnapshot(service, version, key,
                    key == null ? 0 : inventory.get(key));
        }
        gtlcore$sampledService = service;
        gtlcore$sampledVersion = version;
        gtlcore$needsBaseline = false;
    }

    @Override
    public void gtlcore$requestsChanged() {
        gtlcore$needsBaseline = true;
        resetWatcher();
    }

    @Inject(method = "updateWatcher", at = @At("RETURN"))
    private void gtlcore$rebind(IStackWatcher watcher, CallbackInfo ci) {
        // Binding may run while the grid is being assembled. Read stock only on an active tick.
        gtlcore$needsBaseline = true;
    }

    @Inject(method = "deserialize(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void gtlcore$loaded(CompoundTag tag, CallbackInfo ci) {
        gtlcore$needsBaseline = true;
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void gtlcore$slotChanged(int slot, CallbackInfo ci) {
        gtlcore$requestsChanged();
        ci.cancel();
    }

    @Inject(method = "computeAmountToCraft", at = @At("HEAD"), cancellable = true)
    private void gtlcore$avoidStockOverflow(int slot, CallbackInfoReturnable<Long> cir) {
        var request = host.getRequests().get(slot);
        cir.setReturnValue(request.getKey() != null &&
                !((RequesterStorageAccounting) get(slot)).gtlcore$hasStock(request.getAmount()) ? request.getBatch() : 0L);
    }
}
