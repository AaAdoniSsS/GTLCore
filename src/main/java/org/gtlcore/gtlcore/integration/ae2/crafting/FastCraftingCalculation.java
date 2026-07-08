package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FastCraftingCalculation {

    private static final int STRUCTURE_CACHE_MAX_SIZE = 8192;
    private static final int MISSING_PREVIEW_MAX_PROCESSED_DEMANDS = 65536;
    private static final int CRAFT_LESS_MAX_VALIDATION_ATTEMPTS = 4;
    private static final Map<CandidatePatternCacheKey, List<PatternPlan>> SHARED_CANDIDATE_PATTERN_CACHE = new LinkedHashMap<CandidatePatternCacheKey, List<PatternPlan>>(512, 0.75f, true);
    private static final Map<CandidatePatternCacheKey, Boolean> SHARED_CANDIDATE_MULTIPLE_PATH_CACHE = new LinkedHashMap<CandidatePatternCacheKey, Boolean>(512, 0.75f, true);
    private static final Map<PatternPlanCacheKey, PatternPlan> SHARED_PATTERN_PLAN_CACHE = new LinkedHashMap<PatternPlanCacheKey, PatternPlan>(1024, 0.75f, true);
    private final Level level;
    private final ICraftingService craftingService;
    private final Object craftingCacheOwner;
    private final long craftingPatternVersion;
    private final Object levelCacheKey;
    private final boolean preferFastMissingPlan;
    private final KeyCounter sourceInventory;
    private final KeyCounter produced = new KeyCounter();
    private final FastKeyCounter used = new FastKeyCounter();
    private final FastKeyCounter emitted = new FastKeyCounter();
    private final FastKeyCounter missing = new FastKeyCounter();
    private final Map<IPatternDetails, Long> crafts = new IdentityHashMap<IPatternDetails, Long>();
    private final Map<AEKey, PatternPlan> selectedPatterns = new HashMap<AEKey, PatternPlan>();
    private final Map<AEKey, List<PatternPlan>> candidatePatternCache = new HashMap<AEKey, List<PatternPlan>>();
    private final IdentityHashMap<IPatternDetails, Map<AEKey, PatternPlan>> patternPlanCache = new IdentityHashMap();
    private final Map<AEKey, Collection<IPatternDetails>> craftingForCache = new HashMap<AEKey, Collection<IPatternDetails>>();
    private final Map<AEKey, Boolean> canEmitCache = new HashMap<AEKey, Boolean>();
    private final Map<AEKey, Boolean> craftableOrEmittableCache = new HashMap<AEKey, Boolean>();
    private final Map<FuzzyCraftableKey, AEKey> fuzzyCraftableCache = new HashMap<FuzzyCraftableKey, AEKey>();
    private final Map<FuzzyTemplateKey, List<TemplateAmount>> sourceFuzzyTemplateCache = new HashMap<FuzzyTemplateKey, List<TemplateAmount>>();
    private final Object2IntOpenHashMap<AEKey> amountPerByteCache = new Object2IntOpenHashMap();
    private final Set<AEKey> resolving = new HashSet<AEKey>();
    private final List<CounterChange> counterChanges = new ArrayList<CounterChange>();
    private final List<CraftChange> craftChanges = new ArrayList<CraftChange>();
    private final List<SelectedPatternChange> selectedPatternChanges = new ArrayList<SelectedPatternChange>();
    private final List<CalculationSnapshot> activeSnapshots = new ArrayList<CalculationSnapshot>();
    private final Map<CounterChangeKey, Integer> counterChangeIndexes = new HashMap<CounterChangeKey, Integer>();
    private final IdentityHashMap<IPatternDetails, Integer> craftChangeIndexes = new IdentityHashMap();
    private final Map<AEKey, Integer> selectedPatternChangeIndexes = new HashMap<AEKey, Integer>();
    private long bytes;
    private long missingAmount;
    private boolean multiplePaths;

    private FastCraftingCalculation(Level level, IGrid grid, CalculationStrategy strategy) {
        long l;
        this.level = level;
        this.craftingService = grid.getCraftingService();
        this.craftingCacheOwner = this.craftingService;
        ICraftingService iCraftingService = this.craftingService;
        if (iCraftingService instanceof ICraftingPatternVersion) {
            ICraftingPatternVersion version = (ICraftingPatternVersion) iCraftingService;
            l = version.gtlcore$getCraftingPatternVersion();
        } else {
            l = 0L;
        }
        this.craftingPatternVersion = l;
        this.levelCacheKey = level;
        this.preferFastMissingPlan = strategy == CalculationStrategy.REPORT_MISSING_ITEMS;
        this.sourceInventory = grid.getStorageService().getCachedInventory();
    }

    public static ICraftingPlan tryBuild(Level level, IGrid grid, AEKey output, long requestedAmount, CalculationStrategy strategy) {
        if (requestedAmount <= 0L) {
            return null;
        }
        if (strategy == CalculationStrategy.CRAFT_LESS) {
            return FastCraftingCalculation.tryBuildCraftLess(level, grid, output, requestedAmount);
        }
        FastCraftingCalculation calculation = new FastCraftingCalculation(level, grid, strategy);
        if (strategy == CalculationStrategy.REPORT_MISSING_ITEMS) {
            ICraftingPlan queuedPlan = calculation.tryBuildQueuedPlan(output, requestedAmount);
            if (queuedPlan != null) {
                return queuedPlan;
            }
            calculation = new FastCraftingCalculation(level, grid, strategy);
        }
        if (!calculation.requestNode(output, 1L, requestedAmount, null, true)) {
            return null;
        }
        return calculation.toPlan(output, requestedAmount);
    }

    private static ICraftingPlan tryBuildCraftLess(Level level, IGrid grid, AEKey output, long requestedAmount) {
        CraftLessAttempt requestedAttempt = FastCraftingCalculation.buildCraftLessAttempt(level, grid, output, requestedAmount, false);
        if (requestedAttempt == null) {
            return null;
        }
        if (!requestedAttempt.plan.simulation()) {
            return requestedAttempt.plan;
        }
        long candidateAmount = requestedAttempt.calculation.estimateCraftLessAmount(requestedAmount);
        for (int i = 0; i < 4 && candidateAmount > 0L; ++i) {
            CraftLessAttempt candidateAttempt = FastCraftingCalculation.buildCraftLessAttempt(level, grid, output, candidateAmount, true);
            if (candidateAttempt == null) {
                return requestedAttempt.plan;
            }
            if (!candidateAttempt.plan.simulation()) {
                return candidateAttempt.plan;
            }
            long nextCandidate = candidateAttempt.calculation.estimateCraftLessAmount(candidateAmount);
            if (nextCandidate >= candidateAmount) {
                --candidateAmount;
                continue;
            }
            candidateAmount = nextCandidate;
        }
        return requestedAttempt.plan;
    }

    private static CraftLessAttempt buildCraftLessAttempt(Level level, IGrid grid, AEKey output, long requestedAmount, boolean allowBranchFallback) {
        FastCraftingCalculation calculation = new FastCraftingCalculation(level, grid, CalculationStrategy.REPORT_MISSING_ITEMS);
        ICraftingPlan queuedPlan = calculation.tryBuildQueuedPlan(output, requestedAmount);
        if (!(queuedPlan == null || queuedPlan.simulation() && allowBranchFallback)) {
            return new CraftLessAttempt(queuedPlan, calculation);
        }
        CraftLessAttempt queuedAttempt = queuedPlan == null ? null : new CraftLessAttempt(queuedPlan, calculation);
        calculation = new FastCraftingCalculation(level, grid, CalculationStrategy.CRAFT_LESS);
        if (!calculation.requestNode(output, 1L, requestedAmount, null, true)) {
            return queuedAttempt;
        }
        return new CraftLessAttempt(calculation.toPlan(output, requestedAmount), calculation);
    }

    private ICraftingPlan tryBuildQueuedPlan(AEKey output, long requestedAmount) {
        if (!this.requestQueuedPlan(output, requestedAmount)) {
            return null;
        }
        return this.toPlan(output, requestedAmount);
    }

    private ICraftingPlan toPlan(AEKey output, long requestedAmount) {
        long bytes = Math.max(1L, this.bytes);
        return new CraftingPlan(new GenericStack(output, requestedAmount), bytes, this.missingAmount > 0L, this.multiplePaths, this.used.toKeyCounter(), this.emitted.toKeyCounter(), this.missing.toKeyCounter(), this.crafts);
    }

    private long estimateCraftLessAmount(long requestedAmount) {
        if (requestedAmount <= 1L || this.missingAmount <= 0L) {
            return 0L;
        }
        long best = requestedAmount;
        for (Object2LongMap.Entry entry : Object2LongMaps.fastIterable(this.missing.amounts)) {
            AEKey key;
            long usedAmount;
            long totalRequired;
            long missingAmount = entry.getLongValue();
            if (missingAmount <= 0L || (totalRequired = NumberUtils.saturatedAdd(usedAmount = this.used.get(key = (AEKey) entry.getKey()), missingAmount)) <= 0L) continue;
            long available = this.sourceInventory.get(key);
            if (available <= 0L) {
                return 0L;
            }
            long candidate = FastCraftingCalculation.scaleFloor(requestedAmount, Math.min(available, totalRequired), totalRequired);
            if ((best = Math.min(best, candidate)) > 0L) continue;
            return 0L;
        }
        return Math.min(best, requestedAmount - 1L);
    }

    private boolean requestQueuedPlan(AEKey output, long requestedAmount) {
        MissingDemandQueue demands = new MissingDemandQueue();
        demands.add(MissingDemandKey.root(output), requestedAmount);
        int processedDemands = 0;
        while (!demands.isEmpty()) {
            if (++processedDemands > 65536) {
                this.addRemainingDemandsAsMissing(demands);
                return true;
            }
            MissingDemand demand = demands.poll();
            if (this.processMissingDemand(demands, demand.key, demand.units)) continue;
            return false;
        }
        return true;
    }

    private void addRemainingDemandsAsMissing(MissingDemandQueue demands) {
        while (!demands.isEmpty()) {
            MissingDemand demand = demands.poll();
            this.addDemandAsMissing(demand.key, demand.units);
        }
    }

    private void addDemandAsMissing(MissingDemandKey demand, long requestedUnits) {
        long remainingUnits;
        if (requestedUnits <= 0L || demand.unitAmount <= 0L) {
            return;
        }
        long l = remainingUnits = demand.ignoreExisting ? requestedUnits : this.consumeAvailableTemplates(demand.parentInput, demand.what, demand.unitAmount, requestedUnits);
        if (remainingUnits > 0L) {
            this.addMissing(demand.what, NumberUtils.saturatedMultiply(demand.unitAmount, remainingUnits));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean processMissingDemand(MissingDemandQueue demands, MissingDemandKey demand, long requestedUnits) {
        long remainingUnits;
        if (requestedUnits <= 0L) {
            return true;
        }
        if (demand.unitAmount <= 0L) {
            return false;
        }
        long l = remainingUnits = demand.ignoreExisting ? requestedUnits : this.consumeAvailableTemplates(demand.parentInput, demand.what, demand.unitAmount, requestedUnits);
        if (remainingUnits <= 0L) {
            return true;
        }
        long remainingItems = NumberUtils.saturatedMultiply(demand.unitAmount, remainingUnits);
        if (demand.parentInput != null && demand.parentInput.getRemainingKey(demand.what) != null) {
            this.addMissing(demand.what, remainingItems);
            return true;
        }
        if (this.canEmitFor(demand.what)) {
            this.addCounter(this.emitted, demand.what, remainingItems);
            this.addBytes(demand.what, remainingItems);
            return true;
        }
        if (!this.resolving.add(demand.what)) {
            this.addMissing(demand.what, remainingItems);
            return true;
        }
        try {
            List<PatternPlan> plans = this.getCandidatePatterns(demand.what);
            if (plans.isEmpty()) {
                this.addMissing(demand.what, remainingItems);
                boolean bl = true;
                return bl;
            }
            PatternPlan plan = plans.get(0);
            long times = FastCraftingCalculation.ceilDiv(remainingItems, plan.outputCount);
            this.addPreviewOutputs(plan, demand.what, remainingItems, times);
            for (PatternInput input : plan.inputs) {
                long childUnits = NumberUtils.saturatedMultiply(input.units, times);
                demands.add(MissingDemandKey.input(input), childUnits);
            }
            this.mergeCraft(plan.pattern, times);
            this.addBytes(demand.what, remainingItems);
            this.bytes = NumberUtils.saturatedAdd(this.bytes, times);
            boolean bl = true;
            return bl;
        } finally {
            this.resolving.remove(demand.what);
        }
    }

    private void addPreviewOutputs(PatternPlan plan, AEKey what, long remainingItems, long times) {
        long targetRemaining = remainingItems;
        for (GenericStack output : plan.pattern.getOutputs()) {
            long producedAmount = NumberUtils.saturatedMultiply(output.amount(), times);
            if (producedAmount <= 0L) continue;
            if (targetRemaining > 0L && Objects.equals(output.what(), what)) {
                long consumed = Math.min(producedAmount, targetRemaining);
                targetRemaining -= consumed;
                producedAmount -= consumed;
            }
            if (producedAmount <= 0L) continue;
            this.addCounter(this.produced, output.what(), producedAmount);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean requestNode(AEKey what, long unitAmount, long requestedUnits, IPatternDetails.IInput parentInput, boolean ignoreExisting) {
        long remainingUnits;
        if (requestedUnits <= 0L) {
            return true;
        }
        if (unitAmount <= 0L) {
            return false;
        }
        long l = remainingUnits = ignoreExisting ? requestedUnits : this.consumeAvailableTemplates(parentInput, what, unitAmount, requestedUnits);
        if (remainingUnits <= 0L) {
            return true;
        }
        if (parentInput != null && parentInput.getRemainingKey(what) != null) {
            return false;
        }
        long remainingItems = NumberUtils.saturatedMultiply(unitAmount, remainingUnits);
        if (this.canEmitFor(what)) {
            this.addCounter(this.emitted, what, remainingItems);
            this.addBytes(what, remainingItems);
            return true;
        }
        if (!this.resolving.add(what)) {
            return false;
        }
        try {
            List<PatternPlan> plans = this.getCandidatePatterns(what);
            if (plans.isEmpty()) {
                this.addMissing(what, remainingItems);
                boolean bl = true;
                return bl;
            }
            if (plans.size() == 1) {
                PatternPlan plan = plans.get(0);
                boolean crafted = this.craftWithPlan(plan, what, remainingItems);
                if (crafted) {
                    this.putSelectedPattern(what, plan);
                }
                boolean bl = crafted;
                return bl;
            }
            for (PatternPlan plan : plans) {
                long branchMissing;
                CalculationSnapshot snapshot = this.snapshot();
                long missingBefore = this.missingAmount;
                if (this.craftWithPlan(plan, what, remainingItems) && (branchMissing = this.missingAmount - missingBefore) <= 0L) {
                    this.commit(snapshot);
                    this.putSelectedPattern(what, plan);
                    boolean bl = true;
                    return bl;
                }
                this.restore(snapshot);
            }
            PatternPlan plan = plans.get(0);
            CalculationSnapshot snapshot = this.snapshot();
            if (this.craftWithPlan(plan, what, remainingItems)) {
                this.commit(snapshot);
                this.putSelectedPattern(what, plan);
                boolean bl = true;
                return bl;
            }
            this.restore(snapshot);
            this.addMissing(what, remainingItems);
            boolean bl = true;
            return bl;
        } finally {
            this.resolving.remove(what);
        }
    }

    private boolean craftWithPlan(PatternPlan plan, AEKey what, long remainingItems) {
        long times = FastCraftingCalculation.ceilDiv(remainingItems, plan.outputCount);
        for (PatternInput input : plan.inputs) {
            long childUnits = NumberUtils.saturatedMultiply(input.units, times);
            if (this.requestNode(input.what, input.unitAmount, childUnits, input.parentInput, false)) continue;
            return false;
        }
        long targetRemaining = remainingItems;
        for (GenericStack output : plan.pattern.getOutputs()) {
            long producedAmount = NumberUtils.saturatedMultiply(output.amount(), times);
            if (producedAmount <= 0L) continue;
            if (targetRemaining > 0L && Objects.equals(output.what(), what)) {
                long consumed = Math.min(producedAmount, targetRemaining);
                targetRemaining -= consumed;
                producedAmount -= consumed;
            }
            if (producedAmount <= 0L) continue;
            this.addCounter(this.produced, output.what(), producedAmount);
        }
        if (targetRemaining > 0L) {
            long extracted = this.consumeProducedExact(what, targetRemaining);
            if (extracted != targetRemaining) {
                return false;
            }
            targetRemaining = 0L;
        }
        if (targetRemaining > 0L) {
            return false;
        }
        this.mergeCraft(plan.pattern, times);
        this.addBytes(what, remainingItems);
        this.bytes = NumberUtils.saturatedAdd(this.bytes, times);
        return true;
    }

    private long consumeAvailableTemplates(IPatternDetails.IInput parentInput, AEKey what, long unitAmount, long requestedUnits) {
        if (parentInput == null) {
            long remaining = this.consumeExactTemplates(this.produced, null, what, unitAmount, requestedUnits);
            return this.consumeStoredExactTemplates(what, unitAmount, remaining);
        }
        long remaining = requestedUnits;
        for (GenericStack possible : parentInput.getPossibleInputs()) {
            long templateAmount = possible.amount();
            if (templateAmount <= 0L) continue;
            remaining = this.consumeFuzzyTemplates(this.produced, null, parentInput, possible.what(), templateAmount, remaining);
            if (remaining <= 0L) {
                return 0L;
            }
            remaining = this.consumeStoredFuzzyTemplates(parentInput, possible.what(), templateAmount, remaining);
            if (remaining > 0L) continue;
            return 0L;
        }
        return remaining;
    }

    private long consumeStoredExactTemplates(AEKey what, long unitAmount, long requestedUnits) {
        if (requestedUnits <= 0L) {
            return 0L;
        }
        long available = this.getStoredAvailable(what);
        long availableUnits = available / unitAmount;
        if (availableUnits <= 0L) {
            return requestedUnits;
        }
        long extractedUnits = Math.min(availableUnits, requestedUnits);
        long extractedAmount = unitAmount * extractedUnits;
        this.addCounter(this.used, what, extractedAmount);
        this.addBytes(what, extractedAmount);
        return requestedUnits - extractedUnits;
    }

    private long consumeExactTemplates(KeyCounter source, KeyCounter usedTarget, AEKey what, long unitAmount, long requestedUnits) {
        if (requestedUnits <= 0L) {
            return 0L;
        }
        long available = source.get(what);
        long availableUnits = available / unitAmount;
        if (availableUnits <= 0L) {
            return requestedUnits;
        }
        long extractedUnits = Math.min(availableUnits, requestedUnits);
        long extractedAmount = unitAmount * extractedUnits;
        this.removeCounter(source, what, extractedAmount);
        if (usedTarget != null) {
            this.addCounter(usedTarget, what, extractedAmount);
            this.addBytes(what, extractedAmount);
        }
        return requestedUnits - extractedUnits;
    }

    private long consumeFuzzyTemplates(KeyCounter source, KeyCounter usedTarget, IPatternDetails.IInput input, AEKey template, long templateAmount, long requestedUnits) {
        if (requestedUnits <= 0L) {
            return 0L;
        }
        ArrayList<TemplateAmount> availableTemplates = new ArrayList<TemplateAmount>();
        for (Object2LongMap.Entry entry : source.findFuzzy(template, FuzzyMode.IGNORE_ALL)) {
            AEKey key = (AEKey) entry.getKey();
            if (!input.isValid(key, this.level)) continue;
            availableTemplates.add(new TemplateAmount(key, entry.getLongValue()));
        }
        long remaining = requestedUnits;
        for (TemplateAmount available : availableTemplates) {
            long availableUnits = available.amount / templateAmount;
            if (availableUnits <= 0L) continue;
            long extractedUnits = Math.min(availableUnits, remaining);
            long extractedAmount = templateAmount * extractedUnits;
            this.removeCounter(source, available.what, extractedAmount);
            if (usedTarget != null) {
                this.addCounter(usedTarget, available.what, extractedAmount);
                this.addBytes(available.what, extractedAmount);
            }
            if ((remaining -= extractedUnits) > 0L) continue;
            return 0L;
        }
        return remaining;
    }

    private long consumeStoredFuzzyTemplates(IPatternDetails.IInput input, AEKey template, long templateAmount, long requestedUnits) {
        if (requestedUnits <= 0L) {
            return 0L;
        }
        long remaining = requestedUnits;
        for (TemplateAmount available : this.getStoredFuzzyTemplates(input, template)) {
            long availableAmount = available.amount - this.used.get(available.what);
            long availableUnits = availableAmount / templateAmount;
            if (availableUnits <= 0L) continue;
            long extractedUnits = Math.min(availableUnits, remaining);
            long extractedAmount = templateAmount * extractedUnits;
            this.addCounter(this.used, available.what, extractedAmount);
            this.addBytes(available.what, extractedAmount);
            if ((remaining -= extractedUnits) > 0L) continue;
            return 0L;
        }
        return remaining;
    }

    private List<TemplateAmount> getStoredFuzzyTemplates(IPatternDetails.IInput input, AEKey template) {
        FuzzyTemplateKey cacheKey = new FuzzyTemplateKey(input, template);
        List<TemplateAmount> cached = this.sourceFuzzyTemplateCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ArrayList<TemplateAmount> templates = new ArrayList<TemplateAmount>();
        for (Object2LongMap.Entry entry : this.sourceInventory.findFuzzy(template, FuzzyMode.IGNORE_ALL)) {
            AEKey key = (AEKey) entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0L || !input.isValid(key, this.level)) continue;
            templates.add(new TemplateAmount(key, amount));
        }
        this.sourceFuzzyTemplateCache.put(cacheKey, templates);
        return templates;
    }

    private long getStoredAvailable(AEKey what) {
        long usedAmount;
        long available = this.sourceInventory.get(what);
        return available > (usedAmount = this.used.get(what)) ? available - usedAmount : 0L;
    }

    private long consumeProducedExact(AEKey what, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long available = this.produced.get(what);
        long extracted = Math.min(available, amount);
        if (extracted > 0L) {
            this.removeCounter(this.produced, what, extracted);
        }
        return extracted;
    }

    private List<PatternPlan> getCandidatePatterns(AEKey what) {
        PatternPlan cached;
        List<PatternPlan> plans = this.getCachedCandidatePatterns(what);
        if (plans.size() > 1) {
            this.markMultiplePaths();
        }
        if ((cached = this.selectedPatterns.get(what)) == null || plans.isEmpty() || plans.get(0) == cached) {
            return plans;
        }
        ArrayList<PatternPlan> preferredFirst = new ArrayList<PatternPlan>(plans.size() + 1);
        preferredFirst.add(cached);
        for (PatternPlan plan : plans) {
            if (plan == cached) continue;
            preferredFirst.add(plan);
        }
        return preferredFirst;
    }

    private List<PatternPlan> getCachedCandidatePatterns(AEKey what) {
        List<PatternPlan> cachedPlans = this.candidatePatternCache.get(what);
        if (cachedPlans != null) {
            return cachedPlans;
        }
        if (this.preferFastMissingPlan) {
            return this.getFastMissingCandidatePatterns(what);
        }
        CandidatePatternCacheKey cacheKey = new CandidatePatternCacheKey(this.craftingCacheOwner, this.craftingPatternVersion, this.levelCacheKey, what);
        cachedPlans = FastCraftingCalculation.getSharedCandidatePatterns(cacheKey);
        if (cachedPlans != null) {
            List<PatternPlan> result = this.orderCandidatePlans(cachedPlans);
            this.candidatePatternCache.put(what, result);
            return result;
        }
        Collection<IPatternDetails> patterns = this.getCraftingFor(what);
        if (patterns.isEmpty()) {
            List<PatternPlan> result = List.of();
            this.candidatePatternCache.put(what, result);
            FastCraftingCalculation.putSharedCandidatePatterns(cacheKey, result);
            return List.of();
        }
        ArrayList<PatternPlan> plans = new ArrayList<PatternPlan>(patterns.size());
        for (IPatternDetails pattern : patterns) {
            PatternPlan plan = this.getPatternPlan(pattern, what);
            if (plan == null) continue;
            plans.add(plan);
        }
        List<PatternPlan> result = plans.isEmpty() ? List.of() : List.copyOf(plans);
        FastCraftingCalculation.putSharedCandidatePatterns(cacheKey, result);
        result = this.orderCandidatePlans(result);
        this.candidatePatternCache.put(what, result);
        return result;
    }

    private List<PatternPlan> getFastMissingCandidatePatterns(AEKey what) {
        boolean multipleCandidatePatterns;
        CandidatePatternCacheKey cacheKey = new CandidatePatternCacheKey(this.craftingCacheOwner, this.craftingPatternVersion, this.levelCacheKey, what);
        List<PatternPlan> cachedPlans = FastCraftingCalculation.getSharedCandidatePatterns(cacheKey);
        if (cachedPlans != null) {
            if (FastCraftingCalculation.hasSharedCandidateMultiplePaths(cacheKey)) {
                this.markMultiplePaths();
            }
            this.candidatePatternCache.put(what, cachedPlans);
            return cachedPlans;
        }
        Collection<IPatternDetails> patterns = this.getCraftingFor(what);
        boolean bl = multipleCandidatePatterns = patterns.size() > 1;
        if (patterns.isEmpty()) {
            List<PatternPlan> result = List.of();
            this.candidatePatternCache.put(what, result);
            FastCraftingCalculation.putSharedCandidatePatterns(cacheKey, result, false);
            return result;
        }
        if (multipleCandidatePatterns) {
            this.markMultiplePaths();
        }
        for (IPatternDetails pattern : patterns) {
            PatternPlan plan = this.getPatternPlan(pattern, what);
            if (plan == null) continue;
            List<PatternPlan> result = List.of(plan);
            this.candidatePatternCache.put(what, result);
            FastCraftingCalculation.putSharedCandidatePatterns(cacheKey, result, multipleCandidatePatterns);
            return result;
        }
        List result = List.of();
        this.candidatePatternCache.put(what, result);
        FastCraftingCalculation.putSharedCandidatePatterns(cacheKey, result, multipleCandidatePatterns);
        return result;
    }

    private List<PatternPlan> orderCandidatePlans(List<PatternPlan> plans) {
        if (plans.size() <= 1) {
            return plans;
        }
        ArrayList<PatternPlan> sorted = new ArrayList<PatternPlan>(plans);
        sorted.sort(this::comparePatternPlans);
        return List.copyOf(sorted);
    }

    private PatternPlan getPatternPlan(IPatternDetails pattern, AEKey what) {
        Map plansByOutput = this.patternPlanCache.computeIfAbsent(pattern, ignored -> new HashMap());
        PatternPlan cached = (PatternPlan) plansByOutput.get(what);
        if (cached != null) {
            return cached;
        }
        PatternPlanCacheKey cacheKey = new PatternPlanCacheKey(this.craftingCacheOwner, this.craftingPatternVersion, this.levelCacheKey, pattern, what);
        cached = FastCraftingCalculation.getSharedPatternPlan(cacheKey);
        if (cached != null) {
            plansByOutput.put(what, cached);
            return cached;
        }
        PatternPlan plan = this.buildPatternPlan(pattern, what);
        if (plan != null) {
            plansByOutput.put(what, plan);
            FastCraftingCalculation.putSharedPatternPlan(cacheKey, plan);
        }
        return plan;
    }

    private PatternPlan buildPatternPlan(IPatternDetails pattern, AEKey what) {
        long outputCount = FastCraftingCalculation.getOutputCount(pattern, what);
        if (outputCount <= 0L) {
            return null;
        }
        IPatternDetails.IInput[] rawInputs = pattern.getInputs();
        ArrayList<PatternInput> inputs = new ArrayList<PatternInput>(rawInputs.length);
        for (int i = 0; i < rawInputs.length; ++i) {
            IPatternDetails.IInput input = rawInputs[i];
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                return null;
            }
            if (AEUtils.isIntegratedCircuit(possibleInputs[0].what())) continue;
            PatternInput plannedInput = this.buildPatternInput(input, possibleInputs);
            if (plannedInput == null || input.getRemainingKey(plannedInput.what) != null || FastCraftingCalculation.isOutput(pattern, plannedInput.what)) {
                return null;
            }
            FastCraftingCalculation.mergePatternInput(inputs, plannedInput);
        }
        return new PatternPlan(pattern, outputCount, inputs.toArray(new PatternInput[0]));
    }

    private PatternInput buildPatternInput(IPatternDetails.IInput input, GenericStack[] possibleInputs) {
        GenericStack firstInput = possibleInputs[0];
        AEKey childKey = this.resolveCraftedKey(input, possibleInputs, firstInput.what());
        IPatternDetails.IInput requestInput = FastCraftingCalculation.shouldUseExactInput(input, possibleInputs, firstInput.what(), childKey) ? null : input;
        return new PatternInput(childKey, firstInput.amount(), input.getMultiplier(), requestInput, new AE2CraftingRequestMergeKey(childKey, firstInput.amount(), requestInput));
    }

    private AEKey resolveCraftedKey(IPatternDetails.IInput input, GenericStack[] possibleInputs, AEKey requestedKey) {
        if (this.isCraftableOrEmittable(requestedKey)) {
            return requestedKey;
        }
        if (possibleInputs.length == 1) {
            return requestedKey;
        }
        long firstAmount = possibleInputs[0].amount();
        for (GenericStack possible : possibleInputs) {
            if (possible.amount() != firstAmount || !this.isCraftableOrEmittable(possible.what())) continue;
            return possible.what();
        }
        for (GenericStack possible : possibleInputs) {
            AEKey fuzzyCraftable;
            if (possible.amount() != firstAmount || (fuzzyCraftable = this.getFuzzyCraftable(input, possible.what())) == null) continue;
            return fuzzyCraftable;
        }
        return requestedKey;
    }

    private static boolean shouldUseExactInput(IPatternDetails.IInput input, GenericStack[] possibleInputs, AEKey firstInputKey, AEKey childKey) {
        return possibleInputs.length == 1 && Objects.equals(firstInputKey, childKey) && input.getRemainingKey(firstInputKey) == null;
    }

    private static void mergePatternInput(List<PatternInput> inputs, PatternInput input) {
        for (int i = 0; i < inputs.size(); ++i) {
            PatternInput existing = inputs.get(i);
            if (!Objects.equals(existing.mergeKey, input.mergeKey)) continue;
            inputs.set(i, existing.withAdditionalUnits(input.units));
            return;
        }
        inputs.add(input);
    }

    private int estimateMissingInputs(PatternPlan plan) {
        int missingInputs = 0;
        for (PatternInput input : plan.inputs) {
            if (this.isCraftableOrEmittable(input.what) || this.getStoredAvailable(input.what) > 0L) continue;
            ++missingInputs;
        }
        return missingInputs;
    }

    private boolean canEmitFor(AEKey what) {
        Boolean cached = this.canEmitCache.get(what);
        if (cached != null) {
            return cached;
        }
        boolean canEmit = this.craftingService.canEmitFor(what);
        this.canEmitCache.put(what, canEmit);
        return canEmit;
    }

    private boolean isCraftableOrEmittable(AEKey what) {
        Boolean cached = this.craftableOrEmittableCache.get(what);
        if (cached != null) {
            return cached;
        }
        boolean craftable = this.canEmitFor(what) || !this.getCraftingFor(what).isEmpty();
        this.craftableOrEmittableCache.put(what, craftable);
        return craftable;
    }

    private Collection<IPatternDetails> getCraftingFor(AEKey what) {
        Collection<IPatternDetails> cached = this.craftingForCache.get(what);
        if (cached != null) {
            return cached;
        }
        Collection patterns = this.craftingService.getCraftingFor(what);
        this.craftingForCache.put(what, patterns);
        return patterns;
    }

    private AEKey getFuzzyCraftable(IPatternDetails.IInput input, AEKey what) {
        FuzzyCraftableKey cacheKey = new FuzzyCraftableKey(input, what);
        if (this.fuzzyCraftableCache.containsKey(cacheKey)) {
            return this.fuzzyCraftableCache.get(cacheKey);
        }
        AEKey fuzzyCraftable = this.craftingService.getFuzzyCraftable(what, candidate -> input.isValid(candidate, this.level));
        this.fuzzyCraftableCache.put(cacheKey, fuzzyCraftable);
        return fuzzyCraftable;
    }

    private int comparePatternPlans(PatternPlan left, PatternPlan right) {
        int result = Integer.compare(this.estimateMissingInputs(left), this.estimateMissingInputs(right));
        if (result != 0) {
            return result;
        }
        result = Integer.compare(left.inputs.length, right.inputs.length);
        if (result != 0) {
            return result;
        }
        return Long.compare(right.outputCount, left.outputCount);
    }

    private static boolean isOutput(IPatternDetails pattern, AEKey what) {
        for (GenericStack output : pattern.getOutputs()) {
            if (!what.matches(output)) continue;
            return true;
        }
        return false;
    }

    private static long getOutputCount(IPatternDetails pattern, AEKey what) {
        long amount = 0L;
        for (GenericStack output : pattern.getOutputs()) {
            if (!what.matches(output)) continue;
            amount = NumberUtils.saturatedAdd(amount, output.amount());
        }
        return amount;
    }

    private void addBytes(AEKey what, long amount) {
        int amountPerByte = this.amountPerByteCache.getInt(what);
        if (amountPerByte == 0) {
            amountPerByte = Math.max(1, what.getAmountPerByte());
            this.amountPerByteCache.put(what, amountPerByte);
        }
        this.bytes = NumberUtils.saturatedAdd(this.bytes, FastCraftingCalculation.ceilDiv(amount, amountPerByte));
    }

    private void addMissing(AEKey what, long amount) {
        if (amount <= 0L) {
            return;
        }
        this.addCounter(this.missing, what, amount);
        this.missingAmount = NumberUtils.saturatedAdd(this.missingAmount, amount);
        this.addBytes(what, amount);
    }

    private CalculationSnapshot snapshot() {
        CalculationSnapshot snapshot = new CalculationSnapshot(this.counterChanges.size(), this.craftChanges.size(), this.selectedPatternChanges.size(), this.bytes, this.missingAmount, this.multiplePaths);
        this.activeSnapshots.add(snapshot);
        return snapshot;
    }

    private void commit(CalculationSnapshot snapshot) {
        this.closeSnapshot(snapshot);
        if (this.activeSnapshots.isEmpty()) {
            this.clearChangeTracking();
        }
    }

    private void restore(CalculationSnapshot snapshot) {
        Record change;
        int i;
        for (i = this.counterChanges.size() - 1; i >= snapshot.counterChangeCount; --i) {
            change = this.counterChanges.get(i);
            ((CounterChange) change).restore();
            this.restoreCounterChangeIndex((CounterChange) change);
        }
        this.counterChanges.subList(snapshot.counterChangeCount, this.counterChanges.size()).clear();
        for (i = this.craftChanges.size() - 1; i >= snapshot.craftChangeCount; --i) {
            change = this.craftChanges.get(i);
            ((CraftChange) change).restore(this.crafts);
            this.restoreCraftChangeIndex((CraftChange) change);
        }
        this.craftChanges.subList(snapshot.craftChangeCount, this.craftChanges.size()).clear();
        for (i = this.selectedPatternChanges.size() - 1; i >= snapshot.selectedPatternChangeCount; --i) {
            change = this.selectedPatternChanges.get(i);
            ((SelectedPatternChange) change).restore(this.selectedPatterns);
            this.restoreSelectedPatternChangeIndex((SelectedPatternChange) change);
        }
        this.selectedPatternChanges.subList(snapshot.selectedPatternChangeCount, this.selectedPatternChanges.size()).clear();
        this.bytes = snapshot.bytes;
        this.missingAmount = snapshot.missingAmount;
        this.multiplePaths = snapshot.multiplePaths;
        this.closeSnapshot(snapshot);
    }

    private void addCounter(KeyCounter counter, AEKey key, long amount) {
        if (amount <= 0L) {
            return;
        }
        this.recordCounterChange(counter, key);
        counter.add(key, amount);
    }

    private void addCounter(FastKeyCounter counter, AEKey key, long amount) {
        if (amount <= 0L) {
            return;
        }
        this.recordCounterChange(counter, key);
        counter.add(key, amount);
    }

    private void removeCounter(KeyCounter counter, AEKey key, long amount) {
        if (amount <= 0L) {
            return;
        }
        this.recordCounterChange(counter, key);
        counter.remove(key, amount);
    }

    private void removeCounter(FastKeyCounter counter, AEKey key, long amount) {
        if (amount <= 0L) {
            return;
        }
        this.recordCounterChange(counter, key);
        counter.remove(key, amount);
    }

    private void mergeCraft(IPatternDetails pattern, long times) {
        this.recordCraftChange(pattern);
        this.crafts.merge(pattern, times, NumberUtils::saturatedAdd);
    }

    private void putSelectedPattern(AEKey what, PatternPlan plan) {
        this.recordSelectedPatternChange(what);
        this.selectedPatterns.put(what, plan);
    }

    private void recordCounterChange(Object counter, AEKey key) {
        if (this.activeSnapshots.isEmpty()) {
            return;
        }
        int boundary = this.currentSnapshot().counterChangeCount;
        CounterChangeKey changeKey = new CounterChangeKey(counter, key);
        Integer previousIndex = this.counterChangeIndexes.get(changeKey);
        if (previousIndex != null && previousIndex >= boundary) {
            return;
        }
        this.counterChangeIndexes.put(changeKey, this.counterChanges.size());
        this.counterChanges.add(new CounterChange(changeKey, FastCraftingCalculation.getCounterAmount(counter, key), previousIndex == null ? -1 : previousIndex));
    }

    private void recordCraftChange(IPatternDetails pattern) {
        if (this.activeSnapshots.isEmpty()) {
            return;
        }
        int boundary = this.currentSnapshot().craftChangeCount;
        Integer previousIndex = this.craftChangeIndexes.get(pattern);
        if (previousIndex != null && previousIndex >= boundary) {
            return;
        }
        this.craftChangeIndexes.put(pattern, this.craftChanges.size());
        this.craftChanges.add(new CraftChange(pattern, this.crafts.get(pattern), this.crafts.containsKey(pattern), previousIndex == null ? -1 : previousIndex));
    }

    private void recordSelectedPatternChange(AEKey what) {
        if (this.activeSnapshots.isEmpty()) {
            return;
        }
        int boundary = this.currentSnapshot().selectedPatternChangeCount;
        Integer previousIndex = this.selectedPatternChangeIndexes.get(what);
        if (previousIndex != null && previousIndex >= boundary) {
            return;
        }
        this.selectedPatternChangeIndexes.put(what, this.selectedPatternChanges.size());
        this.selectedPatternChanges.add(new SelectedPatternChange(what, this.selectedPatterns.get(what), this.selectedPatterns.containsKey(what), previousIndex == null ? -1 : previousIndex));
    }

    private CalculationSnapshot currentSnapshot() {
        return this.activeSnapshots.get(this.activeSnapshots.size() - 1);
    }

    private void closeSnapshot(CalculationSnapshot snapshot) {
        int lastIndex = this.activeSnapshots.size() - 1;
        if (lastIndex >= 0 && this.activeSnapshots.get(lastIndex) == snapshot) {
            this.activeSnapshots.remove(lastIndex);
        } else {
            this.activeSnapshots.remove(snapshot);
        }
    }

    private void restoreCounterChangeIndex(CounterChange change) {
        if (change.previousIndex >= 0) {
            this.counterChangeIndexes.put(change.changeKey, change.previousIndex);
        } else {
            this.counterChangeIndexes.remove(change.changeKey);
        }
    }

    private void restoreCraftChangeIndex(CraftChange change) {
        if (change.previousIndex >= 0) {
            this.craftChangeIndexes.put(change.pattern, change.previousIndex);
        } else {
            this.craftChangeIndexes.remove(change.pattern);
        }
    }

    private void restoreSelectedPatternChangeIndex(SelectedPatternChange change) {
        if (change.previousIndex >= 0) {
            this.selectedPatternChangeIndexes.put(change.what, change.previousIndex);
        } else {
            this.selectedPatternChangeIndexes.remove(change.what);
        }
    }

    private void clearChangeTracking() {
        this.counterChanges.clear();
        this.craftChanges.clear();
        this.selectedPatternChanges.clear();
        this.counterChangeIndexes.clear();
        this.craftChangeIndexes.clear();
        this.selectedPatternChangeIndexes.clear();
    }

    private void markMultiplePaths() {
        this.multiplePaths = true;
    }

    private static long getCounterAmount(Object counter, AEKey key) {
        if (counter instanceof FastKeyCounter) {
            FastKeyCounter fastCounter = (FastKeyCounter) counter;
            return fastCounter.get(key);
        }
        return ((KeyCounter) counter).get(key);
    }

    private static void restoreCounterAmount(Object counter, AEKey key, long amount) {
        if (counter instanceof FastKeyCounter) {
            FastKeyCounter fastCounter = (FastKeyCounter) counter;
            fastCounter.set(key, amount);
            return;
        }
        KeyCounter keyCounter = (KeyCounter) counter;
        if (amount == 0L) {
            keyCounter.remove(key);
        } else {
            keyCounter.set(key, amount);
        }
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0L) {
            return 0L;
        }
        if (divisor <= 1L) {
            return value;
        }
        return 1L + (value - 1L) / divisor;
    }

    private static long scaleFloor(long value, long numerator, long denominator) {
        if (value <= 0L || numerator <= 0L || denominator <= 0L) {
            return 0L;
        }
        if (numerator >= denominator) {
            return value;
        }
        if (value <= Long.MAX_VALUE / numerator) {
            return value * numerator / denominator;
        }
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static List<PatternPlan> getSharedCandidatePatterns(CandidatePatternCacheKey key) {
        Map<CandidatePatternCacheKey, List<PatternPlan>> map = SHARED_CANDIDATE_PATTERN_CACHE;
        synchronized (map) {
            return SHARED_CANDIDATE_PATTERN_CACHE.get(key);
        }
    }

    private static void putSharedCandidatePatterns(CandidatePatternCacheKey key, List<PatternPlan> plans) {
        FastCraftingCalculation.putSharedCandidatePatterns(key, plans, plans.size() > 1);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void putSharedCandidatePatterns(CandidatePatternCacheKey key, List<PatternPlan> plans, boolean multiplePaths) {
        Map<CandidatePatternCacheKey, List<PatternPlan>> map = SHARED_CANDIDATE_PATTERN_CACHE;
        synchronized (map) {
            SHARED_CANDIDATE_PATTERN_CACHE.put(key, plans);
            SHARED_CANDIDATE_MULTIPLE_PATH_CACHE.put(key, multiplePaths);
            FastCraftingCalculation.trimSharedCandidatePatternCache();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static boolean hasSharedCandidateMultiplePaths(CandidatePatternCacheKey key) {
        Map<CandidatePatternCacheKey, List<PatternPlan>> map = SHARED_CANDIDATE_PATTERN_CACHE;
        synchronized (map) {
            return Boolean.TRUE.equals(SHARED_CANDIDATE_MULTIPLE_PATH_CACHE.get(key));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static PatternPlan getSharedPatternPlan(PatternPlanCacheKey key) {
        Map<PatternPlanCacheKey, PatternPlan> map = SHARED_PATTERN_PLAN_CACHE;
        synchronized (map) {
            return SHARED_PATTERN_PLAN_CACHE.get(key);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void putSharedPatternPlan(PatternPlanCacheKey key, PatternPlan plan) {
        Map<PatternPlanCacheKey, PatternPlan> map = SHARED_PATTERN_PLAN_CACHE;
        synchronized (map) {
            SHARED_PATTERN_PLAN_CACHE.put(key, plan);
            FastCraftingCalculation.trimSharedCache(SHARED_PATTERN_PLAN_CACHE);
        }
    }

    private static void trimSharedCache(Map<?, ?> cache) {
        while (cache.size() > 8192) {
            Iterator<?> iterator = cache.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static void trimSharedCandidatePatternCache() {
        while (SHARED_CANDIDATE_PATTERN_CACHE.size() > 8192) {
            Iterator<CandidatePatternCacheKey> iterator = SHARED_CANDIDATE_PATTERN_CACHE.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            CandidatePatternCacheKey key = iterator.next();
            iterator.remove();
            SHARED_CANDIDATE_MULTIPLE_PATH_CACHE.remove(key);
        }
    }

    private static final class FastKeyCounter {

        private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap();

        private FastKeyCounter() {}

        private void add(AEKey key, long amount) {
            if (amount <= 0L) {
                return;
            }
            this.amounts.put(key, NumberUtils.saturatedAdd(this.amounts.getLong(key), amount));
        }

        private void remove(AEKey key, long amount) {
            if (amount <= 0L) {
                return;
            }
            this.set(key, this.amounts.getLong(key) - amount);
        }

        private long get(AEKey key) {
            return this.amounts.getLong(key);
        }

        private void set(AEKey key, long amount) {
            if (amount == 0L) {
                this.amounts.removeLong(key);
            } else {
                this.amounts.put(key, amount);
            }
        }

        private KeyCounter toKeyCounter() {
            KeyCounter counter = new KeyCounter();
            for (Object2LongMap.Entry entry : Object2LongMaps.fastIterable(this.amounts)) {
                long amount = entry.getLongValue();
                if (amount == 0L) continue;
                counter.set((AEKey) entry.getKey(), amount);
            }
            return counter;
        }
    }

    private record CraftLessAttempt(ICraftingPlan plan, FastCraftingCalculation calculation) {}

    private static final class MissingDemandQueue {

        private final ArrayDeque<MissingDemandKey> queue = new ArrayDeque();
        private final Object2LongOpenHashMap<MissingDemandKey> amounts = new Object2LongOpenHashMap();

        private MissingDemandQueue() {}

        private void add(MissingDemandKey key, long units) {
            if (units <= 0L) {
                return;
            }
            long previous = this.amounts.getLong(key);
            this.amounts.put(key, NumberUtils.saturatedAdd(previous, units));
            if (previous == 0L) {
                this.queue.addLast(key);
            }
        }

        private boolean isEmpty() {
            return this.queue.isEmpty();
        }

        private MissingDemand poll() {
            MissingDemandKey key = this.queue.removeFirst();
            long units = this.amounts.removeLong(key);
            return new MissingDemand(key, units);
        }
    }

    private static final class MissingDemandKey {

        private final AEKey what;
        private final long unitAmount;
        private final IPatternDetails.IInput parentInput;
        private final boolean ignoreExisting;
        private final Object mergeKey;
        private final int hash;

        private MissingDemandKey(AEKey what, long unitAmount, IPatternDetails.IInput parentInput, boolean ignoreExisting, Object mergeKey) {
            this.what = what;
            this.unitAmount = unitAmount;
            this.parentInput = parentInput;
            this.ignoreExisting = ignoreExisting;
            this.mergeKey = mergeKey;
            this.hash = Objects.hash(this.mergeKey, this.ignoreExisting);
        }

        private static MissingDemandKey root(AEKey what) {
            return new MissingDemandKey(what, 1L, null, true, new AE2CraftingRequestMergeKey(what, 1L, null));
        }

        private static MissingDemandKey input(PatternInput input) {
            return new MissingDemandKey(input.what, input.unitAmount, input.parentInput, false, input.mergeKey);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MissingDemandKey)) {
                return false;
            }
            MissingDemandKey other = (MissingDemandKey) obj;
            return this.ignoreExisting == other.ignoreExisting && Objects.equals(this.mergeKey, other.mergeKey);
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private record MissingDemand(MissingDemandKey key, long units) {}

    private record PatternPlan(IPatternDetails pattern, long outputCount, PatternInput[] inputs) {}

    private record PatternInput(AEKey what, long unitAmount, long units, IPatternDetails.IInput parentInput, Object mergeKey) {

        private PatternInput withAdditionalUnits(long additionalUnits) {
            return new PatternInput(this.what, this.unitAmount, NumberUtils.saturatedAdd(this.units, additionalUnits), this.parentInput, this.mergeKey);
        }
    }

    private record CalculationSnapshot(int counterChangeCount, int craftChangeCount, int selectedPatternChangeCount, long bytes, long missingAmount, boolean multiplePaths) {}

    private record TemplateAmount(AEKey what, long amount) {}

    private static final class FuzzyTemplateKey {

        private final IPatternDetails.IInput input;
        private final AEKey template;
        private final int hash;

        private FuzzyTemplateKey(IPatternDetails.IInput input, AEKey template) {
            this.input = input;
            this.template = template;
            this.hash = 31 * System.identityHashCode(input) + template.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FuzzyTemplateKey)) {
                return false;
            }
            FuzzyTemplateKey other = (FuzzyTemplateKey) obj;
            return this.input == other.input && this.template.equals(other.template);
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private static final class CandidatePatternCacheKey {

        private final Object craftingService;
        private final long craftingPatternVersion;
        private final Object level;
        private final AEKey what;
        private final int hash;

        private CandidatePatternCacheKey(Object craftingService, long craftingPatternVersion, Object level, AEKey what) {
            this.craftingService = craftingService;
            this.craftingPatternVersion = craftingPatternVersion;
            this.level = level;
            this.what = what;
            this.hash = Objects.hash(System.identityHashCode(craftingService), craftingPatternVersion, System.identityHashCode(level), what);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CandidatePatternCacheKey)) {
                return false;
            }
            CandidatePatternCacheKey other = (CandidatePatternCacheKey) obj;
            return this.craftingService == other.craftingService && this.craftingPatternVersion == other.craftingPatternVersion && this.level == other.level && Objects.equals(this.what, other.what);
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private static final class PatternPlanCacheKey {

        private final Object craftingService;
        private final long craftingPatternVersion;
        private final Object level;
        private final IPatternDetails pattern;
        private final AEKey what;
        private final int hash;

        private PatternPlanCacheKey(Object craftingService, long craftingPatternVersion, Object level, IPatternDetails pattern, AEKey what) {
            this.craftingService = craftingService;
            this.craftingPatternVersion = craftingPatternVersion;
            this.level = level;
            this.pattern = pattern;
            this.what = what;
            this.hash = Objects.hash(System.identityHashCode(craftingService), craftingPatternVersion, System.identityHashCode(level), System.identityHashCode(pattern), what);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PatternPlanCacheKey)) {
                return false;
            }
            PatternPlanCacheKey other = (PatternPlanCacheKey) obj;
            return this.craftingService == other.craftingService && this.craftingPatternVersion == other.craftingPatternVersion && this.level == other.level && this.pattern == other.pattern && Objects.equals(this.what, other.what);
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private static final class FuzzyCraftableKey {

        private final AEKey what;
        private final Class<?> inputClass;
        private final GenericStack[] possibleInputs;
        private final AEKey[] remainingKeys;
        private final int hash;

        private FuzzyCraftableKey(IPatternDetails.IInput input, AEKey what) {
            this.what = what;
            this.inputClass = input.getClass();
            this.possibleInputs = (GenericStack[]) input.getPossibleInputs().clone();
            this.remainingKeys = new AEKey[this.possibleInputs.length];
            for (int i = 0; i < this.possibleInputs.length; ++i) {
                this.remainingKeys[i] = input.getRemainingKey(this.possibleInputs[i].what());
            }
            this.hash = Objects.hash(what, this.inputClass, Arrays.hashCode(this.possibleInputs), Arrays.hashCode(this.remainingKeys));
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FuzzyCraftableKey)) {
                return false;
            }
            FuzzyCraftableKey other = (FuzzyCraftableKey) obj;
            return Objects.equals(this.what, other.what) && Objects.equals(this.inputClass, other.inputClass) && Arrays.equals(this.possibleInputs, other.possibleInputs) && Arrays.equals(this.remainingKeys, other.remainingKeys);
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private record CounterChange(CounterChangeKey changeKey, long oldAmount, int previousIndex) {

        private void restore() {
            FastCraftingCalculation.restoreCounterAmount(this.changeKey.counter, this.changeKey.key, this.oldAmount);
        }
    }

    private record CraftChange(IPatternDetails pattern, Long oldTimes, boolean hadOldTimes, int previousIndex) {

        private void restore(Map<IPatternDetails, Long> crafts) {
            if (this.hadOldTimes) {
                crafts.put(this.pattern, this.oldTimes);
            } else {
                crafts.remove(this.pattern);
            }
        }
    }

    private record SelectedPatternChange(AEKey what, PatternPlan oldPlan, boolean hadOldPlan, int previousIndex) {

        private void restore(Map<AEKey, PatternPlan> selectedPatterns) {
            if (this.hadOldPlan) {
                selectedPatterns.put(this.what, this.oldPlan);
            } else {
                selectedPatterns.remove(this.what);
            }
        }
    }

    private static final class CounterChangeKey {

        private final Object counter;
        private final AEKey key;
        private final int hash;

        private CounterChangeKey(Object counter, AEKey key) {
            this.counter = counter;
            this.key = key;
            this.hash = 31 * System.identityHashCode(counter) + key.hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CounterChangeKey)) {
                return false;
            }
            CounterChangeKey other = (CounterChangeKey) obj;
            return this.counter == other.counter && Objects.equals(this.key, other.key);
        }

        public int hashCode() {
            return this.hash;
        }
    }
}
