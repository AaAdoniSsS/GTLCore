package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingTemplateHelper.CalculationTemplateCacheKey;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingSimulationStateFastAccess;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;
import org.gtlcore.gtlcore.integration.ae2.crafting.IMaxFastCraftingProviderVersion;
import org.gtlcore.gtlcore.integration.ae2.crafting.IMaxFastNetworkInventoryFingerprint;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.BoundaryFailureDependencies;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.CompilationCache;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.google.common.base.Stopwatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.function.Supplier;

@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements ICraftingCalculation {

    @Unique
    private AE2CalculationMode gTLCore$calculationMode = AE2CalculationMode.MAX_FAST;
    @Unique
    private final Map<CalculationTemplateCacheKey, List<InputTemplate>> gTLCore$templateCache = new HashMap<>();
    @Unique
    private long gTLCore$templateValidationEpoch;
    @Unique
    private MaxFastMetrics gTLCore$maxFastMetrics;
    @Unique
    private CompilationCache gTLCore$maxFastCompilationCache;
    @Unique
    private int gTLCore$maxFastStrictBoundaryProbeDepth;
    @Unique
    private final ArrayDeque<KeyCounter> gTLCore$maxFastDeferredMissingScopes = new ArrayDeque<>();
    @Unique
    private int gTLCore$maxFastDeferredMissingCaptureDepth;
    @Unique
    private final ArrayDeque<BoundaryFailureDependencies> gTLCore$maxFastBoundaryFailureScopes = new ArrayDeque<>();

    @Shadow(remap = false)
    @Final
    private Object monitor;
    @Shadow(remap = false)
    @Final
    private Stopwatch watch;
    @Shadow(remap = false)
    @Final
    private Level level;
    @Shadow(remap = false)
    @Final
    private NetworkCraftingSimulationState networkInv;
    @Shadow(remap = false)
    @Final
    private CraftingTreeNode tree;
    @Shadow(remap = false)
    @Final
    private ICraftingSimulationRequester simRequester;
    @Shadow(remap = false)
    @Final
    private CalculationStrategy strategy;
    @Shadow(remap = false)
    @Final
    private AEKey output;
    @Shadow(remap = false)
    @Final
    private long requestedAmount;
    @Shadow(remap = false)
    private boolean running;
    @Shadow(remap = false)
    private boolean done;

    @Shadow(remap = false)
    private void logCraftingJob(ICraftingPlan plan) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private ICraftingPlan computePlan() throws InterruptedException {
        throw new AssertionError();
    }

    /**
     * @author Dragons
     * @reason 优化性能
     */
    @Overwrite(remap = false)
    public ICraftingPlan run() {
        ICraftingPlan plan = null;
        Throwable failure = null;
        try {
            this.gTLCore$calculationMode = AEUtils.getCalculationMode();
            if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
                this.gTLCore$maxFastMetrics = new MaxFastMetrics();
                this.gTLCore$maxFastCompilationCache = new CompilationCache();
                if (this.gTLCore$maxFastMetrics.isDiagnosticLoggingEnabled()) {
                    var gridNode = this.simRequester.getGridNode();
                    long craftingProviderVersionTick = gridNode == null ? 0L :
                            ((IMaxFastCraftingProviderVersion) gridNode.getGrid().getCraftingService())
                                    .gtlcore$getMaxFastCraftingProviderVersionTick();
                    this.gTLCore$maxFastMetrics.recordDiagnosticContext(
                            craftingProviderVersionTick,
                            ((IMaxFastNetworkInventoryFingerprint) this.networkInv)
                                    .gtlcore$getMaxFastInventoryFingerprint());
                }
                plan = gTLCore$computeMaxFastPlan();
            } else {
                plan = this.computePlan();
            }
            this.logCraftingJob(plan);
            return plan;
        } catch (Exception ex) {
            failure = ex;
            AELog.info(ex, "Exception during async crafting calculation.");
            throw new RuntimeException(ex);
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            if (this.gTLCore$maxFastMetrics != null) {
                this.gTLCore$maxFastMetrics.abortActiveAttempt();
                try {
                    this.gTLCore$maxFastMetrics.logSummary(
                            this.output,
                            this.requestedAmount,
                            this.strategy,
                            this.level,
                            plan,
                            failure);
                } catch (Throwable logFailure) {
                    try {
                        GTLCore.LOGGER.warn("Failed to write MAX_FAST calculation metrics", logFailure);
                    } catch (Throwable ignored) {
                        // Diagnostic logging must not prevent the calculation from being marked finished.
                    }
                }
            }
            this.finish();
        }
    }

    @Unique
    private ICraftingPlan gTLCore$computeMaxFastPlan() throws InterruptedException {
        long startedNanos = System.nanoTime();
        try {
            return this.computePlan();
        } finally {
            this.gTLCore$maxFastMetrics.recordCalculationNanos(System.nanoTime() - startedNanos);
        }
    }

    /**
     * @author Dragons
     * @reason 优化性能
     */
    @Overwrite(remap = false)
    private void finish() {
        synchronized (this.monitor) {
            this.running = false;
            this.done = true;
            if (this.watch.isRunning()) {
                this.watch.stop();
            }
            this.monitor.notifyAll();
        }
        this.gTLCore$clearAttemptState();
    }

    /**
     * @author Dragons
     * @reason 优化性能
     */
    @Overwrite(remap = false)
    public boolean simulateFor(int micros) {
        return !this.done;
    }

    @Inject(method = "handlePausing", at = @At("HEAD"), cancellable = true, remap = false)
    private void gTLCore$handleOriginalPausing(CallbackInfo ci) throws InterruptedException {
        this.gtlcore$handlePausing();
        ci.cancel();
    }

    @Override
    @Unique
    public void gtlcore$handlePausing() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    @Override
    @Unique
    public AE2CalculationMode gtlcore$getCalculationMode() {
        return this.gTLCore$calculationMode;
    }

    @Override
    @Unique
    public MaxFastMetrics gtlcore$getMaxFastMetrics() {
        return this.gTLCore$maxFastMetrics;
    }

    @Override
    @Unique
    public CompilationCache gtlcore$getMaxFastCompilationCache() {
        return this.gTLCore$maxFastCompilationCache;
    }

    @Override
    @Unique
    public void gtlcore$beginMaxFastStrictBoundaryProbe() {
        this.gTLCore$maxFastStrictBoundaryProbeDepth++;
    }

    @Override
    @Unique
    public void gtlcore$endMaxFastStrictBoundaryProbe() {
        if (this.gTLCore$maxFastStrictBoundaryProbeDepth <= 0) {
            throw new IllegalStateException("MAX_FAST strict boundary probe scope underflow");
        }
        this.gTLCore$maxFastStrictBoundaryProbeDepth--;
    }

    @Override
    @Unique
    public boolean gtlcore$isMaxFastStrictBoundaryProbeActive() {
        return this.gTLCore$maxFastStrictBoundaryProbeDepth > 0;
    }

    @Override
    @Unique
    public void gtlcore$beginMaxFastDeferredMissingScope() {
        this.gTLCore$maxFastDeferredMissingScopes.push(new KeyCounter());
    }

    @Override
    @Unique
    public void gtlcore$commitMaxFastDeferredMissingScope() {
        KeyCounter missing = gTLCore$popMaxFastDeferredMissingScope("commit");
        KeyCounter target = this.gTLCore$maxFastDeferredMissingScopes.peek();
        if (target == null) {
            target = ((CraftingCalculation) (Object) this).getMissingItems();
        }
        for (var entry : missing) {
            gTLCore$saturatedAdd(target, entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    @Unique
    public void gtlcore$discardMaxFastDeferredMissingScope() {
        gTLCore$popMaxFastDeferredMissingScope("discard");
    }

    @Override
    @Unique
    public void gtlcore$beginMaxFastDeferredMissingCapture() {
        if (this.gTLCore$maxFastDeferredMissingScopes.isEmpty()) {
            throw new IllegalStateException("MAX_FAST deferred missing capture requires a transaction scope");
        }
        this.gTLCore$maxFastDeferredMissingCaptureDepth++;
    }

    @Override
    @Unique
    public void gtlcore$endMaxFastDeferredMissingCapture() {
        if (this.gTLCore$maxFastDeferredMissingCaptureDepth <= 0) {
            throw new IllegalStateException("MAX_FAST deferred missing capture scope underflow");
        }
        this.gTLCore$maxFastDeferredMissingCaptureDepth--;
    }

    @Override
    @Unique
    public boolean gtlcore$isMaxFastDeferredMissingCaptureActive() {
        return this.gTLCore$maxFastDeferredMissingCaptureDepth > 0;
    }

    @Override
    @Unique
    public void gtlcore$recordMaxFastDeferredMissing(AEKey key, long amount) {
        KeyCounter missing = this.gTLCore$maxFastDeferredMissingScopes.peek();
        if (missing == null || !gtlcore$isMaxFastDeferredMissingCaptureActive()) {
            throw new IllegalStateException("MAX_FAST deferred missing was recorded outside a capture scope");
        }
        gTLCore$saturatedAdd(missing, key, amount);
    }

    @Override
    @Unique
    public void gtlcore$beginMaxFastBoundaryFailureScope(BoundaryFailureDependencies failureDependencies) {
        failureDependencies.reset();
        this.gTLCore$maxFastBoundaryFailureScopes.push(failureDependencies);
    }

    @Override
    @Unique
    public void gtlcore$endMaxFastBoundaryFailureScope() {
        BoundaryFailureDependencies failures = this.gTLCore$maxFastBoundaryFailureScopes.poll();
        if (failures == null) {
            throw new IllegalStateException("MAX_FAST boundary failure scope underflow");
        }
    }

    @Override
    @Unique
    public void gtlcore$propagateMaxFastBoundaryFailure(BoundaryFailureDependencies failureDependencies) {
        BoundaryFailureDependencies parent = this.gTLCore$maxFastBoundaryFailureScopes.peek();
        if (parent == null) {
            return;
        }
        if (!failureDependencies.hasRawFailures()) {
            parent.markUnknown();
        } else {
            parent.addRawFrom(failureDependencies);
        }
    }

    @Override
    @Unique
    public void gtlcore$recordMaxFastBoundaryFailure(AEKey key, long templateAmount,
                                                     IPatternDetails.IInput input, long missingTemplates) {
        BoundaryFailureDependencies failures = this.gTLCore$maxFastBoundaryFailureScopes.peek();
        if (failures != null) {
            failures.record(key, templateAmount, input, missingTemplates);
        }
    }

    @Override
    @Unique
    public void gtlcore$markMaxFastBoundaryFailureUnknown() {
        BoundaryFailureDependencies failures = this.gTLCore$maxFastBoundaryFailureScopes.peek();
        if (failures != null) {
            failures.markUnknown();
        }
    }

    @Unique
    private KeyCounter gTLCore$popMaxFastDeferredMissingScope(String operation) {
        KeyCounter missing = this.gTLCore$maxFastDeferredMissingScopes.poll();
        if (missing == null) {
            throw new IllegalStateException("Cannot " + operation + " a missing MAX_FAST deferred missing scope");
        }
        return missing;
    }

    @Unique
    private static void gTLCore$saturatedAdd(KeyCounter counter, AEKey key, long amount) {
        counter.set(key, NumberUtils.saturatedAdd(counter.get(key), amount));
    }

    @Override
    @Unique
    public Iterable<InputTemplate> gtlcore$getCachedTemplates(ICraftingInventory inv,
                                                              IPatternDetails.IInput input,
                                                              Level level,
                                                              AEKey what,
                                                              Supplier<Iterable<InputTemplate>> loader) {
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            if (inv instanceof ICraftingSimulationStateFastAccess state) {
                return state.gtlcore$getMaxFastTemplates(input, level, what,
                        this.gTLCore$templateValidationEpoch,
                        this.gTLCore$maxFastMetrics);
            }
            this.gTLCore$maxFastMetrics.recordTemplateFallback();
            return loader.get();
        }

        if (this.gTLCore$calculationMode == AE2CalculationMode.LEGACY) {
            return loader.get();
        }

        var key = new CalculationTemplateCacheKey(inv, input, level, what, this.gTLCore$calculationMode);
        var cached = this.gTLCore$templateCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<InputTemplate> templates = new ArrayList<>();
        for (var template : loader.get()) {
            templates.add(template);
        }
        this.gTLCore$templateCache.put(key, templates);
        return templates;
    }

    @Override
    @Unique
    public void gtlcore$clearTemplateCache() {
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            this.gTLCore$templateValidationEpoch++;
            this.gTLCore$maxFastMetrics.recordValidationEpochBump();
            return;
        }

        if (this.gTLCore$templateCache.isEmpty()) {
            return;
        }
        this.gTLCore$templateCache.clear();
    }

    @Redirect(
              method = "runCraftAttempt",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"),
              remap = false)
    private void redirectTreeRequest(
                                     CraftingTreeNode tree,
                                     CraftingSimulationState craftingInventory,
                                     long amount,
                                     KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        switch (this.gTLCore$calculationMode) {
            case MAX_FAST -> ((ICraftingTreeNode) tree).maxFastRequest(craftingInventory, amount, containerItems);
            case ULTRA_FAST -> ((ICraftingTreeNode) tree).ultraFastRequest(craftingInventory, amount, containerItems);
            case FAST -> ((ICraftingTreeNode) tree).fastRequest(craftingInventory, amount, containerItems);
            case LEGACY -> ((ICraftingTreeNode) tree).legacyRequest(craftingInventory, amount, containerItems);
        }
    }

    @Redirect(
              method = "runCraftAttempt",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/crafting/inv/CraftingSimulationState;buildCraftingPlan(Lappeng/crafting/inv/CraftingSimulationState;Lappeng/crafting/CraftingCalculation;J)Lappeng/crafting/CraftingPlan;"),
              remap = false)
    private CraftingPlan gTLCore$recordMaxFastPlanBuild(CraftingSimulationState craftingInventory,
                                                        CraftingCalculation calculation, long amount) {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return CraftingSimulationState.buildCraftingPlan(craftingInventory, calculation, amount);
        }

        long startedNanos = System.nanoTime();
        try {
            return CraftingSimulationState.buildCraftingPlan(craftingInventory, calculation, amount);
        } finally {
            this.gTLCore$maxFastMetrics.recordPlanBuildNanos(System.nanoTime() - startedNanos);
        }
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), remap = false)
    private void gTLCore$beginCraftAttempt(boolean simulate, long amount,
                                           CallbackInfoReturnable<CraftingPlan> cir) {
        if (this.gTLCore$calculationMode != AE2CalculationMode.LEGACY) {
            ((ICraftingTreeNode) this.tree).gtlcore$resetFastState();
        }
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            this.gTLCore$maxFastMetrics.beginAttempt(amount, simulate);
        }
    }

    @Inject(method = "runCraftAttempt", at = @At("RETURN"), remap = false)
    private void gTLCore$finishMaxFastAttempt(boolean simulate, long amount,
                                              CallbackInfoReturnable<CraftingPlan> cir) {
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            this.gTLCore$maxFastMetrics.finishAttempt(cir.getReturnValue() != null);
        }
    }

    @Unique
    private void gTLCore$clearAttemptState() {
        this.gTLCore$templateCache.clear();
        this.gTLCore$templateValidationEpoch = 0L;
        this.gTLCore$maxFastStrictBoundaryProbeDepth = 0;
        this.gTLCore$maxFastDeferredMissingCaptureDepth = 0;
        this.gTLCore$maxFastDeferredMissingScopes.clear();
        this.gTLCore$maxFastBoundaryFailureScopes.clear();
        this.gTLCore$maxFastMetrics = null;
        if (this.gTLCore$maxFastCompilationCache != null) {
            this.gTLCore$maxFastCompilationCache.clear();
        }
        this.gTLCore$maxFastCompilationCache = null;
    }
}
