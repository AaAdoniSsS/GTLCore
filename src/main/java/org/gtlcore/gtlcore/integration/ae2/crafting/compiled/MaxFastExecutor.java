package org.gtlcore.gtlcore.integration.ae2.crafting.compiled;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingSimulationStateFastAccess;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeProcess;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics.AggregationFallbackReason;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MaxFastExecutor {

    private static final int INITIAL_FRAME_CAPACITY = 16;
    private static final AEKey[] EMPTY_EXTERNAL_ANCESTORS = new AEKey[0];

    private static final byte FRAME_NODE_ENTER = 1;
    private static final byte FRAME_RUN_SINGLE = 2;
    private static final byte FRAME_PROCESS_FINISH = 3;
    private static final byte FRAME_NODE_AFTER_PROCESS = 4;
    private static final byte FRAME_PROCESS_CHILDREN = 5;
    private static final long SKIP_SEGMENT_AGGREGATION = 0L;
    private static final long TRY_SEGMENT_AGGREGATION = 1L;
    private static final long BOUNDARY_ATTEMPT_FAILED = -1L;
    private static final int ROOT_CUT_PREFILTER_NODE_BUDGET = 128;
    private static final int PREPARED_BOUNDARY_CACHE_LIMIT = 2048;

    private enum RootCutPrefilterResult {
        PASS,
        REJECT,
        UNKNOWN
    }

    public enum CandidateSegmentResult {
        SUCCEEDED,
        CERTIFIED_MISSING,
        STRUCTURAL_FALLBACK,
        EXECUTION_FALLBACK
    }

    private final CompilationCache compilationCache;
    private final @Nullable IPatternDetails forcedRootPattern;
    private final boolean forcedRootAncestorCut;
    private final ExecutionStack stack = new ExecutionStack();
    private AggregatedGraph aggregatedGraph;
    private GraphCompilation aggregationFailure;
    private AEKey[] aggregatedExternalAncestors = EMPTY_EXTERNAL_ANCESTORS;
    private long aggregatedGraphCompileNanos;
    private long[] aggregatedRequests = new long[0];
    private long[] aggregatedRemainingRequests = new long[0];
    private long[] aggregatedTotalRequestedItems = new long[0];
    private long[] aggregatedProcessTimes = new long[0];
    private boolean[] aggregatedCompletedNodes = new boolean[0];
    private boolean[] aggregatedPendingBoundaries = new boolean[0];
    private int[] aggregatedOutstandingChildren = new int[0];
    private BoundaryFailureDependencies[] aggregatedBoundaryFailures = new BoundaryFailureDependencies[0];
    private ICraftingTreeNode[] aggregatedPreparedBoundaries = new ICraftingTreeNode[0];
    private final Map<BoundaryRuntimeKey, ICraftingTreeNode> preparedBoundaryCache = new HashMap<>();
    private final Set<Object> aggregatedChangedPrimaryKeys = new HashSet<>();
    private final Set<Object> aggregatedNextChangedPrimaryKeys = new HashSet<>();
    private final Object2LongOpenHashMap<AEKey> aggregatedCompletionDeltas = new Object2LongOpenHashMap<>();
    private final BitSet aggregatedReadyCompletionRanks = new BitSet();

    public MaxFastExecutor(CompilationCache compilationCache) {
        this(compilationCache, null, false);
    }

    public MaxFastExecutor(CompilationCache compilationCache, @Nullable IPatternDetails forcedRootPattern) {
        this(compilationCache, forcedRootPattern, false);
    }

    public MaxFastExecutor(CompilationCache compilationCache, @Nullable IPatternDetails forcedRootPattern,
                           boolean forcedRootAncestorCut) {
        this.compilationCache = Objects.requireNonNull(compilationCache);
        this.forcedRootPattern = forcedRootPattern;
        this.forcedRootAncestorCut = forcedRootAncestorCut;
        if (forcedRootAncestorCut && forcedRootPattern == null) {
            throw new IllegalArgumentException("A forced root ancestor cut requires a forced root pattern");
        }
    }

    public void resetAttemptState() {
        this.preparedBoundaryCache.clear();
        Arrays.fill(this.aggregatedPreparedBoundaries, null);
        this.stack.clear();
    }

    public void execute(ICraftingTreeNode root, CraftingSimulationState inv, long requestedAmount,
                        @Nullable KeyCounter containerItems, MaxFastMetrics metrics)
                                                                                     throws CraftBranchFailure,
                                                                                     InterruptedException {
        if (containerItems == null && prepareAggregatedGraph(root, metrics, false)) {
            if (validateAggregatedTemplates(root, this.aggregatedGraph, inv, metrics)) {
                root.gtlcore$setMaxFastLogicalNodeCount(this.aggregatedGraph.nodes().length);
                if (tryExecuteAggregated(
                        root,
                        this.aggregatedGraph,
                        inv,
                        requestedAmount,
                        false,
                        false,
                        metrics)) {
                    return;
                }
                root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            } else {
                recordAggregationRejection(metrics, false, AggregationFallbackReason.DYNAMIC_INPUT, 0L);
            }
        }

        root.gtlcore$setMaxFastLogicalNodeCount(-1L);
        if (containerItems != null) {
            metrics.recordAggregationFallback(AggregationFallbackReason.ROOT_CONTAINER, 0L);
        }
        executeStack(root, inv, requestedAmount, containerItems, containerItems != null, metrics);
    }

    public void executeChild(ICraftingTreeNode root, CraftingSimulationState inv, long requestedAmount,
                             @Nullable KeyCounter containerItems, MaxFastMetrics metrics)
                                                                                          throws CraftBranchFailure,
                                                                                          InterruptedException {
        root.gtlcore$setMaxFastLogicalNodeCount(-1L);
        executeStack(root, inv, requestedAmount, containerItems, true, metrics);
    }

    public boolean tryExecuteSegment(ICraftingTreeNode root, CraftingSimulationState inv, long requestedAmount,
                                     MaxFastMetrics metrics) throws CraftBranchFailure, InterruptedException {
        if (!prepareAggregatedGraph(root, metrics, true)) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            return false;
        }
        if (!validateAggregatedTemplates(root, this.aggregatedGraph, inv, metrics)) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            recordAggregationRejection(metrics, true, AggregationFallbackReason.DYNAMIC_INPUT, 0L);
            return false;
        }

        root.gtlcore$setMaxFastLogicalNodeCount(this.aggregatedGraph.nodes().length);
        if (tryExecuteAggregated(root, this.aggregatedGraph, inv, requestedAmount, true, true, metrics)) {
            return true;
        }
        root.gtlcore$setMaxFastLogicalNodeCount(-1L);
        return false;
    }

    public CandidateSegmentResult tryExecuteCandidateSegment(ICraftingTreeNode root, CraftingSimulationState inv,
                                                             long requestedAmount, MaxFastMetrics metrics)
                                                                                                           throws InterruptedException {
        if (!prepareAggregatedGraph(root, metrics, true)) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            return CandidateSegmentResult.STRUCTURAL_FALLBACK;
        }
        if (!validateAggregatedTemplates(root, this.aggregatedGraph, inv, metrics)) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            recordAggregationRejection(metrics, true, AggregationFallbackReason.DYNAMIC_INPUT, 0L);
            return CandidateSegmentResult.STRUCTURAL_FALLBACK;
        }

        root.gtlcore$setMaxFastLogicalNodeCount(this.aggregatedGraph.nodes().length);
        try {
            if (tryExecuteAggregated(root, this.aggregatedGraph, inv, requestedAmount, true, true, metrics)) {
                return CandidateSegmentResult.SUCCEEDED;
            }
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            return CandidateSegmentResult.EXECUTION_FALLBACK;
        } catch (CraftBranchFailure failure) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            if (!this.aggregatedGraph.boundaryTransactionGuard() &&
                    failure instanceof MaxFastCraftBranchFailure) {
                return CandidateSegmentResult.CERTIFIED_MISSING;
            }
            return CandidateSegmentResult.EXECUTION_FALLBACK;
        } catch (InterruptedException | RuntimeException | Error failure) {
            root.gtlcore$setMaxFastLogicalNodeCount(-1L);
            throw failure;
        }
    }

    public boolean shouldBypassCycleCandidateFailure(ICraftingTreeNode root, CraftingSimulationState inventory,
                                                     AEKey key, long amount, IPatternDetails pattern,
                                                     long requestedAmount, AEKey[] externalAncestors) {
        CycleFailureKey failureKey = new CycleFailureKey(
                key,
                amount,
                pattern,
                requestedAmount,
                externalAncestors);
        BoundaryFailureDependencies failure = this.compilationCache.cycleFailures.get(failureKey);
        if (failure == null) {
            return false;
        }
        if (!failure.shouldRetry(root, inventory)) {
            return true;
        }
        this.compilationCache.cycleFailures.remove(failureKey);
        return false;
    }

    public boolean cacheCycleCandidateFailure(ICraftingTreeNode root, CraftingSimulationState inventory,
                                              AEKey key, long amount, IPatternDetails pattern,
                                              long requestedAmount, AEKey[] externalAncestors,
                                              BoundaryFailureDependencies failure) {
        BoundaryFailureDependencies cachedFailure = failure.copyWithCapacityTargets(root, inventory);
        if (cachedFailure == null) {
            return false;
        }
        this.compilationCache.cycleFailures.put(
                new CycleFailureKey(key, amount, pattern, requestedAmount, externalAncestors),
                cachedFailure);
        return true;
    }

    private boolean validateAggregatedTemplates(ICraftingTreeNode root, AggregatedGraph graph,
                                                CraftingSimulationState inv, MaxFastMetrics metrics) {
        long startedNanos = System.nanoTime();
        try {
            if (graph.onlyStructuralExactInputs() &&
                    this.compilationCache.validatedStructuralExactGraphs.contains(graph)) {
                return true;
            }
            for (AggregatedNode node : graph.nodes()) {
                IPatternDetails.IInput input = node.parentInput();
                if (input == null) {
                    continue;
                }
                if (node.structuralExactTemplate() != null) {
                    boolean valid = isCurrentStructuralExactInput(root, node, input);
                    metrics.recordAggregationExactTemplateValidation(valid);
                    if (!valid) {
                        return false;
                    }
                    continue;
                }
                for (InputTemplate template : getAggregatedTemplates(root, inv, node)) {
                    if (input.getRemainingKey(template.key()) != null) {
                        return false;
                    }
                }
            }
            if (graph.onlyStructuralExactInputs()) {
                this.compilationCache.validatedStructuralExactGraphs.add(graph);
            }
            return true;
        } finally {
            metrics.recordAggregationTemplateValidationNanos(System.nanoTime() - startedNanos);
        }
    }

    private static boolean isCurrentStructuralExactInput(ICraftingTreeNode root, AggregatedNode node,
                                                         IPatternDetails.IInput input) {
        GenericStack[] possibleInputs = input.getPossibleInputs();
        if (possibleInputs.length != 1) {
            return false;
        }
        GenericStack possibleInput = possibleInputs[0];
        return possibleInput.amount() == node.amount() && node.key().equals(possibleInput.what()) &&
                input.isValid(node.key(), root.gtlcore$getMaxFastLevel()) &&
                input.getRemainingKey(node.key()) == null;
    }

    private static Iterable<InputTemplate> getAggregatedTemplates(ICraftingTreeNode root,
                                                                  CraftingSimulationState inv,
                                                                  AggregatedNode node) {
        IPatternDetails.IInput input = Objects.requireNonNull(node.parentInput());
        Level level = root.gtlcore$getMaxFastLevel();
        return root.gtlcore$getMaxFastCalculation().gtlcore$getCachedTemplates(
                inv,
                input,
                level,
                node.key(),
                () -> CraftingCpuHelper.getValidItemTemplates(inv, input, level));
    }

    private boolean prepareAggregatedGraph(ICraftingTreeNode root, MaxFastMetrics metrics, boolean segment)
                                                                                                            throws InterruptedException {
        if (segment) {
            metrics.recordAggregationSegmentAttempt();
        }
        AEKey[] externalAncestors = segment ? root.gtlcore$getMaxFastAncestorKeys() : EMPTY_EXTERNAL_ANCESTORS;
        this.aggregatedExternalAncestors = externalAncestors;
        this.aggregatedGraphCompileNanos = 0L;
        if (this.aggregatedGraph != null) {
            metrics.recordAggregationGraph(
                    this.aggregatedGraph.nodes().length,
                    this.aggregatedGraph.logicalNodeCount(),
                    0L,
                    true);
            return true;
        }
        if (this.aggregationFailure != null) {
            recordAggregationRejection(metrics, segment, this.aggregationFailure.failureReason(), 0L);
            return false;
        }

        GraphCacheKey cacheKey = new GraphCacheKey(
                root.gtlcore$getMaxFastKey(),
                root.gtlcore$getMaxFastAmount(),
                this.forcedRootPattern,
                this.forcedRootAncestorCut,
                externalAncestors);
        GraphCompilation cachedCompilation = this.compilationCache.graphs.get(cacheKey);
        if (cachedCompilation == null && externalAncestors.length > 0) {
            long contextScanStartedNanos = System.nanoTime();
            List<GraphCompilation> variants = this.compilationCache.graphVariants.get(cacheKey.baseKey());
            int variantCount = variants == null ? 0 : variants.size();
            int variantsChecked = 0;
            if (variants != null) {
                for (GraphCompilation variant : variants) {
                    variantsChecked++;
                    if (variant.contextSignature().matches(root)) {
                        cachedCompilation = variant;
                        this.compilationCache.graphs.put(cacheKey, variant);
                        if (variant.graph() != null) {
                            metrics.recordAggregationGraphContextReuse();
                        }
                        break;
                    }
                }
            }
            metrics.recordAggregationGraphContextScan(
                    variantCount,
                    variantsChecked,
                    cachedCompilation != null,
                    System.nanoTime() - contextScanStartedNanos);
        }
        if (cachedCompilation != null) {
            if (cachedCompilation.graph() != null) {
                this.aggregatedGraph = cachedCompilation.graph();
                metrics.recordAggregationGraph(
                        this.aggregatedGraph.nodes().length,
                        this.aggregatedGraph.logicalNodeCount(),
                        0L,
                        true);
                return true;
            }
            this.aggregationFailure = cachedCompilation;
            recordAggregationRejection(metrics, segment, cachedCompilation.failureReason(), 0L);
            return false;
        }

        long startedNanos = System.nanoTime();
        GraphCompilation compilation;
        ContextDecisionCollector contextDecisions = externalAncestors.length == 0 ?
                null : new ContextDecisionCollector();
        try {
            compilation = compileAggregatedGraph(root, metrics, contextDecisions);
        } catch (InterruptedException | RuntimeException | Error failure) {
            metrics.recordAggregationCompileNanos(System.nanoTime() - startedNanos);
            throw failure;
        }
        compilation = compilation.withContextSignature(
                contextDecisions == null ? ContextSignature.EMPTY : contextDecisions.freeze());
        this.compilationCache.graphs.put(cacheKey, compilation);
        if (contextDecisions != null) {
            this.compilationCache.graphVariants
                    .computeIfAbsent(cacheKey.baseKey(), ignored -> new ArrayList<>())
                    .add(compilation);
        }
        long elapsedNanos = System.nanoTime() - startedNanos;
        this.aggregatedGraphCompileNanos = elapsedNanos;
        if (compilation.graph() != null) {
            this.aggregatedGraph = compilation.graph();
            metrics.recordAggregationGraph(
                    this.aggregatedGraph.nodes().length,
                    this.aggregatedGraph.logicalNodeCount(),
                    elapsedNanos,
                    false);
            return true;
        }

        this.aggregationFailure = compilation;
        metrics.recordAggregationCompileFailure(
                compilation.failureReason(),
                compilation.failureKey(),
                compilation.patternCandidates(),
                compilation.scannedNodes());
        recordAggregationRejection(metrics, segment, compilation.failureReason(), elapsedNanos);
        return false;
    }

    private static void recordAggregationRejection(MaxFastMetrics metrics, boolean segment,
                                                   AggregationFallbackReason reason, long compileNanos) {
        if (segment) {
            metrics.recordAggregationSegmentRejection(reason, compileNanos);
        } else {
            metrics.recordAggregationFallback(reason, compileNanos);
        }
    }

    private GraphCompilation compileAggregatedGraph(ICraftingTreeNode root, MaxFastMetrics metrics,
                                                    @Nullable ContextDecisionCollector contextDecisions)
                                                                                                         throws InterruptedException {
        ICraftingService craftingService = root.gtlcore$getMaxFastCraftingService();
        Level level = root.gtlcore$getMaxFastLevel();
        long expansionStartedNanos = System.nanoTime();
        List<AnalyzedNode> analyzedNodes = new ArrayList<>();
        Map<RequestKey, AnalyzedNode> analyzedByRequest = new HashMap<>();
        Map<AEKey, AnalyzedProgram> resolvedPrograms = new HashMap<>();
        AnalyzedNode analyzedRoot = new AnalyzedNode(
                0,
                new RequestKey(root.gtlcore$getMaxFastKey(), root.gtlcore$getMaxFastAmount(), null));
        analyzedNodes.add(analyzedRoot);
        analyzedByRequest.put(analyzedRoot.requestKey(), analyzedRoot);

        try {
            for (int cursor = 0; cursor < analyzedNodes.size(); cursor++) {
                root.gtlcore$checkMaxFastCancellation();
                AnalyzedNode node = analyzedNodes.get(cursor);
                metrics.recordProgramLookup();
                boolean forcedRoot = cursor == 0 && this.forcedRootPattern != null;
                AnalyzedProgram program = forcedRoot ? null : resolvedPrograms.get(node.key());
                if (program == null) {
                    metrics.recordAggregationContextCacheMiss();
                    AnalyzedProgram analyzedProgram = this.compilationCache.analyzedPrograms.get(node.key());
                    if (analyzedProgram == null) {
                        analyzedProgram = analyzeProgram(node.key(), craftingService, level, metrics);
                        this.compilationCache.analyzedPrograms.put(node.key(), analyzedProgram);
                        metrics.recordAggregationAnalysisCacheMiss();
                    } else {
                        metrics.recordAggregationAnalysisCacheHit();
                    }
                    program = resolveProgram(
                            root,
                            analyzedProgram,
                            contextDecisions,
                            forcedRoot ? this.forcedRootPattern : null,
                            forcedRoot && this.forcedRootAncestorCut);
                    if (!forcedRoot) {
                        resolvedPrograms.put(node.key(), program);
                    }
                } else {
                    metrics.recordAggregationContextCacheHit();
                }
                if (program.failureReason() != null) {
                    return GraphCompilation.failure(
                            program.failureReason(),
                            node.key(),
                            program.patternCandidates(),
                            cursor + 1);
                }

                // A ROOT_CUT graph is certified only when every expanded descendant
                // is already a single, aggregate-safe, structurally exact program.
                // Reject that certificate at the first known counterexample instead
                // of building the remaining graph only to discard it later.
                if (this.forcedRootAncestorCut && !isRootCutProgramSafe(program)) {
                    return GraphCompilation.failure(
                            AggregationFallbackReason.BASELINE_PROGRAM,
                            node.key(),
                            program.patternCandidates(),
                            cursor + 1);
                }

                node.applyProgram(program);
                for (CandidateAnalysis candidate : node.candidates()) {
                    for (AnalyzedEdge edge : candidate.edges()) {
                        AnalyzedNode child = analyzedByRequest.get(edge.childRequestKey());
                        if (child == null) {
                            child = new AnalyzedNode(analyzedNodes.size(), edge.childRequestKey());
                            analyzedByRequest.put(edge.childRequestKey(), child);
                            analyzedNodes.add(child);
                        }
                    }
                }
            }
        } finally {
            metrics.recordAggregationExpansionNanos(System.nanoTime() - expansionStartedNanos);
        }

        long sccStartedNanos = System.nanoTime();
        SccAnalysis sccAnalysis;
        try {
            sccAnalysis = analyzeScc(root, analyzedNodes, metrics);
        } finally {
            metrics.recordAggregationSccNanos(System.nanoTime() - sccStartedNanos);
        }
        long executableStartedNanos = System.nanoTime();
        List<AggregatedNode> nodes;
        try {
            nodes = buildExecutableGraph(
                    analyzedRoot,
                    analyzedByRequest,
                    sccAnalysis,
                    metrics,
                    this.forcedRootPattern == null && !this.forcedRootAncestorCut);
            if (this.forcedRootPattern != null && nodes.get(0).barrier()) {
                return GraphCompilation.failure(
                        AggregationFallbackReason.BASELINE_PROGRAM,
                        analyzedRoot.key(),
                        1,
                        nodes.size());
            }
            if (this.forcedRootAncestorCut &&
                    (containsBoundary(nodes) || !hasOnlyStructuralExactInputs(nodes))) {
                return GraphCompilation.failure(
                        AggregationFallbackReason.BASELINE_PROGRAM,
                        analyzedRoot.key(),
                        1,
                        nodes.size());
            }
        } finally {
            metrics.recordAggregationExecutableNanos(System.nanoTime() - executableStartedNanos);
        }

        long finalizationStartedNanos = System.nanoTime();
        try {
            int[] topologicalOrder = buildTopologicalOrder(nodes);
            if (topologicalOrder == null) {
                return GraphCompilation.failure(AggregationFallbackReason.CYCLE, null, -1, nodes.size());
            }
            boolean boundaryTransactionGuard = containsBoundary(nodes);
            // ponytail: byproduct feedback only fires when a node emits a key other than
            // its own. No extra outputs -> execution graph == dependency graph, already
            // proven acyclic by buildTopologicalOrder above, so both feedback scans and
            // the primary-key index they need are provably no-ops. Skip them.
            if (graphHasExtraOutputs(nodes, analyzedByRequest)) {
                Map<Object, List<AggregatedNode>> nodesByPrimaryKey = indexNodesByPrimaryKey(nodes);
                if (hasUnsafeOutputFeedback(nodes, nodesByPrimaryKey, level) ||
                        boundaryTransactionGuard &&
                                hasUnsafeBarrierOutputFeedback(nodes, analyzedByRequest, nodesByPrimaryKey, level)) {
                    return GraphCompilation.failure(
                            AggregationFallbackReason.OUTPUT_INPUT_FEEDBACK,
                            null,
                            -1,
                            nodes.size());
                }
            }

            long logicalNodeCount = countLogicalNodes(nodes, topologicalOrder);
            int[] topologicalRanks = indexTopologicalRanks(topologicalOrder);
            int[][] parentIndexes = buildParentIndexes(nodes);
            if (boundaryTransactionGuard) {
                metrics.recordAggregationBoundaryTransactionGuardGraph();
            }
            return GraphCompilation.success(new AggregatedGraph(
                    nodes.toArray(AggregatedNode[]::new),
                    topologicalOrder,
                    topologicalRanks,
                    parentIndexes,
                    logicalNodeCount,
                    boundaryTransactionGuard,
                    hasOnlyStructuralExactInputs(nodes)));
        } finally {
            metrics.recordAggregationFinalizationNanos(System.nanoTime() - finalizationStartedNanos);
        }
    }

    private static AnalyzedProgram analyzeProgram(AEKey key, ICraftingService craftingService, Level level,
                                                  MaxFastMetrics metrics) {
        if (craftingService.canEmitFor(key)) {
            if (metrics.isDiagnosticLoggingEnabled()) {
                metrics.recordAnalyzedProgramStructure(key, true, List.of());
            }
            return AnalyzedProgram.emitterProgram();
        }

        Collection<IPatternDetails> candidates = craftingService.getCraftingFor(key);
        if (metrics.isDiagnosticLoggingEnabled()) {
            metrics.recordAnalyzedProgramStructure(key, false, candidates);
        }
        if (candidates.isEmpty()) {
            return AnalyzedProgram.terminalProgram();
        }

        List<CandidateAnalysis> candidateAnalyses = new ArrayList<>(candidates.size());
        for (IPatternDetails details : candidates) {
            candidateAnalyses.add(analyzeCandidate(key, details, craftingService, level));
        }
        return AnalyzedProgram.candidateProgram(candidateAnalyses);
    }

    private static boolean isRootCutProgramSafe(AnalyzedProgram program) {
        if (program.prefixOnly() || program.terminal()) {
            return true;
        }
        if (program.candidates().size() != 1) {
            return false;
        }
        CandidateAnalysis candidate = program.candidates().get(0);
        return candidate.failureReason() == null && candidate.aggregateSafe() &&
                hasStructurallyExactInputs(candidate.details());
    }

    private static AnalyzedProgram resolveProgram(ICraftingTreeNode root, AnalyzedProgram analyzedProgram,
                                                  @Nullable ContextDecisionCollector contextDecisions,
                                                  @Nullable IPatternDetails forcedPattern,
                                                  boolean trustForcedPatternContext) {
        if (forcedPattern != null) {
            if (analyzedProgram.prefixOnly() || analyzedProgram.terminal()) {
                return AnalyzedProgram.failure(AggregationFallbackReason.BASELINE_PROGRAM, 0);
            }

            CandidateAnalysis forcedCandidate = null;
            for (CandidateAnalysis candidate : analyzedProgram.candidates()) {
                if (candidate.details() == forcedPattern) {
                    forcedCandidate = candidate;
                    break;
                }
            }
            boolean allowed = forcedCandidate != null &&
                    (trustForcedPatternContext || root.gtlcore$isMaxFastPatternContextAllowed(forcedPattern));
            if (contextDecisions != null && !trustForcedPatternContext) {
                contextDecisions.record(forcedPattern, allowed);
            }
            if (!allowed) {
                return AnalyzedProgram.failure(
                        AggregationFallbackReason.MULTIPLE_PATTERN_CANDIDATES,
                        analyzedProgram.patternCandidates());
            }
            if (forcedCandidate.failureReason() != null) {
                return AnalyzedProgram.failure(forcedCandidate.failureReason(), 1);
            }
            return AnalyzedProgram.candidateProgram(List.of(forcedCandidate));
        }

        if (analyzedProgram.prefixOnly() || analyzedProgram.terminal()) {
            return analyzedProgram;
        }

        List<CandidateAnalysis> candidates = analyzedProgram.candidates();
        List<CandidateAnalysis> allowedCandidates = candidates;
        if (contextDecisions != null) {
            List<CandidateAnalysis> filteredCandidates = null;
            for (int index = 0; index < candidates.size(); index++) {
                CandidateAnalysis candidate = candidates.get(index);
                boolean allowed = root.gtlcore$isMaxFastPatternContextAllowed(candidate.details());
                contextDecisions.record(candidate.details(), allowed);
                if (allowed) {
                    if (filteredCandidates != null) {
                        filteredCandidates.add(candidate);
                    }
                } else if (filteredCandidates == null) {
                    filteredCandidates = new ArrayList<>(candidates.size() - 1);
                    filteredCandidates.addAll(candidates.subList(0, index));
                }
            }
            if (filteredCandidates != null) {
                if (filteredCandidates.isEmpty()) {
                    return AnalyzedProgram.terminalProgram();
                }
                allowedCandidates = filteredCandidates;
            }
        }

        for (CandidateAnalysis candidate : allowedCandidates) {
            if (candidate.failureReason() != null) {
                return AnalyzedProgram.failure(candidate.failureReason(), allowedCandidates.size());
            }
        }
        return allowedCandidates == candidates ? analyzedProgram : AnalyzedProgram.candidateProgram(allowedCandidates);
    }

    private static CandidateAnalysis analyzeCandidate(AEKey nodeKey, IPatternDetails details,
                                                      ICraftingService craftingService, Level level) {
        GenericStack[] outputs = details.getOutputs().clone();
        long outputPerPattern = getOutputCount(outputs, nodeKey);
        boolean aggregateSafe = outputPerPattern > 0;
        boolean structurallyExactInputs = hasStructurallyExactInputs(details);
        Map<RequestKey, EdgeAccumulator> childRequests = new LinkedHashMap<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length != 1) {
                return CandidateAnalysis.failure(details, AggregationFallbackReason.DYNAMIC_INPUT);
            }

            GenericStack possibleInput = possibleInputs[0];
            if (!input.isValid(possibleInput.what(), level)) {
                return CandidateAnalysis.failure(details, AggregationFallbackReason.DYNAMIC_INPUT);
            }
            AEKey childKey = resolveExactChildKey(craftingService, input, possibleInput.what(), level);
            if (childKey == null) {
                return CandidateAnalysis.failure(details, AggregationFallbackReason.DYNAMIC_INPUT);
            }

            long requestMultiplier = input.getMultiplier();
            if (possibleInput.amount() <= 0 || requestMultiplier <= 0) {
                aggregateSafe = false;
            }
            if (input.getRemainingKey(childKey) != null || nodeKey.matches(possibleInput) ||
                    outputMatches(outputs, possibleInput.what())) {
                aggregateSafe = false;
            }

            RequestKey childRequestKey = new RequestKey(
                    childKey,
                    possibleInput.amount(),
                    input,
                    structurallyExactInputs);
            EdgeAccumulator accumulator = childRequests.get(childRequestKey);
            if (accumulator == null) {
                childRequests.put(childRequestKey, new EdgeAccumulator(requestMultiplier));
            } else {
                accumulator.add(requestMultiplier);
            }
        }

        List<AnalyzedEdge> edges = new ArrayList<>(childRequests.size());
        for (Map.Entry<RequestKey, EdgeAccumulator> entry : childRequests.entrySet()) {
            EdgeAccumulator accumulator = entry.getValue();
            edges.add(new AnalyzedEdge(
                    entry.getKey(),
                    accumulator.requestMultiplier(),
                    accumulator.occurrences()));
        }
        return CandidateAnalysis.success(details, outputs, outputPerPattern, aggregateSafe, edges);
    }

    private static boolean hasStructurallyExactInputs(IPatternDetails details) {
        if (details.getClass() == AEProcessingPattern.class) {
            return true;
        }
        return details.getClass() == AECraftingPattern.class &&
                !((AECraftingPattern) details).canSubstitute();
    }

    private static boolean outputMatches(GenericStack[] outputs, AEKey inputKey) {
        for (GenericStack output : outputs) {
            if (inputKey.matches(output)) {
                return true;
            }
        }
        return false;
    }

    private static SccAnalysis analyzeScc(ICraftingTreeNode root, List<AnalyzedNode> nodes,
                                          MaxFastMetrics metrics) throws InterruptedException {
        Map<AEKey, Integer> keyIndexes = new HashMap<>();
        List<AEKey> keys = new ArrayList<>();
        for (AnalyzedNode node : nodes) {
            root.gtlcore$checkMaxFastCancellation();
            if (!keyIndexes.containsKey(node.key())) {
                keyIndexes.put(node.key(), keys.size());
                keys.add(node.key());
            }
        }

        int keyCount = keys.size();
        List<List<Integer>> successors = new ArrayList<>(keyCount);
        List<List<Integer>> predecessors = new ArrayList<>(keyCount);
        boolean[] selfLoops = new boolean[keyCount];
        for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
            successors.add(new ArrayList<>());
            predecessors.add(new ArrayList<>());
        }

        boolean[] expandedKeys = new boolean[keyCount];
        for (AnalyzedNode node : nodes) {
            root.gtlcore$checkMaxFastCancellation();
            int sourceIndex = keyIndexes.get(node.key());
            if (expandedKeys[sourceIndex]) {
                continue;
            }
            expandedKeys[sourceIndex] = true;
            for (CandidateAnalysis candidate : node.candidates()) {
                for (AnalyzedEdge edge : candidate.edges()) {
                    int targetIndex = keyIndexes.get(edge.childRequestKey().key());
                    addStructuralEdge(successors, predecessors, selfLoops, sourceIndex, targetIndex);
                }
                for (GenericStack output : candidate.outputs()) {
                    if (node.key().matches(output)) {
                        continue;
                    }
                    Integer targetIndex = keyIndexes.get(output.what());
                    if (targetIndex != null) {
                        addStructuralEdge(successors, predecessors, selfLoops, sourceIndex, targetIndex);
                    }
                }
            }
        }

        boolean[] visited = new boolean[keyCount];
        int[] finishOrder = new int[keyCount];
        int finishCount = 0;
        int[] nodeStack = new int[keyCount];
        int[] edgeStack = new int[keyCount];
        for (int start = 0; start < keyCount; start++) {
            root.gtlcore$checkMaxFastCancellation();
            if (visited[start]) {
                continue;
            }

            int top = 0;
            nodeStack[top] = start;
            edgeStack[top] = 0;
            visited[start] = true;
            while (top >= 0) {
                root.gtlcore$checkMaxFastCancellation();
                int nodeIndex = nodeStack[top];
                List<Integer> nodeSuccessors = successors.get(nodeIndex);
                int edgeIndex = edgeStack[top];
                if (edgeIndex < nodeSuccessors.size()) {
                    int successor = nodeSuccessors.get(edgeIndex);
                    edgeStack[top] = edgeIndex + 1;
                    if (!visited[successor]) {
                        visited[successor] = true;
                        top++;
                        nodeStack[top] = successor;
                        edgeStack[top] = 0;
                    }
                } else {
                    finishOrder[finishCount++] = nodeIndex;
                    top--;
                }
            }
        }

        int[] componentIds = new int[keyCount];
        Arrays.fill(componentIds, -1);
        int[] componentSizes = new int[keyCount];
        int componentCount = 0;
        for (int orderIndex = finishCount - 1; orderIndex >= 0; orderIndex--) {
            root.gtlcore$checkMaxFastCancellation();
            int start = finishOrder[orderIndex];
            if (componentIds[start] != -1) {
                continue;
            }

            int stackSize = 1;
            nodeStack[0] = start;
            componentIds[start] = componentCount;
            while (stackSize > 0) {
                root.gtlcore$checkMaxFastCancellation();
                int nodeIndex = nodeStack[--stackSize];
                componentSizes[componentCount]++;
                for (int predecessor : predecessors.get(nodeIndex)) {
                    if (componentIds[predecessor] == -1) {
                        componentIds[predecessor] = componentCount;
                        nodeStack[stackSize++] = predecessor;
                    }
                }
            }
            componentCount++;
        }

        boolean[] cyclicKeys = new boolean[keyCount];
        for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
            cyclicKeys[keyIndex] = selfLoops[keyIndex] || componentSizes[componentIds[keyIndex]] > 1;
        }

        boolean[] cyclicNodes = new boolean[nodes.size()];
        for (AnalyzedNode node : nodes) {
            cyclicNodes[node.index()] = cyclicKeys[keyIndexes.get(node.key())];
        }
        long programCompilationStartedNanos = System.nanoTime();
        SccProgram[] programsByNode;
        try {
            programsByNode = buildSccPrograms(
                    root,
                    nodes,
                    keys,
                    keyIndexes,
                    componentIds,
                    componentSizes,
                    cyclicKeys,
                    metrics);
        } finally {
            metrics.recordSccProgramCompileNanos(System.nanoTime() - programCompilationStartedNanos);
        }
        return new SccAnalysis(cyclicNodes, programsByNode);
    }

    private static SccProgram[] buildSccPrograms(ICraftingTreeNode root,
                                                 List<AnalyzedNode> analyzedNodes,
                                                 List<AEKey> keys,
                                                 Map<AEKey, Integer> keyIndexes,
                                                 int[] componentIds,
                                                 int[] componentSizes,
                                                 boolean[] cyclicKeys,
                                                 MaxFastMetrics metrics) throws InterruptedException {
        SccProgram[] programsByAnalyzedNode = new SccProgram[analyzedNodes.size()];
        AnalyzedNode[] analyzedByKeyIndex = new AnalyzedNode[keys.size()];
        for (AnalyzedNode analyzedNode : analyzedNodes) {
            root.gtlcore$checkMaxFastCancellation();
            int keyIndex = keyIndexes.get(analyzedNode.key());
            if (analyzedByKeyIndex[keyIndex] == null) {
                analyzedByKeyIndex[keyIndex] = analyzedNode;
            }
        }

        SccProgram[] programsByComponent = new SccProgram[componentSizes.length];
        for (int componentId = 0; componentId < componentSizes.length; componentId++) {
            root.gtlcore$checkMaxFastCancellation();
            if (componentSizes[componentId] <= 1) {
                continue;
            }
            SccProgram program = buildSccProgram(
                    root,
                    componentId,
                    analyzedByKeyIndex,
                    keys,
                    keyIndexes,
                    componentIds);
            if (program != null) {
                programsByComponent[componentId] = program;
                metrics.recordSccProgramCompiled(
                        program.nodes().length,
                        program.internalEdges());
            }
        }

        for (AnalyzedNode analyzedNode : analyzedNodes) {
            root.gtlcore$checkMaxFastCancellation();
            int keyIndex = keyIndexes.get(analyzedNode.key());
            if (cyclicKeys[keyIndex]) {
                programsByAnalyzedNode[analyzedNode.index()] = programsByComponent[componentIds[keyIndex]];
            }
        }
        return programsByAnalyzedNode;
    }

    private static @Nullable SccProgram buildSccProgram(ICraftingTreeNode root,
                                                        int componentId,
                                                        AnalyzedNode[] analyzedByKeyIndex,
                                                        List<AEKey> keys,
                                                        Map<AEKey, Integer> keyIndexes,
                                                        int[] componentIds) throws InterruptedException {
        Map<AEKey, Integer> localIndexes = new LinkedHashMap<>();
        for (int keyIndex = 0; keyIndex < componentIds.length; keyIndex++) {
            if (componentIds[keyIndex] == componentId) {
                localIndexes.put(keys.get(keyIndex), localIndexes.size());
            }
        }

        SccNode[] sccNodes = new SccNode[localIndexes.size()];
        int internalEdges = 0;
        for (Map.Entry<AEKey, Integer> entry : localIndexes.entrySet()) {
            root.gtlcore$checkMaxFastCancellation();
            AEKey key = entry.getKey();
            AnalyzedNode analyzedNode = analyzedByKeyIndex[keyIndexes.get(key)];
            if (analyzedNode == null || analyzedNode.candidates().size() != 1) {
                return null;
            }
            CandidateAnalysis candidate = analyzedNode.candidates().get(0);
            if (candidate.failureReason() != null || !candidate.aggregateSafe() ||
                    !hasStructurallyExactInputs(candidate.details()) ||
                    candidate.outputPerPattern() <= 0 || candidate.edges() == null ||
                    candidate.outputs() == null) {
                return null;
            }

            for (GenericStack output : candidate.outputs()) {
                if (output.amount() <= 0) {
                    return null;
                }
                if (!key.matches(output) && localIndexes.containsKey(output.what())) {
                    return null;
                }
            }

            List<SccInput> inputs = new ArrayList<>(candidate.edges().size());
            for (AnalyzedEdge edge : candidate.edges()) {
                long amountPerPattern = NumberUtils.saturatedMultiply(
                        edge.childRequestKey().amount(),
                        edge.requestMultiplier());
                if (amountPerPattern <= 0 || amountPerPattern == Long.MAX_VALUE) {
                    return null;
                }
                Integer internalIndex = localIndexes.get(edge.childRequestKey().key());
                if (internalIndex != null) {
                    internalEdges++;
                }
                inputs.add(new SccInput(
                        edge.childRequestKey(),
                        edge.childRequestKey().key(),
                        amountPerPattern,
                        internalIndex == null ? -1 : internalIndex,
                        edge.childRequestKey().input()));
            }
            sccNodes[entry.getValue()] = new SccNode(
                    key,
                    candidate.details(),
                    candidate.outputs(),
                    candidate.outputPerPattern(),
                    inputs.toArray(SccInput[]::new));
        }
        return new SccProgram(
                sccNodes,
                Map.copyOf(localIndexes),
                buildSccConsumers(sccNodes),
                new SccRuntimeGate(),
                internalEdges);
    }

    private static int[][] buildSccConsumers(SccNode[] nodes) {
        int[] consumerCounts = new int[nodes.length];
        for (SccNode node : nodes) {
            for (SccInput input : node.inputs()) {
                if (input.internalNodeIndex() >= 0) {
                    consumerCounts[input.internalNodeIndex()]++;
                }
            }
        }

        int[][] consumersByNode = new int[nodes.length][];
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            consumersByNode[nodeIndex] = new int[consumerCounts[nodeIndex]];
        }
        Arrays.fill(consumerCounts, 0);
        for (int consumerIndex = 0; consumerIndex < nodes.length; consumerIndex++) {
            for (SccInput input : nodes[consumerIndex].inputs()) {
                int producerIndex = input.internalNodeIndex();
                if (producerIndex >= 0) {
                    consumersByNode[producerIndex][consumerCounts[producerIndex]++] = consumerIndex;
                }
            }
        }
        return consumersByNode;
    }

    private static void addStructuralEdge(List<List<Integer>> successors,
                                          List<List<Integer>> predecessors,
                                          boolean[] selfLoops,
                                          int sourceIndex, int targetIndex) {
        successors.get(sourceIndex).add(targetIndex);
        predecessors.get(targetIndex).add(sourceIndex);
        if (sourceIndex == targetIndex) {
            selfLoops[sourceIndex] = true;
        }
    }

    private static List<AggregatedNode> buildExecutableGraph(AnalyzedNode analyzedRoot,
                                                             Map<RequestKey, AnalyzedNode> analyzedByRequest,
                                                             SccAnalysis sccAnalysis,
                                                             MaxFastMetrics metrics,
                                                             boolean prefilterCycleCandidates) {
        List<AggregatedNode> nodes = new ArrayList<>();
        Map<RequestKey, AggregatedNode> executableByRequest = new HashMap<>();
        Map<AEKey, IdentityHashMap<IPatternDetails, RootCutPrefilterResult>> rootCutPrefilterCache = prefilterCycleCandidates ? new HashMap<>() : Map.of();
        AggregatedNode rootNode = new AggregatedNode(0, analyzedRoot.requestKey());
        nodes.add(rootNode);
        executableByRequest.put(rootNode.requestKey(), rootNode);

        for (int cursor = 0; cursor < nodes.size(); cursor++) {
            AggregatedNode node = nodes.get(cursor);
            AnalyzedNode analyzedNode = analyzedByRequest.get(node.requestKey());
            if (analyzedNode.prefixOnly()) {
                node.setPrefixOnly();
                continue;
            }
            if (analyzedNode.terminal()) {
                node.setTerminal();
                metrics.recordTerminalProgramCompiled();
                continue;
            }

            CandidateAnalysis candidate = analyzedNode.candidates().size() == 1 ?
                    analyzedNode.candidates().get(0) : null;
            boolean cycleBoundary = sccAnalysis.cyclicNodes()[analyzedNode.index()];
            if (candidate == null || !candidate.aggregateSafe() || cycleBoundary) {
                boolean candidateGraphsEligible = candidate == null && !cycleBoundary &&
                        allCandidatesAggregateSafe(analyzedNode.candidates());
                boolean cycleCandidateGraphEligible = cycleBoundary && candidate != null && candidate.aggregateSafe();
                if (cycleCandidateGraphEligible && !hasStructurallyExactInputs(candidate.details())) {
                    cycleCandidateGraphEligible = false;
                    if (prefilterCycleCandidates) {
                        metrics.recordAggregationCycleCandidateGraphPrefilterRejectRootNonExact();
                    }
                } else if (cycleCandidateGraphEligible && prefilterCycleCandidates) {
                    IdentityHashMap<IPatternDetails, RootCutPrefilterResult> candidatesByPattern = rootCutPrefilterCache.computeIfAbsent(node.key(), ignored -> new IdentityHashMap<>());
                    RootCutPrefilterResult prefilterResult = candidatesByPattern.get(candidate.details());
                    if (prefilterResult == null) {
                        prefilterResult = prefilterRootCutCandidate(analyzedNode, candidate, analyzedByRequest);
                        candidatesByPattern.put(candidate.details(), prefilterResult);
                    }
                    if (prefilterResult == RootCutPrefilterResult.REJECT) {
                        cycleCandidateGraphEligible = false;
                        metrics.recordAggregationCycleCandidateGraphPrefilterRejectDescendant();
                    } else if (prefilterResult == RootCutPrefilterResult.UNKNOWN) {
                        metrics.recordAggregationCycleCandidateGraphPrefilterUnknown();
                    }
                }
                node.setBarrier(
                        analyzedNode.candidates(),
                        candidateGraphsEligible,
                        cycleCandidateGraphEligible);
                if (prefilterCycleCandidates) {
                    node.setSccProgram(sccAnalysis.programsByNode()[analyzedNode.index()]);
                }
                if (node.sccProgram() != null) {
                    node.setSccExternalDependenciesEligible(addSccExternalDependencies(
                            node,
                            node.sccProgram(),
                            analyzedByRequest,
                            sccAnalysis,
                            executableByRequest,
                            nodes,
                            metrics));
                }
                if (candidateGraphsEligible) {
                    metrics.recordAggregationCandidateGraphEligibleNode();
                }
                if (cycleCandidateGraphEligible) {
                    metrics.recordAggregationCycleCandidateGraphEligibleNode();
                }
                metrics.recordAggregationBoundary(
                        candidate == null,
                        candidate != null && !candidate.aggregateSafe(),
                        cycleBoundary);
                metrics.recordBaselineProgramCompiled(
                        candidate == null ? MaxFastMetrics.BarrierReason.MULTI_PATH :
                                MaxFastMetrics.BarrierReason.INVALID_METADATA);
                continue;
            }

            node.setProgram(candidate.details(), candidate.outputs(), candidate.outputPerPattern());
            metrics.recordSingleProgramCompiled();
            for (AnalyzedEdge analyzedEdge : candidate.edges()) {
                AnalyzedNode analyzedChild = analyzedByRequest.get(analyzedEdge.childRequestKey());
                boolean childIsBarrier = isAnalyzedBarrier(analyzedChild, sccAnalysis.cyclicNodes());
                boolean mergeableSccBarrier = childIsBarrier &&
                        sccAnalysis.programsByNode()[analyzedChild.index()] != null;
                AggregatedNode child = !childIsBarrier || mergeableSccBarrier ?
                        executableByRequest.get(analyzedChild.requestKey()) : null;
                if (child == null) {
                    child = new AggregatedNode(nodes.size(), analyzedChild.requestKey());
                    if (!childIsBarrier || mergeableSccBarrier) {
                        executableByRequest.put(analyzedChild.requestKey(), child);
                    }
                    nodes.add(child);
                } else if (mergeableSccBarrier) {
                    metrics.recordSccBoundaryNodeReuse();
                }
                node.addEdge(new AggregatedEdge(
                        child.index(),
                        analyzedEdge.requestMultiplier(),
                        analyzedEdge.occurrences()));
                child.incrementIndegree();
            }
        }
        return nodes;
    }

    private static boolean addSccExternalDependencies(
                                                      AggregatedNode node,
                                                      SccProgram program,
                                                      Map<RequestKey, AnalyzedNode> analyzedByRequest,
                                                      SccAnalysis sccAnalysis,
                                                      Map<RequestKey, AggregatedNode> executableByRequest,
                                                      List<AggregatedNode> nodes,
                                                      MaxFastMetrics metrics) {
        Set<RequestKey> externalRequests = new LinkedHashSet<>();
        for (SccNode sccNode : program.nodes()) {
            for (SccInput input : sccNode.inputs()) {
                if (input.internalNodeIndex() >= 0) {
                    continue;
                }
                externalRequests.add(input.requestKey());
            }
        }
        if (externalRequests.isEmpty()) {
            return false;
        }
        for (RequestKey requestKey : externalRequests) {
            AnalyzedNode analyzedChild = analyzedByRequest.get(requestKey);
            if (analyzedChild == null || isAnalyzedBarrier(analyzedChild, sccAnalysis.cyclicNodes())) {
                return false;
            }
        }
        for (RequestKey requestKey : externalRequests) {
            AnalyzedNode analyzedChild = analyzedByRequest.get(requestKey);
            AggregatedNode child = executableByRequest.get(analyzedChild.requestKey());
            if (child == null) {
                child = new AggregatedNode(nodes.size(), analyzedChild.requestKey());
                executableByRequest.put(analyzedChild.requestKey(), child);
                nodes.add(child);
            }
            if (node.addEdgeIfAbsent(child.index())) {
                child.incrementIndegree();
            }
        }
        return true;
    }

    private static boolean allCandidatesAggregateSafe(List<CandidateAnalysis> candidates) {
        if (candidates.size() <= 1) {
            return false;
        }
        for (CandidateAnalysis candidate : candidates) {
            if (!candidate.aggregateSafe()) {
                return false;
            }
        }
        return true;
    }

    private static RootCutPrefilterResult prefilterRootCutCandidate(
                                                                    AnalyzedNode rootNode, CandidateAnalysis rootCandidate,
                                                                    Map<RequestKey, AnalyzedNode> analyzedByRequest) {
        if (!hasStructurallyExactInputs(rootCandidate.details())) {
            return RootCutPrefilterResult.REJECT;
        }

        RootCutPrefilterState state = new RootCutPrefilterState();
        Set<AEKey> activeKeys = new HashSet<>();
        activeKeys.add(rootNode.key());
        try {
            return scanRootCutCandidate(
                    rootCandidate,
                    rootNode.key(),
                    analyzedByRequest,
                    activeKeys,
                    state);
        } catch (RuntimeException failure) {
            return RootCutPrefilterResult.UNKNOWN;
        }
    }

    private static RootCutPrefilterResult scanRootCutNode(
                                                          AnalyzedNode node, AEKey rootKey, Map<RequestKey, AnalyzedNode> analyzedByRequest,
                                                          Set<AEKey> activeKeys, RootCutPrefilterState state) {
        if (!state.consume()) {
            return RootCutPrefilterResult.UNKNOWN;
        }
        if (node.program == null) {
            return RootCutPrefilterResult.UNKNOWN;
        }
        if (node.program.failureReason() != null) {
            return RootCutPrefilterResult.REJECT;
        }
        if (node.prefixOnly() || node.terminal()) {
            return RootCutPrefilterResult.PASS;
        }

        CandidateAnalysis selected = null;
        for (CandidateAnalysis candidate : node.candidates()) {
            if (isRootCutBlocked(candidate.details(), rootKey)) {
                continue;
            }
            if (selected != null) {
                return RootCutPrefilterResult.REJECT;
            }
            selected = candidate;
        }
        if (selected == null) {
            return RootCutPrefilterResult.PASS;
        }
        if (activeKeys.contains(node.key())) {
            return RootCutPrefilterResult.REJECT;
        }
        for (AEKey activeKey : activeKeys) {
            if (!activeKey.equals(rootKey) && candidateOutputsKey(selected, activeKey)) {
                return RootCutPrefilterResult.REJECT;
            }
        }

        activeKeys.add(node.key());
        RootCutPrefilterResult result = scanRootCutCandidate(
                selected,
                rootKey,
                analyzedByRequest,
                activeKeys,
                state);
        activeKeys.remove(node.key());
        return result;
    }

    private static RootCutPrefilterResult scanRootCutCandidate(
                                                               CandidateAnalysis candidate, AEKey rootKey, Map<RequestKey, AnalyzedNode> analyzedByRequest,
                                                               Set<AEKey> activeKeys, RootCutPrefilterState state) {
        if (candidate.failureReason() != null || !candidate.aggregateSafe() ||
                !hasStructurallyExactInputs(candidate.details())) {
            return RootCutPrefilterResult.REJECT;
        }
        List<AnalyzedEdge> edges = candidate.edges();
        if (edges == null) {
            return RootCutPrefilterResult.UNKNOWN;
        }
        for (AnalyzedEdge edge : edges) {
            AnalyzedNode child = analyzedByRequest.get(edge.childRequestKey());
            if (child == null) {
                return RootCutPrefilterResult.UNKNOWN;
            }
            RootCutPrefilterResult result = scanRootCutNode(
                    child,
                    rootKey,
                    analyzedByRequest,
                    activeKeys,
                    state);
            if (result != RootCutPrefilterResult.PASS) {
                return result;
            }
        }
        return RootCutPrefilterResult.PASS;
    }

    private static boolean isRootCutBlocked(IPatternDetails details, AEKey rootKey) {
        for (GenericStack output : details.getOutputs()) {
            if (rootKey.matches(output)) {
                return true;
            }
        }
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length > 0 && rootKey.matches(possibleInputs[0])) {
                return true;
            }
        }
        return false;
    }

    private static boolean candidateOutputsKey(CandidateAnalysis candidate, AEKey key) {
        for (GenericStack output : candidate.outputs()) {
            // Keep cycle prefiltering on the same matching contract as the
            // notRecursive and output-feedback checks.
            if (key.matches(output)) {
                return true;
            }
        }
        return false;
    }

    private static final class RootCutPrefilterState {

        private int remaining = ROOT_CUT_PREFILTER_NODE_BUDGET;

        private boolean consume() {
            return this.remaining-- > 0;
        }
    }

    private static boolean isAnalyzedBarrier(AnalyzedNode node, boolean[] cyclicNodes) {
        if (cyclicNodes[node.index()] || node.candidates().size() != 1) {
            return true;
        }
        return !node.candidates().get(0).aggregateSafe();
    }

    private static boolean graphHasExtraOutputs(List<AggregatedNode> nodes,
                                                Map<RequestKey, AnalyzedNode> analyzedByRequest) {
        for (AggregatedNode node : nodes) {
            if (node.barrier()) {
                AnalyzedNode analyzed = analyzedByRequest.get(node.requestKey());
                for (CandidateAnalysis candidate : analyzed.candidates()) {
                    if (candidate.outputs() != null && emitsOtherKey(candidate.outputs(), node.key())) {
                        return true;
                    }
                }
            } else if (node.outputs() != null && emitsOtherKey(node.outputs(), node.key())) {
                return true;
            }
        }
        return false;
    }

    private static boolean emitsOtherKey(GenericStack[] outputs, AEKey key) {
        for (GenericStack output : outputs) {
            if (!key.matches(output)) {
                return true;
            }
        }
        return false;
    }

    private static Map<Object, List<AggregatedNode>> indexNodesByPrimaryKey(List<AggregatedNode> nodes) {
        Map<Object, List<AggregatedNode>> nodesByPrimaryKey = new HashMap<>();
        for (AggregatedNode node : nodes) {
            nodesByPrimaryKey.computeIfAbsent(node.key().getPrimaryKey(), ignored -> new ArrayList<>()).add(node);
        }
        return nodesByPrimaryKey;
    }

    private static boolean hasUnsafeBarrierOutputFeedback(List<AggregatedNode> nodes,
                                                          Map<RequestKey, AnalyzedNode> analyzedByRequest,
                                                          Map<Object, List<AggregatedNode>> nodesByPrimaryKey,
                                                          Level level) {
        for (AggregatedNode barrier : nodes) {
            if (!barrier.barrier()) {
                continue;
            }
            AnalyzedNode analyzedBarrier = analyzedByRequest.get(barrier.requestKey());
            for (CandidateAnalysis candidate : analyzedBarrier.candidates()) {
                for (GenericStack output : candidate.outputs()) {
                    if (barrier.key().matches(output)) {
                        continue;
                    }
                    List<AggregatedNode> suppliedNodes = nodesByPrimaryKey.get(output.what().getPrimaryKey());
                    if (suppliedNodes == null) {
                        continue;
                    }
                    for (AggregatedNode suppliedNode : suppliedNodes) {
                        if (!suppliedNode.acceptsTemplate(output.what(), level)) {
                            continue;
                        }
                        if (!suppliedNode.terminal() || suppliedNode.parentInput() != null &&
                                suppliedNode.parentInput().getRemainingKey(output.what()) != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static @Nullable AEKey resolveExactChildKey(ICraftingService craftingService,
                                                        IPatternDetails.IInput input, AEKey inputKey, Level level) {
        if (craftingService.isCraftable(inputKey) || craftingService.canEmitFor(inputKey)) {
            return inputKey;
        }
        AEKey fuzzyCraftable = craftingService.getFuzzyCraftable(
                inputKey,
                candidate -> input.isValid(candidate, level));
        return fuzzyCraftable == null || Objects.equals(fuzzyCraftable, inputKey) ? inputKey : null;
    }

    private static long getOutputCount(GenericStack[] outputs, AEKey key) {
        long outputCount = 0L;
        for (GenericStack output : outputs) {
            if (key.matches(output)) {
                outputCount = NumberUtils.saturatedAdd(outputCount, output.amount());
            }
        }
        return outputCount;
    }

    private static @Nullable int[] buildTopologicalOrder(List<AggregatedNode> nodes) {
        int[] remainingIndegrees = new int[nodes.size()];
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (AggregatedNode node : nodes) {
            remainingIndegrees[node.index()] = node.indegree();
            if (node.indegree() == 0) {
                ready.addLast(node.index());
            }
        }

        int[] order = new int[nodes.size()];
        int outputIndex = 0;
        while (!ready.isEmpty()) {
            int nodeIndex = ready.removeFirst();
            order[outputIndex++] = nodeIndex;
            for (AggregatedEdge edge : nodes.get(nodeIndex).edges()) {
                if (--remainingIndegrees[edge.childIndex()] == 0) {
                    ready.addLast(edge.childIndex());
                }
            }
        }
        return outputIndex == nodes.size() ? order : null;
    }

    private static boolean hasUnsafeOutputFeedback(List<AggregatedNode> nodes,
                                                   Map<Object, List<AggregatedNode>> nodesByPrimaryKey,
                                                   Level level) {
        List<List<Integer>> executionSuccessors = new ArrayList<>(nodes.size());
        int[] executionIndegrees = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            executionSuccessors.add(new ArrayList<>());
        }

        for (AggregatedNode consumer : nodes) {
            for (AggregatedEdge dependency : consumer.edges()) {
                executionSuccessors.get(dependency.childIndex()).add(consumer.index());
                executionIndegrees[consumer.index()]++;
            }

            if (consumer.outputs() == null) {
                continue;
            }
            for (GenericStack output : consumer.outputs()) {
                if (consumer.key().matches(output)) {
                    continue;
                }
                List<AggregatedNode> suppliedNodes = nodesByPrimaryKey.get(output.what().getPrimaryKey());
                if (suppliedNodes == null) {
                    continue;
                }
                for (AggregatedNode suppliedNode : suppliedNodes) {
                    if (!suppliedNode.acceptsTemplate(output.what(), level)) {
                        continue;
                    }
                    if (suppliedNode.parentInput() != null &&
                            suppliedNode.parentInput().getRemainingKey(output.what()) != null) {
                        return true;
                    }
                    if (!suppliedNode.terminal()) {
                        return true;
                    }
                    executionSuccessors.get(consumer.index()).add(suppliedNode.index());
                    executionIndegrees[suppliedNode.index()]++;
                }
            }
        }

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int nodeIndex = 0; nodeIndex < executionIndegrees.length; nodeIndex++) {
            if (executionIndegrees[nodeIndex] == 0) {
                ready.addLast(nodeIndex);
            }
        }

        int visitedNodes = 0;
        while (!ready.isEmpty()) {
            int nodeIndex = ready.removeFirst();
            visitedNodes++;
            for (int successor : executionSuccessors.get(nodeIndex)) {
                if (--executionIndegrees[successor] == 0) {
                    ready.addLast(successor);
                }
            }
        }
        return visitedNodes != nodes.size();
    }

    private static long countLogicalNodes(List<AggregatedNode> nodes, int[] topologicalOrder) {
        long[] instanceCounts = new long[nodes.size()];
        instanceCounts[0] = 1L;
        long logicalNodeCount = 0L;
        for (int nodeIndex : topologicalOrder) {
            long instances = instanceCounts[nodeIndex];
            logicalNodeCount = NumberUtils.saturatedAdd(logicalNodeCount, instances);
            for (AggregatedEdge edge : nodes.get(nodeIndex).edges()) {
                long childInstances = NumberUtils.saturatedMultiply(instances, edge.occurrences());
                instanceCounts[edge.childIndex()] = NumberUtils.saturatedAdd(
                        instanceCounts[edge.childIndex()],
                        childInstances);
            }
        }
        return logicalNodeCount;
    }

    private static int[] indexTopologicalRanks(int[] topologicalOrder) {
        int[] ranks = new int[topologicalOrder.length];
        for (int rank = 0; rank < topologicalOrder.length; rank++) {
            ranks[topologicalOrder[rank]] = rank;
        }
        return ranks;
    }

    private static int[][] buildParentIndexes(List<AggregatedNode> nodes) {
        int[] parentCounts = new int[nodes.size()];
        for (AggregatedNode node : nodes) {
            for (AggregatedEdge edge : node.edges()) {
                parentCounts[edge.childIndex()]++;
            }
        }

        int[][] parentIndexes = new int[nodes.size()][];
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            parentIndexes[nodeIndex] = new int[parentCounts[nodeIndex]];
            parentCounts[nodeIndex] = 0;
        }
        for (int parentIndex = 0; parentIndex < nodes.size(); parentIndex++) {
            for (AggregatedEdge edge : nodes.get(parentIndex).edges()) {
                int childIndex = edge.childIndex();
                parentIndexes[childIndex][parentCounts[childIndex]++] = parentIndex;
            }
        }
        return parentIndexes;
    }

    private static boolean containsBoundary(List<AggregatedNode> nodes) {
        for (AggregatedNode node : nodes) {
            if (node.barrier()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOnlyStructuralExactInputs(List<AggregatedNode> nodes) {
        for (AggregatedNode node : nodes) {
            if (node.parentInput() != null && node.structuralExactTemplate() == null) {
                return false;
            }
        }
        return true;
    }

    private boolean tryExecuteAggregated(ICraftingTreeNode root, AggregatedGraph graph,
                                         CraftingSimulationState inv, long requestedAmount,
                                         boolean rootPrefixHandled, boolean segment, MaxFastMetrics metrics)
                                                                                                             throws CraftBranchFailure,
                                                                                                             InterruptedException {
        ICraftingCalculation calculation = root.gtlcore$getMaxFastCalculation();
        boolean simulation = root.gtlcore$isMaxFastSimulation();
        if (!graph.boundaryTransactionGuard() ||
                simulation && calculation.gtlcore$isMaxFastDeferredMissingCaptureActive()) {
            executeAggregated(
                    root,
                    graph,
                    this.aggregatedExternalAncestors,
                    inv,
                    requestedAmount,
                    rootPrefixHandled,
                    segment,
                    BoundaryResolutionMode.NONE,
                    metrics);
            return true;
        }

        boolean strictParentProbe = simulation && calculation.gtlcore$isMaxFastStrictBoundaryProbeActive();
        boolean reportMissing = simulation && !strictParentProbe && !segment;
        BoundaryResolutionMode boundaryMode = reportMissing ?
                BoundaryResolutionMode.REPORT_MISSING : BoundaryResolutionMode.STRICT;
        metrics.recordAggregationBoundaryTransactionGuardRun();
        if (simulation) {
            metrics.recordAggregationBoundaryTransactionSimulationProbe();
        }
        long setupStartedNanos = System.nanoTime();
        ChildCraftingSimulationState child;
        try {
            child = new ChildCraftingSimulationState(inv);
        } finally {
            metrics.recordAggregationBoundaryTransactionSetupNanos(System.nanoTime() - setupStartedNanos);
        }
        boolean deferredMissingScope = boundaryMode == BoundaryResolutionMode.REPORT_MISSING;
        if (deferredMissingScope) {
            calculation.gtlcore$beginMaxFastDeferredMissingScope();
        }
        boolean localStrictScope = simulation && boundaryMode == BoundaryResolutionMode.STRICT && !strictParentProbe;
        try {
            if (localStrictScope) {
                calculation.gtlcore$beginMaxFastStrictBoundaryProbe();
            }
            try {
                executeAggregated(
                        root,
                        graph,
                        this.aggregatedExternalAncestors,
                        child,
                        requestedAmount,
                        rootPrefixHandled,
                        segment,
                        boundaryMode,
                        metrics);
            } finally {
                if (localStrictScope) {
                    calculation.gtlcore$endMaxFastStrictBoundaryProbe();
                }
            }
        } catch (CraftBranchFailure failure) {
            if (deferredMissingScope) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            if (simulation) {
                if (strictParentProbe) {
                    metrics.recordAggregationBoundaryTransactionSimulationPropagation();
                } else {
                    metrics.recordAggregationBoundaryTransactionSimulationFallback(
                            root.gtlcore$getMaxFastKey(),
                            graph.nodes().length,
                            graph.logicalNodeCount(),
                            this.aggregatedExternalAncestors.length,
                            this.aggregatedGraphCompileNanos);
                }
            } else {
                metrics.recordAggregationBoundaryTransactionCraftFailure();
            }
            if (strictParentProbe) {
                throw failure;
            }
            return false;
        } catch (InterruptedException | RuntimeException | Error failure) {
            if (deferredMissingScope) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            throw failure;
        }
        long applyStartedNanos = System.nanoTime();
        try {
            child.applyDiff(inv);
            if (deferredMissingScope) {
                deferredMissingScope = false;
                calculation.gtlcore$commitMaxFastDeferredMissingScope();
            }
        } catch (RuntimeException | Error failure) {
            if (deferredMissingScope) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            throw failure;
        } finally {
            metrics.recordAggregationBoundaryTransactionApplyNanos(System.nanoTime() - applyStartedNanos);
        }
        if (simulation) {
            metrics.recordAggregationBoundaryTransactionSimulationSuccess();
        }
        return true;
    }

    private void executeAggregated(ICraftingTreeNode root, AggregatedGraph graph, AEKey[] externalAncestors,
                                   CraftingSimulationState inv,
                                   long requestedAmount, boolean rootPrefixHandled, boolean segment,
                                   BoundaryResolutionMode boundaryMode,
                                   MaxFastMetrics metrics)
                                                           throws CraftBranchFailure,
                                                           InterruptedException {
        metrics.beginExecutor();
        long startedNanos = System.nanoTime();
        long executedFrames = 0L;
        long mergedRequests = 0L;
        long logicalNodeCount = graph.nodes().length;
        AggregatedNode[] nodes = graph.nodes();
        ensureAggregatedScratchCapacity(nodes.length);
        long[] requests = this.aggregatedRequests;
        long[] remainingRequests = this.aggregatedRemainingRequests;
        long[] totalRequestedItems = this.aggregatedTotalRequestedItems;
        long[] processTimes = this.aggregatedProcessTimes;
        boolean scheduledBoundaries = boundaryMode != BoundaryResolutionMode.NONE;
        boolean[] completedNodes = this.aggregatedCompletedNodes;
        boolean[] pendingBoundaries = this.aggregatedPendingBoundaries;
        boolean[] pendingSccBoundaries = new boolean[nodes.length];
        int[] outstandingChildren = this.aggregatedOutstandingChildren;
        BitSet readyCompletionRanks = this.aggregatedReadyCompletionRanks;
        Set<Object> changedPrimaryKeys = this.aggregatedChangedPrimaryKeys;
        Set<Object> nextChangedPrimaryKeys = this.aggregatedNextChangedPrimaryKeys;
        Map<Object, List<Integer>> boundaryDependencyIndex = scheduledBoundaries ? new HashMap<>() : Map.of();
        List<Integer> unknownDependencyBoundaries = scheduledBoundaries ? new ArrayList<>() : List.of();
        Arrays.fill(requests, 0, nodes.length, 0L);
        Arrays.fill(remainingRequests, 0, nodes.length, 0L);
        Arrays.fill(totalRequestedItems, 0, nodes.length, 0L);
        Arrays.fill(processTimes, 0, nodes.length, 0L);
        if (scheduledBoundaries) {
            Arrays.fill(completedNodes, 0, nodes.length, false);
            Arrays.fill(pendingBoundaries, 0, nodes.length, false);
            Arrays.fill(outstandingChildren, 0, nodes.length, 0);
            Arrays.fill(this.aggregatedPreparedBoundaries, 0, nodes.length, null);
            readyCompletionRanks.clear();
            changedPrimaryKeys.clear();
            nextChangedPrimaryKeys.clear();
            this.aggregatedCompletionDeltas.clear();
        }
        requests[0] = requestedAmount;
        int pendingBoundaryCount = 0;
        long propagationStartedNanos = System.nanoTime();
        long propagationNanos = 0L;
        long completionStartedNanos = 0L;
        long completionNanos = 0L;

        try {
            for (int nodeIndex : graph.topologicalOrder()) {
                long request = requests[nodeIndex];
                if (request <= 0) {
                    continue;
                }

                executedFrames++;
                AggregatedNode node = nodes[nodeIndex];
                root.gtlcore$checkMaxFastCancellation();
                boolean prefixHandled = rootPrefixHandled && nodeIndex == 0;
                long remainingRequestCount;
                if (prefixHandled) {
                    remainingRequestCount = request;
                } else {
                    metrics.recordNodeEnter();
                    inv.addStackBytes(node.key(), node.amount(), request);
                    long extracted = extractAggregatedTemplates(
                            root,
                            inv,
                            node,
                            request,
                            metrics);
                    remainingRequestCount = request - extracted;
                }
                if (remainingRequestCount == 0) {
                    metrics.recordPrefixSatisfied();
                    continue;
                }
                if (node.prefixOnly()) {
                    inv.emitItems(
                            node.key(),
                            NumberUtils.saturatedMultiply(node.amount(), remainingRequestCount));
                    metrics.recordPrefixSatisfied();
                    continue;
                }

                remainingRequests[nodeIndex] = remainingRequestCount;
                if (node.barrier()) {
                    SccAttempt sccAttempt = tryExecuteScc(
                            root,
                            inv,
                            node,
                            remainingRequestCount,
                            null,
                            metrics);
                    if (sccAttempt.status() == SccAttemptStatus.SUCCESS) {
                        logicalNodeCount = NumberUtils.saturatedAdd(
                                logicalNodeCount,
                                node.sccProgram().nodes().length - 1L);
                        continue;
                    }
                    if (sccAttempt.status() == SccAttemptStatus.DEFER && scheduledBoundaries &&
                            propagateSccExternalDemands(
                                    node,
                                    sccAttempt.externalDemands(),
                                    nodes,
                                    requests)) {
                        pendingSccBoundaries[nodeIndex] = true;
                        pendingBoundaries[nodeIndex] = true;
                        unknownDependencyBoundaries.add(nodeIndex);
                        pendingBoundaryCount++;
                        continue;
                    }
                    if (sccAttempt.status() == SccAttemptStatus.DEFER) {
                        node.sccProgram().runtimeGate().disable();
                        metrics.recordSccExecutionFallback();
                    }
                    long boundaryNodes;
                    if (scheduledBoundaries) {
                        BoundaryFailureDependencies failureDependencies = getBoundaryFailureDependencies(nodeIndex);
                        boundaryNodes = tryExecuteScheduledBoundary(
                                root,
                                inv,
                                nodeIndex,
                                node,
                                remainingRequestCount,
                                externalAncestors,
                                false,
                                false,
                                failureDependencies,
                                pendingBoundaryCount > 0 ? changedPrimaryKeys : null,
                                metrics);
                        if (boundaryNodes == BOUNDARY_ATTEMPT_FAILED) {
                            pendingBoundaries[nodeIndex] = true;
                            indexBoundaryDependencies(
                                    nodeIndex,
                                    failureDependencies,
                                    boundaryDependencyIndex,
                                    unknownDependencyBoundaries);
                            pendingBoundaryCount++;
                            metrics.recordAggregationBoundarySchedulerInitialFailure();
                            continue;
                        }
                    } else {
                        long startedBarrierNanos = System.nanoTime();
                        try {
                            boundaryNodes = root.gtlcore$runMaxFastBarrier(
                                    inv,
                                    node.key(),
                                    node.amount(),
                                    remainingRequestCount,
                                    node.barrierPatterns(),
                                    externalAncestors,
                                    node.cycleCandidateGraphEligible());
                        } finally {
                            metrics.recordBaselineExecution(System.nanoTime() - startedBarrierNanos);
                        }
                    }
                    logicalNodeCount = NumberUtils.saturatedAdd(
                            logicalNodeCount,
                            Math.max(0L, boundaryNodes - 1L));
                    continue;
                }

                long remainingItems = NumberUtils.saturatedMultiply(node.amount(), remainingRequestCount);
                totalRequestedItems[nodeIndex] = remainingItems;
                if (node.terminal()) {
                    metrics.recordTerminalExecution();
                    continue;
                }

                metrics.recordSinglePathExecution();
                long times = ceilDiv(remainingItems, node.outputPerPattern());
                processTimes[nodeIndex] = times;
                metrics.recordCompiledProcess(times);
                for (AggregatedEdge edge : node.edges()) {
                    metrics.recordChildRequest();
                    long childRequest = NumberUtils.saturatedMultiply(edge.requestMultiplier(), times);
                    if (requests[edge.childIndex()] != 0) {
                        mergedRequests++;
                        if (nodes[edge.childIndex()].sccProgram() != null) {
                            metrics.recordSccBoundaryDemandMerge();
                        }
                    }
                    requests[edge.childIndex()] = NumberUtils.saturatedAdd(
                            requests[edge.childIndex()],
                            childRequest);
                }
            }

            propagationNanos = System.nanoTime() - propagationStartedNanos;
            completionStartedNanos = System.nanoTime();
            int[] order = graph.topologicalOrder();
            ICraftingCalculation calculation = root.gtlcore$getMaxFastCalculation();
            if (scheduledBoundaries) {
                boolean schedulerRecoveryNeeded = pendingBoundaryCount > 0;
                initializeAggregatedCompletionAgenda(
                        graph,
                        processTimes,
                        pendingBoundaries,
                        outstandingChildren,
                        readyCompletionRanks);
                while (pendingBoundaryCount > 0) {
                    int completed = completeReadyAggregatedNodes(
                            inv,
                            calculation,
                            graph,
                            processTimes,
                            totalRequestedItems,
                            completedNodes,
                            outstandingChildren,
                            readyCompletionRanks,
                            this.aggregatedCompletionDeltas,
                            changedPrimaryKeys);
                    executedFrames += 2L * completed;

                    boolean boundaryResolved = false;
                    if (!changedPrimaryKeys.isEmpty()) {
                        BitSet retryCandidates = new BitSet(nodes.length);
                        for (Object changedKey : changedPrimaryKeys) {
                            List<Integer> indexedBoundaries = boundaryDependencyIndex.get(changedKey);
                            if (indexedBoundaries != null) {
                                for (int nodeIndex : indexedBoundaries) {
                                    retryCandidates.set(nodeIndex);
                                }
                            }
                        }
                        for (int nodeIndex : unknownDependencyBoundaries) {
                            retryCandidates.set(nodeIndex);
                        }
                        for (int nodeIndex : order) {
                            if (!pendingBoundaries[nodeIndex] || !retryCandidates.get(nodeIndex)) {
                                continue;
                            }
                            AggregatedNode node = nodes[nodeIndex];
                            BoundaryFailureDependencies failureDependencies = getBoundaryFailureDependencies(nodeIndex);
                            if (pendingSccBoundaries[nodeIndex]) {
                                metrics.recordAggregationBoundarySchedulerDependencyWakeup();
                                metrics.recordAggregationBoundarySchedulerRetry();
                                SccAttempt sccAttempt = tryExecuteScc(
                                        root,
                                        inv,
                                        node,
                                        remainingRequests[nodeIndex],
                                        nextChangedPrimaryKeys,
                                        metrics);
                                if (sccAttempt.status() == SccAttemptStatus.DEFER) {
                                    continue;
                                }
                                if (sccAttempt.status() == SccAttemptStatus.SUCCESS) {
                                    pendingSccBoundaries[nodeIndex] = false;
                                    pendingBoundaries[nodeIndex] = false;
                                    pendingBoundaryCount--;
                                    boundaryResolved = true;
                                    resolveAggregatedDependency(
                                            graph,
                                            nodeIndex,
                                            processTimes,
                                            completedNodes,
                                            outstandingChildren,
                                            readyCompletionRanks);
                                    metrics.recordAggregationBoundarySchedulerRetrySuccess();
                                    logicalNodeCount = NumberUtils.saturatedAdd(
                                            logicalNodeCount,
                                            node.sccProgram().nodes().length - 1L);
                                    continue;
                                }
                                pendingSccBoundaries[nodeIndex] = false;
                            } else {
                                metrics.recordAggregationBoundarySchedulerDependencyCheck();
                                if (!failureDependencies.shouldRetry(
                                        root,
                                        inv,
                                        changedPrimaryKeys)) {
                                    metrics.recordAggregationBoundarySchedulerDependencySkip();
                                    continue;
                                }
                                metrics.recordAggregationBoundarySchedulerDependencyWakeup();
                                metrics.recordAggregationBoundarySchedulerRetry();
                            }

                            long boundaryNodes = tryExecuteScheduledBoundary(
                                    root,
                                    inv,
                                    nodeIndex,
                                    node,
                                    remainingRequests[nodeIndex],
                                    externalAncestors,
                                    true,
                                    false,
                                    failureDependencies,
                                    nextChangedPrimaryKeys,
                                    metrics);
                            if (boundaryNodes == BOUNDARY_ATTEMPT_FAILED) {
                                continue;
                            }

                            pendingBoundaries[nodeIndex] = false;
                            pendingBoundaryCount--;
                            boundaryResolved = true;
                            resolveAggregatedDependency(
                                    graph,
                                    nodeIndex,
                                    processTimes,
                                    completedNodes,
                                    outstandingChildren,
                                    readyCompletionRanks);
                            metrics.recordAggregationBoundarySchedulerRetrySuccess();
                            logicalNodeCount = NumberUtils.saturatedAdd(
                                    logicalNodeCount,
                                    Math.max(0L, boundaryNodes - 1L));
                        }
                    }
                    changedPrimaryKeys.clear();
                    if (boundaryResolved) {
                        Set<Object> swap = changedPrimaryKeys;
                        changedPrimaryKeys = nextChangedPrimaryKeys;
                        nextChangedPrimaryKeys = swap;
                        nextChangedPrimaryKeys.clear();
                        continue;
                    }
                    nextChangedPrimaryKeys.clear();
                    if (boundaryMode == BoundaryResolutionMode.STRICT) {
                        throw createPendingBoundaryFailure(
                                calculation,
                                nodes,
                                order,
                                pendingBoundaries,
                                remainingRequests);
                    }

                    int deferredNodeIndex = findFirstPendingBoundary(order, pendingBoundaries);
                    AggregatedNode deferredNode = nodes[deferredNodeIndex];
                    long boundaryNodes = tryExecuteScheduledBoundary(
                            root,
                            inv,
                            deferredNodeIndex,
                            deferredNode,
                            remainingRequests[deferredNodeIndex],
                            externalAncestors,
                            true,
                            true,
                            getBoundaryFailureDependencies(deferredNodeIndex),
                            changedPrimaryKeys,
                            metrics);
                    if (boundaryNodes == BOUNDARY_ATTEMPT_FAILED) {
                        throw createPendingBoundaryFailure(
                                calculation,
                                nodes,
                                order,
                                pendingBoundaries,
                                remainingRequests);
                    }
                    pendingBoundaries[deferredNodeIndex] = false;
                    pendingBoundaryCount--;
                    resolveAggregatedDependency(
                            graph,
                            deferredNodeIndex,
                            processTimes,
                            completedNodes,
                            outstandingChildren,
                            readyCompletionRanks);
                    metrics.recordAggregationBoundarySchedulerDeferredMissing();
                    logicalNodeCount = NumberUtils.saturatedAdd(
                            logicalNodeCount,
                            Math.max(0L, boundaryNodes - 1L));
                }

                int completed = completeReadyAggregatedNodes(
                        inv,
                        calculation,
                        graph,
                        processTimes,
                        totalRequestedItems,
                        completedNodes,
                        outstandingChildren,
                        readyCompletionRanks,
                        this.aggregatedCompletionDeltas,
                        null);
                executedFrames += 2L * completed;
                verifyAggregatedCompletion(
                        processTimes,
                        completedNodes,
                        outstandingChildren,
                        readyCompletionRanks,
                        nodes.length);
                if (schedulerRecoveryNeeded) {
                    metrics.recordAggregationBoundarySchedulerRecoveredGraph();
                }
            } else {
                int completed = 0;
                for (int orderIndex = order.length - 1; orderIndex >= 0; orderIndex--) {
                    int nodeIndex = order[orderIndex];
                    long times = processTimes[nodeIndex];
                    if (times <= 0) {
                        continue;
                    }

                    executedFrames += 2;
                    completeAggregatedNode(inv, nodes[nodeIndex], times, totalRequestedItems[nodeIndex]);
                    completed++;
                }
                if (completed > 0) {
                    calculation.gtlcore$clearTemplateCache();
                }
            }

            boolean captureTerminalMissing = boundaryMode == BoundaryResolutionMode.REPORT_MISSING;
            if (captureTerminalMissing) {
                calculation.gtlcore$beginMaxFastDeferredMissingCapture();
            }
            try {
                for (int nodeIndex : order) {
                    AggregatedNode node = nodes[nodeIndex];
                    long terminalRequestCount = remainingRequests[nodeIndex];
                    if (!node.terminal() || terminalRequestCount <= 0) {
                        continue;
                    }

                    executedFrames++;
                    root.gtlcore$checkMaxFastCancellation();
                    long available = extractAggregatedTemplates(
                            root,
                            inv,
                            node,
                            terminalRequestCount,
                            metrics);
                    long missingRequestCount = terminalRequestCount - available;
                    if (missingRequestCount > 0) {
                        long missingItems = NumberUtils.saturatedMultiply(node.amount(), missingRequestCount);
                        IPatternDetails.IInput input = node.parentInput();
                        InputTemplate structuralExactTemplate = node.structuralExactTemplate();
                        boolean exactDependency = input != null && structuralExactTemplate != null;
                        root.gtlcore$reportMaxFastMissing(
                                node.key(),
                                missingItems,
                                exactDependency ? null : input,
                                input == null ? 1L : node.amount(),
                                input == null ? missingItems : missingRequestCount);
                    }
                }
            } finally {
                if (captureTerminalMissing) {
                    calculation.gtlcore$endMaxFastDeferredMissingCapture();
                }
            }
            root.gtlcore$setMaxFastLogicalNodeCount(logicalNodeCount);
            completionNanos = System.nanoTime() - completionStartedNanos;
        } finally {
            long finishedNanos = System.nanoTime();
            if (scheduledBoundaries) {
                Arrays.fill(this.aggregatedPreparedBoundaries, 0, nodes.length, null);
                this.aggregatedCompletionDeltas.clear();
                readyCompletionRanks.clear();
            }
            if (completionStartedNanos == 0L) {
                propagationNanos = finishedNanos - propagationStartedNanos;
            } else if (completionNanos == 0L) {
                completionNanos = finishedNanos - completionStartedNanos;
            }
            long elapsedNanos = finishedNanos - startedNanos;
            metrics.recordAggregationExecution(
                    elapsedNanos,
                    propagationNanos,
                    completionNanos,
                    mergedRequests,
                    segment);
            metrics.recordExecutor(elapsedNanos, executedFrames, 0);
        }
    }

    private SccAttempt tryExecuteScc(ICraftingTreeNode root, CraftingSimulationState inv,
                                     AggregatedNode boundaryNode, long requestedAmount,
                                     @Nullable Set<Object> changedPrimaryKeys,
                                     MaxFastMetrics metrics) throws InterruptedException {
        SccProgram program = boundaryNode.sccProgram();
        if (program == null) {
            return SccAttempt.fallback();
        }
        if (program.runtimeGate().disabled()) {
            metrics.recordSccRuntimeBypass();
            return SccAttempt.fallback();
        }
        if (program.hasExternalInputs() && !boundaryNode.sccExternalDependenciesEligible()) {
            return rejectSccPreflight(program, metrics);
        }

        metrics.recordSccRun();
        long startedNanos = System.nanoTime();
        try {
            int rootNodeIndex = program.nodeIndex(boundaryNode.key());
            long totalRequestedItems = NumberUtils.saturatedMultiply(boundaryNode.amount(), requestedAmount);
            if (rootNodeIndex < 0 || totalRequestedItems <= 0 || totalRequestedItems == Long.MAX_VALUE ||
                    !validateSccProgram(root, program)) {
                return rejectSccContract(program, metrics);
            }
            if (!hasSccStarter(inv, program) && !program.hasExternalInputs()) {
                return rejectSccPreflight(program, metrics);
            }

            SccPlanResult plan = planSccExecution(
                    root,
                    inv,
                    program,
                    rootNodeIndex,
                    totalRequestedItems,
                    metrics);
            if (plan == null) {
                return rejectSccPlanning(program, metrics);
            }
            if (!plan.externalDemands().isEmpty()) {
                if (!boundaryNode.sccExternalDependenciesEligible() ||
                        !program.runtimeGate().tryAcquireExternalDeferral()) {
                    return rejectSccPlanning(program, metrics);
                }
                metrics.recordSccExternalDependencyDeferral();
                return SccAttempt.defer(plan.externalDemands());
            }
            long[] processTimes = plan.processTimes();

            ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
            long[] remainingTimes = processTimes.clone();
            boolean[] queued = new boolean[program.nodes().length];
            ArrayDeque<Integer> worklist = new ArrayDeque<>(program.nodes().length);
            int unfinishedNodes = 0;
            for (int nodeIndex = 0; nodeIndex < remainingTimes.length; nodeIndex++) {
                if (remainingTimes[nodeIndex] > 0) {
                    unfinishedNodes++;
                    queued[nodeIndex] = true;
                    worklist.addLast(nodeIndex);
                }
            }

            long batchExecutions = 0L;
            long worklistWakeups = 0L;
            while (!worklist.isEmpty()) {
                int nodeIndex = worklist.removeFirst();
                queued[nodeIndex] = false;
                root.gtlcore$checkMaxFastCancellation();
                worklistWakeups++;
                long remaining = remainingTimes[nodeIndex];
                if (remaining <= 0) {
                    continue;
                }
                SccNode node = program.nodes()[nodeIndex];
                long runnable = getSccRunnableBatches(child, node, remaining);
                if (runnable <= 0) {
                    continue;
                }
                if (!consumeSccInputs(child, node, runnable)) {
                    return rejectSccExecution(program, metrics);
                }
                for (SccInput input : node.inputs()) {
                    child.addStackBytes(input.key(), input.amountPerPattern(), runnable);
                }
                completeSccPattern(child, node, runnable);
                remainingTimes[nodeIndex] -= runnable;
                if (remainingTimes[nodeIndex] == 0) {
                    unfinishedNodes--;
                }
                batchExecutions++;
                for (int consumerIndex : program.consumersByNode()[nodeIndex]) {
                    if (remainingTimes[consumerIndex] > 0 && !queued[consumerIndex]) {
                        queued[consumerIndex] = true;
                        worklist.addLast(consumerIndex);
                    }
                }
            }
            if (unfinishedNodes > 0) {
                metrics.recordSccCertifiedBlocked();
                return rejectSccExecution(program, metrics);
            }

            long extracted = child.extract(boundaryNode.key(), totalRequestedItems, Actionable.MODULATE);
            if (extracted != totalRequestedItems) {
                return rejectSccExecution(program, metrics);
            }
            child.applyDiff(inv);
            if (changedPrimaryKeys != null) {
                ((ICraftingSimulationStateFastAccess) child)
                        .gtlcore$collectMaxFastPositiveDiff(changedPrimaryKeys);
            }
            root.gtlcore$getMaxFastCalculation().gtlcore$clearTemplateCache();
            metrics.recordSccSuccess(
                    worklistWakeups,
                    batchExecutions,
                    unfinishedNodeCount(processTimes));
            return SccAttempt.success();
        } finally {
            metrics.recordSccExecutionNanos(System.nanoTime() - startedNanos);
        }
    }

    private record SccPlanResult(long[] processTimes, Map<RequestKey, Long> externalDemands) {}

    private enum SccAttemptStatus {
        SUCCESS,
        FALLBACK,
        DEFER
    }

    private record SccAttempt(SccAttemptStatus status, Map<RequestKey, Long> externalDemands) {

        private static SccAttempt success() {
            return new SccAttempt(SccAttemptStatus.SUCCESS, Map.of());
        }

        private static SccAttempt fallback() {
            return new SccAttempt(SccAttemptStatus.FALLBACK, Map.of());
        }

        private static SccAttempt defer(Map<RequestKey, Long> externalDemands) {
            return new SccAttempt(SccAttemptStatus.DEFER, externalDemands);
        }
    }

    private static boolean hasSccStarter(CraftingSimulationState inventory, SccProgram program) {
        for (SccNode node : program.nodes()) {
            if (getSccRunnableBatches(inventory, node, 1L) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean propagateSccExternalDemands(
                                                       AggregatedNode boundaryNode,
                                                       Map<RequestKey, Long> externalDemands,
                                                       AggregatedNode[] nodes,
                                                       long[] requests) {
        for (Map.Entry<RequestKey, Long> demand : externalDemands.entrySet()) {
            long missingItems = demand.getValue();
            if (missingItems <= 0 || missingItems == Long.MAX_VALUE) {
                return false;
            }
            int childIndex = -1;
            for (AggregatedEdge edge : boundaryNode.edges()) {
                if (nodes[edge.childIndex()].requestKey().equals(demand.getKey())) {
                    childIndex = edge.childIndex();
                    break;
                }
            }
            if (childIndex < 0 || nodes[childIndex].amount() <= 0) {
                return false;
            }
            long childRequests = ceilDiv(missingItems, nodes[childIndex].amount());
            if (childRequests <= 0 || childRequests == Long.MAX_VALUE) {
                return false;
            }
            requests[childIndex] = NumberUtils.saturatedAdd(requests[childIndex], childRequests);
            if (requests[childIndex] == Long.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private static int unfinishedNodeCount(long[] processTimes) {
        int count = 0;
        for (long times : processTimes) {
            if (times > 0) {
                count++;
            }
        }
        return count;
    }

    private static SccAttempt rejectSccPreflight(SccProgram program, MaxFastMetrics metrics) {
        program.runtimeGate().disable();
        metrics.recordSccPreflightRejection();
        return SccAttempt.fallback();
    }

    private static SccAttempt rejectSccContract(SccProgram program, MaxFastMetrics metrics) {
        program.runtimeGate().disable();
        metrics.recordSccContractFallback();
        return SccAttempt.fallback();
    }

    private static SccAttempt rejectSccPlanning(SccProgram program, MaxFastMetrics metrics) {
        program.runtimeGate().disable();
        metrics.recordSccPlanningFallback();
        return SccAttempt.fallback();
    }

    private static SccAttempt rejectSccExecution(SccProgram program, MaxFastMetrics metrics) {
        program.runtimeGate().disable();
        metrics.recordSccExecutionFallback();
        return SccAttempt.fallback();
    }

    private static boolean validateSccProgram(ICraftingTreeNode root, SccProgram program) {
        Level level = root.gtlcore$getMaxFastLevel();
        for (SccNode node : program.nodes()) {
            if (getOutputCount(node.details().getOutputs(), node.key()) != node.outputPerPattern()) {
                return false;
            }
            for (SccInput input : node.inputs()) {
                IPatternDetails.IInput sourceInput = input.sourceInput();
                if (sourceInput == null || sourceInput.getMultiplier() <= 0) {
                    return false;
                }
                GenericStack[] possibleInputs = sourceInput.getPossibleInputs();
                if (possibleInputs.length != 1 || !possibleInputs[0].what().equals(input.key()) ||
                        !sourceInput.isValid(input.key(), level) ||
                        sourceInput.getRemainingKey(input.key()) != null ||
                        NumberUtils.saturatedMultiply(
                                possibleInputs[0].amount(),
                                sourceInput.getMultiplier()) != input.amountPerPattern()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @Nullable SccPlanResult planSccExecution(ICraftingTreeNode root,
                                                            CraftingSimulationState parentInventory,
                                                            SccProgram program,
                                                            int rootNodeIndex,
                                                            long totalRequestedItems,
                                                            MaxFastMetrics metrics) throws InterruptedException {
        ChildCraftingSimulationState planningInventory = new ChildCraftingSimulationState(parentInventory);
        long[] demandedItems = new long[program.nodes().length];
        long[] processTimes = new long[program.nodes().length];
        Map<RequestKey, Long> externalDemands = new LinkedHashMap<>();
        boolean[] queued = new boolean[program.nodes().length];
        ArrayDeque<Integer> worklist = new ArrayDeque<>();
        demandedItems[rootNodeIndex] = totalRequestedItems;
        queued[rootNodeIndex] = true;
        worklist.addLast(rootNodeIndex);
        long updates = 0L;
        long maxUpdates = (long) program.nodes().length * Long.SIZE;

        while (!worklist.isEmpty()) {
            int nodeIndex = worklist.removeFirst();
            queued[nodeIndex] = false;
            root.gtlcore$checkMaxFastCancellation();
            SccNode node = program.nodes()[nodeIndex];
            long neededTimes = ceilDiv(demandedItems[nodeIndex], node.outputPerPattern());
            long previousTimes = processTimes[nodeIndex];
            if (neededTimes <= previousTimes) {
                continue;
            }
            if (++updates > maxUpdates) {
                return null;
            }
            long additionalTimes = neededTimes - previousTimes;
            processTimes[nodeIndex] = neededTimes;
            for (SccInput input : node.inputs()) {
                long requiredItems = NumberUtils.saturatedMultiply(
                        input.amountPerPattern(),
                        additionalTimes);
                if (requiredItems <= 0 || requiredItems == Long.MAX_VALUE) {
                    return null;
                }
                long available = planningInventory.extract(input.key(), requiredItems, Actionable.MODULATE);
                long missing = requiredItems - available;
                if (missing <= 0) {
                    continue;
                }
                if (input.internalNodeIndex() < 0) {
                    externalDemands.merge(
                            input.requestKey(),
                            missing,
                            NumberUtils::saturatedAdd);
                    continue;
                }
                int childIndex = input.internalNodeIndex();
                long updatedDemand = NumberUtils.saturatedAdd(demandedItems[childIndex], missing);
                if (updatedDemand == Long.MAX_VALUE) {
                    return null;
                }
                demandedItems[childIndex] = updatedDemand;
                if (!queued[childIndex]) {
                    queued[childIndex] = true;
                    worklist.addLast(childIndex);
                    metrics.recordSccWorklistWakeup();
                }
            }
        }
        return new SccPlanResult(processTimes, Map.copyOf(externalDemands));
    }

    private static long getSccRunnableBatches(CraftingSimulationState inventory,
                                              SccNode node, long remainingTimes) {
        long runnable = remainingTimes;
        for (SccInput input : node.inputs()) {
            long available = inventory.extract(input.key(), Long.MAX_VALUE, Actionable.SIMULATE);
            runnable = Math.min(runnable, available / input.amountPerPattern());
            if (runnable == 0) {
                break;
            }
        }
        return runnable;
    }

    private static boolean consumeSccInputs(CraftingSimulationState inventory, SccNode node, long times) {
        for (SccInput input : node.inputs()) {
            long required = NumberUtils.saturatedMultiply(input.amountPerPattern(), times);
            if (required <= 0 || required == Long.MAX_VALUE ||
                    inventory.extract(input.key(), required, Actionable.MODULATE) != required) {
                return false;
            }
        }
        return true;
    }

    private static void completeSccPattern(CraftingSimulationState inventory, SccNode node, long times) {
        for (GenericStack output : node.outputs()) {
            inventory.insert(
                    output.what(),
                    NumberUtils.saturatedMultiply(output.amount(), times),
                    Actionable.MODULATE);
        }
        inventory.addCrafting(node.details(), times);
        inventory.addBytes(times);
    }

    private long tryExecuteScheduledBoundary(ICraftingTreeNode root, CraftingSimulationState inv,
                                             int nodeIndex, AggregatedNode node, long requestedAmount,
                                             AEKey[] externalAncestors, boolean recheckAvailable,
                                             boolean captureMissing,
                                             BoundaryFailureDependencies failureDependencies,
                                             @Nullable Set<Object> changedPrimaryKeys,
                                             MaxFastMetrics metrics)
                                                                     throws InterruptedException {
        ICraftingCalculation calculation = root.gtlcore$getMaxFastCalculation();
        metrics.recordAggregationBoundarySchedulerAttempt();
        ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
        boolean deferredScopeActive = false;
        boolean captureActive = false;
        boolean failureScopeActive = false;
        if (captureMissing) {
            calculation.gtlcore$beginMaxFastDeferredMissingScope();
            deferredScopeActive = true;
            calculation.gtlcore$beginMaxFastDeferredMissingCapture();
            captureActive = true;
        } else {
            calculation.gtlcore$beginMaxFastBoundaryFailureScope(failureDependencies);
            failureScopeActive = true;
        }

        try {
            long boundaryNodes;
            try {
                long remainingRequestedAmount = requestedAmount;
                if (recheckAvailable) {
                    long newlyAvailable = extractAggregatedTemplates(
                            root,
                            child,
                            node,
                            requestedAmount,
                            metrics);
                    remainingRequestedAmount -= newlyAvailable;
                }
                if (remainingRequestedAmount == 0) {
                    boundaryNodes = 1L;
                } else {
                    long startedBarrierNanos = System.nanoTime();
                    try {
                        ICraftingTreeNode boundary = getOrPrepareBoundary(
                                root,
                                nodeIndex,
                                node,
                                externalAncestors,
                                metrics);
                        boundaryNodes = root.gtlcore$runPreparedMaxFastBarrier(
                                child,
                                boundary,
                                remainingRequestedAmount);
                    } finally {
                        metrics.recordBaselineExecution(System.nanoTime() - startedBarrierNanos);
                    }
                }
            } finally {
                if (captureActive) {
                    calculation.gtlcore$endMaxFastDeferredMissingCapture();
                    captureActive = false;
                }
            }

            if (failureScopeActive) {
                calculation.gtlcore$endMaxFastBoundaryFailureScope();
                failureScopeActive = false;
                failureDependencies.reset();
            }
            child.applyDiff(inv);
            if (deferredScopeActive) {
                deferredScopeActive = false;
                calculation.gtlcore$commitMaxFastDeferredMissingScope();
            }
            if (changedPrimaryKeys != null) {
                ((ICraftingSimulationStateFastAccess) child)
                        .gtlcore$collectMaxFastPositiveDiff(changedPrimaryKeys);
            }
            return boundaryNodes;
        } catch (CraftBranchFailure failure) {
            if (failureScopeActive) {
                if (!(failure instanceof MaxFastCraftBranchFailure)) {
                    calculation.gtlcore$markMaxFastBoundaryFailureUnknown();
                }
                calculation.gtlcore$endMaxFastBoundaryFailureScope();
                failureScopeActive = false;
                failureDependencies.bindCapacityTargets(root, inv);
                if (failureDependencies.isUnknown()) {
                    metrics.recordAggregationBoundarySchedulerUnknownFailure();
                }
            }
            metrics.recordAggregationBoundarySchedulerAttemptFailure();
            if (deferredScopeActive) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            return BOUNDARY_ATTEMPT_FAILED;
        } catch (InterruptedException failure) {
            if (failureScopeActive) {
                calculation.gtlcore$endMaxFastBoundaryFailureScope();
            }
            if (deferredScopeActive) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            throw failure;
        } catch (RuntimeException | Error failure) {
            if (failureScopeActive) {
                calculation.gtlcore$endMaxFastBoundaryFailureScope();
            }
            if (deferredScopeActive) {
                calculation.gtlcore$discardMaxFastDeferredMissingScope();
            }
            throw failure;
        }
    }

    private ICraftingTreeNode getOrPrepareBoundary(ICraftingTreeNode root, int nodeIndex,
                                                   AggregatedNode node, AEKey[] externalAncestors,
                                                   MaxFastMetrics metrics) {
        ICraftingTreeNode boundary = this.aggregatedPreparedBoundaries[nodeIndex];
        if (boundary == null) {
            BoundaryRuntimeKey cacheKey = new BoundaryRuntimeKey(
                    node.requestKey(),
                    node.barrierPatterns(),
                    externalAncestors,
                    node.candidateGraphsEligible(),
                    node.cycleCandidateGraphEligible());
            boundary = this.preparedBoundaryCache.get(cacheKey);
            if (boundary == null) {
                boundary = root.gtlcore$prepareMaxFastBarrier(
                        node.key(),
                        node.amount(),
                        node.barrierPatterns(),
                        externalAncestors,
                        node.candidateGraphsEligible(),
                        node.cycleCandidateGraphEligible());
                if (this.preparedBoundaryCache.size() < PREPARED_BOUNDARY_CACHE_LIMIT) {
                    this.preparedBoundaryCache.put(cacheKey, boundary);
                }
                metrics.recordAggregationBoundaryRuntimeBuild();
            } else {
                metrics.recordAggregationBoundaryRuntimeReuse();
            }
            this.aggregatedPreparedBoundaries[nodeIndex] = boundary;
        } else {
            metrics.recordAggregationBoundaryRuntimeReuse();
        }

        long setupStartedNanos = System.nanoTime();
        try {
            boundary.gtlcore$beginMaxFastRuntimeAttempt();
        } finally {
            metrics.recordAggregationBoundarySetupNanos(System.nanoTime() - setupStartedNanos);
        }
        return boundary;
    }

    private static void initializeAggregatedCompletionAgenda(AggregatedGraph graph, long[] processTimes,
                                                             boolean[] pendingBoundaries,
                                                             int[] outstandingChildren,
                                                             BitSet readyCompletionRanks) {
        AggregatedNode[] nodes = graph.nodes();
        for (int nodeIndex : graph.topologicalOrder()) {
            if (processTimes[nodeIndex] <= 0) {
                continue;
            }

            int outstanding = 0;
            for (AggregatedEdge edge : nodes[nodeIndex].edges()) {
                int childIndex = edge.childIndex();
                if (processTimes[childIndex] > 0 || pendingBoundaries[childIndex]) {
                    outstanding++;
                }
            }
            outstandingChildren[nodeIndex] = outstanding;
            if (outstanding == 0) {
                readyCompletionRanks.set(graph.topologicalRanks()[nodeIndex]);
            }
        }
    }

    private static int completeReadyAggregatedNodes(CraftingSimulationState inv,
                                                    ICraftingCalculation calculation,
                                                    AggregatedGraph graph,
                                                    long[] processTimes, long[] totalRequestedItems,
                                                    boolean[] completedNodes, int[] outstandingChildren,
                                                    BitSet readyCompletionRanks,
                                                    Object2LongOpenHashMap<AEKey> completionDeltas,
                                                    @Nullable Set<Object> changedPrimaryKeys) {
        if (changedPrimaryKeys != null) {
            completionDeltas.clear();
        }
        AggregatedNode[] nodes = graph.nodes();
        int completed = 0;
        int readyRank;
        while ((readyRank = readyCompletionRanks.previousSetBit(nodes.length - 1)) >= 0) {
            readyCompletionRanks.clear(readyRank);
            int nodeIndex = graph.topologicalOrder()[readyRank];
            long times = processTimes[nodeIndex];
            if (times <= 0 || completedNodes[nodeIndex] || outstandingChildren[nodeIndex] != 0) {
                throw new IllegalStateException("MAX_FAST completion agenda contains a node that is not ready");
            }

            AggregatedNode node = nodes[nodeIndex];
            completeAggregatedNode(inv, node, times, totalRequestedItems[nodeIndex]);
            if (changedPrimaryKeys != null) {
                recordAggregatedInventoryDelta(
                        node,
                        times,
                        totalRequestedItems[nodeIndex],
                        completionDeltas);
            }
            completedNodes[nodeIndex] = true;
            completed++;
            resolveAggregatedDependency(
                    graph,
                    nodeIndex,
                    processTimes,
                    completedNodes,
                    outstandingChildren,
                    readyCompletionRanks);
        }
        if (completed > 0) {
            calculation.gtlcore$clearTemplateCache();
        }
        if (changedPrimaryKeys != null) {
            for (Object2LongMap.Entry<AEKey> entry : completionDeltas.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    changedPrimaryKeys.add(entry.getKey().getPrimaryKey());
                }
            }
        }
        return completed;
    }

    private static void resolveAggregatedDependency(AggregatedGraph graph, int resolvedNodeIndex,
                                                    long[] processTimes, boolean[] completedNodes,
                                                    int[] outstandingChildren,
                                                    BitSet readyCompletionRanks) {
        for (int parentIndex : graph.parentIndexes()[resolvedNodeIndex]) {
            if (processTimes[parentIndex] <= 0 || completedNodes[parentIndex]) {
                continue;
            }

            int outstanding = --outstandingChildren[parentIndex];
            if (outstanding < 0) {
                throw new IllegalStateException("MAX_FAST completion dependency was resolved more than once");
            }
            if (outstanding == 0) {
                readyCompletionRanks.set(graph.topologicalRanks()[parentIndex]);
            }
        }
    }

    private static void verifyAggregatedCompletion(long[] processTimes, boolean[] completedNodes,
                                                   int[] outstandingChildren, BitSet readyCompletionRanks,
                                                   int nodeCount) {
        if (!readyCompletionRanks.isEmpty()) {
            throw new IllegalStateException("MAX_FAST completion agenda retained ready nodes after final drain");
        }
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            if (processTimes[nodeIndex] > 0 &&
                    (!completedNodes[nodeIndex] || outstandingChildren[nodeIndex] != 0)) {
                throw new IllegalStateException("MAX_FAST completion agenda left an unfinished production node");
            }
        }
    }

    private static void recordAggregatedInventoryDelta(AggregatedNode node, long times,
                                                       long totalRequestedItems,
                                                       Object2LongOpenHashMap<AEKey> completionDeltas) {
        for (GenericStack output : node.outputs()) {
            if (output.amount() <= 0) {
                continue;
            }
            mergeSaturated(
                    completionDeltas,
                    output.what(),
                    NumberUtils.saturatedMultiply(output.amount(), times));
        }
        mergeSaturated(completionDeltas, node.key(), -totalRequestedItems);
    }

    private static void mergeSaturated(Object2LongOpenHashMap<AEKey> amounts, AEKey key, long amount) {
        amounts.put(key, NumberUtils.saturatedAdd(amounts.getLong(key), amount));
    }

    private static void completeAggregatedNode(CraftingSimulationState inv, AggregatedNode node, long times,
                                               long totalRequestedItems) {
        completeAggregatedPattern(inv, node, times);
        long available = inv.extract(node.key(), totalRequestedItems, Actionable.MODULATE);
        if (available != totalRequestedItems) {
            throw new IllegalStateException(
                    "MAX_FAST aggregated pattern did not produce the requested output: " + node.key() +
                            ", requested=" + totalRequestedItems + ", available=" + available + ", times=" + times);
        }
    }

    private static int findFirstPendingBoundary(int[] order, boolean[] pendingBoundaries) {
        for (int nodeIndex : order) {
            if (pendingBoundaries[nodeIndex]) {
                return nodeIndex;
            }
        }
        throw new IllegalStateException("MAX_FAST boundary scheduler lost its pending boundary");
    }

    private CraftBranchFailure createPendingBoundaryFailure(ICraftingCalculation calculation,
                                                            AggregatedNode[] nodes, int[] order,
                                                            boolean[] pendingBoundaries,
                                                            long[] remainingRequests) {
        int nodeIndex = findFirstPendingBoundary(order, pendingBoundaries);
        AggregatedNode node = nodes[nodeIndex];
        calculation.gtlcore$propagateMaxFastBoundaryFailure(getBoundaryFailureDependencies(nodeIndex));
        return new MaxFastCraftBranchFailure(
                node.key(),
                NumberUtils.saturatedMultiply(node.amount(), remainingRequests[nodeIndex]));
    }

    private static long extractAggregatedTemplates(ICraftingTreeNode root, CraftingSimulationState inv,
                                                   AggregatedNode node, long requestedAmount,
                                                   MaxFastMetrics metrics) {
        long remaining = requestedAmount;
        IPatternDetails.IInput input = node.parentInput();
        InputTemplate structuralExactTemplate = node.structuralExactTemplate();
        if (structuralExactTemplate != null) {
            if (input != null) {
                metrics.recordAggregationExactTemplateExtraction();
            }
            return extractTemplate(inv, structuralExactTemplate, requestedAmount);
        }

        for (InputTemplate template : getAggregatedTemplates(root, inv, node)) {
            long extracted = extractTemplate(inv, template, remaining);
            remaining -= extracted;
            if (remaining == 0) {
                break;
            }
        }
        return requestedAmount - remaining;
    }

    private static long extractTemplate(CraftingSimulationState inv, InputTemplate template, long requestedAmount) {
        if (requestedAmount <= 0) {
            return 0L;
        }
        if (template.amount() == 1L) {
            return inv.extract(template.key(), requestedAmount, Actionable.MODULATE);
        }
        return CraftingCpuHelper.extractTemplates(inv, template, requestedAmount);
    }

    private static void completeAggregatedPattern(CraftingSimulationState inv, AggregatedNode node, long times) {
        for (GenericStack output : node.outputs()) {
            inv.insert(
                    output.what(),
                    NumberUtils.saturatedMultiply(output.amount(), times),
                    Actionable.MODULATE);
        }
        inv.addCrafting(node.details(), times);
        inv.addBytes(times);
    }

    private void ensureAggregatedScratchCapacity(int requiredCapacity) {
        if (this.aggregatedRequests.length >= requiredCapacity) {
            return;
        }

        this.aggregatedRequests = new long[requiredCapacity];
        this.aggregatedRemainingRequests = new long[requiredCapacity];
        this.aggregatedTotalRequestedItems = new long[requiredCapacity];
        this.aggregatedProcessTimes = new long[requiredCapacity];
        this.aggregatedCompletedNodes = new boolean[requiredCapacity];
        this.aggregatedPendingBoundaries = new boolean[requiredCapacity];
        this.aggregatedOutstandingChildren = new int[requiredCapacity];
        this.aggregatedBoundaryFailures = new BoundaryFailureDependencies[requiredCapacity];
        this.aggregatedPreparedBoundaries = new ICraftingTreeNode[requiredCapacity];
    }

    private BoundaryFailureDependencies getBoundaryFailureDependencies(int nodeIndex) {
        BoundaryFailureDependencies dependencies = this.aggregatedBoundaryFailures[nodeIndex];
        if (dependencies == null) {
            dependencies = new BoundaryFailureDependencies();
            this.aggregatedBoundaryFailures[nodeIndex] = dependencies;
        }
        return dependencies;
    }

    private static void indexBoundaryDependencies(
                                                  int nodeIndex,
                                                  BoundaryFailureDependencies dependencies,
                                                  Map<Object, List<Integer>> dependencyIndex,
                                                  List<Integer> unknownBoundaries) {
        if (dependencies.unknown || dependencies.capacityTargets.isEmpty() || dependencies.rawRequirements.isEmpty()) {
            unknownBoundaries.add(nodeIndex);
            return;
        }

        Set<Object> indexedKeys = new HashSet<>();
        for (BoundaryFailureRequirement requirement : dependencies.rawRequirements) {
            IPatternDetails.IInput input = requirement.input();
            if (input == null) {
                indexedKeys.add(requirement.key().getPrimaryKey());
                continue;
            }
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                unknownBoundaries.add(nodeIndex);
                return;
            }
            for (GenericStack possibleInput : possibleInputs) {
                indexedKeys.add(possibleInput.what().getPrimaryKey());
            }
        }
        if (indexedKeys.isEmpty()) {
            unknownBoundaries.add(nodeIndex);
            return;
        }
        for (Object primaryKey : indexedKeys) {
            dependencyIndex.computeIfAbsent(primaryKey, ignored -> new ArrayList<>()).add(nodeIndex);
        }
    }

    private void executeStack(ICraftingTreeNode root, CraftingSimulationState inv, long requestedAmount,
                              @Nullable KeyCounter containerItems, boolean tryRootSegment, MaxFastMetrics metrics)
                                                                                                                   throws CraftBranchFailure,
                                                                                                                   InterruptedException {
        metrics.beginExecutor();
        long startedNanos = System.nanoTime();
        long executedFrames = 0L;
        this.stack.clear();
        this.stack.push(
                FRAME_NODE_ENTER,
                root,
                requestedAmount,
                tryRootSegment ? TRY_SEGMENT_AGGREGATION : SKIP_SEGMENT_AGGREGATION);
        boolean rootNodePending = true;

        try {
            while (!this.stack.isEmpty()) {
                executedFrames++;
                int frame = this.stack.popIndex();
                byte kind = this.stack.kind(frame);
                Object reference = this.stack.takeReference(frame);
                long valueA = this.stack.valueA(frame);
                long valueB = this.stack.valueB(frame);

                switch (kind) {
                    case FRAME_NODE_ENTER -> {
                        ICraftingTreeNode node = (ICraftingTreeNode) reference;
                        KeyCounter nodeContainerItems = rootNodePending ? containerItems : null;
                        rootNodePending = false;
                        runNodeEnter(
                                node,
                                inv,
                                valueA,
                                nodeContainerItems,
                                valueB == TRY_SEGMENT_AGGREGATION,
                                metrics);
                    }
                    case FRAME_RUN_SINGLE -> runSingle((MaxFastNodeProgram) reference, valueA, valueB, metrics);
                    case FRAME_PROCESS_FINISH -> ((ICraftingTreeProcess) reference)
                            .gtlcore$completeMaxFast(inv, valueA);
                    case FRAME_NODE_AFTER_PROCESS -> runNodeAfterProcess(
                            (MaxFastNodeProgram) reference,
                            inv,
                            valueA,
                            valueB);
                    case FRAME_PROCESS_CHILDREN -> runProcessChildren(
                            (MaxFastNodeProgram) reference,
                            valueA,
                            (int) valueB,
                            metrics);
                    default -> throw new IllegalStateException("Unknown MAX_FAST execution frame: " + kind);
                }
            }
        } finally {
            metrics.recordExecutor(
                    System.nanoTime() - startedNanos,
                    executedFrames,
                    this.stack.highWaterMark());
            this.stack.clear();
        }
    }

    private void runNodeEnter(ICraftingTreeNode node, CraftingSimulationState inv, long requestedAmount,
                              @Nullable KeyCounter containerItems, boolean trySegment, MaxFastMetrics metrics)
                                                                                                               throws CraftBranchFailure,
                                                                                                               InterruptedException {
        metrics.recordNodeEnter();
        long remainingRequestCount = node.gtlcore$runMaxFastPrefix(inv, requestedAmount, containerItems);
        if (remainingRequestCount == 0) {
            node.gtlcore$setMaxFastLogicalNodeCount(1L);
            metrics.recordPrefixSatisfied();
            return;
        }

        if (trySegment && node.gtlcore$tryMaxFastAggregation(inv, remainingRequestCount, metrics)) {
            return;
        }

        MaxFastNodeProgram program = node.gtlcore$getOrCreateMaxFastProgram(metrics);
        node.gtlcore$checkMaxFastCancellation();
        long totalRequestedItems = NumberUtils.saturatedMultiply(remainingRequestCount, program.nodeAmount());

        switch (program.kind()) {
            case SINGLE_PATH -> {
                metrics.recordSinglePathExecution();
                this.stack.push(
                        FRAME_RUN_SINGLE,
                        program,
                        totalRequestedItems,
                        remainingRequestCount);
            }
            case TERMINAL -> {
                metrics.recordTerminalExecution();
                node.gtlcore$reportMaxFastMissing(totalRequestedItems);
            }
            case BASELINE_TAIL -> {
                long startedNanos = System.nanoTime();
                try {
                    node.gtlcore$runUltraFastTail(inv, totalRequestedItems, remainingRequestCount);
                } finally {
                    metrics.recordBaselineExecution(System.nanoTime() - startedNanos);
                }
            }
        }
    }

    private void runSingle(MaxFastNodeProgram program, long totalRequestedItems, long requestedAmount,
                           MaxFastMetrics metrics)
                                                   throws CraftBranchFailure,
                                                   InterruptedException {
        ICraftingTreeNode owner = program.owner();
        owner.gtlcore$checkMaxFastCancellation();
        ICraftingTreeProcess process = program.process();
        if (!process.getPossible() || totalRequestedItems <= 0) {
            owner.gtlcore$reportMaxFastMissing(totalRequestedItems);
            return;
        }

        long times = ceilDiv(totalRequestedItems, program.outputPerPattern());
        metrics.recordCompiledProcess(times);
        this.stack.push(FRAME_NODE_AFTER_PROCESS, program, totalRequestedItems, requestedAmount);
        this.stack.push(FRAME_PROCESS_FINISH, process, times, 0);
        this.stack.push(FRAME_PROCESS_CHILDREN, program, times, 0);
    }

    private void runProcessChildren(MaxFastNodeProgram program, long times, int childIndex, MaxFastMetrics metrics) {
        if (childIndex >= program.childCount()) {
            return;
        }

        metrics.recordChildRequest();
        this.stack.push(FRAME_PROCESS_CHILDREN, program, times, childIndex + 1L);
        long childRequestedAmount = NumberUtils.saturatedMultiply(program.childMultiplier(childIndex), times);
        this.stack.push(
                FRAME_NODE_ENTER,
                (ICraftingTreeNode) program.childNode(childIndex),
                childRequestedAmount,
                SKIP_SEGMENT_AGGREGATION);
    }

    private void runNodeAfterProcess(MaxFastNodeProgram program, CraftingSimulationState inv,
                                     long totalRequestedItems, long requestedAmount) throws CraftBranchFailure {
        ICraftingTreeNode owner = program.owner();
        long available = owner.gtlcore$extractMaxFastOutput(inv, totalRequestedItems);
        if (available == 0) {
            long times = ceilDiv(totalRequestedItems, program.outputPerPattern());
            owner.gtlcore$throwMaxFastMissingOutput(
                    (appeng.crafting.CraftingTreeProcess) program.process(),
                    totalRequestedItems,
                    requestedAmount,
                    times);
            return;
        }

        long remaining = totalRequestedItems - available;
        if (remaining > 0) {
            this.stack.push(FRAME_RUN_SINGLE, program, remaining, requestedAmount);
        }
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0) {
            return 0;
        }
        if (divisor <= 1) {
            return value;
        }
        return 1 + (value - 1) / divisor;
    }

    private enum BoundaryResolutionMode {
        NONE,
        STRICT,
        REPORT_MISSING
    }

    public static final class BoundaryFailureDependencies {

        private final List<BoundaryFailureRequirement> rawRequirements = new ArrayList<>();
        private final List<BoundaryCapacityTarget> capacityTargets = new ArrayList<>();
        private boolean unknown;

        public void reset() {
            this.rawRequirements.clear();
            this.capacityTargets.clear();
            this.unknown = false;
        }

        public void record(AEKey key, long templateAmount,
                           @Nullable IPatternDetails.IInput input, long missingTemplates) {
            if (key == null || templateAmount <= 0 || missingTemplates <= 0) {
                markUnknown();
                return;
            }
            BoundaryFailureRequirement requirement = new BoundaryFailureRequirement(
                    key,
                    templateAmount,
                    input,
                    missingTemplates);
            for (int i = 0; i < this.rawRequirements.size(); i++) {
                BoundaryFailureRequirement existing = this.rawRequirements.get(i);
                if (existing.sameDescriptor(requirement)) {
                    if (missingTemplates < existing.missingTemplates()) {
                        this.rawRequirements.set(i, requirement);
                    }
                    return;
                }
            }
            this.rawRequirements.add(requirement);
        }

        public void markUnknown() {
            this.unknown = true;
        }

        public boolean hasRawFailures() {
            return this.unknown || !this.rawRequirements.isEmpty();
        }

        public void addRawFrom(BoundaryFailureDependencies child) {
            this.unknown |= child.unknown;
            for (BoundaryFailureRequirement requirement : child.rawRequirements) {
                record(
                        requirement.key(),
                        requirement.templateAmount(),
                        requirement.input(),
                        requirement.missingTemplates());
            }
        }

        private boolean isUnknown() {
            return this.unknown || this.rawRequirements.isEmpty() || this.capacityTargets.isEmpty();
        }

        private @Nullable BoundaryFailureDependencies copyWithCapacityTargets(
                                                                              ICraftingTreeNode root, CraftingSimulationState parentInventory) {
            BoundaryFailureDependencies copy = new BoundaryFailureDependencies();
            copy.addRawFrom(this);
            copy.bindCapacityTargets(root, parentInventory);
            return copy.isUnknown() ? null : copy;
        }

        private boolean shouldRetry(ICraftingTreeNode root, CraftingSimulationState inventory) {
            if (isUnknown()) {
                return true;
            }

            for (BoundaryCapacityTarget target : this.capacityTargets) {
                long available = countAvailableTemplates(root, inventory, target.requirement());
                if (available < 0) {
                    this.unknown = true;
                    return true;
                }
                if (available >= target.requiredCapacity()) {
                    return true;
                }
            }
            return false;
        }

        private void bindCapacityTargets(ICraftingTreeNode root, CraftingSimulationState parentInventory) {
            this.capacityTargets.clear();
            if (this.unknown || this.rawRequirements.isEmpty()) {
                return;
            }

            for (BoundaryFailureRequirement requirement : this.rawRequirements) {
                long baseline = countAvailableTemplates(root, parentInventory, requirement);
                if (baseline < 0) {
                    this.unknown = true;
                    this.capacityTargets.clear();
                    return;
                }
                this.capacityTargets.add(new BoundaryCapacityTarget(
                        requirement,
                        NumberUtils.saturatedAdd(baseline, requirement.missingTemplates())));
            }
        }

        private boolean shouldRetry(ICraftingTreeNode root, CraftingSimulationState inventory,
                                    Set<Object> changedPrimaryKeys) {
            if (isUnknown()) {
                return true;
            }

            for (BoundaryCapacityTarget target : this.capacityTargets) {
                BoundaryFailureRequirement requirement = target.requirement();
                if (!matchesChangedPrimaryKey(requirement, changedPrimaryKeys)) {
                    continue;
                }
                long available = countAvailableTemplates(root, inventory, requirement);
                if (available < 0) {
                    this.unknown = true;
                    return true;
                }
                if (available >= target.requiredCapacity()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesChangedPrimaryKey(BoundaryFailureRequirement requirement,
                                                        Set<Object> changedPrimaryKeys) {
            IPatternDetails.IInput input = requirement.input();
            if (input == null) {
                return changedPrimaryKeys.contains(requirement.key().getPrimaryKey());
            }

            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                return true;
            }
            for (GenericStack possibleInput : possibleInputs) {
                if (changedPrimaryKeys.contains(possibleInput.what().getPrimaryKey())) {
                    return true;
                }
            }
            return false;
        }

        private static long countAvailableTemplates(ICraftingTreeNode root,
                                                    CraftingSimulationState inventory,
                                                    BoundaryFailureRequirement requirement) {
            IPatternDetails.IInput input = requirement.input();
            if (input == null) {
                long available = inventory.extract(requirement.key(), Long.MAX_VALUE, Actionable.SIMULATE);
                return available / requirement.templateAmount();
            }

            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                return -1L;
            }

            Level level = root.gtlcore$getMaxFastLevel();
            Iterable<InputTemplate> templates = root.gtlcore$getMaxFastCalculation().gtlcore$getCachedTemplates(
                    inventory,
                    input,
                    level,
                    requirement.key(),
                    () -> CraftingCpuHelper.getValidItemTemplates(inventory, input, level));
            Map<AEKey, Long> remainingByKey = new HashMap<>();
            long availableTemplates = 0L;
            for (InputTemplate template : templates) {
                if (template.amount() <= 0) {
                    return -1L;
                }
                long remainingItems = remainingByKey.computeIfAbsent(
                        template.key(),
                        key -> inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
                long templateCount = remainingItems / template.amount();
                if (templateCount == 0) {
                    continue;
                }
                availableTemplates = NumberUtils.saturatedAdd(availableTemplates, templateCount);
                remainingByKey.put(
                        template.key(),
                        remainingItems - templateCount * template.amount());
            }
            return availableTemplates;
        }
    }

    private record BoundaryFailureRequirement(AEKey key, long templateAmount,
                                              @Nullable IPatternDetails.IInput input,
                                              long missingTemplates) {

        private boolean sameDescriptor(BoundaryFailureRequirement other) {
            return this.key.equals(other.key) &&
                    this.templateAmount == other.templateAmount &&
                    this.input == other.input;
        }
    }

    private record BoundaryCapacityTarget(BoundaryFailureRequirement requirement, long requiredCapacity) {}

    private record AggregatedGraph(AggregatedNode[] nodes, int[] topologicalOrder, int[] topologicalRanks,
                                   int[][] parentIndexes, long logicalNodeCount,
                                   boolean boundaryTransactionGuard,
                                   boolean onlyStructuralExactInputs) {}

    private record GraphCompilation(@Nullable AggregatedGraph graph,
                                    @Nullable AggregationFallbackReason failureReason,
                                    @Nullable AEKey failureKey,
                                    int patternCandidates,
                                    int scannedNodes,
                                    ContextSignature contextSignature) {

        private static GraphCompilation success(AggregatedGraph graph) {
            return new GraphCompilation(graph, null, null, -1, graph.nodes().length, ContextSignature.EMPTY);
        }

        private static GraphCompilation failure(AggregationFallbackReason reason, @Nullable AEKey failureKey,
                                                int patternCandidates, int scannedNodes) {
            return new GraphCompilation(
                    null,
                    reason,
                    failureKey,
                    patternCandidates,
                    scannedNodes,
                    ContextSignature.EMPTY);
        }

        private GraphCompilation withContextSignature(ContextSignature contextSignature) {
            return new GraphCompilation(
                    this.graph,
                    this.failureReason,
                    this.failureKey,
                    this.patternCandidates,
                    this.scannedNodes,
                    contextSignature);
        }
    }

    public static final class CompilationCache {

        private final Map<AEKey, AnalyzedProgram> analyzedPrograms = new HashMap<>();
        private final Map<GraphCacheKey, GraphCompilation> graphs = new HashMap<>();
        private final Map<GraphBaseKey, List<GraphCompilation>> graphVariants = new HashMap<>();
        private final Map<CycleFailureKey, BoundaryFailureDependencies> cycleFailures = new HashMap<>();
        private final Set<AggregatedGraph> validatedStructuralExactGraphs = Collections.newSetFromMap(new IdentityHashMap<>());

        public void clear() {
            this.analyzedPrograms.clear();
            this.graphs.clear();
            this.graphVariants.clear();
            this.cycleFailures.clear();
            this.validatedStructuralExactGraphs.clear();
        }
    }

    private static final class CycleFailureKey {

        private final AEKey key;
        private final long amount;
        private final IPatternDetails pattern;
        private final long requestedAmount;
        private final AEKey[] externalAncestors;
        private final int hash;

        private CycleFailureKey(AEKey key, long amount, IPatternDetails pattern,
                                long requestedAmount, AEKey[] externalAncestors) {
            this.key = key;
            this.amount = amount;
            this.pattern = pattern;
            this.requestedAmount = requestedAmount;
            this.externalAncestors = externalAncestors.length == 0 ?
                    EMPTY_EXTERNAL_ANCESTORS : externalAncestors.clone();
            this.hash = 31 * (31 * (31 * (31 * key.hashCode() + Long.hashCode(amount)) +
                    System.identityHashCode(pattern)) + Long.hashCode(requestedAmount)) +
                    Arrays.hashCode(this.externalAncestors);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CycleFailureKey other)) {
                return false;
            }
            return this.amount == other.amount && this.requestedAmount == other.requestedAmount &&
                    this.key.equals(other.key) && this.pattern == other.pattern &&
                    Arrays.equals(this.externalAncestors, other.externalAncestors);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class BoundaryRuntimeKey {

        private final RequestKey requestKey;
        private final IPatternDetails[] barrierPatterns;
        private final AEKey[] externalAncestors;
        private final boolean candidateGraphsEligible;
        private final boolean cycleCandidateGraphEligible;
        private final int hash;

        private BoundaryRuntimeKey(RequestKey requestKey, IPatternDetails[] barrierPatterns,
                                   AEKey[] externalAncestors, boolean candidateGraphsEligible,
                                   boolean cycleCandidateGraphEligible) {
            this.requestKey = requestKey;
            this.barrierPatterns = barrierPatterns.clone();
            this.externalAncestors = externalAncestors.length == 0 ?
                    EMPTY_EXTERNAL_ANCESTORS : externalAncestors.clone();
            this.candidateGraphsEligible = candidateGraphsEligible;
            this.cycleCandidateGraphEligible = cycleCandidateGraphEligible;
            this.hash = 31 * (31 * (31 * (31 * requestKey.hashCode() + Arrays.hashCode(this.barrierPatterns)) +
                    Arrays.hashCode(this.externalAncestors)) + Boolean.hashCode(candidateGraphsEligible)) +
                    Boolean.hashCode(cycleCandidateGraphEligible);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BoundaryRuntimeKey other)) {
                return false;
            }
            return this.candidateGraphsEligible == other.candidateGraphsEligible &&
                    this.cycleCandidateGraphEligible == other.cycleCandidateGraphEligible &&
                    this.requestKey.equals(other.requestKey) &&
                    Arrays.equals(this.barrierPatterns, other.barrierPatterns) &&
                    Arrays.equals(this.externalAncestors, other.externalAncestors);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class GraphBaseKey {

        private final AEKey key;
        private final long amount;
        private final @Nullable IPatternDetails forcedRootPattern;
        private final boolean forcedRootAncestorCut;
        private final int hash;

        private GraphBaseKey(AEKey key, long amount, @Nullable IPatternDetails forcedRootPattern,
                             boolean forcedRootAncestorCut) {
            this.key = key;
            this.amount = amount;
            this.forcedRootPattern = forcedRootPattern;
            this.forcedRootAncestorCut = forcedRootAncestorCut;
            this.hash = 31 * (31 * (31 * key.hashCode() + Long.hashCode(amount)) +
                    System.identityHashCode(forcedRootPattern)) + Boolean.hashCode(forcedRootAncestorCut);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof GraphBaseKey other)) {
                return false;
            }
            return this.amount == other.amount && this.forcedRootAncestorCut == other.forcedRootAncestorCut &&
                    this.key.equals(other.key) &&
                    this.forcedRootPattern == other.forcedRootPattern;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class GraphCacheKey {

        private final AEKey key;
        private final long amount;
        private final @Nullable IPatternDetails forcedRootPattern;
        private final boolean forcedRootAncestorCut;
        private final AEKey[] externalAncestors;
        private final int hash;

        private GraphCacheKey(AEKey key, long amount, @Nullable IPatternDetails forcedRootPattern,
                              boolean forcedRootAncestorCut,
                              AEKey[] externalAncestors) {
            this.key = key;
            this.amount = amount;
            this.forcedRootPattern = forcedRootPattern;
            this.forcedRootAncestorCut = forcedRootAncestorCut;
            this.externalAncestors = externalAncestors.length == 0 ?
                    EMPTY_EXTERNAL_ANCESTORS : externalAncestors.clone();
            this.hash = 31 * (31 * (31 * (31 * key.hashCode() + Long.hashCode(amount)) +
                    System.identityHashCode(forcedRootPattern)) + Boolean.hashCode(forcedRootAncestorCut)) +
                    Arrays.hashCode(this.externalAncestors);
        }

        private GraphBaseKey baseKey() {
            return new GraphBaseKey(
                    this.key,
                    this.amount,
                    this.forcedRootPattern,
                    this.forcedRootAncestorCut);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof GraphCacheKey other)) {
                return false;
            }
            return this.amount == other.amount && this.forcedRootAncestorCut == other.forcedRootAncestorCut &&
                    this.key.equals(other.key) &&
                    this.forcedRootPattern == other.forcedRootPattern &&
                    Arrays.equals(this.externalAncestors, other.externalAncestors);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private static final class ContextDecisionCollector {

        private final IdentityHashMap<IPatternDetails, Boolean> decisions = new IdentityHashMap<>();

        private void record(IPatternDetails details, boolean allowed) {
            this.decisions.put(details, allowed);
        }

        private ContextSignature freeze() {
            if (this.decisions.isEmpty()) {
                return ContextSignature.EMPTY;
            }

            IPatternDetails[] patterns = new IPatternDetails[this.decisions.size()];
            boolean[] allowed = new boolean[patterns.length];
            int index = 0;
            for (Map.Entry<IPatternDetails, Boolean> entry : this.decisions.entrySet()) {
                patterns[index] = entry.getKey();
                allowed[index] = entry.getValue();
                index++;
            }
            return new ContextSignature(patterns, allowed);
        }
    }

    private static final class ContextSignature {

        private static final ContextSignature EMPTY = new ContextSignature(new IPatternDetails[0], new boolean[0]);

        private final IPatternDetails[] patterns;
        private final boolean[] allowed;

        private ContextSignature(IPatternDetails[] patterns, boolean[] allowed) {
            this.patterns = patterns;
            this.allowed = allowed;
        }

        private boolean matches(ICraftingTreeNode root) {
            for (int index = 0; index < this.patterns.length; index++) {
                if (root.gtlcore$isMaxFastPatternContextAllowed(this.patterns[index]) != this.allowed[index]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class RequestKey {

        private final AEKey key;
        private final long amount;
        private final IPatternDetails.IInput input;
        private final boolean structuralExactTemplate;
        private final int hash;

        private RequestKey(AEKey key, long amount, @Nullable IPatternDetails.IInput input) {
            this(key, amount, input, false);
        }

        private RequestKey(AEKey key, long amount, @Nullable IPatternDetails.IInput input,
                           boolean structuralExactTemplate) {
            this.key = key;
            this.amount = amount;
            this.input = input;
            this.structuralExactTemplate = structuralExactTemplate;
            this.hash = 31 * (31 * (31 * key.hashCode() + Long.hashCode(amount)) +
                    System.identityHashCode(input)) + Boolean.hashCode(structuralExactTemplate);
        }

        private AEKey key() {
            return this.key;
        }

        private long amount() {
            return this.amount;
        }

        private @Nullable IPatternDetails.IInput input() {
            return this.input;
        }

        private boolean structuralExactTemplate() {
            return this.structuralExactTemplate;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RequestKey other)) {
                return false;
            }
            return this.amount == other.amount && this.input == other.input &&
                    this.structuralExactTemplate == other.structuralExactTemplate && this.key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private record AnalyzedProgram(boolean prefixOnly,
                                   boolean terminal,
                                   List<CandidateAnalysis> candidates,
                                   @Nullable AggregationFallbackReason failureReason,
                                   int patternCandidates) {

        private static AnalyzedProgram emitterProgram() {
            return new AnalyzedProgram(true, false, List.of(), null, 0);
        }

        private static AnalyzedProgram terminalProgram() {
            return new AnalyzedProgram(false, true, List.of(), null, 0);
        }

        private static AnalyzedProgram candidateProgram(List<CandidateAnalysis> candidates) {
            return new AnalyzedProgram(false, false, List.copyOf(candidates), null, candidates.size());
        }

        private static AnalyzedProgram failure(AggregationFallbackReason reason, int patternCandidates) {
            return new AnalyzedProgram(false, false, List.of(), reason, patternCandidates);
        }
    }

    private record CandidateAnalysis(IPatternDetails details,
                                     @Nullable GenericStack[] outputs,
                                     long outputPerPattern,
                                     boolean aggregateSafe,
                                     @Nullable List<AnalyzedEdge> edges,
                                     @Nullable AggregationFallbackReason failureReason) {

        private static CandidateAnalysis success(IPatternDetails details, GenericStack[] outputs,
                                                 long outputPerPattern, boolean aggregateSafe,
                                                 List<AnalyzedEdge> edges) {
            return new CandidateAnalysis(details, outputs, outputPerPattern, aggregateSafe, List.copyOf(edges), null);
        }

        private static CandidateAnalysis failure(IPatternDetails details, AggregationFallbackReason reason) {
            return new CandidateAnalysis(details, null, 0L, false, null, reason);
        }
    }

    private static final class AnalyzedEdge {

        private final RequestKey childRequestKey;
        private final long requestMultiplier;
        private final int occurrences;

        private AnalyzedEdge(RequestKey childRequestKey, long requestMultiplier, int occurrences) {
            this.childRequestKey = childRequestKey;
            this.requestMultiplier = requestMultiplier;
            this.occurrences = occurrences;
        }

        private RequestKey childRequestKey() {
            return this.childRequestKey;
        }

        private long requestMultiplier() {
            return this.requestMultiplier;
        }

        private int occurrences() {
            return this.occurrences;
        }
    }

    private static final class AnalyzedNode {

        private final int index;
        private final RequestKey requestKey;
        private AnalyzedProgram program;

        private AnalyzedNode(int index, RequestKey requestKey) {
            this.index = index;
            this.requestKey = requestKey;
        }

        private int index() {
            return this.index;
        }

        private RequestKey requestKey() {
            return this.requestKey;
        }

        private AEKey key() {
            return this.requestKey.key();
        }

        private List<CandidateAnalysis> candidates() {
            return this.program.candidates();
        }

        private void applyProgram(AnalyzedProgram program) {
            this.program = program;
        }

        private boolean prefixOnly() {
            return this.program.prefixOnly();
        }

        private boolean terminal() {
            return this.program.terminal();
        }
    }

    private record SccAnalysis(boolean[] cyclicNodes, SccProgram[] programsByNode) {}

    private record SccProgram(SccNode[] nodes,
                              Map<AEKey, Integer> nodeIndexes,
                              int[][] consumersByNode,
                              SccRuntimeGate runtimeGate,
                              int internalEdges) {

        private int nodeIndex(AEKey key) {
            return this.nodeIndexes.getOrDefault(key, -1);
        }

        private boolean hasExternalInputs() {
            for (SccNode node : this.nodes) {
                for (SccInput input : node.inputs()) {
                    if (input.internalNodeIndex() < 0) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class SccRuntimeGate {

        private static final int MAX_EXTERNAL_DEFERRALS = 1;
        private boolean disabled;
        private int externalDeferrals;

        private boolean disabled() {
            return this.disabled;
        }

        private void disable() {
            this.disabled = true;
        }

        private boolean tryAcquireExternalDeferral() {
            if (this.disabled || this.externalDeferrals >= MAX_EXTERNAL_DEFERRALS) {
                return false;
            }
            this.externalDeferrals++;
            return true;
        }
    }

    private record SccNode(AEKey key,
                           IPatternDetails details,
                           GenericStack[] outputs,
                           long outputPerPattern,
                           SccInput[] inputs) {}

    private record SccInput(RequestKey requestKey,
                            AEKey key,
                            long amountPerPattern,
                            int internalNodeIndex,
                            @Nullable IPatternDetails.IInput sourceInput) {}

    private static final class AggregatedNode {

        private final int index;
        private final AEKey key;
        private final long amount;
        private final IPatternDetails.IInput parentInput;
        private final RequestKey requestKey;
        private final @Nullable InputTemplate structuralExactTemplate;
        private List<AggregatedEdge> edges = List.of();
        private IPatternDetails details;
        private GenericStack[] outputs;
        private long outputPerPattern;
        private int indegree;
        private boolean prefixOnly;
        private boolean terminal;
        private boolean barrier;
        private IPatternDetails[] barrierPatterns;
        private boolean candidateGraphsEligible;
        private boolean cycleCandidateGraphEligible;
        private @Nullable SccProgram sccProgram;
        private boolean sccExternalDependenciesEligible;

        private AggregatedNode(int index, RequestKey requestKey) {
            this.index = index;
            this.key = requestKey.key();
            this.amount = requestKey.amount();
            this.parentInput = requestKey.input();
            this.requestKey = requestKey;
            this.structuralExactTemplate = this.parentInput == null || requestKey.structuralExactTemplate() ?
                    new InputTemplate(this.key, this.amount) : null;
        }

        private int index() {
            return this.index;
        }

        private AEKey key() {
            return this.key;
        }

        private long amount() {
            return this.amount;
        }

        private @Nullable IPatternDetails.IInput parentInput() {
            return this.parentInput;
        }

        private @Nullable InputTemplate structuralExactTemplate() {
            return this.structuralExactTemplate;
        }

        private RequestKey requestKey() {
            return this.requestKey;
        }

        private boolean acceptsTemplate(AEKey templateKey, Level level) {
            return this.parentInput == null ? this.key.equals(templateKey) : this.parentInput.isValid(templateKey, level);
        }

        private List<AggregatedEdge> edges() {
            return this.edges;
        }

        private void addEdge(AggregatedEdge edge) {
            if (this.edges.isEmpty()) {
                this.edges = new ArrayList<>();
            }
            this.edges.add(edge);
        }

        private boolean addEdgeIfAbsent(int childIndex) {
            for (AggregatedEdge edge : this.edges()) {
                if (edge.childIndex() == childIndex) {
                    return false;
                }
            }
            this.addEdge(new AggregatedEdge(childIndex, 1L, 1));
            return true;
        }

        private IPatternDetails details() {
            return this.details;
        }

        private GenericStack[] outputs() {
            return this.outputs;
        }

        private long outputPerPattern() {
            return this.outputPerPattern;
        }

        private void setProgram(IPatternDetails details, GenericStack[] outputs, long outputPerPattern) {
            this.details = details;
            this.outputs = outputs;
            this.outputPerPattern = outputPerPattern;
        }

        private int indegree() {
            return this.indegree;
        }

        private void incrementIndegree() {
            this.indegree++;
        }

        private boolean prefixOnly() {
            return this.prefixOnly;
        }

        private void setPrefixOnly() {
            this.prefixOnly = true;
        }

        private boolean terminal() {
            return this.terminal;
        }

        private void setTerminal() {
            this.terminal = true;
        }

        private boolean barrier() {
            return this.barrier;
        }

        private IPatternDetails[] barrierPatterns() {
            return this.barrierPatterns;
        }

        private boolean candidateGraphsEligible() {
            return this.candidateGraphsEligible;
        }

        private boolean cycleCandidateGraphEligible() {
            return this.cycleCandidateGraphEligible;
        }

        private @Nullable SccProgram sccProgram() {
            return this.sccProgram;
        }

        private boolean sccExternalDependenciesEligible() {
            return this.sccExternalDependenciesEligible;
        }

        private void setBarrier(List<CandidateAnalysis> candidates, boolean candidateGraphsEligible,
                                boolean cycleCandidateGraphEligible) {
            this.barrier = true;
            this.candidateGraphsEligible = candidateGraphsEligible;
            this.cycleCandidateGraphEligible = cycleCandidateGraphEligible;
            this.barrierPatterns = new IPatternDetails[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                this.barrierPatterns[i] = candidates.get(i).details();
            }
        }

        private void setSccProgram(@Nullable SccProgram sccProgram) {
            this.sccProgram = sccProgram;
        }

        private void setSccExternalDependenciesEligible(boolean eligible) {
            this.sccExternalDependenciesEligible = eligible;
        }
    }

    private record AggregatedEdge(int childIndex, long requestMultiplier, int occurrences) {}

    private static final class EdgeAccumulator {

        private long requestMultiplier;
        private int occurrences = 1;

        private EdgeAccumulator(long requestMultiplier) {
            this.requestMultiplier = requestMultiplier;
        }

        private void add(long additionalMultiplier) {
            this.requestMultiplier = NumberUtils.saturatedAdd(this.requestMultiplier, additionalMultiplier);
            this.occurrences++;
        }

        private long requestMultiplier() {
            return this.requestMultiplier;
        }

        private int occurrences() {
            return this.occurrences;
        }
    }

    private static final class ExecutionStack {

        private byte[] kinds = new byte[INITIAL_FRAME_CAPACITY];
        private Object[] references = new Object[INITIAL_FRAME_CAPACITY];
        private long[] valuesA = new long[INITIAL_FRAME_CAPACITY];
        private long[] valuesB = new long[INITIAL_FRAME_CAPACITY];
        private int top;
        private int highWaterMark;

        private boolean isEmpty() {
            return this.top == 0;
        }

        private void push(byte kind, Object reference, long valueA, long valueB) {
            ensureCapacity(this.top + 1);
            int frame = this.top++;
            this.kinds[frame] = kind;
            this.references[frame] = reference;
            this.valuesA[frame] = valueA;
            this.valuesB[frame] = valueB;
            this.highWaterMark = Math.max(this.highWaterMark, this.top);
        }

        private int popIndex() {
            return --this.top;
        }

        private byte kind(int frame) {
            return this.kinds[frame];
        }

        private Object takeReference(int frame) {
            Object reference = this.references[frame];
            this.references[frame] = null;
            return reference;
        }

        private long valueA(int frame) {
            return this.valuesA[frame];
        }

        private long valueB(int frame) {
            return this.valuesB[frame];
        }

        private int highWaterMark() {
            return this.highWaterMark;
        }

        private void clear() {
            Arrays.fill(this.references, 0, this.highWaterMark, null);
            this.top = 0;
            this.highWaterMark = 0;
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= this.references.length) {
                return;
            }

            int currentCapacity = this.references.length;
            int grownCapacity = currentCapacity > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : currentCapacity * 2;
            int newCapacity = Math.max(requiredCapacity, grownCapacity);
            this.kinds = Arrays.copyOf(this.kinds, newCapacity);
            this.references = Arrays.copyOf(this.references, newCapacity);
            this.valuesA = Arrays.copyOf(this.valuesA, newCapacity);
            this.valuesB = Arrays.copyOf(this.valuesB, newCapacity);
        }
    }
}
