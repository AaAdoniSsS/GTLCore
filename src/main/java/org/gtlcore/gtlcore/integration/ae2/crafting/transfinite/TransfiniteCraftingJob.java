package org.gtlcore.gtlcore.integration.ae2.crafting.transfinite;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingJobSuspensionState;
import org.gtlcore.gtlcore.mixin.ae2.logic.ElapsedTimeTrackerAccessor;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

final class TransfiniteCraftingJob {

    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";

    private final CraftingLink link;
    private final ListCraftingInventory waitingFor;
    private final Object2LongMap<IPatternDetails> tasks = new Object2LongOpenHashMap<>();
    private final ElapsedTimeTracker timeTracker;
    private final @Nullable Integer playerId;

    private GenericStack finalOutput;
    private long remainingAmount;
    private boolean suspended;

    TransfiniteCraftingJob(ICraftingPlan plan, KeyCounter extractionShortfall, CraftingLink link,
                           @Nullable Integer playerId, ListCraftingInventory.ChangeListener waitingForListener) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = this.finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(waitingForListener);
        this.timeTracker = new ElapsedTimeTracker();
        this.link = link;
        this.playerId = playerId;

        for (var entry : plan.emittedItems()) {
            this.waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            addTrackedItems(entry.getLongValue(), entry.getKey());
        }
        for (var entry : plan.missingItems()) {
            addMissingInput(entry.getKey(), entry.getLongValue());
        }
        for (var entry : extractionShortfall) {
            addMissingInput(entry.getKey(), entry.getLongValue());
        }
        for (var entry : plan.patternTimes().entrySet()) {
            this.tasks.put(entry.getKey(), NumberUtils.saturatedAdd(
                    this.tasks.getLong(entry.getKey()), entry.getValue()));
            for (var output : entry.getKey().getOutputs()) {
                long outputAmount = NumberUtils.saturatedMultiply(output.amount(), entry.getValue());
                outputAmount = NumberUtils.saturatedMultiply(outputAmount, output.what().getAmountPerUnit());
                addTrackedItems(outputAmount, output.what());
            }
        }
    }

    TransfiniteCraftingJob(CompoundTag data, TransfiniteCraftingLogic logic) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), logic.getCpu());
        this.finalOutput = GenericStack.readTag(data.getCompound(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.waitingFor = new ListCraftingInventory(logic::onWaitingForChanged);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND));
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        this.playerId = data.contains(NBT_PLAYER_ID, Tag.TAG_INT) ? data.getInt(NBT_PLAYER_ID) : null;
        this.suspended = data.getBoolean(CraftingJobSuspensionState.NBT_SUSPENDED);

        ListTag taskList = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < taskList.size(); i++) {
            CompoundTag taskTag = taskList.getCompound(i);
            AEItemKey pattern = AEItemKey.fromTag(taskTag);
            IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, logic.getCpu().getLevel());
            long progress = taskTag.getLong(NBT_CRAFTING_PROGRESS);
            if (details != null && progress > 0) {
                this.tasks.put(details, progress);
            }
        }
    }

    CompoundTag writeToNbt() {
        CompoundTag data = new CompoundTag();
        CompoundTag linkData = new CompoundTag();
        this.link.writeToNBT(linkData);
        data.put(NBT_LINK, linkData);
        data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(this.finalOutput));
        data.put(NBT_WAITING_FOR, this.waitingFor.writeToNBT());
        data.put(NBT_TIME_TRACKER, this.timeTracker.writeToNBT());

        ListTag taskList = new ListTag();
        for (var entry : this.tasks.object2LongEntrySet()) {
            CompoundTag taskTag = entry.getKey().getDefinition().toTag();
            taskTag.putLong(NBT_CRAFTING_PROGRESS, entry.getLongValue());
            taskList.add(taskTag);
        }
        data.put(NBT_TASKS, taskList);
        data.putLong(NBT_REMAINING_AMOUNT, this.remainingAmount);
        if (this.playerId != null) {
            data.putInt(NBT_PLAYER_ID, this.playerId);
        }
        data.putBoolean(CraftingJobSuspensionState.NBT_SUSPENDED, this.suspended);
        return data;
    }

    private void addTrackedItems(long amount, AEKey key) {
        ((ElapsedTimeTrackerAccessor) this.timeTracker).invokeAddMaxItems(amount, key.getType());
    }

    private void addMissingInput(AEKey key, long amount) {
        this.waitingFor.insert(key, amount, Actionable.MODULATE);
        addTrackedItems(amount, key);
    }

    CraftingLink getLink() {
        return this.link;
    }

    ListCraftingInventory getWaitingFor() {
        return this.waitingFor;
    }

    Object2LongMap<IPatternDetails> getTasks() {
        return this.tasks;
    }

    ElapsedTimeTracker getTimeTracker() {
        return this.timeTracker;
    }

    @Nullable
    Integer getPlayerId() {
        return this.playerId;
    }

    GenericStack getFinalOutput() {
        return this.finalOutput;
    }

    long getRemainingAmount() {
        return this.remainingAmount;
    }

    void setRemainingAmount(long remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    boolean isSuspended() {
        return this.suspended;
    }

    void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }
}
