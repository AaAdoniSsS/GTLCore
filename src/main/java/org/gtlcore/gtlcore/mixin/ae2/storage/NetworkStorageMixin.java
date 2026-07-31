package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.crafting.ManualCraftingInventoryLock;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;

@Mixin(NetworkStorage.class)
public abstract class NetworkStorageMixin implements ThroughputStorageView, ManualCraftingInventoryLock.AvailabilityView {

    @Shadow(remap = false)
    @Final
    private NavigableMap<Integer, List<MEStorage>> priorityInventory;

    @Unique
    private long gtlcore$throughputTopologyVersion;

    @Inject(method = "mount", at = @At("RETURN"), remap = false)
    private void gtlcore$bumpThroughputTopologyOnMount(int priority, MEStorage storage, CallbackInfo ci) {
        gtlcore$throughputTopologyVersion++;
    }

    @Inject(method = "unmount", at = @At("RETURN"), remap = false)
    private void gtlcore$bumpThroughputTopologyOnUnmount(MEStorage storage, CallbackInfo ci) {
        gtlcore$throughputTopologyVersion++;
    }

    @Inject(method = "insert", at = @At("HEAD"), remap = false)
    private void gtlcore$beginInsert(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (!ThroughputMonitorStorageTracker.isTrackingActive() &&
                !ThroughputMonitorStorageTracker.hasPendingOperation()) {
            return;
        }
        ThroughputMonitorStorageTracker.beginInsert((MEStorage) (Object) this, source);
    }

    @Inject(method = "insert", at = @At("RETURN"), remap = false)
    private void gtlcore$recordInsert(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (!ThroughputMonitorStorageTracker.isTrackingActive() &&
                !ThroughputMonitorStorageTracker.hasPendingOperation()) {
            return;
        }
        long inserted = cir.getReturnValue();
        ThroughputMonitorStorageTracker.endInsert(
                (MEStorage) (Object) this,
                what,
                mode == Actionable.MODULATE ? inserted : 0L,
                source);
    }

    @Inject(method = "extract", at = @At("HEAD"), remap = false)
    private void gtlcore$beginExtraction(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (!ThroughputMonitorStorageTracker.isTrackingActive() &&
                !ThroughputMonitorStorageTracker.hasPendingOperation()) {
            return;
        }
        ThroughputMonitorStorageTracker.beginExtraction((MEStorage) (Object) this, source);
    }

    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private long gtlcore$limitExtractionToUnlockedInventory(long amount, AEKey what, long requested,
                                                            Actionable mode, IActionSource source) {
        return ManualCraftingInventoryLock.limitExtraction((MEStorage) (Object) this, what, amount, source);
    }

    @Inject(method = "extract", at = @At("RETURN"), remap = false)
    private void gtlcore$recordExtraction(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (!ThroughputMonitorStorageTracker.isTrackingActive() &&
                !ThroughputMonitorStorageTracker.hasPendingOperation()) {
            return;
        }
        long extracted = cir.getReturnValue();
        ThroughputMonitorStorageTracker.endExtraction(
                (MEStorage) (Object) this,
                what,
                mode == Actionable.MODULATE ? extracted : 0L,
                source);
    }

    @Override
    public Collection<MEStorage> gtlcore$getChildStorages() {
        List<MEStorage> storages = new ArrayList<>();
        for (List<MEStorage> priorityStorages : priorityInventory.values()) {
            storages.addAll(priorityStorages);
        }
        return storages;
    }

    @Override
    public long gtlcore$getAvailableAmount(AEKey what, IActionSource source) {
        long available = 0;
        for (List<MEStorage> priorityStorages : priorityInventory.values()) {
            for (MEStorage storage : priorityStorages) {
                long remaining = Long.MAX_VALUE - available;
                long stored = storage.extract(what, remaining, Actionable.SIMULATE, source);
                if (stored >= remaining) {
                    return Long.MAX_VALUE;
                }
                available += stored;
            }
        }
        return available;
    }

    @Override
    public long gtlcore$getTopologyVersion() {
        return gtlcore$throughputTopologyVersion;
    }
}
