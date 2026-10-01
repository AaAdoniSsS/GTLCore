package org.gtlcore.gtlcore.mixin.meRequester;

import org.gtlcore.gtlcore.integration.ae2.requester.RequesterStorageSync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import appeng.util.SettingsFrom;
import com.almostreliable.merequester.requester.RequesterBlockEntity;
import com.almostreliable.merequester.requester.StorageManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RequesterBlockEntity.class, remap = false)
public abstract class RequesterBlockEntityMixin {

    @Shadow
    @Final
    private StorageManager storageManager;

    @Inject(method = "handleRequests", at = @At("HEAD"))
    private void gtlcore$refreshBeforeRequests(CallbackInfoReturnable<Boolean> cir) {
        ((RequesterStorageSync) storageManager).gtlcore$synchronizeInventory();
    }

    @Inject(method = "importSettings", at = @At("RETURN"))
    private void gtlcore$importedRequests(SettingsFrom mode, CompoundTag input, Player player, CallbackInfo ci) {
        if (mode == SettingsFrom.MEMORY_CARD && input.contains("requests")) {
            ((RequesterStorageSync) storageManager).gtlcore$requestsChanged();
            ((RequesterBlockEntity) (Object) this).saveChanges();
        }
    }
}
