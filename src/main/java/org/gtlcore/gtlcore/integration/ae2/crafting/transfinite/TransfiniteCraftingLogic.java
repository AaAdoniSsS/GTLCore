package org.gtlcore.gtlcore.integration.ae2.crafting.transfinite;

import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchPerformanceLogger;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReason;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReasonState;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternPower;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingDispatchReasonProvider;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;
import org.gtlcore.gtlcore.mixin.ae2.logic.ElapsedTimeTrackerAccessor;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class TransfiniteCraftingLogic implements ICraftingJobSuspension, ICraftingDispatchReasonProvider {

    private static final int DISPATCH_HISTORY_LENGTH = 3;
    private static final int STORE_BATCH_SIZE = 64;
    private static final int MAX_CONSECUTIVE_STORE_FAILURES = 5;
    private static final double POWER_EPSILON = 0.01;
    private static final ElapsedTimeTracker EMPTY_TIME_TRACKER = new ElapsedTimeTracker();

    private final TransfiniteCraftingCPU cpu;
    private final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);
    private final long[] usedDispatches = new long[DISPATCH_HISTORY_LENGTH];
    private final Set<Consumer<AEKey>> listeners = new ObjectOpenHashSet<>();
    private final Map<IPatternDetails, Integer> workingDispatchReasons = new HashMap<>();

    private @Nullable TransfiniteCraftingJob job;
    private Map<AEKey, Integer> publishedDispatchReasons = Map.of();
    private boolean collectDispatchReasons;
    private boolean cantStoreItems;
    private boolean batchingChanges;
    private boolean dirty;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    private long lastPerformanceLogTick = Long.MIN_VALUE;

    TransfiniteCraftingLogic(TransfiniteCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource source,
                                              @Nullable ICraftingRequester requester) {
        if (this.job != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!this.cpu.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        if (!this.inventory.list.isEmpty()) {
            AELog.warn("Transfinite crafting CPU inventory is not empty when a job is submitted.");
        }

        KeyCounter extractionShortfall = new KeyCounter();
        if (plan instanceof MissingCraftingPlan) {
            extractAvailableInitialItems(plan, grid, source, extractionShortfall);
        } else {
            GenericStack missingIngredient = CraftingCpuHelper.tryExtractInitialItems(
                    plan, grid, this.inventory, source);
            if (missingIngredient != null) {
                return CraftingSubmitResult.missingIngredient(missingIngredient);
            }
        }

        Integer playerId = source.player()
                .map(player -> player instanceof ServerPlayer serverPlayer ?
                        IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        UUID craftId = UUID.randomUUID();
        CraftingLink cpuLink = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false), this.cpu);
        this.job = new TransfiniteCraftingJob(
                plan, extractionShortfall, cpuLink, playerId, this::onWaitingForChanged);
        indexAllWaitingItems();
        markChanged();
        notifyJobOwner(this.job, CraftingJobStatusPacket.Status.STARTED);

        if (requester == null) {
            return CraftingSubmitResult.successful(null);
        }

        CraftingLink requesterLink = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        craftingService.addLink(cpuLink);
        craftingService.addLink(requesterLink);
        return CraftingSubmitResult.successful(requesterLink);
    }

    private void extractAvailableInitialItems(ICraftingPlan plan, IGrid grid, IActionSource source,
                                              KeyCounter extractionShortfall) {
        var storage = grid.getStorageService().getInventory();
        for (var entry : plan.usedItems()) {
            AEKey key = entry.getKey();
            long required = entry.getLongValue();
            long extracted = storage.extract(key, required, Actionable.MODULATE, source);
            if (extracted > 0) {
                this.inventory.insert(key, extracted, Actionable.MODULATE);
            }
            if (extracted < required) {
                extractionShortfall.add(key, required - extracted);
            }
        }
    }

    public void tickCraftingLogic(IEnergyService energyService, CraftingService craftingService) {
        this.batchingChanges = true;
        try {
            if (!CraftingDispatchPerformanceLogger.isEnabled()) {
                tickCraftingLogicInternal(energyService, craftingService, null);
                return;
            }

            long startedAt = System.nanoTime();
            var metrics = new CraftingDispatchPerformanceLogger.Metrics();
            long dispatched = tickCraftingLogicInternal(energyService, craftingService, metrics);
            long currentTick = TickHandler.instance().getCurrentTick();
            if (CraftingDispatchPerformanceLogger.logIfNeeded(
                    "transfinite", this.cpu.getLevel(), this.cpu.getHost().getPos(), this.cpu.getId(),
                    System.nanoTime() - startedAt, dispatched, this.cpu.getParallelism(), getTaskKindCount(),
                    getWaitingKindCount(), this.inventory.list.size(), this.cpu.getHost().getActiveJobCount(),
                    this.cantStoreItems, metrics, currentTick, this.lastPerformanceLogTick)) {
                this.lastPerformanceLogTick = currentTick;
            }
        } finally {
            this.batchingChanges = false;
            flushDirty();
        }
    }

    private long tickCraftingLogicInternal(IEnergyService energyService, CraftingService craftingService,
                                           @Nullable CraftingDispatchPerformanceLogger.Metrics metrics) {
        this.collectDispatchReasons = !this.listeners.isEmpty();
        this.workingDispatchReasons.clear();

        if (!this.cpu.isActive()) {
            markAllRemaining(CraftingDispatchReason.CPU_INACTIVE);
            publishDispatchReasons();
            return 0;
        }

        this.cantStoreItems = false;
        if (this.job == null) {
            storeItems();
            this.cantStoreItems = !this.inventory.list.isEmpty();
            publishDispatchReasons();
            return 0;
        }
        if (this.job.getLink().isCanceled()) {
            cancel();
            publishDispatchReasons();
            return 0;
        }
        if (this.job.isSuspended()) {
            markAllRemaining(CraftingDispatchReason.JOB_SUSPENDED);
            publishDispatchReasons();
            return 0;
        }

        long recentlyUsed = 0;
        for (long used : this.usedDispatches) {
            recentlyUsed = NumberUtils.saturatedAdd(recentlyUsed, used);
        }
        long dispatchBudget = Math.max(0, this.cpu.getParallelism() - recentlyUsed);
        long dispatchedCalls = 0;
        if (dispatchBudget > 0) {
            long remainingOperations = dispatchBudget;
            while (remainingOperations > 0) {
                long pushed = executeCrafting(
                        remainingOperations, craftingService, energyService, this.cpu.getLevel(), metrics);
                if (pushed <= 0) {
                    break;
                }
                dispatchedCalls = NumberUtils.saturatedAdd(dispatchedCalls, pushed);
                remainingOperations -= pushed;
            }
        } else {
            markAllRemaining(CraftingDispatchReason.CPU_OPERATION_LIMIT);
        }

        recoverFinalOutputFromNetwork();

        if (dispatchedCalls >= dispatchBudget && dispatchBudget > 0) {
            markAllUnclassified(CraftingDispatchReason.CPU_OPERATION_LIMIT);
        }
        System.arraycopy(this.usedDispatches, 0, this.usedDispatches, 1, this.usedDispatches.length - 1);
        this.usedDispatches[0] = dispatchedCalls;
        publishDispatchReasons();
        return dispatchedCalls;
    }

    private int getTaskKindCount() {
        return this.job == null ? 0 : this.job.getTasks().size();
    }

    private int getWaitingKindCount() {
        return this.job == null ? 0 : this.job.getWaitingFor().list.size();
    }

    public long executeCrafting(long maxDispatches, CraftingService craftingService, IEnergyService energyService,
                                Level level) {
        return executeCrafting(maxDispatches, craftingService, energyService, level, null);
    }

    private long executeCrafting(long maxDispatches, CraftingService craftingService, IEnergyService energyService,
                                 Level level, @Nullable CraftingDispatchPerformanceLogger.Metrics metrics) {
        TransfiniteCraftingJob currentJob = this.job;
        if (currentJob == null || maxDispatches <= 0) {
            return 0;
        }

        long dispatchedCalls = 0;
        var taskIterator = Object2LongMaps.fastIterator(currentJob.getTasks());
        taskLoop:
        while (taskIterator.hasNext() && dispatchedCalls < maxDispatches) {
            var task = taskIterator.next();
            long taskOperations = task.getLongValue();
            if (taskOperations <= 0) {
                taskIterator.remove();
                continue;
            }

            IPatternDetails details = task.getKey();
            boolean processing = details.supportsPushInputsToExternalInventory();
            boolean providerSeen = false;
            boolean idleProviderSeen = false;
            boolean providerRejected = false;
            int taskReasonMask = 0;

            for (var provider : craftingService.getProviders(details)) {
                if (metrics != null) {
                    metrics.recordProviderVisit();
                }
                providerSeen = true;
                if (provider.isBusy()) {
                    continue;
                }
                idleProviderSeen = true;

                boolean autoExpand = CraftingPatternAutoExpand.canAutoExpand(processing, provider);
                long requestedOperations = taskOperations;
                long operations = autoExpand ? CraftingPatternAutoExpand.getOperations(
                        true, provider, details, requestedOperations) : 1;
                operations = Math.max(1, Math.min(operations, requestedOperations));

                KeyCounter expectedOutputs = new KeyCounter();
                KeyCounter expectedContainerItems = new KeyCounter();
                long materialStartedAt = metrics == null ? 0 : System.nanoTime();
                KeyCounter[] craftingContainer = processing ?
                        (autoExpand ? AEUtils.extractForProcessingPattern(
                                details, this.inventory, expectedOutputs, operations) :
                                AEUtils.extractForProcessingPattern(details, this.inventory, expectedOutputs)) :
                        AEUtils.extractForCraftPattern(
                                details, this.inventory, level, expectedOutputs, expectedContainerItems);
                if (metrics != null) {
                    metrics.recordMaterialAttempt(System.nanoTime() - materialStartedAt,
                            craftingContainer != null);
                }

                if (craftingContainer == null) {
                    taskReasonMask |= CraftingDispatchReason.WAITING_FOR_INPUTS.mask();
                    break;
                }

                long energyStartedAt = metrics == null ? 0 : System.nanoTime();
                double patternPower = CraftingPatternPower.forCpu(
                        CraftingCpuHelper.calculatePatternPower(craftingContainer), autoExpand, operations);
                boolean hasPower = energyService.extractAEPower(
                        patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) >=
                        patternPower - POWER_EPSILON;
                if (metrics != null) {
                    metrics.recordEnergyWork(System.nanoTime() - energyStartedAt);
                }
                if (!hasPower) {
                    long reinjectStartedAt = metrics == null ? 0 : System.nanoTime();
                    CraftingCpuHelper.reinjectPatternInputs(this.inventory, craftingContainer);
                    if (metrics != null) {
                        metrics.recordMaterialWork(System.nanoTime() - reinjectStartedAt);
                    }
                    taskReasonMask |= CraftingDispatchReason.INSUFFICIENT_POWER.mask();
                    break;
                }

                long pushStartedAt = metrics == null ? 0 : System.nanoTime();
                boolean pushed = provider.pushPattern(details, craftingContainer);
                if (metrics != null) {
                    metrics.recordPush(System.nanoTime() - pushStartedAt, pushed ? operations : 0);
                }
                if (!pushed) {
                    long reinjectStartedAt = metrics == null ? 0 : System.nanoTime();
                    CraftingCpuHelper.reinjectPatternInputs(this.inventory, craftingContainer);
                    if (metrics != null) {
                        metrics.recordMaterialWork(System.nanoTime() - reinjectStartedAt);
                    }
                    providerRejected = true;
                    continue;
                }

                taskReasonMask = 0;
                energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                dispatchedCalls = NumberUtils.saturatedAdd(dispatchedCalls, 1);
                for (var expectedOutput : expectedOutputs) {
                    currentJob.getWaitingFor().insert(
                            expectedOutput.getKey(), expectedOutput.getLongValue(), Actionable.MODULATE);
                }
                for (var containerItem : expectedContainerItems) {
                    currentJob.getWaitingFor().insert(
                            containerItem.getKey(), containerItem.getLongValue(), Actionable.MODULATE);
                    ((ElapsedTimeTrackerAccessor) currentJob.getTimeTracker()).invokeAddMaxItems(
                            containerItem.getLongValue(), containerItem.getKey().getType());
                }

                taskOperations -= operations;
                task.setValue(taskOperations);
                markChanged();
                if (taskOperations <= 0) {
                    taskIterator.remove();
                    this.workingDispatchReasons.remove(details);
                    continue taskLoop;
                }
                if (dispatchedCalls >= maxDispatches) {
                    break taskLoop;
                }
                if (autoExpand) {
                    continue taskLoop;
                }
            }

            if (!providerSeen) {
                taskReasonMask |= CraftingDispatchReason.NO_PROVIDER.mask();
            } else if (!idleProviderSeen) {
                taskReasonMask |= CraftingDispatchReason.PROVIDERS_BUSY.mask();
            } else if (providerRejected) {
                taskReasonMask |= CraftingDispatchReason.PROVIDER_REJECTED.mask();
            }
            recordTaskReason(details, taskReasonMask);
        }
        return dispatchedCalls;
    }

    /**
     * The crafting storage provider normally claims produced items before they reach regular network storage.
     * Recover a final output that slipped through that routing boundary, but only after every task that can
     * produce the same key has been dispatched. This keeps pending production protected from consuming stock
     * that belongs to a future dispatch.
     */
    private void recoverFinalOutputFromNetwork() {
        TransfiniteCraftingJob currentJob = this.job;
        if (currentJob == null || currentJob.getFinalOutput() == null) {
            return;
        }

        AEKey finalKey = currentJob.getFinalOutput().what();
        long waiting = currentJob.getWaitingFor().extract(finalKey, Long.MAX_VALUE, Actionable.SIMULATE);
        if (waiting <= 0 || getPendingOutputs(finalKey) > 0) {
            return;
        }

        IGrid grid = this.cpu.getGrid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        long available = storage.extract(finalKey, waiting, Actionable.SIMULATE, this.cpu.getActionSource());
        if (available <= 0) {
            return;
        }

        long extracted = storage.extract(finalKey, Math.min(waiting, available), Actionable.MODULATE,
                this.cpu.getActionSource());
        if (extracted > 0) {
            long inserted = insert(finalKey, extracted, Actionable.MODULATE);
            if (inserted < extracted) {
                long remainder = extracted - inserted;
                long returned = storage.insert(finalKey, remainder, Actionable.MODULATE, this.cpu.getActionSource());
                if (returned < remainder) {
                    this.inventory.insert(finalKey, remainder - returned, Actionable.MODULATE);
                }
            }
        }
    }

    public long insert(AEKey what, long amount, Actionable mode) {
        TransfiniteCraftingJob currentJob = this.job;
        if (what == null || currentJob == null || amount <= 0) {
            return 0;
        }

        long accepted = currentJob.getWaitingFor().extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }
        accepted = Math.min(amount, accepted);

        long inserted = accepted;
        if (what.matches(currentJob.getFinalOutput())) {
            inserted = currentJob.getLink().insert(what, accepted, mode);
        }

        if (mode == Actionable.MODULATE) {
            ((ElapsedTimeTrackerAccessor) currentJob.getTimeTracker()).invokeDecrementItems(
                    accepted, what.getType());
            currentJob.getWaitingFor().extract(what, accepted, Actionable.MODULATE);
            if (what.matches(currentJob.getFinalOutput())) {
                postChange(what);
                long remaining = Math.max(0, currentJob.getRemainingAmount() - accepted);
                currentJob.setRemainingAmount(remaining);
                if (remaining == 0) {
                    finishJob(true);
                }
            } else {
                this.inventory.insert(what, accepted, Actionable.MODULATE);
            }
            markChanged();
        }
        return inserted;
    }

    public void cancel() {
        if (this.job != null) {
            finishJob(false);
        }
    }

    private void finishJob(boolean success) {
        TransfiniteCraftingJob completedJob = this.job;
        if (completedJob == null) {
            return;
        }
        if (success) {
            completedJob.getLink().markDone();
        } else {
            completedJob.getLink().cancel();
        }

        for (var waiting : completedJob.getWaitingFor().list) {
            this.cpu.getHost().updateWaitingIndex(this.cpu, waiting.getKey(), false);
        }
        completedJob.getWaitingFor().clear();
        for (var task : completedJob.getTasks().object2LongEntrySet()) {
            for (var output : task.getKey().getOutputs()) {
                postChange(output.what());
            }
        }
        notifyJobOwner(completedJob, success ? CraftingJobStatusPacket.Status.FINISHED :
                CraftingJobStatusPacket.Status.CANCELLED);
        this.job = null;
        this.workingDispatchReasons.clear();
        storeItems();
        markChanged();
    }

    public void storeItems() {
        Preconditions.checkState(this.job == null,
                "CPU should not have a job while returning its inventory");
        if (this.inventory.list.isEmpty()) {
            return;
        }
        IGrid grid = this.cpu.getGrid();
        if (grid == null) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        int attempted = 0;
        int consecutiveFailures = 0;
        boolean changed = false;
        for (var entry : this.inventory.list) {
            if (attempted >= STORE_BATCH_SIZE || consecutiveFailures >= MAX_CONSECUTIVE_STORE_FAILURES) {
                break;
            }
            attempted++;
            long inserted = storage.insert(
                    entry.getKey(), entry.getLongValue(), Actionable.MODULATE, this.cpu.getActionSource());
            if (inserted <= 0) {
                consecutiveFailures++;
                continue;
            }
            consecutiveFailures = 0;
            changed = true;
            postChange(entry.getKey());
            entry.setValue(entry.getLongValue() - inserted);
        }
        if (changed) {
            this.inventory.list.removeZeros();
            markChanged();
        }
    }

    public void readFromNbt(CompoundTag data) {
        this.inventory.readFromNBT(data.getList("inventory", CompoundTag.TAG_COMPOUND));
        if (data.contains("job", CompoundTag.TAG_COMPOUND)) {
            this.job = new TransfiniteCraftingJob(data.getCompound("job"), this);
            if (this.job.getFinalOutput() == null) {
                finishJob(false);
            } else {
                indexAllWaitingItems();
            }
        }
    }

    public void writeToNbt(CompoundTag data) {
        data.put("inventory", this.inventory.writeToNBT());
        if (this.job != null) {
            data.put("job", this.job.writeToNbt());
        }
    }

    public boolean hasJob() {
        return this.job != null;
    }

    public boolean canBeRemoved() {
        return this.job == null && this.inventory.list.isEmpty();
    }

    public @Nullable GenericStack getFinalJobOutput() {
        return this.job == null ? null : this.job.getFinalOutput();
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        return this.job == null ? EMPTY_TIME_TRACKER : this.job.getTimeTracker();
    }

    public @Nullable ICraftingLink getLastLink() {
        return this.job == null ? null : this.job.getLink();
    }

    public long getLastModifiedOnTick() {
        return this.lastModifiedOnTick;
    }

    public void addListener(Consumer<AEKey> listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        this.listeners.remove(listener);
    }

    public long getStored(AEKey key) {
        return this.inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey key) {
        return this.job == null ? 0 :
                this.job.getWaitingFor().extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public boolean isRequesting(AEKey key) {
        if (this.job == null) {
            return false;
        }
        GenericStack finalOutput = this.job.getFinalOutput();
        return finalOutput != null && key.matches(finalOutput) || this.getWaitingFor(key) > 0;
    }

    public boolean isRequestingAny() {
        return this.job != null;
    }

    public long getPendingOutputs(AEKey key) {
        long count = 0;
        if (this.job != null) {
            for (var task : this.job.getTasks().object2LongEntrySet()) {
                for (var output : task.getKey().getOutputs()) {
                    if (key.matches(output)) {
                        count = NumberUtils.saturatedAdd(count,
                                NumberUtils.saturatedMultiply(output.amount(), task.getLongValue()));
                    }
                }
            }
        }
        return count;
    }

    public void getAllItems(KeyCounter output) {
        output.addAll(this.inventory.list);
        if (this.job == null) {
            return;
        }
        output.addAll(this.job.getWaitingFor().list);
        for (var task : this.job.getTasks().object2LongEntrySet()) {
            for (var taskOutput : task.getKey().getOutputs()) {
                output.add(taskOutput.what(),
                        NumberUtils.saturatedMultiply(taskOutput.amount(), task.getLongValue()));
            }
        }
    }

    public boolean isCantStoreItems() {
        return this.cantStoreItems;
    }

    TransfiniteCraftingCPU getCpu() {
        return this.cpu;
    }

    void onWaitingForChanged(AEKey key) {
        postChange(key);
        this.cpu.getHost().updateWaitingIndex(this.cpu, key, getWaitingFor(key) > 0);
    }

    private void indexAllWaitingItems() {
        if (this.job == null) {
            return;
        }
        for (var entry : this.job.getWaitingFor().list) {
            this.cpu.getHost().updateWaitingIndex(this.cpu, entry.getKey(), true);
        }
    }

    private void markChanged() {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        this.dirty = true;
        if (!this.batchingChanges) {
            flushDirty();
        }
    }

    private void flushDirty() {
        if (this.dirty) {
            this.dirty = false;
            this.cpu.markDirty();
        }
    }

    private void postChange(AEKey key) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : this.listeners) {
            listener.accept(key);
        }
    }

    private void notifyJobOwner(TransfiniteCraftingJob changedJob, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        Integer playerId = changedJob.getPlayerId();
        if (playerId == null) {
            return;
        }
        var server = this.cpu.getLevel().getServer();
        var player = IPlayerRegistry.getConnected(server, playerId);
        if (player != null && changedJob.getFinalOutput() != null) {
            NetworkHandler.instance().sendTo(new CraftingJobStatusPacket(
                    changedJob.getLink().getCraftingID(),
                    changedJob.getFinalOutput().what(),
                    changedJob.getFinalOutput().amount(),
                    changedJob.getRemainingAmount(),
                    status), player);
        }
    }

    private static boolean isWrittenBookOutput(@Nullable GenericStack output) {
        return output != null && output.what() instanceof AEItemKey itemKey &&
                itemKey.getItem() == Items.WRITTEN_BOOK && itemKey.hasTag() &&
                itemKey.getTag().contains("display");
    }

    @Override
    public boolean gtlcore$isJobSuspended() {
        return this.job != null && this.job.isSuspended();
    }

    @Override
    public void gtlcore$setJobSuspended(boolean suspended) {
        if (this.job != null && this.job.isSuspended() != suspended) {
            this.job.setSuspended(suspended);
            markChanged();
        }
    }

    @Override
    public int gtlcore$getDispatchReasonMask(AEKey key) {
        return this.publishedDispatchReasons.getOrDefault(key, 0);
    }

    private void recordTaskReason(IPatternDetails details, int reasonMask) {
        if (!this.collectDispatchReasons) {
            return;
        }
        if (reasonMask == 0) {
            this.workingDispatchReasons.remove(details);
        } else {
            this.workingDispatchReasons.put(details, reasonMask);
        }
    }

    private void markAllRemaining(CraftingDispatchReason reason) {
        if (!this.collectDispatchReasons || this.job == null) {
            return;
        }
        for (IPatternDetails details : this.job.getTasks().keySet()) {
            this.workingDispatchReasons.put(details, reason.mask());
        }
    }

    private void markAllUnclassified(CraftingDispatchReason reason) {
        if (!this.collectDispatchReasons || this.job == null) {
            return;
        }
        for (IPatternDetails details : this.job.getTasks().keySet()) {
            this.workingDispatchReasons.putIfAbsent(details, reason.mask());
        }
    }

    private void publishDispatchReasons() {
        if (!this.collectDispatchReasons) {
            this.publishedDispatchReasons = Map.of();
            return;
        }
        Map<AEKey, Integer> current = new Object2IntOpenHashMap<>();
        if (this.job != null) {
            for (IPatternDetails details : this.job.getTasks().keySet()) {
                int reasonMask = this.workingDispatchReasons.getOrDefault(details, 0);
                if (reasonMask == 0) {
                    continue;
                }
                for (GenericStack output : details.getOutputs()) {
                    current.merge(output.what(), reasonMask, (existing, added) -> existing | added);
                }
            }
        }
        for (AEKey changed : CraftingDispatchReasonState.changedKeys(this.publishedDispatchReasons, current)) {
            postChange(changed);
        }
        this.publishedDispatchReasons = Map.copyOf(current);

        if (this.job != null && this.job.getTasks().isEmpty() && isWrittenBookOutput(getFinalJobOutput())) {
            finishJob(true);
        }
    }
}
