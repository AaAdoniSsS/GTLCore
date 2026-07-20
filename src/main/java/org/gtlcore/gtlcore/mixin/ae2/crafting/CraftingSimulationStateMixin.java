package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingTemplateHelper;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingTemplateHelper.MaxFastTemplateEntry;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingSimulationStateFastAccess;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
    @Unique
    private Map<IPatternDetails.IInput, MaxFastTemplateEntry> gTLCore$maxFastTemplateCache;
    @Unique
    private Level gTLCore$maxFastTemplateCacheLevel;
    @Unique
    private Reference2LongOpenHashMap<Object> gTLCore$templateMembershipEpochs;
    @Unique
    private Set<AEKey> gTLCore$visibleTemplateKeys;
    @Unique
    private ReferenceOpenHashSet<Object> gTLCore$templateLocalMembershipChanges;

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

    /**
     * @author .
     * @reason Prevent emitted-item counters from wrapping for very large crafting requests.
     */
    @Overwrite(remap = false)
    public void emitItems(AEKey key, long amount) {
        gTLCore$saturatedAdd(this.emittedItems, key, amount);
    }

    /**
     * @author .
     * @reason Prevent per-pattern operation counts from wrapping for very large crafting requests.
     */
    @Overwrite(remap = false)
    public void addCrafting(IPatternDetails details, long times) {
        this.crafts.merge(details, times, NumberUtils::saturatedAdd);
    }

    @Override
    @Unique
    public void gtlcore$mergeRequiredExtract(AEKey key, long amount) {
        long uncommitted = NumberUtils.saturatedAdd(
                this.unmodifiedCache.get(key),
                -this.modifiableCache.get(key));
        this.updateRequiredExtract(key, NumberUtils.saturatedAdd(uncommitted, amount));
    }

    @Override
    @Unique
    public void gtlcore$directInsert(AEKey key, long amount) {
        gTLCore$ensureFuzzyCached(key);
        gTLCore$addAndTrackTemplateMember(this.modifiableCache, key, amount);
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
        emitItems(key, amount);
    }

    @Override
    @Unique
    public void gtlcore$directAddBytes(double bytes) {
        this.bytes += bytes;
    }

    @Override
    @Unique
    public void gtlcore$directAddCrafting(IPatternDetails details, long times) {
        addCrafting(details, times);
    }

    @Override
    @Unique
    public void gtlcore$collectMaxFastPositiveDiff(Set<Object> changedPrimaryKeys) {
        for (var entry : this.modifiableCache) {
            if (entry.getLongValue() > this.unmodifiedCache.get(entry.getKey())) {
                changedPrimaryKeys.add(entry.getKey().getPrimaryKey());
            }
        }
    }

    @Override
    @Unique
    public ICraftingSimulationStateFastAccess gtlcore$getMaxFastTemplateDelegate(IPatternDetails.IInput input) {
        gTLCore$enableMaxFastTemplateMembershipTracking();
        if (!gTLCore$canDelegateMaxFastTemplates(input)) {
            return null;
        }
        if ((Object) this instanceof ChildCraftingSimulationStateAccessor child &&
                child.gtlcore$getParent() instanceof ICraftingSimulationStateFastAccess parent) {
            return parent;
        }
        return null;
    }

    @Override
    @Unique
    public Iterable<InputTemplate> gtlcore$getMaxFastTemplates(IPatternDetails.IInput input, Level level, AEKey what,
                                                               long validationEpoch, MaxFastMetrics metrics) {
        long startedNanos = System.nanoTime();
        ICraftingSimulationStateFastAccess owner = this;
        long delegations = 0L;
        for (;;) {
            ICraftingSimulationStateFastAccess parent = owner.gtlcore$getMaxFastTemplateDelegate(input);
            if (parent == null) {
                break;
            }
            owner = parent;
            delegations++;
        }
        metrics.recordTemplateParentDelegations(delegations);
        try {
            return owner.gtlcore$getMaxFastOwnedTemplates(input, level, what, validationEpoch, metrics);
        } finally {
            metrics.recordTemplateNanos(System.nanoTime() - startedNanos);
        }
    }

    @Override
    @Unique
    public Iterable<InputTemplate> gtlcore$getMaxFastOwnedTemplates(IPatternDetails.IInput input, Level level,
                                                                    AEKey what, long validationEpoch,
                                                                    MaxFastMetrics metrics) {
        gTLCore$enableMaxFastTemplateCache(level);
        metrics.recordTemplateLookup();
        MaxFastTemplateEntry entry = this.gTLCore$maxFastTemplateCache.get(input);
        if (entry == null) {
            GenericStack[] possibleInputs = CraftingTemplateHelper.snapshotPossibleInputs(input);
            entry = gTLCore$buildMaxFastTemplateEntry(input, level, possibleInputs, validationEpoch);
            this.gTLCore$maxFastTemplateCache.put(input, entry);
            metrics.recordTemplateColdBuild(entry.rawCandidates().length, entry.validTemplates().size());
            return entry.validTemplates();
        }

        if (entry.validationEpoch() != validationEpoch) {
            GenericStack[] currentPossibleInputs = input.getPossibleInputs();
            if (!CraftingTemplateHelper.samePossibleInputs(entry.possibleInputsSnapshot(), currentPossibleInputs)) {
                GenericStack[] possibleInputs = CraftingTemplateHelper.snapshotPossibleInputs(currentPossibleInputs);
                entry = gTLCore$buildMaxFastTemplateEntry(
                        input, level, possibleInputs, validationEpoch);
                metrics.recordTemplatePossibleInputsRebuild(
                        entry.rawCandidates().length,
                        entry.validTemplates().size());
            } else if (!gTLCore$matchesMembershipEpochs(entry)) {
                entry = gTLCore$buildMaxFastTemplateEntry(
                        input, level, entry.possibleInputsSnapshot(), validationEpoch);
                metrics.recordTemplateMembershipRebuild(
                        entry.rawCandidates().length,
                        entry.validTemplates().size());
            } else {
                List<InputTemplate> validTemplates = CraftingTemplateHelper.revalidateCandidates(
                        entry.rawCandidates(), entry.validTemplates(), input, level);
                entry.updateValidation(validTemplates, validationEpoch);
                metrics.recordTemplateValidation(entry.validTemplates().size());
                return entry.validTemplates();
            }
            this.gTLCore$maxFastTemplateCache.put(input, entry);
            return entry.validTemplates();
        }

        if (!gTLCore$matchesMembershipEpochs(entry)) {
            entry = gTLCore$buildMaxFastTemplateEntry(
                    input, level, entry.possibleInputsSnapshot(), validationEpoch);
            this.gTLCore$maxFastTemplateCache.put(input, entry);
            metrics.recordTemplateMembershipRebuild(
                    entry.rawCandidates().length,
                    entry.validTemplates().size());
        } else {
            metrics.recordTemplateHit();
        }
        return entry.validTemplates();
    }

    @Redirect(
              method = "insert",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/stacks/KeyCounter;add(Lappeng/api/stacks/AEKey;J)V"),
              remap = false)
    private void gTLCore$trackInsertedTemplateMember(KeyCounter cache, AEKey key, long amount) {
        gTLCore$addAndTrackTemplateMember(cache, key, amount);
    }

    @Redirect(
              method = "ignore",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/stacks/KeyCounter;set(Lappeng/api/stacks/AEKey;J)V"),
              remap = false)
    private void gTLCore$trackIgnoredTemplateMember(KeyCounter cache, AEKey key, long amount) {
        if (cache != this.modifiableCache || this.gTLCore$templateMembershipEpochs == null) {
            cache.set(key, amount);
            return;
        }

        long before = cache.get(key);
        cache.set(key, amount);
        gTLCore$recordTemplateMemberChange(key, before, cache.get(key));
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
            gTLCore$saturatedAdd(this.modifiableCache, fuzzyKey, amount);
            gTLCore$saturatedAdd(this.unmodifiedCache, fuzzyKey, amount);
            if (this.gTLCore$visibleTemplateKeys != null) {
                this.gTLCore$visibleTemplateKeys.add(fuzzyKey);
            }
        }

        if (!found) {
            this.unmodifiedCache.add(what, 0);
        }
    }

    @Unique
    private void gTLCore$enableMaxFastTemplateCache(Level level) {
        if (this.gTLCore$maxFastTemplateCache == null || this.gTLCore$maxFastTemplateCacheLevel != level) {
            this.gTLCore$maxFastTemplateCache = new IdentityHashMap<>();
            this.gTLCore$maxFastTemplateCacheLevel = level;
        }
        gTLCore$enableMaxFastTemplateMembershipTracking();
    }

    @Unique
    private void gTLCore$enableMaxFastTemplateMembershipTracking() {
        if (this.gTLCore$templateMembershipEpochs != null) {
            return;
        }
        this.gTLCore$templateMembershipEpochs = new Reference2LongOpenHashMap<>();
        this.gTLCore$templateMembershipEpochs.defaultReturnValue(0L);
        this.gTLCore$visibleTemplateKeys = new HashSet<>();
        for (var entry : this.modifiableCache) {
            AEKey key = entry.getKey();
            this.gTLCore$visibleTemplateKeys.add(key);
            if (this.unmodifiedCache.get(key) <= 0) {
                gTLCore$markTemplateMembershipChanged(key.getPrimaryKey());
            }
        }
    }

    @Unique
    private boolean gTLCore$canDelegateMaxFastTemplates(IPatternDetails.IInput input) {
        if (this.gTLCore$templateLocalMembershipChanges == null ||
                this.gTLCore$templateLocalMembershipChanges.isEmpty()) {
            return true;
        }
        for (GenericStack possibleInput : input.getPossibleInputs()) {
            if (this.gTLCore$templateLocalMembershipChanges.contains(possibleInput.what().getPrimaryKey())) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private MaxFastTemplateEntry gTLCore$buildMaxFastTemplateEntry(IPatternDetails.IInput input, Level level,
                                                                   GenericStack[] possibleInputs,
                                                                   long validationEpoch) {
        Object[] primaryGroups = gTLCore$getPrimaryGroups(possibleInputs);
        InputTemplate[] rawCandidates = CraftingTemplateHelper
                .loadRawCandidates((ICraftingInventory) (Object) this, possibleInputs)
                .toArray(InputTemplate[]::new);
        long[] membershipEpochs = new long[primaryGroups.length];
        for (int i = 0; i < primaryGroups.length; i++) {
            membershipEpochs[i] = this.gTLCore$templateMembershipEpochs.getLong(primaryGroups[i]);
        }
        List<InputTemplate> validTemplates = CraftingTemplateHelper.filterValidCandidates(rawCandidates, input, level);
        return new MaxFastTemplateEntry(
                possibleInputs, rawCandidates, validTemplates, primaryGroups, membershipEpochs, validationEpoch);
    }

    @Unique
    private boolean gTLCore$matchesMembershipEpochs(MaxFastTemplateEntry entry) {
        Object[] primaryGroups = entry.primaryGroups();
        long[] membershipEpochs = entry.membershipEpochs();
        for (int i = 0; i < primaryGroups.length; i++) {
            if (membershipEpochs[i] != this.gTLCore$templateMembershipEpochs.getLong(primaryGroups[i])) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static Object[] gTLCore$getPrimaryGroups(GenericStack[] possibleInputs) {
        ReferenceOpenHashSet<Object> seen = new ReferenceOpenHashSet<>();
        List<Object> primaryGroups = new ArrayList<>(possibleInputs.length);
        for (GenericStack possibleInput : possibleInputs) {
            Object primaryGroup = possibleInput.what().getPrimaryKey();
            if (seen.add(primaryGroup)) {
                primaryGroups.add(primaryGroup);
            }
        }
        return primaryGroups.toArray();
    }

    @Unique
    private void gTLCore$addAndTrackTemplateMember(KeyCounter cache, AEKey key, long amount) {
        if (cache != this.modifiableCache || this.gTLCore$templateMembershipEpochs == null) {
            gTLCore$saturatedAdd(cache, key, amount);
            return;
        }

        long before = cache.get(key);
        gTLCore$saturatedAdd(cache, key, amount);
        gTLCore$recordTemplateMemberChange(key, before, cache.get(key));
    }

    @Unique
    private static void gTLCore$saturatedAdd(KeyCounter counter, AEKey key, long amount) {
        counter.set(key, NumberUtils.saturatedAdd(counter.get(key), amount));
    }

    @Unique
    private void gTLCore$recordTemplateMemberChange(AEKey key, long before, long after) {
        boolean newMember = this.gTLCore$visibleTemplateKeys.add(key);
        if (!newMember && (before > 0 || after <= 0)) {
            return;
        }

        Object primaryGroup = key.getPrimaryKey();
        gTLCore$markTemplateMembershipChanged(primaryGroup);
        this.gTLCore$templateMembershipEpochs.put(
                primaryGroup,
                this.gTLCore$templateMembershipEpochs.getLong(primaryGroup) + 1L);
    }

    @Unique
    private void gTLCore$markTemplateMembershipChanged(Object primaryGroup) {
        if (this.gTLCore$templateLocalMembershipChanges == null) {
            this.gTLCore$templateLocalMembershipChanges = new ReferenceOpenHashSet<>();
        }
        this.gTLCore$templateLocalMembershipChanges.add(primaryGroup);
    }
}
