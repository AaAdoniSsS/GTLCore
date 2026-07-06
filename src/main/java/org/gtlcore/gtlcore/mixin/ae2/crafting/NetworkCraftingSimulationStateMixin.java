package org.gtlcore.gtlcore.mixin.ae2.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;

@Mixin(NetworkCraftingSimulationState.class)
public abstract class NetworkCraftingSimulationStateMixin {

    @Unique
    private IStorageService gTLCore$storageService;
    @Unique
    private IActionSource gTLCore$actionSource;
    @Unique
    private KeyCounter gTLCore$sourceCache;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gTLCore$captureSource(IStorageService storageService, IActionSource actionSource, CallbackInfo ci) {
        this.gTLCore$storageService = storageService;
        this.gTLCore$actionSource = actionSource;
        this.gTLCore$sourceCache = actionSource == null ? null : storageService.getCachedInventory();
    }

    @Redirect(
              method = "<init>",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/networking/storage/IStorageService;getCachedInventory()Lappeng/api/stacks/KeyCounter;"),
              remap = false)
    private KeyCounter gTLCore$skipFullInventoryCopy(IStorageService storageService) {
        return new KeyCounter();
    }

    /**
     * @author .
     * @reason 避免每次下单计算复制整个ME缓存库存
     */
    @Overwrite(remap = false)
    protected long simulateExtractParent(AEKey what, long amount) {
        KeyCounter sourceCache = this.gTLCore$sourceCache;
        if (sourceCache == null) {
            return 0;
        }

        long cachedAmount = sourceCache.get(what);
        if (cachedAmount <= 0) {
            return 0;
        }

        long available = Math.min(cachedAmount, amount);
        if (AEConfig.instance().isCraftingSimulatedExtraction() && this.gTLCore$storageService != null &&
                this.gTLCore$actionSource != null) {
            available = this.gTLCore$storageService.getInventory()
                    .extract(what, available, Actionable.SIMULATE, this.gTLCore$actionSource);
        }
        return Math.min(available, amount);
    }

    /**
     * @author .
     * @reason 避免每次下单计算复制整个ME缓存库存
     */
    @Overwrite(remap = false)
    protected Iterable<AEKey> findFuzzyParent(AEKey what) {
        KeyCounter sourceCache = this.gTLCore$sourceCache;
        if (what == null || sourceCache == null) {
            return Collections.emptyList();
        }

        var fuzzyEntries = sourceCache.findFuzzy(what, FuzzyMode.IGNORE_ALL);
        if (fuzzyEntries.isEmpty()) {
            return Collections.emptyList();
        }

        var keys = new ArrayList<AEKey>(fuzzyEntries.size());
        boolean simulatedExtraction = AEConfig.instance().isCraftingSimulatedExtraction();
        for (var entry : fuzzyEntries) {
            AEKey fuzzyKey = entry.getKey();
            long cachedAmount = entry.getLongValue();
            if (cachedAmount <= 0) {
                continue;
            }
            if (simulatedExtraction && this.gTLCore$storageService != null && this.gTLCore$actionSource != null &&
                    this.gTLCore$storageService.getInventory()
                            .extract(fuzzyKey, cachedAmount, Actionable.SIMULATE, this.gTLCore$actionSource) <= 0) {
                continue;
            }
            keys.add(fuzzyKey);
        }
        return keys;
    }
}
