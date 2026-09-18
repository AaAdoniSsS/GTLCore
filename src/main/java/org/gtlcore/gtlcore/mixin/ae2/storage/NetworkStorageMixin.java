package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.crafting.ManualCraftingInventoryLock;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        ThroughputMonitorStorageTracker.onTopologyChanged((MEStorage) (Object) this);
    }

    @Inject(method = "unmount", at = @At("RETURN"), remap = false)
    private void gtlcore$bumpThroughputTopologyOnUnmount(MEStorage storage, CallbackInfo ci) {
        gtlcore$throughputTopologyVersion++;
        ThroughputMonitorStorageTracker.onTopologyChanged((MEStorage) (Object) this);
    }

    @WrapMethod(method = "insert", remap = false)
    private long gtlcore$trackInsert(AEKey what, long amount, Actionable mode, IActionSource source,
                                     Operation<Long> original) {
        if (mode != Actionable.MODULATE || !ThroughputMonitorStorageTracker.isTrackingActive()) {
            return original.call(what, amount, mode, source);
        }
        ThroughputMonitorStorageTracker.beginInsert((MEStorage) (Object) this, source);
        long inserted;
        try {
            inserted = original.call(what, amount, mode, source);
            ThroughputMonitorStorageTracker.endInsert((MEStorage) (Object) this, what, inserted, source);
        } catch (RuntimeException | Error failure) {
            ThroughputMonitorStorageTracker.abortOperation();
            throw failure;
        }
        return inserted;
    }

    @WrapMethod(method = "extract", remap = false)
    private long gtlcore$trackExtraction(AEKey what, long amount, Actionable mode, IActionSource source,
                                         Operation<Long> original) {
        if (mode != Actionable.MODULATE || !ThroughputMonitorStorageTracker.isTrackingActive()) {
            return original.call(what, amount, mode, source);
        }
        ThroughputMonitorStorageTracker.beginExtraction((MEStorage) (Object) this, source);
        long extracted;
        try {
            extracted = original.call(what, amount, mode, source);
            ThroughputMonitorStorageTracker.endExtraction((MEStorage) (Object) this, what, extracted, source);
        } catch (RuntimeException | Error failure) {
            ThroughputMonitorStorageTracker.abortOperation();
            throw failure;
        }
        return extracted;
    }

    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private long gtlcore$limitExtractionToUnlockedInventory(long amount, AEKey what, long requested,
                                                            Actionable mode, IActionSource source) {
        return ManualCraftingInventoryLock.limitExtraction((MEStorage) (Object) this, what, amount, source);
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
