package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.AE2CraftingCalculationLogger;
import org.gtlcore.gtlcore.integration.ae2.crafting.AE2CraftingPlanCache;
import org.gtlcore.gtlcore.integration.ae2.crafting.FastCraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStorageVersion;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import com.google.common.base.Stopwatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.Supplier;

@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationMixin implements ICraftingCalculation {

    @Unique
    private AE2CalculationMode gTLCore$calculationMode = AE2CalculationMode.MAX_FAST;
    @Unique
    private final Map<TemplateCacheKey, List<InputTemplate>> gTLCore$templateCache = new HashMap<>();
    @Unique
    private final Map<BranchKey, Long> gTLCore$maxFastFailedBranches = new HashMap<>();
    @Unique
    private final Map<AEKey, Object> gTLCore$maxFastPreferredBranches = new HashMap<>();
    @Unique
    private final AE2CraftingCalculationLogger.Counters gTLCore$craftingLogCounters = new AE2CraftingCalculationLogger.Counters();
    @Unique
    private boolean gTLCore$craftingCalculationLogEnabled;
    @Unique
    private long gTLCore$craftingLogId;
    @Unique
    private long gTLCore$craftingLogStartedNanos;
    @Unique
    private static final String gTLCore$LOG_SOURCE_CACHE = "cache";
    @Unique
    private static final String gTLCore$LOG_SOURCE_MAX_FAST = "max_fast";
    @Unique
    private static final String gTLCore$LOG_SOURCE_VANILLA = "vanilla";

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
    private AEKey output;
    @Shadow(remap = false)
    @Final
    private long requestedAmount;
    @Shadow(remap = false)
    @Final
    private CalculationStrategy strategy;
    @Shadow(remap = false)
    @Final
    private ICraftingSimulationRequester simRequester;
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
        try {
            this.gTLCore$calculationMode = AEUtils.getCalculationMode();
            this.gTLCore$startCraftingLog();
            ICraftingPlan cachedPlan = this.gTLCore$getCachedPlan();
            if (cachedPlan != null) {
                this.logCraftingJob(cachedPlan);
                this.gTLCore$finishCraftingLog(cachedPlan, gTLCore$LOG_SOURCE_CACHE);
                return cachedPlan;
            }
            ICraftingPlan fastPlan = this.gTLCore$tryFastPlan();
            if (fastPlan != null) {
                this.gTLCore$cachePlan(fastPlan);
                this.logCraftingJob(fastPlan);
                this.gTLCore$finishCraftingLog(fastPlan, gTLCore$LOG_SOURCE_MAX_FAST);
                return fastPlan;
            }
            var plan = computePlan();
            this.gTLCore$cachePlan(plan);
            this.logCraftingJob(plan);
            this.gTLCore$finishCraftingLog(plan, gTLCore$LOG_SOURCE_VANILLA);
            return plan;
        } catch (Exception ex) {
            this.gTLCore$failCraftingLog(ex);
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
    public Iterable<InputTemplate> gtlcore$getCachedTemplates(ICraftingInventory inv,
                                                              IPatternDetails.IInput input,
                                                              Level level,
                                                              AEKey what,
                                                              Supplier<Iterable<InputTemplate>> loader) {
        if (this.gTLCore$calculationMode == AE2CalculationMode.LEGACY) {
            return loader.get();
        }

        var key = new TemplateCacheKey(inv, input, level, what, this.gTLCore$calculationMode);
        var cached = this.gTLCore$templateCache.get(key);
        if (cached != null) {
            if (this.gTLCore$craftingCalculationLogEnabled) {
                this.gTLCore$craftingLogCounters.recordTemplateCacheHit();
            }
            return cached;
        }

        if (this.gTLCore$craftingCalculationLogEnabled) {
            this.gTLCore$craftingLogCounters.recordTemplateCacheMiss();
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
    public boolean gtlcore$shouldSkipBranch(AEKey output, IPatternDetails details, long requestedAmount) {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return false;
        }

        Long failedAt = this.gTLCore$maxFastFailedBranches.get(new BranchKey(output, details, this.gTLCore$calculationMode));
        return failedAt != null && requestedAmount >= failedAt;
    }

    @Override
    @Unique
    public void gtlcore$recordBranchFailure(AEKey output, IPatternDetails details, long requestedAmount) {
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            this.gTLCore$maxFastFailedBranches.merge(
                    new BranchKey(output, details, this.gTLCore$calculationMode),
                    requestedAmount,
                    Math::min);
        }
        if (this.gTLCore$craftingCalculationLogEnabled) {
            this.gTLCore$craftingLogCounters.recordBranchFailure();
        }
    }

    @Override
    @Unique
    public void gtlcore$recordBranchSkip(AEKey output, IPatternDetails details, long requestedAmount) {
        if (this.gTLCore$craftingCalculationLogEnabled) {
            this.gTLCore$craftingLogCounters.recordBranchSkip();
        }
    }

    @Override
    @Unique
    public void gtlcore$recordBranchSuccess(AEKey output, IPatternDetails details, long craftedAmount) {
        if (this.gTLCore$calculationMode == AE2CalculationMode.MAX_FAST) {
            var key = new BranchKey(output, details, this.gTLCore$calculationMode);
            Long failedAt = this.gTLCore$maxFastFailedBranches.get(key);
            if (failedAt != null && craftedAmount >= failedAt) {
                this.gTLCore$maxFastFailedBranches.remove(key);
            }
            this.gTLCore$maxFastPreferredBranches.put(output, details.getDefinition());
        }
        if (this.gTLCore$craftingCalculationLogEnabled) {
            this.gTLCore$craftingLogCounters.recordBranchSuccess();
        }
    }

    @Override
    @Unique
    public Object gtlcore$getPreferredBranchDefinition(AEKey output) {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return null;
        }
        return this.gTLCore$maxFastPreferredBranches.get(output);
    }

    @Override
    @Unique
    public void gtlcore$clearTemplateCache() {
        if (this.gTLCore$templateCache.isEmpty()) {
            return;
        }
        if (this.gTLCore$craftingCalculationLogEnabled) {
            this.gTLCore$craftingLogCounters.recordTemplateCacheClear();
        }
        this.gTLCore$templateCache.clear();
    }

    @Override
    @Unique
    public boolean gtlcore$isCraftingCalculationLogEnabled() {
        return this.gTLCore$craftingCalculationLogEnabled;
    }

    @Override
    @Unique
    public AE2CraftingCalculationLogger.Counters gtlcore$getCraftingCalculationLogCounters() {
        return this.gTLCore$craftingLogCounters;
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

    @Unique
    private void gTLCore$clearAttemptState() {
        this.gTLCore$templateCache.clear();
        this.gTLCore$maxFastFailedBranches.clear();
        this.gTLCore$maxFastPreferredBranches.clear();
    }

    @Unique
    private void gTLCore$startCraftingLog() {
        this.gTLCore$craftingLogCounters.reset();
        this.gTLCore$craftingCalculationLogEnabled = ConfigHolder.INSTANCE.ae2CraftingCalculationLogEnabled;
        if (!this.gTLCore$craftingCalculationLogEnabled) {
            return;
        }

        this.gTLCore$craftingLogId = AE2CraftingCalculationLogger.nextId();
        this.gTLCore$craftingLogStartedNanos = System.nanoTime();
        AE2CraftingCalculationLogger.writeStart(
                this.gTLCore$craftingLogId,
                this.gTLCore$calculationMode,
                this.output,
                this.requestedAmount,
                this.level);
    }

    @Unique
    private void gTLCore$finishCraftingLog(ICraftingPlan plan, String source) {
        if (!this.gTLCore$craftingCalculationLogEnabled) {
            return;
        }

        AE2CraftingCalculationLogger.writeSuccess(
                this.gTLCore$craftingLogId,
                this.gTLCore$craftingLogStartedNanos,
                source,
                this.gTLCore$calculationMode,
                this.output,
                this.requestedAmount,
                this.level,
                plan,
                this.gTLCore$craftingLogCounters);
    }

    @Unique
    private void gTLCore$failCraftingLog(Throwable error) {
        if (!this.gTLCore$craftingCalculationLogEnabled) {
            return;
        }

        AE2CraftingCalculationLogger.writeFailure(
                this.gTLCore$craftingLogId,
                this.gTLCore$craftingLogStartedNanos,
                this.gTLCore$calculationMode,
                this.output,
                this.requestedAmount,
                this.level,
                this.missing,
                error,
                this.gTLCore$craftingLogCounters);
    }

    @Unique
    private ICraftingPlan gTLCore$getCachedPlan() {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return null;
        }

        var gridNode = this.simRequester.getGridNode();
        if (gridNode == null) {
            return null;
        }

        IGrid grid = gridNode.getGrid();
        return AE2CraftingPlanCache.get(
                grid,
                gTLCore$getStorageVersion(grid),
                this.level.dimension().location(),
                this.output,
                this.requestedAmount,
                this.strategy,
                this.gTLCore$calculationMode);
    }

    @Unique
    private void gTLCore$cachePlan(ICraftingPlan plan) {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return;
        }

        var gridNode = this.simRequester.getGridNode();
        if (gridNode == null) {
            return;
        }

        IGrid grid = gridNode.getGrid();
        AE2CraftingPlanCache.put(
                grid,
                gTLCore$getStorageVersion(grid),
                this.level.dimension().location(),
                this.output,
                this.requestedAmount,
                this.strategy,
                this.gTLCore$calculationMode,
                plan);
    }

    @Unique
    private ICraftingPlan gTLCore$tryFastPlan() {
        if (this.gTLCore$calculationMode != AE2CalculationMode.MAX_FAST) {
            return null;
        }

        var gridNode = this.simRequester.getGridNode();
        if (gridNode == null) {
            return null;
        }

        return FastCraftingCalculation.tryBuild(
                this.level,
                gridNode.getGrid(),
                this.output,
                this.requestedAmount,
                this.strategy);
    }

    @Unique
    private static long gTLCore$getStorageVersion(IGrid grid) {
        var storageService = grid.getStorageService();
        if (storageService instanceof ICraftingStorageVersion versionedStorage) {
            return versionedStorage.gtlcore$getStorageVersion();
        }
        return 0;
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
        private final int hash;

        private BranchKey(AEKey output, IPatternDetails details, AE2CalculationMode mode) {
            this.output = output;
            this.pattern = details.getDefinition();
            this.mode = mode;
            this.hash = Objects.hash(this.output, this.pattern, this.mode);
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
            return this.hash;
        }
    }
}
