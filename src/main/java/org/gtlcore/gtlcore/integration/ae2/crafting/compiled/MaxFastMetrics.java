package org.gtlcore.gtlcore.integration.ae2.crafting.compiled;

import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

public final class MaxFastMetrics {

    public enum BarrierReason {
        MULTI_PATH,
        LIMIT_QUANTITY,
        CONTAINER,
        NON_POSITIVE_OUTPUT,
        INVALID_METADATA
    }

    public enum AggregationFallbackReason {
        ROOT_CONTAINER,
        DYNAMIC_INPUT,
        MULTIPLE_PATTERN_CANDIDATES,
        BASELINE_PROGRAM,
        OUTPUT_INPUT_FEEDBACK,
        CYCLE
    }

    private enum Result {
        CRAFTABLE,
        SIMULATED_MISSING,
        NO_PLAN,
        INTERRUPTED,
        ERROR
    }

    private record SimulationFallbackGraph(AEKey rootKey,
                                           int uniqueNodes,
                                           long logicalNodes,
                                           int ancestorDepth,
                                           long compileNanos,
                                           @Nullable AEKey failureKey,
                                           long failureAmount) {}

    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
    private static final long UNAVAILABLE_VALUE = -1L;
    private static final String NO_FAILURE = "none";

    private long calculationNanos;
    private boolean attemptActive;
    private long activeAttemptStartedNanos;
    private long attempts;
    private long successfulAttempts;
    private long failedAttempts;
    private long simulationAttempts;
    private long abortedAttempts;
    private long attemptNanos;
    private long maxAttemptNanos;
    private long minAttemptRequest;
    private long maxAttemptRequest;
    private long branchFailureReports;
    private @Nullable AEKey branchFailureFirstKey;
    private long branchFailureFirstAmount = UNAVAILABLE_VALUE;

    private long executorRuns;
    private long executorNanos;
    private int executorDepth;
    private long framesExecuted;
    private int stackPeak;
    private long nodeEnters;
    private long prefixSatisfied;

    private long aggregationRuns;
    private long aggregationNanos;
    private long aggregationGraphBuilds;
    private long aggregationGraphReuses;
    private long aggregationGraphContextReuses;
    private long aggregationGraphContextLookups;
    private long aggregationGraphContextMatches;
    private long aggregationGraphContextVariantChecks;
    private int aggregationGraphContextMaxVariants;
    private long aggregationGraphContextScanNanos;
    private long aggregationContextCacheHits;
    private long aggregationContextCacheMisses;
    private long aggregationAnalysisCacheHits;
    private long aggregationAnalysisCacheMisses;
    private long aggregationCompileNanos;
    private long aggregationExpansionNanos;
    private long aggregationSccNanos;
    private long aggregationExecutableNanos;
    private long aggregationFinalizationNanos;
    private long aggregationPropagationNanos;
    private long aggregationCompletionNanos;
    private int aggregationUniqueNodes;
    private long aggregationLogicalNodes;
    private long aggregationMergedRequests;
    private long aggregationFallbacks;
    private long aggregationFallbackRootContainer;
    private long aggregationFallbackDynamicInput;
    private long aggregationFallbackMultiplePatterns;
    private long aggregationFallbackBaselineProgram;
    private long aggregationFallbackOutputInputFeedback;
    private long aggregationFallbackCycle;
    private long aggregationBoundaryNodes;
    private long aggregationBoundaryMultiplePatterns;
    private long aggregationBoundaryUnsafeProgram;
    private long aggregationBoundaryCycle;
    private long aggregationCandidateGraphEligibleNodes;
    private long aggregationCandidateGraphAttempts;
    private long aggregationCandidateGraphSuccesses;
    private long aggregationCandidateGraphCertifiedMissing;
    private long aggregationCandidateGraphStructuralFallbacks;
    private long aggregationCandidateGraphExecutionFallbacks;
    private long aggregationCandidateGraphAborts;
    private long aggregationCycleCandidateGraphEligibleNodes;
    private long aggregationCycleCandidateGraphPrefilterRejections;
    private long aggregationCycleCandidateGraphPrefilterUnknowns;
    private long aggregationCycleCandidateGraphAttempts;
    private long aggregationCycleCandidateGraphSuccesses;
    private long aggregationCycleCandidateGraphStructuralFallbacks;
    private long aggregationCycleCandidateGraphExecutionFallbacks;
    private long aggregationCycleCandidateGraphDeferredBypasses;
    private long aggregationCycleCandidateGraphAborts;
    private long aggregationBoundaryTransactionGuardGraphs;
    private long aggregationBoundaryTransactionGuardRuns;
    private long aggregationBoundaryTransactionFallbacks;
    private long aggregationBoundaryTransactionCraftFailures;
    private long aggregationBoundaryTransactionSimulationProbes;
    private long aggregationBoundaryTransactionSimulationSuccesses;
    private long aggregationBoundaryTransactionSimulationPropagations;
    private long aggregationBoundaryTransactionSimulationFallbacks;
    private long aggregationBoundarySchedulerAttempts;
    private long aggregationBoundarySchedulerAttemptFailures;
    private long aggregationBoundarySchedulerInitialFailures;
    private long aggregationBoundarySchedulerRetries;
    private long aggregationBoundarySchedulerRetrySuccesses;
    private long aggregationBoundarySchedulerDeferredMissing;
    private long aggregationBoundarySchedulerRecoveredGraphs;
    private long aggregationBoundarySchedulerDependencyChecks;
    private long aggregationBoundarySchedulerDependencyWakeups;
    private long aggregationBoundarySchedulerDependencySkips;
    private long aggregationBoundarySchedulerUnknownFailures;
    private @Nullable AEKey aggregationBoundaryTransactionSimulationFailureKey;
    private long aggregationBoundaryTransactionSimulationFailureAmount = UNAVAILABLE_VALUE;
    private @Nullable SimulationFallbackGraph aggregationBoundaryTransactionSimulationFallbackFirst;
    private @Nullable SimulationFallbackGraph aggregationBoundaryTransactionSimulationFallbackMaxCompile;
    private long aggregationBoundaryTransactionSetupNanos;
    private long aggregationBoundaryTransactionApplyNanos;
    private long aggregationBoundaryRuntimeBuilds;
    private long aggregationBoundaryRuntimeReuses;
    private long aggregationBoundarySetupNanos;
    private long aggregationBoundaryTailNanos;
    private long aggregationSegmentAttempts;
    private long aggregationSegmentRuns;
    private long aggregationSegmentRejections;
    private long aggregationSegmentRejectionRootContainer;
    private long aggregationSegmentRejectionDynamicInput;
    private long aggregationSegmentRejectionMultiplePatterns;
    private long aggregationSegmentRejectionBaselineProgram;
    private long aggregationSegmentRejectionOutputInputFeedback;
    private long aggregationSegmentRejectionCycle;
    private String aggregationCompileFailureReason = NO_FAILURE;
    private String aggregationCompileFailureKeyType = NO_FAILURE;
    private String aggregationCompileFailureKeyId = NO_FAILURE;
    private int aggregationCompileFailurePatternCandidates = (int) UNAVAILABLE_VALUE;
    private int aggregationCompileFailureScannedNodes = (int) UNAVAILABLE_VALUE;

    private long programLookups;
    private long programHits;
    private long singlePrograms;
    private long terminalPrograms;
    private long baselinePrograms;
    private long singlePathExecutions;
    private long terminalExecutions;

    private long baselineExecutions;
    private long baselineNanos;
    private long multiPathBarriers;
    private long limitQuantityBarriers;
    private long containerBarriers;
    private long nonPositiveOutputBarriers;
    private long invalidMetadataBarriers;

    private long compiledProcessRuns;
    private long compiledProcessTimes;
    private long baselineProcessRuns;
    private long baselineProcessTimes;
    private long childRequests;
    private long aggregationExactTemplateValidations;
    private long aggregationExactTemplateValidationFailures;
    private long aggregationExactTemplateExtractions;
    private long aggregationTemplateValidationNanos;

    private boolean templateMetricsActive;
    private long templateLookups;
    private long templateHits;
    private long templateParentDelegations;
    private long templateFallbacks;
    private long templateColdBuilds;
    private long templateValidations;
    private long templatePossibleInputsRebuilds;
    private long templateMembershipRebuilds;
    private long templateNanos;
    private long rawCandidatesScanned;
    private long validCandidatesObserved;
    private int maxRawCandidates;
    private long validationEpochBumps;
    private long planBuildNanos;

    public void beginAttempt(long requestedAmount, boolean simulation) {
        abortActiveAttempt();
        this.attemptActive = true;
        this.activeAttemptStartedNanos = System.nanoTime();
        this.attempts++;
        if (simulation) {
            this.simulationAttempts++;
        }
        if (this.attempts == 1) {
            this.minAttemptRequest = requestedAmount;
            this.maxAttemptRequest = requestedAmount;
        } else {
            this.minAttemptRequest = Math.min(this.minAttemptRequest, requestedAmount);
            this.maxAttemptRequest = Math.max(this.maxAttemptRequest, requestedAmount);
        }
    }

    public void finishAttempt(boolean succeeded) {
        if (!this.attemptActive) {
            return;
        }
        finishAttemptTiming();
        if (succeeded) {
            this.successfulAttempts++;
        } else {
            this.failedAttempts++;
        }
    }

    public void abortActiveAttempt() {
        if (!this.attemptActive) {
            return;
        }
        finishAttemptTiming();
        this.abortedAttempts++;
    }

    public void recordBranchFailure(AEKey key, long amount) {
        this.branchFailureReports++;
        if (this.branchFailureFirstKey == null) {
            this.branchFailureFirstKey = key;
            this.branchFailureFirstAmount = amount;
        }
    }

    public void recordCalculationNanos(long nanos) {
        this.calculationNanos = nanos;
    }

    public void beginExecutor() {
        this.executorDepth++;
    }

    public void recordExecutor(long nanos, long frames, int stackDepth) {
        this.executorRuns++;
        if (this.executorDepth > 0) {
            this.executorDepth--;
        }
        if (this.executorDepth == 0) {
            this.executorNanos += nanos;
        }
        this.framesExecuted = NumberUtils.saturatedAdd(this.framesExecuted, frames);
        this.stackPeak = Math.max(this.stackPeak, stackDepth);
    }

    public void recordNodeEnter() {
        this.nodeEnters++;
    }

    public void recordPrefixSatisfied() {
        this.prefixSatisfied++;
    }

    public void recordAggregationGraph(int uniqueNodes, long logicalNodes, long compileNanos, boolean reused) {
        if (reused) {
            this.aggregationGraphReuses++;
        } else {
            this.aggregationGraphBuilds++;
        }
        this.aggregationCompileNanos += compileNanos;
        this.aggregationUniqueNodes = Math.max(this.aggregationUniqueNodes, uniqueNodes);
        this.aggregationLogicalNodes = Math.max(this.aggregationLogicalNodes, logicalNodes);
    }

    public void recordAggregationGraphContextReuse() {
        this.aggregationGraphContextReuses++;
    }

    public void recordAggregationGraphContextScan(int variants, int checked, boolean matched, long nanos) {
        this.aggregationGraphContextLookups++;
        if (matched) {
            this.aggregationGraphContextMatches++;
        }
        this.aggregationGraphContextVariantChecks += checked;
        this.aggregationGraphContextMaxVariants = Math.max(this.aggregationGraphContextMaxVariants, variants);
        this.aggregationGraphContextScanNanos += nanos;
    }

    public void recordAggregationCompileNanos(long nanos) {
        this.aggregationCompileNanos += nanos;
    }

    public void recordAggregationContextCacheHit() {
        this.aggregationContextCacheHits++;
    }

    public void recordAggregationContextCacheMiss() {
        this.aggregationContextCacheMisses++;
    }

    public void recordAggregationAnalysisCacheHit() {
        this.aggregationAnalysisCacheHits++;
    }

    public void recordAggregationAnalysisCacheMiss() {
        this.aggregationAnalysisCacheMisses++;
    }

    public void recordAggregationExpansionNanos(long nanos) {
        this.aggregationExpansionNanos += nanos;
    }

    public void recordAggregationSccNanos(long nanos) {
        this.aggregationSccNanos += nanos;
    }

    public void recordAggregationExecutableNanos(long nanos) {
        this.aggregationExecutableNanos += nanos;
    }

    public void recordAggregationFinalizationNanos(long nanos) {
        this.aggregationFinalizationNanos += nanos;
    }

    public void recordAggregationExecution(long nanos, long propagationNanos, long completionNanos,
                                           long mergedRequests, boolean segment) {
        this.aggregationRuns++;
        if (segment) {
            this.aggregationSegmentRuns++;
        }
        this.aggregationNanos += nanos;
        this.aggregationPropagationNanos += propagationNanos;
        this.aggregationCompletionNanos += completionNanos;
        this.aggregationMergedRequests = NumberUtils.saturatedAdd(
                this.aggregationMergedRequests,
                mergedRequests);
    }

    public void recordAggregationBoundary(boolean multiplePatterns, boolean unsafeProgram, boolean cycle) {
        this.aggregationBoundaryNodes++;
        if (multiplePatterns) {
            this.aggregationBoundaryMultiplePatterns++;
        }
        if (unsafeProgram) {
            this.aggregationBoundaryUnsafeProgram++;
        }
        if (cycle) {
            this.aggregationBoundaryCycle++;
        }
    }

    public void recordAggregationCandidateGraphEligibleNode() {
        this.aggregationCandidateGraphEligibleNodes++;
    }

    public void recordAggregationCandidateGraphAttempt() {
        this.aggregationCandidateGraphAttempts++;
    }

    public void recordAggregationCandidateGraphSuccess() {
        this.aggregationCandidateGraphSuccesses++;
    }

    public void recordAggregationCandidateGraphCertifiedMissing() {
        this.aggregationCandidateGraphCertifiedMissing++;
    }

    public void recordAggregationCandidateGraphStructuralFallback() {
        this.aggregationCandidateGraphStructuralFallbacks++;
    }

    public void recordAggregationCandidateGraphExecutionFallback() {
        this.aggregationCandidateGraphExecutionFallbacks++;
    }

    public void recordAggregationCandidateGraphAbort() {
        this.aggregationCandidateGraphAborts++;
    }

    public void recordAggregationCycleCandidateGraphEligibleNode() {
        this.aggregationCycleCandidateGraphEligibleNodes++;
    }

    public void recordAggregationCycleCandidateGraphPrefilterRejection() {
        this.aggregationCycleCandidateGraphPrefilterRejections++;
    }

    public void recordAggregationCycleCandidateGraphPrefilterUnknown() {
        this.aggregationCycleCandidateGraphPrefilterUnknowns++;
    }

    public void recordAggregationCycleCandidateGraphAttempt() {
        this.aggregationCycleCandidateGraphAttempts++;
    }

    public void recordAggregationCycleCandidateGraphSuccess() {
        this.aggregationCycleCandidateGraphSuccesses++;
    }

    public void recordAggregationCycleCandidateGraphStructuralFallback() {
        this.aggregationCycleCandidateGraphStructuralFallbacks++;
    }

    public void recordAggregationCycleCandidateGraphExecutionFallback() {
        this.aggregationCycleCandidateGraphExecutionFallbacks++;
    }

    public void recordAggregationCycleCandidateGraphDeferredBypass() {
        this.aggregationCycleCandidateGraphDeferredBypasses++;
    }

    public void recordAggregationCycleCandidateGraphAbort() {
        this.aggregationCycleCandidateGraphAborts++;
    }

    public void recordAggregationBoundaryTransactionGuardGraph() {
        this.aggregationBoundaryTransactionGuardGraphs++;
    }

    public void recordAggregationBoundaryTransactionGuardRun() {
        this.aggregationBoundaryTransactionGuardRuns++;
    }

    public void recordAggregationBoundaryTransactionCraftFailure() {
        this.aggregationBoundaryTransactionFallbacks++;
        this.aggregationBoundaryTransactionCraftFailures++;
    }

    public void recordAggregationBoundaryTransactionSimulationProbe() {
        this.aggregationBoundaryTransactionSimulationProbes++;
        clearAggregationBoundaryTransactionSimulationFailure();
    }

    public void recordAggregationBoundaryTransactionSimulationSuccess() {
        this.aggregationBoundaryTransactionSimulationSuccesses++;
        clearAggregationBoundaryTransactionSimulationFailure();
    }

    public void recordAggregationBoundaryTransactionSimulationPropagation() {
        this.aggregationBoundaryTransactionSimulationPropagations++;
    }

    public void recordAggregationBoundarySchedulerAttempt() {
        this.aggregationBoundarySchedulerAttempts++;
    }

    public void recordAggregationBoundarySchedulerAttemptFailure() {
        this.aggregationBoundarySchedulerAttemptFailures++;
    }

    public void recordAggregationBoundarySchedulerInitialFailure() {
        this.aggregationBoundarySchedulerInitialFailures++;
    }

    public void recordAggregationBoundarySchedulerRetry() {
        this.aggregationBoundarySchedulerRetries++;
    }

    public void recordAggregationBoundarySchedulerRetrySuccess() {
        this.aggregationBoundarySchedulerRetrySuccesses++;
    }

    public void recordAggregationBoundarySchedulerDeferredMissing() {
        this.aggregationBoundarySchedulerDeferredMissing++;
    }

    public void recordAggregationBoundarySchedulerRecoveredGraph() {
        this.aggregationBoundarySchedulerRecoveredGraphs++;
    }

    public void recordAggregationBoundarySchedulerDependencyCheck() {
        this.aggregationBoundarySchedulerDependencyChecks++;
    }

    public void recordAggregationBoundarySchedulerDependencyWakeup() {
        this.aggregationBoundarySchedulerDependencyWakeups++;
    }

    public void recordAggregationBoundarySchedulerDependencySkip() {
        this.aggregationBoundarySchedulerDependencySkips++;
    }

    public void recordAggregationBoundarySchedulerUnknownFailure() {
        this.aggregationBoundarySchedulerUnknownFailures++;
    }

    public void recordAggregationBoundaryTransactionSimulationFailure(AEKey key, long amount) {
        this.aggregationBoundaryTransactionSimulationFailureKey = key;
        this.aggregationBoundaryTransactionSimulationFailureAmount = amount;
    }

    public void recordAggregationBoundaryTransactionSimulationFallback(AEKey rootKey, int uniqueNodes,
                                                                       long logicalNodes, int ancestorDepth,
                                                                       long compileNanos) {
        this.aggregationBoundaryTransactionFallbacks++;
        this.aggregationBoundaryTransactionSimulationFallbacks++;
        SimulationFallbackGraph fallback = new SimulationFallbackGraph(
                rootKey,
                uniqueNodes,
                logicalNodes,
                ancestorDepth,
                compileNanos,
                this.aggregationBoundaryTransactionSimulationFailureKey,
                this.aggregationBoundaryTransactionSimulationFailureAmount);
        if (this.aggregationBoundaryTransactionSimulationFallbackFirst == null) {
            this.aggregationBoundaryTransactionSimulationFallbackFirst = fallback;
        }
        if (isMoreExpensiveFallback(
                fallback,
                this.aggregationBoundaryTransactionSimulationFallbackMaxCompile)) {
            this.aggregationBoundaryTransactionSimulationFallbackMaxCompile = fallback;
        }
        clearAggregationBoundaryTransactionSimulationFailure();
    }

    private void clearAggregationBoundaryTransactionSimulationFailure() {
        this.aggregationBoundaryTransactionSimulationFailureKey = null;
        this.aggregationBoundaryTransactionSimulationFailureAmount = UNAVAILABLE_VALUE;
    }

    private static boolean isMoreExpensiveFallback(SimulationFallbackGraph candidate,
                                                   @Nullable SimulationFallbackGraph current) {
        if (current == null || candidate.compileNanos() != current.compileNanos()) {
            return current == null || candidate.compileNanos() > current.compileNanos();
        }
        if (candidate.logicalNodes() != current.logicalNodes()) {
            return candidate.logicalNodes() > current.logicalNodes();
        }
        return candidate.ancestorDepth() > current.ancestorDepth();
    }

    public void recordAggregationBoundaryTransactionSetupNanos(long nanos) {
        this.aggregationBoundaryTransactionSetupNanos += nanos;
    }

    public void recordAggregationBoundaryTransactionApplyNanos(long nanos) {
        this.aggregationBoundaryTransactionApplyNanos += nanos;
    }

    public void recordAggregationBoundaryRuntimeBuild() {
        this.aggregationBoundaryRuntimeBuilds++;
    }

    public void recordAggregationBoundaryRuntimeReuse() {
        this.aggregationBoundaryRuntimeReuses++;
    }

    public void recordAggregationBoundarySetupNanos(long nanos) {
        this.aggregationBoundarySetupNanos += nanos;
    }

    public void recordAggregationBoundaryTailNanos(long nanos) {
        this.aggregationBoundaryTailNanos += nanos;
    }

    public void recordAggregationFallback(AggregationFallbackReason reason, long compileNanos) {
        this.aggregationFallbacks++;
        this.aggregationCompileNanos += compileNanos;
        switch (reason) {
            case ROOT_CONTAINER -> this.aggregationFallbackRootContainer++;
            case DYNAMIC_INPUT -> this.aggregationFallbackDynamicInput++;
            case MULTIPLE_PATTERN_CANDIDATES -> this.aggregationFallbackMultiplePatterns++;
            case BASELINE_PROGRAM -> this.aggregationFallbackBaselineProgram++;
            case OUTPUT_INPUT_FEEDBACK -> this.aggregationFallbackOutputInputFeedback++;
            case CYCLE -> this.aggregationFallbackCycle++;
        }
    }

    public void recordAggregationSegmentAttempt() {
        this.aggregationSegmentAttempts++;
    }

    public void recordAggregationSegmentRejection(AggregationFallbackReason reason, long compileNanos) {
        this.aggregationSegmentRejections++;
        this.aggregationCompileNanos += compileNanos;
        switch (reason) {
            case ROOT_CONTAINER -> this.aggregationSegmentRejectionRootContainer++;
            case DYNAMIC_INPUT -> this.aggregationSegmentRejectionDynamicInput++;
            case MULTIPLE_PATTERN_CANDIDATES -> this.aggregationSegmentRejectionMultiplePatterns++;
            case BASELINE_PROGRAM -> this.aggregationSegmentRejectionBaselineProgram++;
            case OUTPUT_INPUT_FEEDBACK -> this.aggregationSegmentRejectionOutputInputFeedback++;
            case CYCLE -> this.aggregationSegmentRejectionCycle++;
        }
    }

    public void recordAggregationCompileFailure(AggregationFallbackReason reason, @Nullable AEKey key,
                                                int patternCandidates, int scannedNodes) {
        if (!NO_FAILURE.equals(this.aggregationCompileFailureReason)) {
            return;
        }

        this.aggregationCompileFailureReason = reason.name();
        if (key != null) {
            this.aggregationCompileFailureKeyType = key.getType().getId().toString();
            this.aggregationCompileFailureKeyId = key.getId().toString();
        }
        this.aggregationCompileFailurePatternCandidates = patternCandidates;
        this.aggregationCompileFailureScannedNodes = scannedNodes;
    }

    public void recordProgramLookup() {
        this.programLookups++;
    }

    public void recordProgramHit() {
        this.programHits++;
    }

    public void recordSingleProgramCompiled() {
        this.singlePrograms++;
    }

    public void recordTerminalProgramCompiled() {
        this.terminalPrograms++;
    }

    public void recordBaselineProgramCompiled(BarrierReason reason) {
        this.baselinePrograms++;
        recordBarrierReason(reason);
    }

    public void recordContainerFallback() {
        recordBarrierReason(BarrierReason.CONTAINER);
    }

    public void recordSinglePathExecution() {
        this.singlePathExecutions++;
    }

    public void recordTerminalExecution() {
        this.terminalExecutions++;
    }

    public void recordBaselineExecution(long nanos) {
        this.baselineExecutions++;
        this.baselineNanos += nanos;
    }

    public void recordCompiledProcess(long times) {
        this.compiledProcessRuns++;
        this.compiledProcessTimes = NumberUtils.saturatedAdd(this.compiledProcessTimes, times);
    }

    public void recordBaselineProcess(long times) {
        this.baselineProcessRuns++;
        this.baselineProcessTimes = NumberUtils.saturatedAdd(this.baselineProcessTimes, times);
    }

    public void recordChildRequest() {
        this.childRequests++;
    }

    public void recordAggregationExactTemplateValidation(boolean valid) {
        this.aggregationExactTemplateValidations++;
        if (!valid) {
            this.aggregationExactTemplateValidationFailures++;
        }
    }

    public void recordAggregationExactTemplateExtraction() {
        this.aggregationExactTemplateExtractions++;
    }

    public void recordAggregationTemplateValidationNanos(long nanos) {
        this.aggregationTemplateValidationNanos += nanos;
    }

    public void recordTemplateLookup() {
        this.templateMetricsActive = true;
        this.templateLookups++;
    }

    public void recordTemplateHit() {
        this.templateHits++;
    }

    public void recordTemplateParentDelegations(long delegations) {
        if (delegations > 0) {
            this.templateParentDelegations = NumberUtils.saturatedAdd(
                    this.templateParentDelegations,
                    delegations);
        }
    }

    public void recordTemplateFallback() {
        this.templateFallbacks++;
    }

    public void recordTemplateColdBuild(int rawCandidates, int validCandidates) {
        this.templateColdBuilds++;
        recordTemplateCandidates(rawCandidates, validCandidates);
    }

    public void recordTemplateValidation(int validCandidates) {
        this.templateValidations++;
        this.validCandidatesObserved = NumberUtils.saturatedAdd(this.validCandidatesObserved, validCandidates);
    }

    public void recordTemplatePossibleInputsRebuild(int rawCandidates, int validCandidates) {
        this.templatePossibleInputsRebuilds++;
        recordTemplateCandidates(rawCandidates, validCandidates);
    }

    public void recordTemplateMembershipRebuild(int rawCandidates, int validCandidates) {
        this.templateMembershipRebuilds++;
        recordTemplateCandidates(rawCandidates, validCandidates);
    }

    public void recordTemplateNanos(long nanos) {
        this.templateNanos += nanos;
    }

    public void recordValidationEpochBump() {
        this.validationEpochBumps++;
    }

    public void recordPlanBuildNanos(long nanos) {
        this.planBuildNanos += nanos;
    }

    public void logSummary(AEKey output, long requestedAmount, CalculationStrategy strategy, Level level,
                           @Nullable ICraftingPlan plan, @Nullable Throwable failure) {
        if (!MaxFastCalculationLogger.isEnabled()) {
            return;
        }
        boolean planAvailable = plan != null;
        GenericStack finalOutput = planAvailable ? plan.finalOutput() : null;
        long plannedAmount = finalOutput != null ? finalOutput.amount() : UNAVAILABLE_VALUE;
        long bytes = planAvailable ? plan.bytes() : UNAVAILABLE_VALUE;
        boolean simulation = planAvailable && plan.simulation();
        boolean multiplePaths = planAvailable && plan.multiplePaths();
        int patternTypes = planAvailable ? plan.patternTimes().size() : (int) UNAVAILABLE_VALUE;
        long patternOperations = planAvailable ? sumPatternOperations(plan) : UNAVAILABLE_VALUE;
        int usedTypes = planAvailable ? plan.usedItems().size() : (int) UNAVAILABLE_VALUE;
        int emittedTypes = planAvailable ? plan.emittedItems().size() : (int) UNAVAILABLE_VALUE;
        int missingTypes = planAvailable ? plan.missingItems().size() : (int) UNAVAILABLE_VALUE;
        AEKey firstMissingKey = null;
        long firstMissingAmount = UNAVAILABLE_VALUE;
        if (planAvailable) {
            for (var entry : plan.missingItems()) {
                firstMissingKey = entry.getKey();
                firstMissingAmount = entry.getLongValue();
                break;
            }
        }
        String firstMissingKeyType = keyType(firstMissingKey);
        String firstMissingKeyId = keyId(firstMissingKey);
        String firstBranchFailureKeyType = keyType(this.branchFailureFirstKey);
        String firstBranchFailureKeyId = keyId(this.branchFailureFirstKey);
        SimulationFallbackGraph firstFallback = this.aggregationBoundaryTransactionSimulationFallbackFirst;
        SimulationFallbackGraph maxCompileFallback = this.aggregationBoundaryTransactionSimulationFallbackMaxCompile;
        Result result = failure instanceof InterruptedException ? Result.INTERRUPTED : failure != null ? Result.ERROR :
                !planAvailable ? Result.NO_PLAN : simulation ? Result.SIMULATED_MISSING : Result.CRAFTABLE;
        String failureType = failure == null ? NO_FAILURE : failure.getClass().getName();

        MaxFastCalculationLogger.info(
                "[MAX_FAST] result={} elapsed_ms={} dimension={} output={} requested={} planned={} strategy={} " +
                        "plan_available={} simulation={} multiple_paths={} bytes={} pattern_types={} " +
                        "pattern_operations={} used_types={} emitted_types={} missing_types={} " +
                        "missing_first_key_type={} missing_first_key_id={} missing_first_amount={} failure={} " +
                        "attempts={} attempt_success={} attempt_failed={} attempt_simulation={} attempt_aborted={} " +
                        "attempt_ms={} attempt_max_ms={} attempt_request_min={} attempt_request_max={} " +
                        "branch_failure_reports={} branch_failure_first_key_type={} " +
                        "branch_failure_first_key_id={} branch_failure_first_amount={} " +
                        "executor_runs={} executor_ms={} frames={} stack_peak={} node_enters={} prefix_satisfied={} " +
                        "aggregation_runs={} aggregation_ms={} aggregation_graph_builds={} " +
                        "aggregation_graph_reuses={} aggregation_graph_context_reuses={} " +
                        "aggregation_graph_context_lookups={} aggregation_graph_context_matches={} " +
                        "aggregation_graph_context_variant_checks={} aggregation_graph_context_max_variants={} " +
                        "aggregation_graph_context_scan_ms={} " +
                        "aggregation_context_cache_hits={} " +
                        "aggregation_context_cache_misses={} aggregation_analysis_cache_hits={} " +
                        "aggregation_analysis_cache_misses={} aggregation_compile_ms={} " +
                        "aggregation_expansion_ms={} aggregation_scc_ms={} " +
                        "aggregation_executable_ms={} aggregation_finalization_ms={} " +
                        "aggregation_propagation_ms={} " +
                        "aggregation_completion_ms={} aggregation_unique_nodes={} " +
                        "aggregation_logical_nodes={} aggregation_merged_requests={} aggregation_fallbacks={} " +
                        "aggregation_fallback_root_container={} aggregation_fallback_dynamic_input={} " +
                        "aggregation_fallback_multiple_patterns={} aggregation_fallback_baseline_program={} " +
                        "aggregation_fallback_output_input_feedback={} aggregation_fallback_cycle={} " +
                        "aggregation_boundary_nodes={} aggregation_boundary_multiple_patterns={} " +
                        "aggregation_boundary_unsafe_program={} aggregation_boundary_cycle={} " +
                        "aggregation_candidate_graph_eligible_nodes={} " +
                        "aggregation_candidate_graph_attempts={} aggregation_candidate_graph_successes={} " +
                        "aggregation_candidate_graph_certified_missing={} " +
                        "aggregation_candidate_graph_structural_fallbacks={} " +
                        "aggregation_candidate_graph_execution_fallbacks={} " +
                        "aggregation_candidate_graph_aborts={} " +
                        "aggregation_cycle_candidate_graph_eligible_nodes={} " +
                        "aggregation_cycle_candidate_graph_prefilter_rejections={} " +
                        "aggregation_cycle_candidate_graph_prefilter_unknowns={} " +
                        "aggregation_cycle_candidate_graph_attempts={} " +
                        "aggregation_cycle_candidate_graph_successes={} " +
                        "aggregation_cycle_candidate_graph_structural_fallbacks={} " +
                        "aggregation_cycle_candidate_graph_execution_fallbacks={} " +
                        "aggregation_cycle_candidate_graph_deferred_bypasses={} " +
                        "aggregation_cycle_candidate_graph_aborts={} " +
                        "aggregation_boundary_transaction_guard_graphs={} " +
                        "aggregation_boundary_transaction_guard_runs={} " +
                        "aggregation_boundary_transaction_fallbacks={} " +
                        "aggregation_boundary_transaction_craft_failures={} " +
                        "aggregation_boundary_transaction_simulation_probes={} " +
                        "aggregation_boundary_transaction_simulation_successes={} " +
                        "aggregation_boundary_transaction_simulation_propagations={} " +
                        "aggregation_boundary_transaction_simulation_fallbacks={} " +
                        "aggregation_boundary_scheduler_attempts={} " +
                        "aggregation_boundary_scheduler_attempt_failures={} " +
                        "aggregation_boundary_scheduler_initial_failures={} " +
                        "aggregation_boundary_scheduler_retries={} " +
                        "aggregation_boundary_scheduler_retry_successes={} " +
                        "aggregation_boundary_scheduler_deferred_missing={} " +
                        "aggregation_boundary_scheduler_recovered_graphs={} " +
                        "aggregation_boundary_scheduler_dependency_checks={} " +
                        "aggregation_boundary_scheduler_dependency_wakeups={} " +
                        "aggregation_boundary_scheduler_dependency_skips={} " +
                        "aggregation_boundary_scheduler_unknown_failures={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_root_key_type={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_root_key_id={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_unique_nodes={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_logical_nodes={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_ancestor_depth={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_compile_ms={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_failure_key_type={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_failure_key_id={} " +
                        "aggregation_boundary_transaction_simulation_fallback_first_failure_amount={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_root_key_type={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_root_key_id={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_unique_nodes={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_logical_nodes={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_ancestor_depth={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_ms={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_failure_key_type={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_failure_key_id={} " +
                        "aggregation_boundary_transaction_simulation_fallback_max_compile_failure_amount={} " +
                        "aggregation_boundary_transaction_setup_ms={} " +
                        "aggregation_boundary_transaction_apply_ms={} " +
                        "aggregation_boundary_runtime_builds={} aggregation_boundary_runtime_reuses={} " +
                        "aggregation_boundary_setup_ms={} " +
                        "aggregation_boundary_tail_inclusive_ms={} " +
                        "program_lookups={} program_hits={} program_compiled={} program_single={} " +
                        "program_terminal={} program_barrier={} single_runs={} terminal_runs={} baseline_runs={} " +
                        "baseline_ms={} barrier_reason_multi_path={} barrier_reason_limit_qty={} " +
                        "barrier_reason_container={} barrier_reason_non_positive_output={} " +
                        "barrier_reason_invalid_metadata={} compiled_process_runs={} " +
                        "compiled_process_times={} baseline_process_runs={} baseline_process_times={} " +
                        "child_requests={} aggregation_exact_template_validations={} " +
                        "aggregation_exact_template_validation_failures={} " +
                        "aggregation_exact_template_extractions={} " +
                        "aggregation_template_validation_ms={} " +
                        "template_metrics_active={} template_lookups={} template_hits={} " +
                        "template_parent_delegations={} " +
                        "template_fallbacks={} " +
                        "template_cold_builds={} template_validations={} template_possible_rebuilds={} " +
                        "template_membership_rebuilds={} template_ms={} raw_candidates={} valid_candidates={} " +
                        "max_raw_candidates={} validation_epoch_bumps={} plan_build_ms={} " +
                        "aggregation_segment_attempts={} aggregation_segment_runs={} " +
                        "aggregation_segment_rejections={} aggregation_segment_rejection_root_container={} " +
                        "aggregation_segment_rejection_dynamic_input={} " +
                        "aggregation_segment_rejection_multiple_patterns={} " +
                        "aggregation_segment_rejection_baseline_program={} " +
                        "aggregation_segment_rejection_output_input_feedback={} " +
                        "aggregation_segment_rejection_cycle={} aggregation_compile_failure_reason={} " +
                        "aggregation_compile_failure_key_type={} aggregation_compile_failure_key_id={} " +
                        "aggregation_compile_failure_pattern_candidates={} " +
                        "aggregation_compile_failure_scanned_nodes={}",
                result,
                toMilliseconds(this.calculationNanos),
                level.dimension().location(),
                output,
                requestedAmount,
                plannedAmount,
                strategy,
                planAvailable,
                simulation,
                multiplePaths,
                bytes,
                patternTypes,
                patternOperations,
                usedTypes,
                emittedTypes,
                missingTypes,
                firstMissingKeyType,
                firstMissingKeyId,
                firstMissingAmount,
                failureType,
                this.attempts,
                this.successfulAttempts,
                this.failedAttempts,
                this.simulationAttempts,
                this.abortedAttempts,
                toMilliseconds(this.attemptNanos),
                toMilliseconds(this.maxAttemptNanos),
                this.attempts == 0 ? UNAVAILABLE_VALUE : this.minAttemptRequest,
                this.attempts == 0 ? UNAVAILABLE_VALUE : this.maxAttemptRequest,
                this.branchFailureReports,
                firstBranchFailureKeyType,
                firstBranchFailureKeyId,
                this.branchFailureFirstAmount,
                this.executorRuns,
                toMilliseconds(this.executorNanos),
                this.framesExecuted,
                this.stackPeak,
                this.nodeEnters,
                this.prefixSatisfied,
                this.aggregationRuns,
                toMilliseconds(this.aggregationNanos),
                this.aggregationGraphBuilds,
                this.aggregationGraphReuses,
                this.aggregationGraphContextReuses,
                this.aggregationGraphContextLookups,
                this.aggregationGraphContextMatches,
                this.aggregationGraphContextVariantChecks,
                this.aggregationGraphContextMaxVariants,
                toMilliseconds(this.aggregationGraphContextScanNanos),
                this.aggregationContextCacheHits,
                this.aggregationContextCacheMisses,
                this.aggregationAnalysisCacheHits,
                this.aggregationAnalysisCacheMisses,
                toMilliseconds(this.aggregationCompileNanos),
                toMilliseconds(this.aggregationExpansionNanos),
                toMilliseconds(this.aggregationSccNanos),
                toMilliseconds(this.aggregationExecutableNanos),
                toMilliseconds(this.aggregationFinalizationNanos),
                toMilliseconds(this.aggregationPropagationNanos),
                toMilliseconds(this.aggregationCompletionNanos),
                this.aggregationUniqueNodes,
                this.aggregationLogicalNodes,
                this.aggregationMergedRequests,
                this.aggregationFallbacks,
                this.aggregationFallbackRootContainer,
                this.aggregationFallbackDynamicInput,
                this.aggregationFallbackMultiplePatterns,
                this.aggregationFallbackBaselineProgram,
                this.aggregationFallbackOutputInputFeedback,
                this.aggregationFallbackCycle,
                this.aggregationBoundaryNodes,
                this.aggregationBoundaryMultiplePatterns,
                this.aggregationBoundaryUnsafeProgram,
                this.aggregationBoundaryCycle,
                this.aggregationCandidateGraphEligibleNodes,
                this.aggregationCandidateGraphAttempts,
                this.aggregationCandidateGraphSuccesses,
                this.aggregationCandidateGraphCertifiedMissing,
                this.aggregationCandidateGraphStructuralFallbacks,
                this.aggregationCandidateGraphExecutionFallbacks,
                this.aggregationCandidateGraphAborts,
                this.aggregationCycleCandidateGraphEligibleNodes,
                this.aggregationCycleCandidateGraphPrefilterRejections,
                this.aggregationCycleCandidateGraphPrefilterUnknowns,
                this.aggregationCycleCandidateGraphAttempts,
                this.aggregationCycleCandidateGraphSuccesses,
                this.aggregationCycleCandidateGraphStructuralFallbacks,
                this.aggregationCycleCandidateGraphExecutionFallbacks,
                this.aggregationCycleCandidateGraphDeferredBypasses,
                this.aggregationCycleCandidateGraphAborts,
                this.aggregationBoundaryTransactionGuardGraphs,
                this.aggregationBoundaryTransactionGuardRuns,
                this.aggregationBoundaryTransactionFallbacks,
                this.aggregationBoundaryTransactionCraftFailures,
                this.aggregationBoundaryTransactionSimulationProbes,
                this.aggregationBoundaryTransactionSimulationSuccesses,
                this.aggregationBoundaryTransactionSimulationPropagations,
                this.aggregationBoundaryTransactionSimulationFallbacks,
                this.aggregationBoundarySchedulerAttempts,
                this.aggregationBoundarySchedulerAttemptFailures,
                this.aggregationBoundarySchedulerInitialFailures,
                this.aggregationBoundarySchedulerRetries,
                this.aggregationBoundarySchedulerRetrySuccesses,
                this.aggregationBoundarySchedulerDeferredMissing,
                this.aggregationBoundarySchedulerRecoveredGraphs,
                this.aggregationBoundarySchedulerDependencyChecks,
                this.aggregationBoundarySchedulerDependencyWakeups,
                this.aggregationBoundarySchedulerDependencySkips,
                this.aggregationBoundarySchedulerUnknownFailures,
                keyType(firstFallback == null ? null : firstFallback.rootKey()),
                keyId(firstFallback == null ? null : firstFallback.rootKey()),
                firstFallback == null ? UNAVAILABLE_VALUE : firstFallback.uniqueNodes(),
                firstFallback == null ? UNAVAILABLE_VALUE : firstFallback.logicalNodes(),
                firstFallback == null ? UNAVAILABLE_VALUE : firstFallback.ancestorDepth(),
                firstFallback == null ? UNAVAILABLE_VALUE : toMilliseconds(firstFallback.compileNanos()),
                keyType(firstFallback == null ? null : firstFallback.failureKey()),
                keyId(firstFallback == null ? null : firstFallback.failureKey()),
                firstFallback == null ? UNAVAILABLE_VALUE : firstFallback.failureAmount(),
                keyType(maxCompileFallback == null ? null : maxCompileFallback.rootKey()),
                keyId(maxCompileFallback == null ? null : maxCompileFallback.rootKey()),
                maxCompileFallback == null ? UNAVAILABLE_VALUE : maxCompileFallback.uniqueNodes(),
                maxCompileFallback == null ? UNAVAILABLE_VALUE : maxCompileFallback.logicalNodes(),
                maxCompileFallback == null ? UNAVAILABLE_VALUE : maxCompileFallback.ancestorDepth(),
                maxCompileFallback == null ? UNAVAILABLE_VALUE :
                        toMilliseconds(maxCompileFallback.compileNanos()),
                keyType(maxCompileFallback == null ? null : maxCompileFallback.failureKey()),
                keyId(maxCompileFallback == null ? null : maxCompileFallback.failureKey()),
                maxCompileFallback == null ? UNAVAILABLE_VALUE : maxCompileFallback.failureAmount(),
                toMilliseconds(this.aggregationBoundaryTransactionSetupNanos),
                toMilliseconds(this.aggregationBoundaryTransactionApplyNanos),
                this.aggregationBoundaryRuntimeBuilds,
                this.aggregationBoundaryRuntimeReuses,
                toMilliseconds(this.aggregationBoundarySetupNanos),
                toMilliseconds(this.aggregationBoundaryTailNanos),
                this.programLookups,
                this.programHits,
                this.singlePrograms + this.terminalPrograms + this.baselinePrograms,
                this.singlePrograms,
                this.terminalPrograms,
                this.baselinePrograms,
                this.singlePathExecutions,
                this.terminalExecutions,
                this.baselineExecutions,
                toMilliseconds(this.baselineNanos),
                this.multiPathBarriers,
                this.limitQuantityBarriers,
                this.containerBarriers,
                this.nonPositiveOutputBarriers,
                this.invalidMetadataBarriers,
                this.compiledProcessRuns,
                this.compiledProcessTimes,
                this.baselineProcessRuns,
                this.baselineProcessTimes,
                this.childRequests,
                this.aggregationExactTemplateValidations,
                this.aggregationExactTemplateValidationFailures,
                this.aggregationExactTemplateExtractions,
                toMilliseconds(this.aggregationTemplateValidationNanos),
                this.templateMetricsActive,
                this.templateLookups,
                this.templateHits,
                this.templateParentDelegations,
                this.templateFallbacks,
                this.templateColdBuilds,
                this.templateValidations,
                this.templatePossibleInputsRebuilds,
                this.templateMembershipRebuilds,
                toMilliseconds(this.templateNanos),
                this.rawCandidatesScanned,
                this.validCandidatesObserved,
                this.maxRawCandidates,
                this.validationEpochBumps,
                toMilliseconds(this.planBuildNanos),
                this.aggregationSegmentAttempts,
                this.aggregationSegmentRuns,
                this.aggregationSegmentRejections,
                this.aggregationSegmentRejectionRootContainer,
                this.aggregationSegmentRejectionDynamicInput,
                this.aggregationSegmentRejectionMultiplePatterns,
                this.aggregationSegmentRejectionBaselineProgram,
                this.aggregationSegmentRejectionOutputInputFeedback,
                this.aggregationSegmentRejectionCycle,
                this.aggregationCompileFailureReason,
                this.aggregationCompileFailureKeyType,
                this.aggregationCompileFailureKeyId,
                this.aggregationCompileFailurePatternCandidates,
                this.aggregationCompileFailureScannedNodes);
    }

    private void finishAttemptTiming() {
        long elapsedNanos = System.nanoTime() - this.activeAttemptStartedNanos;
        this.attemptNanos += elapsedNanos;
        this.maxAttemptNanos = Math.max(this.maxAttemptNanos, elapsedNanos);
        this.attemptActive = false;
    }

    private void recordBarrierReason(BarrierReason reason) {
        switch (reason) {
            case MULTI_PATH -> this.multiPathBarriers++;
            case LIMIT_QUANTITY -> this.limitQuantityBarriers++;
            case CONTAINER -> this.containerBarriers++;
            case NON_POSITIVE_OUTPUT -> this.nonPositiveOutputBarriers++;
            case INVALID_METADATA -> this.invalidMetadataBarriers++;
        }
    }

    private void recordTemplateCandidates(int rawCandidates, int validCandidates) {
        this.rawCandidatesScanned = NumberUtils.saturatedAdd(this.rawCandidatesScanned, rawCandidates);
        this.validCandidatesObserved = NumberUtils.saturatedAdd(this.validCandidatesObserved, validCandidates);
        this.maxRawCandidates = Math.max(this.maxRawCandidates, rawCandidates);
    }

    private static long sumPatternOperations(ICraftingPlan plan) {
        long operations = 0L;
        for (long times : plan.patternTimes().values()) {
            operations = NumberUtils.saturatedAdd(operations, times);
        }
        return operations;
    }

    private static String keyType(@Nullable AEKey key) {
        return key == null ? NO_FAILURE : key.getType().getId().toString();
    }

    private static String keyId(@Nullable AEKey key) {
        return key == null ? NO_FAILURE : key.getId().toString();
    }

    private static double toMilliseconds(long nanos) {
        return nanos / NANOS_PER_MILLISECOND;
    }
}
