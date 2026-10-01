package org.gtlcore.gtlcore.mixin.meRequester;

import org.gtlcore.gtlcore.integration.ae2.requester.RequesterStorageAccounting;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import com.almostreliable.merequester.requester.StorageManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(value = StorageManager.Storage.class, remap = false)
public abstract class StorageAccountingMixin implements RequesterStorageAccounting {

    @Shadow
    private long knownAmount;
    @Shadow
    private long pendingAmount;

    @Unique
    private IStorageService gtlcore$pendingService;
    @Unique
    private long gtlcore$pendingVersion;
    @Unique
    private AEKey gtlcore$pendingKey;

    @Override
    public void gtlcore$updateSnapshot(IStorageService service, long version, @Nullable AEKey requestedKey, long amount) {
        knownAmount = amount;
        if (!gtlcore$samePendingSnapshot(service, version, requestedKey)) pendingAmount = 0;
    }

    @Override
    public void gtlcore$prepareExport(IStorageService service, long version, @Nullable AEKey exportedKey) {
        // Exports made after this sample must remain accounted for until a newer sample arrives.
        if (!gtlcore$samePendingSnapshot(service, version, exportedKey)) pendingAmount = 0;
        gtlcore$pendingService = service;
        gtlcore$pendingVersion = version;
        gtlcore$pendingKey = exportedKey;
    }

    @Override
    public void gtlcore$finishExport(@Nullable AEKey requestedKey) {
        // An old job may still deliver A after the slot was changed to B. Preserve the delivery,
        // but do not count A towards B's replenishment target.
        if (!Objects.equals(requestedKey, gtlcore$pendingKey)) pendingAmount = 0;
        else if (pendingAmount < 0) pendingAmount = Long.MAX_VALUE;
    }

    @Override
    public boolean gtlcore$hasStock(long amount) {
        long known = Math.max(0, knownAmount);
        return known >= amount || Math.max(0, pendingAmount) >= amount - known;
    }

    @Unique
    private boolean gtlcore$samePendingSnapshot(IStorageService service, long version, @Nullable AEKey key) {
        return service == gtlcore$pendingService && version == gtlcore$pendingVersion &&
                Objects.equals(key, gtlcore$pendingKey);
    }

    @Inject(method = "deserialize(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void gtlcore$discardSavedAccounting(CompoundTag tag, CallbackInfo ci) {
        // Pending describes stock already exported into ME, not physical contents of this block.
        // The saved buffer and crafting links remain owned by the original requester.
        knownAmount = -1;
        pendingAmount = 0;
        gtlcore$pendingService = null;
        gtlcore$pendingKey = null;
    }
}
