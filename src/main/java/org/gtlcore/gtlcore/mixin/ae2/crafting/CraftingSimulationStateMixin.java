package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingSimulationStateFastAccess;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.CraftingSimulationState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(CraftingSimulationState.class)
public abstract class CraftingSimulationStateMixin implements ICraftingSimulationStateFastAccess {

    @Shadow(remap = false)
    @Final
    private KeyCounter unmodifiedCache;
    @Shadow(remap = false)
    @Final
    private KeyCounter modifiableCache;
    @Shadow(remap = false)
    @Final
    private KeyCounter emittedItems;
    @Shadow(remap = false)
    private double bytes;
    @Shadow(remap = false)
    @Final
    private Map<IPatternDetails, Long> crafts;
    @Shadow(remap = false)
    @Final
    private KeyCounter requiredExtract;

    @Unique
    private final Set<AEKey> gTLCore$cachedFuzzyKeys = new HashSet<>();

    @Shadow(remap = false)
    protected abstract long simulateExtractParent(AEKey what, long amount);

    @Shadow(remap = false)
    protected abstract Iterable<AEKey> findFuzzyParent(AEKey what);

    @Shadow(remap = false)
    private void updateRequiredExtract(AEKey what, long amount) {}

    /**
     * @author .
     * @reason Reduce child-state diff commit overhead in large AE2 crafting simulations.
     */
    @Overwrite(remap = false)
    public void applyDiff(CraftingSimulationState target) {
        ICraftingSimulationStateFastAccess parent = (ICraftingSimulationStateFastAccess) target;

        for (var entry : this.requiredExtract) {
            parent.gtlcore$mergeRequiredExtract(entry.getKey(), entry.getLongValue());
        }

        for (var entry : this.modifiableCache) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue() - this.unmodifiedCache.get(key);
            if (amount > 0) {
                parent.gtlcore$directInsert(key, amount);
            } else if (amount < 0) {
                long requested = -amount;
                long extracted = parent.gtlcore$directExtract(key, requested);
                if (extracted != requested) {
                    throw new IllegalStateException("Failed to extract from parent. This is a bug!");
                }
            }
        }

        for (var entry : this.emittedItems) {
            parent.gtlcore$directEmit(entry.getKey(), entry.getLongValue());
        }

        parent.gtlcore$directAddBytes(this.bytes);

        for (var entry : this.crafts.entrySet()) {
            parent.gtlcore$directAddCrafting(entry.getKey(), entry.getValue());
        }
    }

    @Override
    @Unique
    public void gtlcore$mergeRequiredExtract(AEKey key, long amount) {
        this.updateRequiredExtract(key, this.unmodifiedCache.get(key) - this.modifiableCache.get(key) + amount);
    }

    @Override
    @Unique
    public void gtlcore$directInsert(AEKey key, long amount) {
        gTLCore$ensureFuzzyCached(key);
        this.modifiableCache.add(key, amount);
    }

    @Override
    @Unique
    public long gtlcore$directExtract(AEKey key, long amount) {
        gTLCore$ensureFuzzyCached(key);
        long available = this.modifiableCache.get(key);
        if (available == 0) {
            return 0;
        }

        long extracted = Math.min(available, amount);
        this.modifiableCache.remove(key, extracted);
        this.updateRequiredExtract(key, this.unmodifiedCache.get(key) - this.modifiableCache.get(key));
        return extracted;
    }

    @Override
    @Unique
    public void gtlcore$directEmit(AEKey key, long amount) {
        this.emittedItems.add(key, amount);
    }

    @Override
    @Unique
    public void gtlcore$directAddBytes(double bytes) {
        this.bytes += bytes;
    }

    @Override
    @Unique
    public void gtlcore$directAddCrafting(IPatternDetails details, long times) {
        this.crafts.merge(details, times, Long::sum);
    }

    @Redirect(
              method = {
                      "insert",
                      "extract",
                      "findFuzzyTemplates",
                      "ignore" },
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/crafting/inv/CraftingSimulationState;cacheFuzzy(Lappeng/api/stacks/AEKey;)V"),
              remap = false)
    private void gTLCore$cacheFuzzy(CraftingSimulationState state, AEKey what) {
        gTLCore$ensureFuzzyCached(what);
    }

    @Unique
    private void gTLCore$ensureFuzzyCached(AEKey what) {
        if (what == null || !this.gTLCore$cachedFuzzyKeys.add(what)) {
            return;
        }

        if (!this.unmodifiedCache.findFuzzy(what, FuzzyMode.IGNORE_ALL).isEmpty()) {
            return;
        }

        boolean found = false;
        for (AEKey fuzzyKey : this.findFuzzyParent(what)) {
            long amount = this.simulateExtractParent(fuzzyKey, Long.MAX_VALUE);
            if (amount != 0) {
                found = true;
            }
            this.modifiableCache.add(fuzzyKey, amount);
            this.unmodifiedCache.add(fuzzyKey, amount);
        }

        if (!found) {
            this.unmodifiedCache.add(what, 0);
        }
    }
}
