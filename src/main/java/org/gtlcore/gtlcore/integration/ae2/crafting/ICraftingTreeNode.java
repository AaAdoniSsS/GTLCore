package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastNodeProgram;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import org.jetbrains.annotations.Nullable;

public interface ICraftingTreeNode {

    final class MaxFastRuntimeTracker {

        private long generation;

        public long advance() {
            return ++this.generation;
        }

        public long current() {
            return this.generation;
        }
    }

    void ultraFastRequest(CraftingSimulationState inv, long requestedAmount,
                          @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void maxFastRequest(CraftingSimulationState inv, long requestedAmount,
                        @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void gtlcore$maxFastChildRequest(CraftingSimulationState inv, long requestedAmount,
                                     @Nullable KeyCounter containerItems) throws CraftBranchFailure,
                                                                          InterruptedException;

    void fastRequest(CraftingSimulationState inv, long requestedAmount,
                     @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void legacyRequest(CraftingSimulationState inv, long requestedAmount,
                       @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    long gtlcore$runMaxFastPrefix(CraftingSimulationState inv, long requestedAmount,
                                  @Nullable KeyCounter containerItems) throws InterruptedException;

    boolean gtlcore$tryMaxFastAggregation(CraftingSimulationState inv, long requestedAmount,
                                          MaxFastMetrics metrics) throws CraftBranchFailure, InterruptedException;

    boolean gtlcore$isMaxFastPatternContextAllowed(IPatternDetails details);

    ICraftingTreeNode gtlcore$prepareMaxFastBarrier(AEKey what, long amount,
                                                    IPatternDetails[] allowedPatterns,
                                                    AEKey[] externalAncestors,
                                                    boolean candidateGraphsEligible,
                                                    boolean cycleCandidateGraphEligible);

    long gtlcore$runPreparedMaxFastBarrier(CraftingSimulationState inv, ICraftingTreeNode boundary,
                                           long requestedAmount) throws CraftBranchFailure,
                                                                 InterruptedException;

    void gtlcore$beginMaxFastRuntimeAttempt();

    void gtlcore$activateMaxFastRuntime(MaxFastRuntimeTracker tracker);

    long gtlcore$runMaxFastBarrier(CraftingSimulationState inv, AEKey what, long amount,
                                   long requestedAmount, IPatternDetails[] allowedPatterns,
                                   AEKey[] externalAncestors,
                                   boolean cycleCandidateGraphEligible)
                                                                        throws CraftBranchFailure,
                                                                        InterruptedException;

    void gtlcore$setMaxFastBoundaryPatterns(IPatternDetails[] allowedPatterns,
                                            boolean candidateGraphsEligible,
                                            boolean cycleCandidateGraphEligible);

    void gtlcore$setMaxFastExternalAncestors(AEKey[] externalAncestors);

    AEKey[] gtlcore$getMaxFastExternalAncestors();

    @Nullable
    ICraftingTreeProcess gtlcore$getMaxFastParentProcess();

    AEKey[] gtlcore$getMaxFastAncestorKeys();

    long gtlcore$getMaxFastNodeCount();

    MaxFastNodeProgram gtlcore$getOrCreateMaxFastProgram(MaxFastMetrics metrics);

    void gtlcore$runUltraFastTail(CraftingSimulationState inv, long totalRequestedItems,
                                  long requestedAmount) throws CraftBranchFailure, InterruptedException;

    void gtlcore$checkMaxFastCancellation() throws InterruptedException;

    long gtlcore$extractMaxFastOutput(CraftingSimulationState inv, long totalRequestedItems);

    void gtlcore$reportMaxFastMissing(long totalRequestedItems) throws CraftBranchFailure;

    void gtlcore$reportMaxFastMissing(AEKey what, long totalRequestedItems) throws CraftBranchFailure;

    void gtlcore$reportMaxFastMissing(AEKey what, long totalRequestedItems,
                                      @Nullable IPatternDetails.IInput input,
                                      long templateAmount, long missingTemplates) throws CraftBranchFailure;

    void gtlcore$throwMaxFastMissingOutput(CraftingTreeProcess process, long totalRequestedItems,
                                           long requestedAmount, long times);

    void gtlcore$resetFastState();

    Object gtlcore$getRequestMergeKey();

    AEKey gtlcore$getMaxFastKey();

    long gtlcore$getMaxFastAmount();

    ICraftingService gtlcore$getMaxFastCraftingService();

    ICraftingCalculation gtlcore$getMaxFastCalculation();

    Level gtlcore$getMaxFastLevel();

    boolean gtlcore$isMaxFastSimulation();

    void gtlcore$setMaxFastLogicalNodeCount(long nodeCount);
}
