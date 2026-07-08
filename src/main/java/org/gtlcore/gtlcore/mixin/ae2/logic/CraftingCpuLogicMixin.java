package org.gtlcore.gtlcore.mixin.ae2.logic;

import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternPower;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(value = CraftingCpuLogic.class, priority = 1100)
public abstract class CraftingCpuLogicMixin implements ICraftingJobSuspension {

    @Shadow(remap = false)
    private ExecutingCraftingJob job;
    @Shadow(remap = false)
    private boolean cantStoreItems = false;

    @Shadow(remap = false)
    @Final
    CraftingCPUCluster cluster;

    @Shadow(remap = false)
    @Final
    private ListCraftingInventory inventory;

    @Shadow(remap = false)
    @Final
    private int[] usedOps;

    @Shadow(remap = false)
    public abstract void storeItems();

    @Shadow(remap = false)
    public abstract void cancel();

    @Shadow(remap = false)
    public @Nullable abstract GenericStack getFinalJobOutput();

    @Shadow(remap = false)
    protected abstract void finishJob(boolean success);

    @Unique
    private boolean core$matchOutput(GenericStack g) {
        return g != null && g.what() instanceof AEItemKey i && i.getItem() == Items.WRITTEN_BOOK &&
                (i.hasTag() && i.getTag().contains("display"));
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        // Don't tick if we're not active.
        if (!cluster.isActive())
            return;
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
        if (this.job == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            return;
        }
        // Check if the job was cancelled.
        if (((ExecutingCraftingJobAccessor) job).getLink().isCanceled()) {
            cancel();
            return;
        }

        if (gtlcore$isJobSuspended()) {
            return;
        }

        var remainingOperations = cluster.getCoProcessors() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final var started = remainingOperations;

        if (remainingOperations > 0) {
            do {
                var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cluster.getLevel());

                if (pushedPatterns > 0) {
                    remainingOperations -= pushedPatterns;
                } else {

                    // Automatic Cancellation
                    if (this.job != null && ((ExecutingCraftingJobAccessor) this.job).getTasks().isEmpty() &&
                            core$matchOutput(this.getFinalJobOutput()))
                        this.finishJob(true);

                    break;
                }
            } while (remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
    }

    /**
     * @author Dragons
     * @reason ME样板总成自动翻倍
     */
    @Overwrite(remap = false)
    public int executeCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
                               Level level) {
        var job = (ExecutingCraftingJobAccessor) (this.job);
        if (job == null) return 0;

        var pushedPatterns = 0;

        var it = job.getTasks().entrySet().iterator();
        taskLoop:
        while (it.hasNext()) {
            var task = it.next();
            var taskProgress = (ExecutingCraftingJobTaskProgressAccessor) (task.getValue());
            if (taskProgress.getValue() <= 0) {
                it.remove();
                continue;
            }

            var details = task.getKey();
            final boolean isProcessing = details instanceof AEProcessingPattern;

            for (var provider : craftingService.getProviders(details)) {
                if (provider.isBusy()) continue;

                final boolean autoExpand = CraftingPatternAutoExpand.canAutoExpand(isProcessing, provider);
                KeyCounter expectedOutputs = new KeyCounter(), expectedContainerItems = new KeyCounter();
                KeyCounter[] craftingContainer = isProcessing ? (autoExpand ? AEUtils.extractForProcessingPattern((AEProcessingPattern) details, inventory, expectedOutputs, taskProgress.getValue()) : AEUtils.extractForProcessingPattern((AEProcessingPattern) details, inventory, expectedOutputs)) : AEUtils.extractForCraftPattern(details, inventory, level, expectedOutputs, expectedContainerItems);

                if (craftingContainer == null) {
                    break;
                }

                var patternPower = CraftingPatternPower.forCpu(CraftingCpuHelper.calculatePatternPower(craftingContainer),
                        autoExpand, taskProgress.getValue());
                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) < patternPower - 0.01) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                    break;
                }

                if (provider.pushPattern(details, craftingContainer)) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;

                    for (var expectedOutput : expectedOutputs) {
                        job.getWaitingFor().insert(expectedOutput.getKey(), expectedOutput.getLongValue(),
                                Actionable.MODULATE);
                    }
                    for (var expectedContainerItem : expectedContainerItems) {
                        job.getWaitingFor().insert(expectedContainerItem.getKey(), expectedContainerItem.getLongValue(),
                                Actionable.MODULATE);
                        ((ElapsedTimeTrackerAccessor) job.getTimeTracker()).invokeAddMaxItems(expectedContainerItem.getLongValue(),
                                expectedContainerItem.getKey().getType());
                    }

                    cluster.markDirty();

                    // 1) AutoExpand
                    if (autoExpand) {
                        taskProgress.setValue(0);
                        it.remove();
                        continue taskLoop;
                    }

                    // 2) Others
                    taskProgress.setValue(taskProgress.getValue() - 1);
                    if (taskProgress.getValue() <= 0) {
                        it.remove();
                        continue taskLoop;
                    }

                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }
                } else {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                }
            }
        }

        return pushedPatterns;
    }

    @Override
    @Unique
    public boolean gtlcore$isJobSuspended() {
        return this.job != null && ((ICraftingJobSuspension) this.job).gtlcore$isJobSuspended();
    }

    @Override
    @Unique
    public void gtlcore$setJobSuspended(boolean suspended) {
        if (this.job != null) {
            ((ICraftingJobSuspension) this.job).gtlcore$setJobSuspended(suspended);
        }
    }
}
