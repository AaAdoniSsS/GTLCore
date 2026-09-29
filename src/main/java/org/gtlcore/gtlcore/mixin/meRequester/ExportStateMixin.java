package org.gtlcore.gtlcore.mixin.meRequester;

import org.gtlcore.gtlcore.integration.ae2.InventorySnapshotVersion;
import org.gtlcore.gtlcore.integration.ae2.requester.RequesterStorageAccounting;

import com.almostreliable.merequester.requester.RequesterBlockEntity;
import com.almostreliable.merequester.requester.StorageManager;
import com.almostreliable.merequester.requester.status.ExportState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ExportState.class, remap = false)
public abstract class ExportStateMixin {

    @WrapOperation(method = "handle",
                   at = @At(value = "INVOKE",
                            target = "Lcom/almostreliable/merequester/requester/StorageManager$Storage;compute(J)Z"))
    private boolean gtlcore$recordExportSnapshot(StorageManager.Storage storage, long inserted, Operation<Boolean> original,
                                                 RequesterBlockEntity host, int slot) {
        var service = host.getMainNodeGrid().getStorageService();
        var accounting = (RequesterStorageAccounting) storage;
        // Reading the version must not trigger a sample between insertion and pending accounting.
        accounting.gtlcore$prepareExport(service, ((InventorySnapshotVersion) service).gtlcore$inventorySnapshotVersion(), storage.getKey());
        boolean result = original.call(storage, inserted);
        accounting.gtlcore$finishExport(host.getRequests().getKey(slot));
        return result;
    }
}
