package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingTreeNode;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
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
    private AE2CalculationMode gTLCore$calculationMode = AE2CalculationMode.ULTRA_FAST;
    @Unique
    private final Map<TemplateCacheKey, List<InputTemplate>> gTLCore$templateCache = new HashMap<>();

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
    private CalculationStrategy strategy;
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
            ICraftingPlan plan = this.computePlan();
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
            case ULTRA_FAST -> ((ICraftingTreeNode) tree).ultraFastRequest(craftingInventory, amount, containerItems);
            case FAST -> ((ICraftingTreeNode) tree).fastRequest(craftingInventory, amount, containerItems);
            case LEGACY -> ((ICraftingTreeNode) tree).legacyRequest(craftingInventory, amount, containerItems);
        }
    }

    @Unique
    private void gTLCore$clearAttemptState() {
        this.gTLCore$templateCache.clear();
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
}
