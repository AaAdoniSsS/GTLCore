package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.AE2CraftingRequestMergeKey;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeProcess;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastCraftBranchFailure;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.BoundaryFailureDependencies;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.CandidateSegmentResult;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics.BarrierReason;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastNodeProgram;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.*;
import appeng.crafting.*;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(CraftingTreeNode.class)
public abstract class CraftingTreeNodeMixin implements ICraftingTreeNode {

    @Unique
    private static final AEKey[] gTLCore$EMPTY_MAX_FAST_ANCESTORS = new AEKey[0];

    @Unique
    private IPatternDetails patternDetails;
    @Unique
    private InputTemplate gTLCore$directTemplate;
    @Unique
    private InputTemplate gTLCore$processingTemplate;
    @Unique
    private Object gTLCore$requestMergeKey;
    @Unique
    private MaxFastNodeProgram gTLCore$maxFastProgram;
    @Unique
    private MaxFastExecutor gTLCore$maxFastExecutor;
    @Unique
    private long gTLCore$maxFastLogicalNodeCount = -1L;
    @Unique
    private ICraftingService gTLCore$craftingService;
    @Unique
    private AEKey[] gTLCore$maxFastExternalAncestors = gTLCore$EMPTY_MAX_FAST_ANCESTORS;
    @Unique
    private ICraftingTreeNode.MaxFastRuntimeTracker gTLCore$maxFastRuntimeTracker;
    @Unique
    private long gTLCore$maxFastActiveGeneration;
    @Unique
    private boolean gTLCore$maxFastCandidateGraphsEligible;
    @Unique
    private boolean gTLCore$maxFastCycleCandidateGraphEligible;
    @Unique
    private ICraftingTreeNode[] gTLCore$maxFastCandidateGraphRoots;
    @Unique
    private MaxFastExecutor[] gTLCore$maxFastCandidateGraphExecutors;
    @Unique
    private BoundaryFailureDependencies gTLCore$maxFastCandidateGraphFailures;
    @Unique
    private ICraftingTreeNode gTLCore$maxFastCycleCandidateGraphRoot;
    @Unique
    private MaxFastExecutor gTLCore$maxFastCycleCandidateGraphExecutor;
    @Unique
    private BoundaryFailureDependencies gTLCore$maxFastCycleCandidateGraphFailures;

    @Shadow(remap = false)
    @Final
    @Mutable
    final IPatternDetails.@Nullable IInput parentInput;
    @Shadow(remap = false)
    @Final
    @Mutable
    private final Level level;
    @Shadow(remap = false)
    @Final
    @Mutable
    private final AEKey what;
    @Shadow(remap = false)
    @Final
    private appeng.crafting.CraftingCalculation job;
    @Shadow(remap = false)
    @Final
    private CraftingTreeProcess parent;
    @Shadow(remap = false)
    private ArrayList<CraftingTreeProcess> nodes;
    @Shadow(remap = false)
    @Final
    private long amount;
    @Shadow(remap = false)
    @Final
    private boolean canEmit;

    public CraftingTreeNodeMixin(IPatternDetails.@Nullable IInput parentInput, Level level, AEKey what) {
        this.parentInput = parentInput;
        this.level = level;
        this.what = what;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void CraftingTreeNode(ICraftingService cc, appeng.crafting.CraftingCalculation job, AEKey what, long amount, CraftingTreeProcess par, int slot, CallbackInfo ci) {
        this.gTLCore$craftingService = cc;
        this.patternDetails = slot == -1 ? null : ((ICraftingTreeProcess) par).getDetails();
    }

    /**
     * @author .
     * @reason Prevent returned-container counters from wrapping for very large crafting requests.
     */
    @Overwrite(remap = false)
    private void addContainerItems(AEKey template, long multiplier, @Nullable KeyCounter outputList) {
        if (outputList == null || this.parentInput == null) {
            return;
        }
        AEKey remainingKey = this.parentInput.getRemainingKey(template);
        if (remainingKey != null) {
            outputList.set(
                    remainingKey,
                    NumberUtils.saturatedAdd(outputList.get(remainingKey), multiplier));
        }
    }

    @Shadow(remap = false)
    private void buildChildPatterns() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    void request(CraftingSimulationState inv, long requestedAmount, @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    abstract long getNodeCount();

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    private Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv) {
        if (this.parentInput == null) {
            return List.of(gTLCore$getDirectTemplate());
        } else if (this.patternDetails instanceof AEProcessingPattern) {
            return List.of(gTLCore$getProcessingTemplate());
        }
        return ((ICraftingCalculation) this.job).gtlcore$getCachedTemplates(
                inv,
                this.parentInput,
                this.level,
                this.what,
                () -> CraftingCpuHelper.getValidItemTemplates(inv, this.parentInput, level));
    }

    @Override
    @Unique
    public void legacyRequest(CraftingSimulationState inv, long requestedAmount,
                              @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        request(inv, requestedAmount, containerItems);
    }

    @Override
    @Unique
    public void fastRequest(CraftingSimulationState inv, long requestedAmount,
                            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        calculation.gtlcore$handlePausing();

        inv.addStackBytes(what, amount, requestedAmount);

        requestedAmount = gTLCore$extractAvailableTemplates(inv, requestedAmount, containerItems, calculation);
        if (requestedAmount == 0) {
            return;
        }

        addContainerItems(what, requestedAmount, containerItems);

        if (this.canEmit) {
            inv.emitItems(this.what, NumberUtils.saturatedMultiply(this.amount, requestedAmount));
            return;
        }

        buildChildPatterns();
        long totalRequestedItems = NumberUtils.saturatedMultiply(requestedAmount, this.amount);
        if (this.nodes.size() == 1) {
            final ICraftingTreeProcess pro = (ICraftingTreeProcess) (this.nodes.get(0));
            var craftedPerPattern = pro.getOutputCountTest(this.what);

            while (pro.getPossible() && totalRequestedItems > 0) {
                long times;
                if (pro.limitsQuantityTest()) {
                    times = 1;
                } else {
                    times = gTLCore$ceilDiv(totalRequestedItems, craftedPerPattern);
                }
                pro.fastRequest(inv, times);

                var available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
                if (available != 0) {
                    totalRequestedItems -= available;

                    if (totalRequestedItems <= 0) {
                        return;
                    }
                } else {
                    var pattern = pro.getDetails().getDefinition();
                    String outputs = Stream.of(pro.getDetails().getOutputs())
                            .map(GenericStack::toString)
                            .collect(Collectors.joining(", "));
                    String errorMessage = """
                            Unexpected error in the crafting calculation: can't find created items.
                            This is an AE2 bug, please report it, with the following important information:

                            - Found none of %s. Remaining request: %d of %d*%d.
                            - Tried crafting %d times the pattern %s with tag %s.
                            - Pattern outputs: %s.
                            """.formatted(what, totalRequestedItems, requestedAmount, amount, times, pattern,
                            pattern.getTag(), outputs);
                    throw new UnsupportedOperationException(errorMessage);
                }
            }
        } else if (this.nodes.size() > 1) {
            // Multiple branches: distribute load evenly across all branches
            // This optimization strategy divides the request equally among available patterns
            // and continues trying all patterns in subsequent iterations

            while (totalRequestedItems > 0) {
                int processCount = this.nodes.size();
                long baseAmount = totalRequestedItems / processCount;
                long remainder = totalRequestedItems % processCount;
                boolean anySucceeded = false;

                for (int i = 0; i < this.nodes.size(); i++) {
                    ICraftingTreeProcess pro = (ICraftingTreeProcess) (this.nodes.get(i));

                    if (!pro.getPossible())
                        continue;

                    long targetAmount = baseAmount + (i < remainder ? 1 : 0);
                    if (targetAmount <= 0) continue;

                    try {
                        var craftedPerPattern = pro.getOutputCountTest(this.what);
                        long times = pro.limitsQuantityTest() ? 1 : gTLCore$ceilDiv(targetAmount, craftedPerPattern);

                        if (times > 0) {
                            final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                            pro.fastRequest(child, times);

                            var available = child.extract(this.what, targetAmount, Actionable.MODULATE);

                            if (available != 0) {
                                child.applyDiff(inv);
                                anySucceeded = true;

                                totalRequestedItems -= available;

                                if (totalRequestedItems <= 0) {
                                    return;
                                }
                            } else {
                                pro.setPossible(false);
                            }
                        }
                    } catch (CraftBranchFailure fail) {
                        // This process failed this iteration, but might succeed later
                    }
                }

                // If no process succeeded in this iteration, we're done trying
                if (!anySucceeded) {
                    break;
                }
            }
        }

        if (this.job.isSimulation()) {
            this.job.getMissingItems().add(this.what, totalRequestedItems);
        } else {
            throw new CraftBranchFailure(this.what, totalRequestedItems);
        }
    }

    @Override
    @Unique
    public void maxFastRequest(CraftingSimulationState inv, long requestedAmount,
                               @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        MaxFastMetrics metrics = ((ICraftingCalculation) this.job).gtlcore$getMaxFastMetrics();
        gTLCore$getMaxFastExecutor().execute(this, inv, requestedAmount, containerItems, metrics);
    }

    @Override
    @Unique
    public void gtlcore$maxFastChildRequest(CraftingSimulationState inv, long requestedAmount,
                                            @Nullable KeyCounter containerItems)
                                                                                 throws CraftBranchFailure,
                                                                                 InterruptedException {
        MaxFastMetrics metrics = ((ICraftingCalculation) this.job).gtlcore$getMaxFastMetrics();
        gTLCore$getMaxFastExecutor().executeChild(this, inv, requestedAmount, containerItems, metrics);
    }

    @Override
    @Unique
    public void ultraFastRequest(CraftingSimulationState inv, long requestedAmount,
                                 @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        requestedAmount = gTLCore$runUltraFastPrefix(inv, requestedAmount, containerItems, calculation);
        if (requestedAmount == 0) {
            return;
        }

        buildChildPatterns();
        long totalRequestedItems = NumberUtils.saturatedMultiply(requestedAmount, this.amount);
        gtlcore$runUltraFastTail(inv, totalRequestedItems, requestedAmount);
    }

    @Override
    @Unique
    public long gtlcore$runMaxFastPrefix(CraftingSimulationState inv, long requestedAmount,
                                         @Nullable KeyCounter containerItems) throws InterruptedException {
        return gTLCore$runUltraFastPrefix(
                inv,
                requestedAmount,
                containerItems,
                (ICraftingCalculation) this.job);
    }

    @Override
    @Unique
    public boolean gtlcore$tryMaxFastAggregation(CraftingSimulationState inv, long requestedAmount,
                                                 MaxFastMetrics metrics)
                                                                         throws CraftBranchFailure,
                                                                         InterruptedException {
        return gTLCore$getMaxFastExecutor().tryExecuteSegment(this, inv, requestedAmount, metrics);
    }

    @Override
    @Unique
    public boolean gtlcore$isMaxFastPatternContextAllowed(IPatternDetails details) {
        return gTLCore$isAllowedByMaxFastExternalAncestors(details) &&
                (this.parent == null || ((ICraftingTreeProcess) this.parent).gtlcore$notRecursive(details));
    }

    @Override
    @Unique
    public ICraftingTreeNode gtlcore$prepareMaxFastBarrier(AEKey what, long amount,
                                                           IPatternDetails[] allowedPatterns,
                                                           AEKey[] externalAncestors,
                                                           boolean candidateGraphsEligible,
                                                           boolean cycleCandidateGraphEligible) {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        MaxFastMetrics metrics = calculation.gtlcore$getMaxFastMetrics();
        long setupStartedNanos = System.nanoTime();
        try {
            CraftingTreeNode boundaryNode = new CraftingTreeNode(
                    this.gTLCore$craftingService,
                    this.job,
                    what,
                    amount,
                    null,
                    -1);
            ICraftingTreeNode boundary = (ICraftingTreeNode) boundaryNode;
            boundary.gtlcore$setMaxFastExternalAncestors(externalAncestors);
            boundary.gtlcore$setMaxFastBoundaryPatterns(
                    allowedPatterns,
                    candidateGraphsEligible,
                    cycleCandidateGraphEligible);
            return boundary;
        } finally {
            metrics.recordAggregationBoundarySetupNanos(System.nanoTime() - setupStartedNanos);
        }
    }

    @Override
    @Unique
    public long gtlcore$runPreparedMaxFastBarrier(CraftingSimulationState inv, ICraftingTreeNode boundary,
                                                  long requestedAmount)
                                                                        throws CraftBranchFailure,
                                                                        InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        MaxFastMetrics metrics = calculation.gtlcore$getMaxFastMetrics();
        long tailStartedNanos = System.nanoTime();
        boolean strictBoundaryProbe = this.job.isSimulation() &&
                !calculation.gtlcore$isMaxFastDeferredMissingCaptureActive();
        try {
            if (strictBoundaryProbe) {
                calculation.gtlcore$beginMaxFastStrictBoundaryProbe();
            }
            try {
                boundary.gtlcore$runUltraFastTail(
                        inv,
                        NumberUtils.saturatedMultiply(boundary.gtlcore$getMaxFastAmount(), requestedAmount),
                        requestedAmount);
            } finally {
                if (strictBoundaryProbe) {
                    calculation.gtlcore$endMaxFastStrictBoundaryProbe();
                }
            }
        } finally {
            metrics.recordAggregationBoundaryTailNanos(System.nanoTime() - tailStartedNanos);
        }
        return boundary.gtlcore$getMaxFastNodeCount();
    }

    @Override
    @Unique
    public void gtlcore$beginMaxFastRuntimeAttempt() {
        if (this.gTLCore$maxFastRuntimeTracker == null) {
            this.gTLCore$maxFastRuntimeTracker = new ICraftingTreeNode.MaxFastRuntimeTracker();
        }
        this.gTLCore$maxFastActiveGeneration = this.gTLCore$maxFastRuntimeTracker.advance();
        this.gTLCore$maxFastLogicalNodeCount = -1L;
        gTLCore$activateMaxFastRuntimeChildren();
    }

    @Override
    @Unique
    public void gtlcore$activateMaxFastRuntime(ICraftingTreeNode.MaxFastRuntimeTracker tracker) {
        this.gTLCore$maxFastRuntimeTracker = tracker;
        this.gTLCore$maxFastActiveGeneration = tracker.current();
        this.gTLCore$maxFastLogicalNodeCount = -1L;
    }

    @Override
    @Unique
    public long gtlcore$runMaxFastBarrier(CraftingSimulationState inv, AEKey what, long amount,
                                          long requestedAmount, IPatternDetails[] allowedPatterns,
                                          AEKey[] externalAncestors,
                                          boolean cycleCandidateGraphEligible)
                                                                               throws CraftBranchFailure,
                                                                               InterruptedException {
        ICraftingTreeNode boundary = gtlcore$prepareMaxFastBarrier(
                what,
                amount,
                allowedPatterns,
                externalAncestors,
                false,
                cycleCandidateGraphEligible);
        return gtlcore$runPreparedMaxFastBarrier(inv, boundary, requestedAmount);
    }

    @Override
    @Unique
    public void gtlcore$setMaxFastBoundaryPatterns(IPatternDetails[] allowedPatterns,
                                                   boolean candidateGraphsEligible,
                                                   boolean cycleCandidateGraphEligible) {
        if (this.nodes != null) {
            throw new IllegalStateException("MAX_FAST boundary patterns were already initialized");
        }
        this.nodes = new ArrayList<>(allowedPatterns.length);
        this.gTLCore$maxFastCandidateGraphsEligible = candidateGraphsEligible;
        this.gTLCore$maxFastCycleCandidateGraphEligible = cycleCandidateGraphEligible;
        CraftingTreeNode owner = (CraftingTreeNode) (Object) this;
        for (IPatternDetails details : allowedPatterns) {
            if (gTLCore$isAllowedByMaxFastExternalAncestors(details)) {
                this.nodes.add(new CraftingTreeProcess(this.gTLCore$craftingService, this.job, details, owner));
            }
        }
    }

    @Override
    @Unique
    public void gtlcore$setMaxFastExternalAncestors(AEKey[] externalAncestors) {
        this.gTLCore$maxFastExternalAncestors = externalAncestors.length == 0 ?
                gTLCore$EMPTY_MAX_FAST_ANCESTORS : externalAncestors.clone();
    }

    @Override
    @Unique
    public AEKey[] gtlcore$getMaxFastExternalAncestors() {
        return this.gTLCore$maxFastExternalAncestors.length == 0 ?
                gTLCore$EMPTY_MAX_FAST_ANCESTORS : this.gTLCore$maxFastExternalAncestors.clone();
    }

    @Override
    @Unique
    public ICraftingTreeProcess gtlcore$getMaxFastParentProcess() {
        return this.parent == null ? null : (ICraftingTreeProcess) this.parent;
    }

    @Override
    @Unique
    public AEKey[] gtlcore$getMaxFastAncestorKeys() {
        int parentDepth = 0;
        ICraftingTreeNode current = this;
        while (true) {
            ICraftingTreeProcess parentProcess = current.gtlcore$getMaxFastParentProcess();
            if (parentProcess == null) {
                break;
            }
            CraftingTreeNode parentNode = parentProcess.gtlcore$getMaxFastParentNode();
            if (parentNode == null) {
                break;
            }
            parentDepth++;
            current = (ICraftingTreeNode) parentNode;
        }

        AEKey[] externalAncestors = current.gtlcore$getMaxFastExternalAncestors();
        int externalLength = externalAncestors.length;
        if (parentDepth == 0) {
            return externalAncestors;
        }

        AEKey[] ancestors = Arrays.copyOf(
                externalAncestors,
                externalLength + parentDepth);
        current = this;
        int index = ancestors.length;
        while (true) {
            ICraftingTreeProcess parentProcess = current.gtlcore$getMaxFastParentProcess();
            if (parentProcess == null) {
                break;
            }
            CraftingTreeNode parentNode = parentProcess.gtlcore$getMaxFastParentNode();
            if (parentNode == null) {
                break;
            }
            ancestors[--index] = ((ICraftingTreeNode) parentNode).gtlcore$getMaxFastKey();
            current = (ICraftingTreeNode) parentNode;
        }
        return ancestors;
    }

    @Override
    @Unique
    public long gtlcore$getMaxFastNodeCount() {
        return getNodeCount();
    }

    @Override
    @Unique
    public MaxFastNodeProgram gtlcore$getOrCreateMaxFastProgram(MaxFastMetrics metrics) {
        metrics.recordProgramLookup();
        if (this.gTLCore$maxFastProgram != null) {
            gTLCore$activateMaxFastRuntimeChildren();
            metrics.recordProgramHit();
            return this.gTLCore$maxFastProgram;
        }

        buildChildPatterns();
        gTLCore$activateMaxFastRuntimeChildren();

        if (this.nodes.isEmpty()) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.terminal(this, this.amount);
            metrics.recordTerminalProgramCompiled();
            return this.gTLCore$maxFastProgram;
        }
        if (this.nodes.size() != 1) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.baselineTail(this, this.amount);
            metrics.recordBaselineProgramCompiled(BarrierReason.MULTI_PATH);
            return this.gTLCore$maxFastProgram;
        }

        ICraftingTreeProcess process = (ICraftingTreeProcess) this.nodes.get(0);
        long outputPerPattern = process.getOutputCountTest(this.what);
        CraftingTreeNode[] childNodes = process.gtlcore$getChildNodes();
        long[] childMultipliers = process.gtlcore$getChildMultipliers();
        if (process.limitsQuantityTest()) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.baselineTail(this, this.amount);
            metrics.recordBaselineProgramCompiled(BarrierReason.LIMIT_QUANTITY);
            return this.gTLCore$maxFastProgram;
        }
        if (process.gtlcore$hasContainerItems()) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.baselineTail(this, this.amount);
            metrics.recordBaselineProgramCompiled(BarrierReason.CONTAINER);
            return this.gTLCore$maxFastProgram;
        }
        if (outputPerPattern <= 0) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.baselineTail(this, this.amount);
            metrics.recordBaselineProgramCompiled(BarrierReason.NON_POSITIVE_OUTPUT);
            return this.gTLCore$maxFastProgram;
        }
        if (childNodes == null || childMultipliers == null || childNodes.length != childMultipliers.length ||
                gTLCore$hasNullChild(childNodes)) {
            this.gTLCore$maxFastProgram = MaxFastNodeProgram.baselineTail(this, this.amount);
            metrics.recordBaselineProgramCompiled(BarrierReason.INVALID_METADATA);
            return this.gTLCore$maxFastProgram;
        }

        this.gTLCore$maxFastProgram = MaxFastNodeProgram.singlePath(
                this,
                process,
                childNodes,
                childMultipliers,
                this.amount,
                outputPerPattern);
        metrics.recordSingleProgramCompiled();
        return this.gTLCore$maxFastProgram;
    }

    @Override
    @Unique
    public void gtlcore$runUltraFastTail(CraftingSimulationState inv, long totalRequestedItems,
                                         long requestedAmount) throws CraftBranchFailure, InterruptedException {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        if (this.nodes.size() == 1) {
            final ICraftingTreeProcess pro = (ICraftingTreeProcess) (this.nodes.get(0));
            var craftedPerPattern = pro.getOutputCountTest(this.what);

            if (gTLCore$tryMaxFastCycleCandidateGraph(inv, requestedAmount, pro, calculation)) {
                return;
            }

            while (pro.getPossible() && totalRequestedItems > 0) {
                long times;
                if (pro.limitsQuantityTest()) {
                    times = 1;
                } else {
                    times = gTLCore$ceilDiv(totalRequestedItems, craftedPerPattern);
                }
                pro.ultraFastRequest(inv, times);

                var available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
                if (available != 0) {
                    totalRequestedItems -= available;

                    if (totalRequestedItems <= 0) {
                        return;
                    }
                } else {
                    gtlcore$throwMaxFastMissingOutput(
                            (CraftingTreeProcess) pro,
                            totalRequestedItems,
                            requestedAmount,
                            times);
                }
            }
        } else if (this.nodes.size() > 1) {
            // Multiple branches: try maximum value for each node only once
            // This optimization strategy attempts the full remaining request on each pattern once

            for (int candidateIndex = 0; candidateIndex < this.nodes.size(); candidateIndex++) {
                CraftingTreeProcess node = this.nodes.get(candidateIndex);
                ICraftingTreeProcess pro = (ICraftingTreeProcess) node;
                CandidateSegmentResult candidateResult = gTLCore$tryMaxFastCandidateGraph(
                        inv,
                        candidateIndex,
                        pro,
                        totalRequestedItems,
                        calculation);
                if (candidateResult == CandidateSegmentResult.SUCCEEDED) {
                    return;
                }
                if (candidateResult == CandidateSegmentResult.CERTIFIED_MISSING) {
                    continue;
                }
                boolean deferredCandidateScope = calculation.gtlcore$isMaxFastDeferredMissingCaptureActive();
                if (deferredCandidateScope) {
                    calculation.gtlcore$beginMaxFastDeferredMissingScope();
                }

                try {
                    var craftedPerPattern = pro.getOutputCountTest(this.what);
                    long times = pro.limitsQuantityTest() ? 1 : gTLCore$ceilDiv(totalRequestedItems, craftedPerPattern);

                    if (times > 0) {
                        final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                        pro.ultraFastRequest(child, times);

                        var available = child.extract(this.what, totalRequestedItems, Actionable.MODULATE);

                        if (available != 0) {
                            child.applyDiff(inv);
                            if (deferredCandidateScope) {
                                deferredCandidateScope = false;
                                calculation.gtlcore$commitMaxFastDeferredMissingScope();
                            }

                            totalRequestedItems -= available;

                            if (totalRequestedItems <= 0) {
                                return;
                            }
                        }
                    }
                } catch (CraftBranchFailure fail) {
                    if (!(fail instanceof MaxFastCraftBranchFailure)) {
                        calculation.gtlcore$markMaxFastBoundaryFailureUnknown();
                    }
                    // This process failed, move to next node
                } finally {
                    if (deferredCandidateScope) {
                        calculation.gtlcore$discardMaxFastDeferredMissingScope();
                    }
                }
            }
        }

        gtlcore$reportMaxFastMissing(totalRequestedItems);
    }

    @Unique
    private CandidateSegmentResult gTLCore$tryMaxFastCandidateGraph(CraftingSimulationState inv,
                                                                    int candidateIndex,
                                                                    ICraftingTreeProcess process,
                                                                    long totalRequestedItems,
                                                                    ICraftingCalculation calculation)
                                                                                                      throws InterruptedException {
        if (!this.gTLCore$maxFastCandidateGraphsEligible) {
            return CandidateSegmentResult.STRUCTURAL_FALLBACK;
        }

        MaxFastMetrics metrics = calculation.gtlcore$getMaxFastMetrics();
        metrics.recordAggregationCandidateGraphAttempt();
        try {
            if (this.amount <= 0 ||
                    totalRequestedItems <= 0 || totalRequestedItems % this.amount != 0 ||
                    process.limitsQuantityTest() || process.gtlcore$hasContainerItems()) {
                metrics.recordAggregationCandidateGraphStructuralFallback();
                return CandidateSegmentResult.STRUCTURAL_FALLBACK;
            }

            gTLCore$prepareMaxFastCandidateGraph(candidateIndex, process.getDetails(), calculation);
            ICraftingTreeNode candidateRoot = this.gTLCore$maxFastCandidateGraphRoots[candidateIndex];
            MaxFastExecutor candidateExecutor = this.gTLCore$maxFastCandidateGraphExecutors[candidateIndex];
            CandidateSegmentResult result = gTLCore$executeMaxFastCandidateGraph(
                    inv,
                    candidateRoot,
                    candidateExecutor,
                    gTLCore$getMaxFastCandidateGraphFailures(),
                    totalRequestedItems / this.amount,
                    calculation,
                    true);
            if (result == CandidateSegmentResult.CERTIFIED_MISSING) {
                metrics.recordAggregationCandidateGraphCertifiedMissing();
                return result;
            }
            if (result == CandidateSegmentResult.STRUCTURAL_FALLBACK) {
                metrics.recordAggregationCandidateGraphStructuralFallback();
                return result;
            }
            if (result == CandidateSegmentResult.EXECUTION_FALLBACK) {
                metrics.recordAggregationCandidateGraphExecutionFallback();
                return result;
            }

            metrics.recordAggregationCandidateGraphSuccess();
            return CandidateSegmentResult.SUCCEEDED;
        } catch (InterruptedException | RuntimeException | Error failure) {
            metrics.recordAggregationCandidateGraphAbort();
            throw failure;
        }
    }

    @Unique
    private boolean gTLCore$tryMaxFastCycleCandidateGraph(CraftingSimulationState inv,
                                                          long requestedAmount,
                                                          ICraftingTreeProcess process,
                                                          ICraftingCalculation calculation)
                                                                                            throws InterruptedException {
        if (!this.gTLCore$maxFastCycleCandidateGraphEligible) {
            return false;
        }

        MaxFastMetrics metrics = calculation.gtlcore$getMaxFastMetrics();
        if (calculation.gtlcore$isMaxFastDeferredMissingCaptureActive()) {
            metrics.recordAggregationCycleCandidateGraphDeferredBypass();
            return false;
        }

        metrics.recordAggregationCycleCandidateGraphAttempt();
        try {
            if (this.amount <= 0 || requestedAmount <= 0 || !process.getPossible() ||
                    process.limitsQuantityTest() || process.gtlcore$hasContainerItems() ||
                    !gTLCore$isAllowedByMaxFastExternalAncestors(process.getDetails())) {
                metrics.recordAggregationCycleCandidateGraphStructuralFallback();
                return false;
            }

            gTLCore$prepareMaxFastCycleCandidateGraph(process.getDetails(), calculation);
            if (this.gTLCore$maxFastCycleCandidateGraphFailures == null) {
                this.gTLCore$maxFastCycleCandidateGraphFailures = new BoundaryFailureDependencies();
            }
            CandidateSegmentResult result = gTLCore$executeMaxFastCandidateGraph(
                    inv,
                    this.gTLCore$maxFastCycleCandidateGraphRoot,
                    this.gTLCore$maxFastCycleCandidateGraphExecutor,
                    this.gTLCore$maxFastCycleCandidateGraphFailures,
                    requestedAmount,
                    calculation,
                    false);
            if (result != CandidateSegmentResult.SUCCEEDED) {
                if (result == CandidateSegmentResult.STRUCTURAL_FALLBACK) {
                    metrics.recordAggregationCycleCandidateGraphStructuralFallback();
                } else {
                    metrics.recordAggregationCycleCandidateGraphExecutionFallback();
                }
                return false;
            }

            metrics.recordAggregationCycleCandidateGraphSuccess();
            return true;
        } catch (InterruptedException | RuntimeException | Error failure) {
            metrics.recordAggregationCycleCandidateGraphAbort();
            throw failure;
        }
    }

    @Unique
    private CandidateSegmentResult gTLCore$executeMaxFastCandidateGraph(
                                                                        CraftingSimulationState inv,
                                                                        ICraftingTreeNode candidateRoot,
                                                                        MaxFastExecutor candidateExecutor,
                                                                        BoundaryFailureDependencies failureDependencies,
                                                                        long requestedAmount,
                                                                        ICraftingCalculation calculation,
                                                                        boolean propagateCertifiedMissing)
                                                                                                           throws InterruptedException {
        ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
        boolean failureScopeActive = false;
        boolean deferredScopeActive = false;
        boolean captureDeferredMissing = calculation.gtlcore$isMaxFastDeferredMissingCaptureActive();

        try {
            calculation.gtlcore$beginMaxFastBoundaryFailureScope(failureDependencies);
            failureScopeActive = true;
            if (captureDeferredMissing) {
                calculation.gtlcore$beginMaxFastDeferredMissingScope();
                deferredScopeActive = true;
            }

            candidateRoot.gtlcore$beginMaxFastRuntimeAttempt();
            CandidateSegmentResult result = candidateExecutor.tryExecuteCandidateSegment(
                    candidateRoot,
                    child,
                    requestedAmount,
                    calculation.gtlcore$getMaxFastMetrics());
            calculation.gtlcore$endMaxFastBoundaryFailureScope();
            failureScopeActive = false;
            if (result == CandidateSegmentResult.CERTIFIED_MISSING) {
                if (propagateCertifiedMissing) {
                    calculation.gtlcore$propagateMaxFastBoundaryFailure(failureDependencies);
                }
                return result;
            }
            if (result != CandidateSegmentResult.SUCCEEDED) {
                return result;
            }

            child.applyDiff(inv);
            if (deferredScopeActive) {
                calculation.gtlcore$commitMaxFastDeferredMissingScope();
                deferredScopeActive = false;
            }
            this.gTLCore$maxFastLogicalNodeCount = candidateRoot.gtlcore$getMaxFastNodeCount();
            return CandidateSegmentResult.SUCCEEDED;
        } finally {
            if (failureScopeActive) {
                calculation.gtlcore$endMaxFastBoundaryFailureScope();
            }
            if (deferredScopeActive) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
        }
    }

    @Unique
    private void gTLCore$prepareMaxFastCandidateGraph(int candidateIndex, IPatternDetails details,
                                                      ICraftingCalculation calculation) {
        if (this.gTLCore$maxFastCandidateGraphRoots == null) {
            this.gTLCore$maxFastCandidateGraphRoots = new ICraftingTreeNode[this.nodes.size()];
            this.gTLCore$maxFastCandidateGraphExecutors = new MaxFastExecutor[this.nodes.size()];
        }
        if (this.gTLCore$maxFastCandidateGraphRoots[candidateIndex] != null) {
            return;
        }

        CraftingTreeNode candidateNode = new CraftingTreeNode(
                this.gTLCore$craftingService,
                this.job,
                this.what,
                this.amount,
                null,
                -1);
        ICraftingTreeNode candidateRoot = (ICraftingTreeNode) candidateNode;
        candidateRoot.gtlcore$setMaxFastExternalAncestors(this.gTLCore$maxFastExternalAncestors);
        this.gTLCore$maxFastCandidateGraphRoots[candidateIndex] = candidateRoot;
        this.gTLCore$maxFastCandidateGraphExecutors[candidateIndex] = new MaxFastExecutor(
                calculation.gtlcore$getMaxFastCompilationCache(),
                details);
    }

    @Unique
    private BoundaryFailureDependencies gTLCore$getMaxFastCandidateGraphFailures() {
        if (this.gTLCore$maxFastCandidateGraphFailures == null) {
            this.gTLCore$maxFastCandidateGraphFailures = new BoundaryFailureDependencies();
        }
        return this.gTLCore$maxFastCandidateGraphFailures;
    }

    @Unique
    private void gTLCore$prepareMaxFastCycleCandidateGraph(IPatternDetails details,
                                                           ICraftingCalculation calculation) {
        if (this.gTLCore$maxFastCycleCandidateGraphRoot != null) {
            return;
        }

        CraftingTreeNode candidateNode = new CraftingTreeNode(
                this.gTLCore$craftingService,
                this.job,
                this.what,
                this.amount,
                null,
                -1);
        ICraftingTreeNode candidateRoot = (ICraftingTreeNode) candidateNode;
        AEKey[] descendantAncestors = Arrays.copyOf(
                this.gTLCore$maxFastExternalAncestors,
                this.gTLCore$maxFastExternalAncestors.length + 1);
        descendantAncestors[descendantAncestors.length - 1] = this.what;
        candidateRoot.gtlcore$setMaxFastExternalAncestors(descendantAncestors);
        this.gTLCore$maxFastCycleCandidateGraphRoot = candidateRoot;
        this.gTLCore$maxFastCycleCandidateGraphExecutor = new MaxFastExecutor(
                calculation.gtlcore$getMaxFastCompilationCache(),
                details,
                true);
    }

    @Override
    @Unique
    public void gtlcore$checkMaxFastCancellation() throws InterruptedException {
        ((ICraftingCalculation) this.job).gtlcore$handlePausing();
    }

    @Override
    @Unique
    public long gtlcore$extractMaxFastOutput(CraftingSimulationState inv, long totalRequestedItems) {
        return inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
    }

    @Override
    @Unique
    public void gtlcore$reportMaxFastMissing(long totalRequestedItems) throws CraftBranchFailure {
        gtlcore$reportMaxFastMissing(this.what, totalRequestedItems);
    }

    @Override
    @Unique
    public void gtlcore$reportMaxFastMissing(AEKey what, long totalRequestedItems) throws CraftBranchFailure {
        if (!this.what.equals(what) || this.parentInput == null) {
            gtlcore$reportMaxFastMissing(what, totalRequestedItems, null, 1L, totalRequestedItems);
            return;
        }

        InputTemplate singleTemplate = gTLCore$getSingleTemplate();
        long missingTemplates = gTLCore$ceilDiv(totalRequestedItems, this.amount);
        if (singleTemplate != null) {
            gTLCore$reportMaxFastMissingWithDependency(
                    what,
                    totalRequestedItems,
                    singleTemplate.key(),
                    null,
                    singleTemplate.amount(),
                    missingTemplates);
        } else {
            gtlcore$reportMaxFastMissing(
                    what,
                    totalRequestedItems,
                    this.parentInput,
                    this.amount,
                    missingTemplates);
        }
    }

    @Override
    @Unique
    public void gtlcore$reportMaxFastMissing(AEKey what, long totalRequestedItems,
                                             @Nullable IPatternDetails.IInput input,
                                             long templateAmount, long missingTemplates) throws CraftBranchFailure {
        gTLCore$reportMaxFastMissingWithDependency(
                what,
                totalRequestedItems,
                what,
                input,
                templateAmount,
                missingTemplates);
    }

    @Unique
    private void gTLCore$reportMaxFastMissingWithDependency(AEKey what, long totalRequestedItems,
                                                            AEKey dependencyKey,
                                                            @Nullable IPatternDetails.IInput input,
                                                            long templateAmount,
                                                            long missingTemplates) throws CraftBranchFailure {
        ICraftingCalculation calculation = (ICraftingCalculation) this.job;
        boolean simulation = this.job.isSimulation();
        if (simulation && calculation.gtlcore$isMaxFastDeferredMissingCaptureActive()) {
            calculation.gtlcore$recordMaxFastDeferredMissing(what, totalRequestedItems);
        } else if (simulation && !calculation.gtlcore$isMaxFastStrictBoundaryProbeActive()) {
            KeyCounter missing = this.job.getMissingItems();
            missing.set(what, NumberUtils.saturatedAdd(missing.get(what), totalRequestedItems));
        } else {
            calculation.gtlcore$recordMaxFastBoundaryFailure(
                    dependencyKey,
                    templateAmount,
                    input,
                    missingTemplates);
            MaxFastMetrics metrics = calculation.gtlcore$getMaxFastMetrics();
            if (metrics != null) {
                if (simulation) {
                    metrics.recordAggregationBoundaryTransactionSimulationFailure(what, totalRequestedItems);
                } else {
                    metrics.recordBranchFailure(what, totalRequestedItems);
                }
            }
            throw new MaxFastCraftBranchFailure(what, totalRequestedItems);
        }
    }

    @Override
    @Unique
    public void gtlcore$throwMaxFastMissingOutput(CraftingTreeProcess process, long totalRequestedItems,
                                                  long requestedAmount, long times) {
        ICraftingTreeProcess processBridge = (ICraftingTreeProcess) process;
        var pattern = processBridge.getDetails().getDefinition();
        String outputs = Stream.of(processBridge.getDetails().getOutputs())
                .map(GenericStack::toString)
                .collect(Collectors.joining(", "));
        String errorMessage = """
                Unexpected error in the crafting calculation: can't find created items.
                This is an AE2 bug, please report it, with the following important information:

                - Found none of %s. Remaining request: %d of %d*%d.
                - Tried crafting %d times the pattern %s with tag %s.
                - Pattern outputs: %s.
                """.formatted(what, totalRequestedItems, requestedAmount, amount, times, pattern,
                pattern.getTag(), outputs);
        throw new UnsupportedOperationException(errorMessage);
    }

    @Override
    @Unique
    public void gtlcore$resetFastState() {
        if (this.nodes == null) {
            return;
        }
        for (CraftingTreeProcess node : this.nodes) {
            ((ICraftingTreeProcess) node).gtlcore$resetFastState();
        }
    }

    @Override
    @Unique
    public Object gtlcore$getRequestMergeKey() {
        if (this.gTLCore$requestMergeKey == null) {
            this.gTLCore$requestMergeKey = new AE2CraftingRequestMergeKey(this.what, this.amount, this.parentInput);
        }
        return this.gTLCore$requestMergeKey;
    }

    @Override
    @Unique
    public AEKey gtlcore$getMaxFastKey() {
        return this.what;
    }

    @Override
    @Unique
    public long gtlcore$getMaxFastAmount() {
        return this.amount;
    }

    @Override
    @Unique
    public ICraftingService gtlcore$getMaxFastCraftingService() {
        return this.gTLCore$craftingService;
    }

    @Override
    @Unique
    public ICraftingCalculation gtlcore$getMaxFastCalculation() {
        return (ICraftingCalculation) this.job;
    }

    @Override
    @Unique
    public Level gtlcore$getMaxFastLevel() {
        return this.level;
    }

    @Override
    @Unique
    public boolean gtlcore$isMaxFastSimulation() {
        return this.job.isSimulation();
    }

    @Override
    @Unique
    public void gtlcore$setMaxFastLogicalNodeCount(long nodeCount) {
        this.gTLCore$maxFastLogicalNodeCount = nodeCount;
    }

    @Inject(method = "getNodeCount", at = @At("HEAD"), cancellable = true, remap = false)
    private void gTLCore$getMaxFastLogicalNodeCount(CallbackInfoReturnable<Long> cir) {
        if (this.gTLCore$maxFastRuntimeTracker != null &&
                this.gTLCore$maxFastActiveGeneration != this.gTLCore$maxFastRuntimeTracker.current()) {
            cir.setReturnValue(0L);
            return;
        }
        if (this.gTLCore$maxFastLogicalNodeCount >= 0) {
            cir.setReturnValue(this.gTLCore$maxFastLogicalNodeCount);
        }
    }

    @Unique
    private void gTLCore$activateMaxFastRuntimeChildren() {
        if (this.gTLCore$maxFastRuntimeTracker == null || this.nodes == null) {
            return;
        }
        for (CraftingTreeProcess process : this.nodes) {
            // Merged duplicate children are never expanded, so their untouched physical count remains one.
            for (CraftingTreeNode child : ((ICraftingTreeProcess) process).gtlcore$getChildNodes()) {
                ((ICraftingTreeNode) child).gtlcore$activateMaxFastRuntime(this.gTLCore$maxFastRuntimeTracker);
            }
        }
    }

    @Inject(method = "notRecursive", at = @At("HEAD"), cancellable = true, remap = false)
    private void gTLCore$checkMaxFastExternalAncestors(IPatternDetails details,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!gTLCore$isAllowedByMaxFastExternalAncestors(details)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static long gTLCore$ceilDiv(long value, long divisor) {
        if (value <= 0) {
            return 0;
        }
        if (divisor <= 1) {
            return value;
        }
        return 1 + (value - 1) / divisor;
    }

    @Unique
    private MaxFastExecutor gTLCore$getMaxFastExecutor() {
        if (this.gTLCore$maxFastExecutor == null) {
            ICraftingCalculation calculation = (ICraftingCalculation) this.job;
            this.gTLCore$maxFastExecutor = new MaxFastExecutor(calculation.gtlcore$getMaxFastCompilationCache());
        }
        return this.gTLCore$maxFastExecutor;
    }

    @Unique
    private long gTLCore$runUltraFastPrefix(CraftingSimulationState inv, long requestedAmount,
                                            @Nullable KeyCounter containerItems,
                                            ICraftingCalculation calculation) throws InterruptedException {
        calculation.gtlcore$handlePausing();

        inv.addStackBytes(what, amount, requestedAmount);

        requestedAmount = gTLCore$extractAvailableTemplates(inv, requestedAmount, containerItems, calculation);
        if (requestedAmount == 0) {
            return 0;
        }

        addContainerItems(what, requestedAmount, containerItems);

        if (this.canEmit) {
            inv.emitItems(this.what, NumberUtils.saturatedMultiply(this.amount, requestedAmount));
            return 0;
        }

        return requestedAmount;
    }

    @Unique
    private static boolean gTLCore$hasNullChild(CraftingTreeNode[] childNodes) {
        for (CraftingTreeNode childNode : childNodes) {
            if (childNode == null) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean gTLCore$isAllowedByMaxFastExternalAncestors(IPatternDetails details) {
        if (this.gTLCore$maxFastExternalAncestors.length == 0) {
            return true;
        }

        for (GenericStack output : details.getOutputs()) {
            if (gTLCore$matchesAnyMaxFastAncestor(output, this.gTLCore$maxFastExternalAncestors)) {
                return false;
            }
        }
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length > 0 &&
                    gTLCore$matchesAnyMaxFastAncestor(possibleInputs[0], this.gTLCore$maxFastExternalAncestors)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static boolean gTLCore$matchesAnyMaxFastAncestor(GenericStack stack, AEKey[] ancestors) {
        for (AEKey ancestor : ancestors) {
            if (ancestor.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private long gTLCore$extractAvailableTemplates(CraftingSimulationState inv, long requestedAmount,
                                                   @Nullable KeyCounter containerItems,
                                                   ICraftingCalculation calculation) {
        InputTemplate singleTemplate = gTLCore$getSingleTemplate();
        if (singleTemplate != null) {
            return gTLCore$extractTemplate(inv, requestedAmount, containerItems, calculation, singleTemplate);
        }

        for (var template : getValidItemTemplates(inv)) {
            requestedAmount = gTLCore$extractTemplate(inv, requestedAmount, containerItems, calculation, template);
            if (requestedAmount == 0) {
                break;
            }
        }
        return requestedAmount;
    }

    @Unique
    private long gTLCore$extractTemplate(CraftingSimulationState inv, long requestedAmount,
                                         @Nullable KeyCounter containerItems,
                                         ICraftingCalculation calculation, InputTemplate template) {
        long extracted = gTLCore$extractTemplates(inv, template, requestedAmount);

        if (extracted > 0) {
            requestedAmount -= extracted;
            addContainerItems(template.key(), extracted, containerItems);
            calculation.gtlcore$clearTemplateCache();
        }
        return requestedAmount;
    }

    @Unique
    private static long gTLCore$extractTemplates(ICraftingInventory inv, InputTemplate template, long requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }
        if (template.amount() == 1) {
            return inv.extract(template.key(), requestedAmount, Actionable.MODULATE);
        }
        return CraftingCpuHelper.extractTemplates(inv, template, requestedAmount);
    }

    @Unique
    private @Nullable InputTemplate gTLCore$getSingleTemplate() {
        if (this.parentInput == null) {
            return gTLCore$getDirectTemplate();
        }
        if (this.patternDetails instanceof AEProcessingPattern) {
            return gTLCore$getProcessingTemplate();
        }
        return null;
    }

    @Unique
    private InputTemplate gTLCore$getDirectTemplate() {
        if (this.gTLCore$directTemplate == null) {
            this.gTLCore$directTemplate = new InputTemplate(this.what, 1);
        }
        return this.gTLCore$directTemplate;
    }

    @Unique
    private InputTemplate gTLCore$getProcessingTemplate() {
        if (this.gTLCore$processingTemplate == null) {
            GenericStack stack = this.parentInput.getPossibleInputs()[0];
            this.gTLCore$processingTemplate = new InputTemplate(stack.what(), stack.amount());
        }
        return this.gTLCore$processingTemplate;
    }
}
