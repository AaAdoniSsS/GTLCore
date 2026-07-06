package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import appeng.hooks.ticking.TickHandler;
import com.google.common.base.Stopwatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements ICraftingCalculation {

    @Unique
    private static final int GTLCORE_PAUSE_CHECK_INTERVAL = 100;

    @Unique
    private AE2CalculationMode gTLCore$calculationMode = AE2CalculationMode.ULTRA_FAST;
    @Unique
    private boolean gTLCore$adaptiveRun;
    @Unique
    private int gTLCore$smallJobBypassChecks;
    @Unique
    private final Map<TemplateCacheKey, List<InputTemplate>> gTLCore$templateCache = new HashMap<>();
    @Unique
    private final Map<BranchKey, Long> gTLCore$failedBranches = new HashMap<>();
    @Unique
    private final Map<BranchKey, Integer> gTLCore$branchSuccesses = new HashMap<>();

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
    private KeyCounter missing;
    @Shadow(remap = false)
    @Final
    private CraftingTreeNode tree;
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
    private int time;
    @Shadow(remap = false)
    private int incTime;

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
        try {
            this.gTLCore$calculationMode = AEUtils.getCalculationMode();
            var plan = this.gTLCore$calculationMode == AE2CalculationMode.ADAPTIVE ? gTLCore$computeAdaptivePlan() : computePlan();
            this.logCraftingJob(plan);
            return plan;
        } catch (Exception ex) {
            AELog.info(ex, "Exception during async crafting calculation.");
            throw new RuntimeException(ex);
        } finally {
            this.finish();
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
            this.gTLCore$adaptiveRun = false;
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
        if (!this.gTLCore$adaptiveRun) {
            return !this.done;
        }
        synchronized (this.monitor) {
            if (this.done) {
                return false;
            }
            if (this.running) {
                return true;
            }

            this.time = gTLCore$normalizeBudget(micros);
            this.watch.reset();
            this.watch.start();
            this.running = true;
            this.monitor.notifyAll();

            while (this.running && !this.done) {
                try {
                    this.monitor.wait();
                } catch (InterruptedException ignored) {}
            }

            return !this.done;
        }
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
        if (!this.gTLCore$adaptiveRun) {
            return;
        }
        if (this.gTLCore$smallJobBypassChecks > 0) {
            this.gTLCore$smallJobBypassChecks--;
            if (this.watch.elapsed(TimeUnit.MICROSECONDS) <= this.time) {
                return;
            }
            this.gTLCore$smallJobBypassChecks = 0;
        } else if (this.incTime <= GTLCORE_PAUSE_CHECK_INTERVAL) {
            this.incTime++;
            return;
        }

        this.incTime = 0;
        synchronized (this.monitor) {
            if (this.watch.elapsed(TimeUnit.MICROSECONDS) > this.time) {
                this.running = false;
                if (this.watch.isRunning()) {
                    this.watch.stop();
                }
                this.monitor.notifyAll();
            }

            while (!this.running && !this.done) {
                this.monitor.wait();
            }
        }
    }

    @Override
    @Unique
    public AE2CalculationMode gtlcore$getCalculationMode() {
        return this.gTLCore$calculationMode;
    }

    @Override
    @Unique
    public Iterable<InputTemplate> gtlcore$getCachedTemplates(ICraftingInventory inv,
                                                              IPatternDetails.IInput input,
                                                              Level level,
                                                              AEKey what,
                                                              Supplier<Iterable<InputTemplate>> loader) {
        if (!this.gTLCore$adaptiveRun) {
            return loader.get();
        }

        var key = new TemplateCacheKey(inv, input, level, what, this.gTLCore$calculationMode);
        return this.gTLCore$templateCache.computeIfAbsent(key, ignored -> {
            List<InputTemplate> templates = new ArrayList<>();
            for (var template : loader.get()) {
                templates.add(template);
            }
            return templates;
        });
    }

    @Override
    @Unique
    public boolean gtlcore$shouldSkipBranch(AEKey output, IPatternDetails details, long requestedAmount) {
        if (!this.gTLCore$adaptiveRun) {
            return false;
        }

        var failedAt = this.gTLCore$failedBranches.get(new BranchKey(output, details, this.gTLCore$calculationMode));
        return failedAt != null && requestedAmount >= failedAt;
    }

    @Override
    @Unique
    public void gtlcore$recordBranchFailure(AEKey output, IPatternDetails details, long requestedAmount) {
        if (this.gTLCore$adaptiveRun) {
            this.gTLCore$failedBranches.merge(
                    new BranchKey(output, details, this.gTLCore$calculationMode),
                    requestedAmount,
                    Math::min);
        }
    }

    @Override
    @Unique
    public void gtlcore$recordBranchSuccess(AEKey output, IPatternDetails details) {
        if (this.gTLCore$adaptiveRun) {
            this.gTLCore$branchSuccesses.merge(new BranchKey(output, details, this.gTLCore$calculationMode), 1, Integer::sum);
        }
    }

    @Override
    @Unique
    public int gtlcore$getBranchSuccesses(AEKey output, IPatternDetails details) {
        if (!this.gTLCore$adaptiveRun) {
            return 0;
        }
        return this.gTLCore$branchSuccesses.getOrDefault(new BranchKey(output, details, this.gTLCore$calculationMode), 0);
    }

    @Override
    @Unique
    public void gtlcore$clearTemplateCache() {
        this.gTLCore$templateCache.clear();
    }

    @Override
    @Unique
    public boolean gtlcore$isAdaptive() {
        return this.gTLCore$adaptiveRun;
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
            case ADAPTIVE -> ((ICraftingTreeNode) tree).adaptiveRequest(craftingInventory, amount, containerItems);
            case ULTRA_FAST -> ((ICraftingTreeNode) tree).ultraFastRequest(craftingInventory, amount, containerItems);
            case FAST -> ((ICraftingTreeNode) tree).fastRequest(craftingInventory, amount, containerItems);
            case LEGACY -> ((ICraftingTreeNode) tree).legacyRequest(craftingInventory, amount, containerItems);
        }
    }

    @Unique
    private ICraftingPlan gTLCore$computeAdaptivePlan() throws InterruptedException {
        this.gTLCore$adaptiveRun = true;
        this.gTLCore$smallJobBypassChecks = ConfigHolder.INSTANCE.ae2CraftingSmallJobBypassChecks;
        TickHandler.instance().registerCraftingSimulation(this.level, (CraftingCalculation) (Object) this);
        this.gTLCore$startSlice(gTLCore$initialBurstBudget());

        var modes = ConfigHolder.INSTANCE.ae2CraftingFallbackOnFailure ?
                new AE2CalculationMode[] { AE2CalculationMode.ADAPTIVE, AE2CalculationMode.FAST, AE2CalculationMode.LEGACY } :
                new AE2CalculationMode[] { AE2CalculationMode.ADAPTIVE };
        RuntimeException lastFailure = null;

        for (int i = 0; i < modes.length; i++) {
            this.gTLCore$calculationMode = modes[i];
            if (i > 0) {
                this.gTLCore$resetForRetry();
            }

            try {
                var plan = this.computePlan();
                return plan;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (i == modes.length - 1) {
                    throw ex;
                }

                AELog.warn(
                        "AE2 adaptive crafting calculation for %s x%d failed in %s, retrying with %s.",
                        this.output,
                        this.requestedAmount,
                        modes[i],
                        modes[i + 1]);
            }
        }

        throw lastFailure == null ? new IllegalStateException("Adaptive crafting calculation did not produce a plan.") : lastFailure;
    }

    @Unique
    private void gTLCore$startSlice(int micros) {
        synchronized (this.monitor) {
            this.time = gTLCore$normalizeBudget(micros);
            if (this.watch.isRunning()) {
                this.watch.stop();
            }
            this.watch.reset();
            this.watch.start();
            this.running = true;
            this.monitor.notifyAll();
        }
    }

    @Unique
    private void gTLCore$resetForRetry() {
        this.missing.clear();
        this.gTLCore$clearAttemptState();
        ((ICraftingTreeNode) this.tree).gtlcore$resetAdaptiveState();
        this.gTLCore$smallJobBypassChecks = ConfigHolder.INSTANCE.ae2CraftingSmallJobBypassChecks;
        this.gTLCore$startSlice(gTLCore$latencyBudget());
    }

    @Unique
    private void gTLCore$clearAttemptState() {
        this.gTLCore$templateCache.clear();
        this.gTLCore$failedBranches.clear();
        this.gTLCore$branchSuccesses.clear();
    }

    @Unique
    private static int gTLCore$minBudget() {
        return Math.max(1, ConfigHolder.INSTANCE.ae2CraftingMinBudgetMicros);
    }

    @Unique
    private static int gTLCore$maxBudget() {
        return Math.max(gTLCore$minBudget(), ConfigHolder.INSTANCE.ae2CraftingMaxBudgetMicros);
    }

    @Unique
    private static int gTLCore$latencyBudget() {
        return Math.max(gTLCore$maxBudget(), ConfigHolder.INSTANCE.ae2CraftingIdleBudgetMicros);
    }

    @Unique
    private static int gTLCore$initialBurstBudget() {
        int burstBudget = ConfigHolder.INSTANCE.ae2CraftingInitialBurstMicros;
        return burstBudget <= 0 ? gTLCore$latencyBudget() : Math.max(gTLCore$latencyBudget(), burstBudget);
    }

    @Unique
    private static int gTLCore$normalizeBudget(int micros) {
        return Math.max(gTLCore$minBudget(), Math.min(gTLCore$initialBurstBudget(), micros));
    }

    @Unique
    private static final class TemplateCacheKey {

        private final IPatternDetails.IInput input;
        private final ICraftingInventory inv;
        private final Level level;
        private final AEKey what;
        private final AE2CalculationMode mode;
        private final int hash;

        private TemplateCacheKey(ICraftingInventory inv, IPatternDetails.IInput input, Level level, AEKey what,
                                 AE2CalculationMode mode) {
            this.inv = inv;
            this.input = input;
            this.level = level;
            this.what = what;
            this.mode = mode;
            this.hash = Objects.hash(
                    System.identityHashCode(inv),
                    System.identityHashCode(input),
                    System.identityHashCode(level),
                    what,
                    mode);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TemplateCacheKey other)) return false;
            return this.inv == other.inv && this.input == other.input && this.level == other.level &&
                    Objects.equals(this.what, other.what) &&
                    this.mode == other.mode;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    @Unique
    private static final class BranchKey {

        private final AEKey output;
        private final Object pattern;
        private final AE2CalculationMode mode;

        private BranchKey(AEKey output, IPatternDetails details, AE2CalculationMode mode) {
            this.output = output;
            this.pattern = details.getDefinition();
            this.mode = mode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BranchKey other)) return false;
            return Objects.equals(this.output, other.output) && Objects.equals(this.pattern, other.pattern) &&
                    this.mode == other.mode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.output, this.pattern, this.mode);
        }
    }
}
