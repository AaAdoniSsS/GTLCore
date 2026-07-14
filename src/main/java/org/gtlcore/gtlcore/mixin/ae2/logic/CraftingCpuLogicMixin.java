package org.gtlcore.gtlcore.mixin.ae2.logic;

import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReason;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReasonState;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternPower;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingDispatchReasonProvider;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(value = CraftingCpuLogic.class, priority = 1100)
public abstract class CraftingCpuLogicMixin implements ICraftingJobSuspension, ICraftingDispatchReasonProvider {

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
    @Final
    private Set<Consumer<AEKey>> listeners;

    @Shadow(remap = false)
    public abstract void storeItems();

    @Shadow(remap = false)
    public abstract void cancel();

    @Invoker(value = "postChange", remap = false)
    protected abstract void gtlcore$invokePostChange(AEKey what);

    @Shadow(remap = false)
    public @Nullable abstract GenericStack getFinalJobOutput();

    @Shadow(remap = false)
    protected abstract void finishJob(boolean success);

    @Unique
    private Map<IPatternDetails, Integer> gtlcore$workingDispatchReasons;

    @Unique
    private Map<AEKey, Integer> gtlcore$publishedDispatchReasons;

    @Unique
    private boolean gtlcore$collectDispatchReasons;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtlcore$initializeDispatchReasons(CraftingCPUCluster cluster, CallbackInfo ci) {
        this.gtlcore$workingDispatchReasons = new HashMap<>();
        this.gtlcore$publishedDispatchReasons = Map.of();
    }

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
        this.gtlcore$collectDispatchReasons = !this.listeners.isEmpty();
        this.gtlcore$workingDispatchReasons.clear();

        // Don't tick if we're not active.
        if (!cluster.isActive()) {
            gtlcore$markAllRemaining(CraftingDispatchReason.CPU_INACTIVE);
            gtlcore$publishDispatchReasons();
            return;
        }
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
        if (this.job == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            gtlcore$publishDispatchReasons();
            return;
        }
        // Check if the job was cancelled.
        if (((ExecutingCraftingJobAccessor) job).getLink().isCanceled()) {
            cancel();
            gtlcore$publishDispatchReasons();
            return;
        }

        if (gtlcore$isJobSuspended()) {
            gtlcore$markAllRemaining(CraftingDispatchReason.JOB_SUSPENDED);
            gtlcore$publishDispatchReasons();
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
        } else {
            gtlcore$markAllRemaining(CraftingDispatchReason.CPU_OPERATION_LIMIT);
        }
        if (remainingOperations <= 0) {
            gtlcore$markAllUnclassified(CraftingDispatchReason.CPU_OPERATION_LIMIT);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
        gtlcore$publishDispatchReasons();
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
            final boolean isProcessing = details.supportsPushInputsToExternalInventory();
            boolean providerSeen = false;
            boolean idleProviderSeen = false;
            boolean providerRejected = false;
            int taskReasonMask = 0;

            for (var provider : craftingService.getProviders(details)) {
                providerSeen = true;
                if (provider.isBusy()) {
                    continue;
                }
                idleProviderSeen = true;

                final boolean autoExpand = CraftingPatternAutoExpand.canAutoExpand(isProcessing, provider);
                final long operations = CraftingPatternAutoExpand.getOperations(isProcessing, provider, details,
                        taskProgress.getValue());
                KeyCounter expectedOutputs = new KeyCounter(), expectedContainerItems = new KeyCounter();
                KeyCounter[] craftingContainer = isProcessing ? (autoExpand ? AEUtils.extractForProcessingPattern(details, inventory, expectedOutputs, operations) : AEUtils.extractForProcessingPattern(details, inventory, expectedOutputs)) : AEUtils.extractForCraftPattern(details, inventory, level, expectedOutputs, expectedContainerItems);

                if (craftingContainer == null) {
                    taskReasonMask |= CraftingDispatchReason.WAITING_FOR_INPUTS.mask();
                    break;
                }

                var patternPower = CraftingPatternPower.forCpu(CraftingCpuHelper.calculatePatternPower(craftingContainer),
                        autoExpand, operations);
                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) < patternPower - 0.01) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                    taskReasonMask |= CraftingDispatchReason.INSUFFICIENT_POWER.mask();
                    break;
                }

                if (provider.pushPattern(details, craftingContainer)) {
                    taskReasonMask = 0;
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
                        taskProgress.setValue(taskProgress.getValue() - operations);
                        if (taskProgress.getValue() <= 0) {
                            it.remove();
                            this.gtlcore$workingDispatchReasons.remove(details);
                            continue taskLoop;
                        }
                        if (pushedPatterns == maxPatterns) {
                            gtlcore$markAllUnclassified(CraftingDispatchReason.CPU_OPERATION_LIMIT);
                            break taskLoop;
                        }
                        continue taskLoop;
                    }

                    // 2) Others
                    taskProgress.setValue(taskProgress.getValue() - 1);
                    if (taskProgress.getValue() <= 0) {
                        it.remove();
                        this.gtlcore$workingDispatchReasons.remove(details);
                        continue taskLoop;
                    }

                    if (pushedPatterns == maxPatterns) {
                        gtlcore$markAllUnclassified(CraftingDispatchReason.CPU_OPERATION_LIMIT);
                        break taskLoop;
                    }
                } else {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                    providerRejected = true;
                }
            }

            if (!providerSeen) {
                taskReasonMask |= CraftingDispatchReason.NO_PROVIDER.mask();
            } else if (!idleProviderSeen) {
                taskReasonMask |= CraftingDispatchReason.PROVIDERS_BUSY.mask();
            } else if (providerRejected) {
                taskReasonMask |= CraftingDispatchReason.PROVIDER_REJECTED.mask();
            }
            gtlcore$recordTaskReason(details, taskReasonMask);
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

    @Override
    @Unique
    public int gtlcore$getDispatchReasonMask(AEKey key) {
        return this.gtlcore$publishedDispatchReasons.getOrDefault(key, 0);
    }

    @Unique
    private void gtlcore$recordTaskReason(IPatternDetails details, int reasonMask) {
        if (!this.gtlcore$collectDispatchReasons) {
            return;
        }
        if (reasonMask == 0) {
            this.gtlcore$workingDispatchReasons.remove(details);
        } else {
            this.gtlcore$workingDispatchReasons.put(details, reasonMask);
        }
    }

    @Unique
    private void gtlcore$markAllRemaining(CraftingDispatchReason reason) {
        if (!this.gtlcore$collectDispatchReasons || this.job == null) {
            return;
        }
        for (IPatternDetails details : ((ExecutingCraftingJobAccessor) this.job).getTasks().keySet()) {
            this.gtlcore$workingDispatchReasons.put(details, reason.mask());
        }
    }

    @Unique
    private void gtlcore$markAllUnclassified(CraftingDispatchReason reason) {
        if (!this.gtlcore$collectDispatchReasons || this.job == null) {
            return;
        }
        for (IPatternDetails details : ((ExecutingCraftingJobAccessor) this.job).getTasks().keySet()) {
            this.gtlcore$workingDispatchReasons.putIfAbsent(details, reason.mask());
        }
    }

    @Unique
    private void gtlcore$publishDispatchReasons() {
        if (!this.gtlcore$collectDispatchReasons) {
            this.gtlcore$publishedDispatchReasons = Map.of();
            return;
        }
        Map<AEKey, Integer> current = new HashMap<>();
        if (this.job != null) {
            for (IPatternDetails details : ((ExecutingCraftingJobAccessor) this.job).getTasks().keySet()) {
                int reasonMask = this.gtlcore$workingDispatchReasons.getOrDefault(details, 0);
                if (reasonMask == 0) {
                    continue;
                }
                for (GenericStack output : details.getOutputs()) {
                    current.merge(output.what(), reasonMask, (existing, added) -> existing | added);
                }
            }
        }

        for (AEKey changed : CraftingDispatchReasonState.changedKeys(
                this.gtlcore$publishedDispatchReasons, current)) {
            gtlcore$invokePostChange(changed);
        }
        this.gtlcore$publishedDispatchReasons = Map.copyOf(current);
    }
}
