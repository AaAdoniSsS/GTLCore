package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteComputationArrayLifecycleLogger;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MECraftingCPUInterfacePartMachine extends MEIOPartMachine {

    private static final int MAX_CPU_NAME_LENGTH = 64;
    private static final int UI_WIDTH = 176;
    private static final int UI_HEIGHT = 80;
    private static final int CONTENT_X = 8;
    private static final int NETWORK_STATUS_Y = 3;
    private static final int CPU_NAME_LABEL_Y = 16;
    private static final int CPU_NAME_FIELD_Y = 27;
    private static final int CPU_NAME_FIELD_HEIGHT = 14;
    private static final int CPU_NAME_FIELD_WIDTH = UI_WIDTH - CONTENT_X * 2;
    private static final int PARALLELISM_LABEL_Y = 47;
    private static final int PARALLELISM_FIELD_Y = 58;
    private static final int PARALLELISM_FIELD_HEIGHT = 14;
    private static final int PARALLELISM_FIELD_WIDTH = UI_WIDTH - CONTENT_X * 2;
    private static final int MAX_PARALLELISM_TEXT_LENGTH = Long.toString(TransfiniteComputationArrayMachine.MAX_PARALLELISM).length();
    private static final String NBT_PARALLELISM = "Parallelism";
    private static final String NBT_LINKED_CONTROLLER = "LinkedController";
    private static final long NO_LINKED_CONTROLLER = Long.MIN_VALUE;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MECraftingCPUInterfacePartMachine.class, MEIOPartMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    @DescSynced
    private String customName = "";

    @DescSynced
    private long parallelism = TransfiniteComputationArrayMachine.DEFAULT_PARALLELISM;

    @DescSynced
    private long linkedControllerPos = NO_LINKED_CONTROLLER;

    @Nullable
    private TickableSubscription updateSubs;
    private boolean needsCraftingCpuSync = true;

    public MECraftingCPUInterfacePartMachine(IMachineBlockEntity holder) {
        super(holder, IO.NONE);
    }

    @Override
    public boolean canShared() {
        return false;
    }

    public List<TransfiniteComputationArrayMachine> getTransfiniteControllers() {
        List<TransfiniteComputationArrayMachine> controllers = getControllers().stream()
                .filter(TransfiniteComputationArrayMachine.class::isInstance)
                .map(TransfiniteComputationArrayMachine.class::cast)
                .toList();
        if (!controllers.isEmpty()) {
            return controllers;
        }

        var level = getLevel();
        if (level == null) {
            return List.of();
        }
        BlockPos controllerPos = this.linkedControllerPos == NO_LINKED_CONTROLLER ?
                findAdjacentController(level) : BlockPos.of(this.linkedControllerPos);
        if (controllerPos == null) {
            return List.of();
        }
        MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
        if (machine instanceof TransfiniteComputationArrayMachine controller && controller.isFormed()) {
            if (this.linkedControllerPos != controllerPos.asLong()) {
                this.linkedControllerPos = controllerPos.asLong();
                markDirty();
            }
            controller.restoreNetworkInterface(this);
            return List.of(controller);
        }
        return List.of();
    }

    @Nullable
    private BlockPos findAdjacentController(Level level) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = getPos().relative(direction);
            MetaMachine machine = MetaMachine.getMachine(level, candidate);
            if (machine instanceof TransfiniteComputationArrayMachine controller && controller.isFormed()) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void addedToController(@NotNull IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof TransfiniteComputationArrayMachine transfiniteController) {
            long controllerPos = transfiniteController.getPos().asLong();
            if (this.linkedControllerPos != controllerPos) {
                this.linkedControllerPos = controllerPos;
                markDirty();
            }
            notifyCraftingCpuChange();
        }
    }

    @Override
    public void removedFromController(@NotNull IMultiController controller) {
        super.removedFromController(controller);
        if (controller instanceof TransfiniteComputationArrayMachine transfiniteController &&
                this.linkedControllerPos == transfiniteController.getPos().asLong()) {
            this.linkedControllerPos = NO_LINKED_CONTROLLER;
            markDirty();
            notifyCraftingCpuChange();
        }
    }

    public IActionSource getActionSource() {
        return this.actionSource;
    }

    public Component resolveCpuName(Component defaultName) {
        return this.customName.isEmpty() ? defaultName : Component.literal(this.customName);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        this.customName = normalizeCpuName(this.customName);
        this.parallelism = tag.contains(NBT_PARALLELISM, Tag.TAG_ANY_NUMERIC) ?
                Math.max(TransfiniteComputationArrayMachine.MIN_PARALLELISM, tag.getLong(NBT_PARALLELISM)) :
                TransfiniteComputationArrayMachine.DEFAULT_PARALLELISM;
        this.linkedControllerPos = tag.contains(NBT_LINKED_CONTROLLER, Tag.TAG_ANY_NUMERIC) ?
                tag.getLong(NBT_LINKED_CONTROLLER) : NO_LINKED_CONTROLLER;
    }

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putLong(NBT_PARALLELISM, this.parallelism);
        if (this.linkedControllerPos != NO_LINKED_CONTROLLER) {
            tag.putLong(NBT_LINKED_CONTROLLER, this.linkedControllerPos);
        } else {
            tag.remove(NBT_LINKED_CONTROLLER);
        }
    }

    @Override
    public @NotNull Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, UI_WIDTH, UI_HEIGHT);
        group.addWidget(new LabelWidget(CONTENT_X, NETWORK_STATUS_Y, () -> this.isOnline ?
                "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline"));
        group.addWidget(new LabelWidget(CONTENT_X, CPU_NAME_LABEL_Y, () -> Component.translatable(
                "gui.gtlcore.me_crafting_cpu_interface.cpu_name", getDisplayedCpuName()).getString()));
        group.addWidget(new AETextInputButtonWidget(
                CONTENT_X, CPU_NAME_FIELD_Y, CPU_NAME_FIELD_WIDTH, CPU_NAME_FIELD_HEIGHT)
                .setText(this.customName)
                .setOnConfirm(this::setCustomName)
                .setButtonTooltips(
                        Component.translatable("gui.gtlcore.me_crafting_cpu_interface.cpu_name.rename"),
                        Component.translatable("gui.gtlcore.me_crafting_cpu_interface.cpu_name.tooltip")));
        group.addWidget(new LabelWidget(CONTENT_X, PARALLELISM_LABEL_Y,
                "gui.gtlcore.transfinite_computation_array.parallelism"));
        group.addWidget(new TextFieldWidget(
                CONTENT_X, PARALLELISM_FIELD_Y, PARALLELISM_FIELD_WIDTH, PARALLELISM_FIELD_HEIGHT,
                this::getParallelismText, this::setParallelismFromText)
                .setNumbersOnly(
                        TransfiniteComputationArrayMachine.MIN_PARALLELISM,
                        TransfiniteComputationArrayMachine.MAX_PARALLELISM)
                .setMaxStringLength(MAX_PARALLELISM_TEXT_LENGTH));
        return group;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        var controllerPositions = getTransfiniteControllers().stream()
                .map(TransfiniteComputationArrayMachine::getPos)
                .toList();
        TransfiniteComputationArrayLifecycleLogger.logNetworkCheckStarted(
                getLevel(), getPos(), controllerPositions, reason, this.isOnline,
                getMainNode().isOnline(), getMainNode().isPowered(),
                getMainNode().isActive(), getMainNode().getGrid() != null);
        boolean lifecycleLogging = TransfiniteComputationArrayLifecycleLogger.isEnabled();
        long startedAtNanos = lifecycleLogging ? System.nanoTime() : 0L;
        boolean onlineBefore = this.isOnline;
        super.onMainNodeStateChanged(reason);
        long superclassFinishedAtNanos = lifecycleLogging ? System.nanoTime() : 0L;
        updateSubscription();
        notifyCraftingCpuChange();
        long finishedAtNanos = lifecycleLogging ? System.nanoTime() : 0L;
        TransfiniteComputationArrayLifecycleLogger.logNetworkCheck(
                getLevel(), getPos(), controllerPositions, reason, onlineBefore, this.isOnline,
                getMainNode().isOnline(), getMainNode().isPowered(),
                getMainNode().isActive(), getMainNode().getGrid() != null,
                superclassFinishedAtNanos - startedAtNanos,
                finishedAtNanos - superclassFinishedAtNanos, finishedAtNanos - startedAtNanos);
    }

    public void notifyCraftingCpuChange() {
        if (isRemote()) {
            return;
        }
        this.needsCraftingCpuSync = true;
        postCraftingCpuChange();
    }

    private void postCraftingCpuChange() {
        getMainNode().ifPresent((grid, node) -> grid.postEvent(new GridCraftingCpuChange(node)));
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.updateSubs != null) {
            this.updateSubs.unsubscribe();
            this.updateSubs = null;
        }
    }

    private void updateSubscription() {
        if (getMainNode().isOnline()) {
            this.updateSubs = subscribeServerTick(this.updateSubs, this::update);
        } else if (this.updateSubs != null) {
            this.updateSubs.unsubscribe();
            this.updateSubs = null;
        }
    }

    private void update() {
        if (!this.needsCraftingCpuSync || getTransfiniteControllers().isEmpty()) {
            return;
        }
        this.needsCraftingCpuSync = false;
        postCraftingCpuChange();
    }

    private Component getDisplayedCpuName() {
        TransfiniteComputationArrayMachine controller = getTransfiniteController();
        Component defaultName = controller == null ?
                Component.translatable("block.gtceu.transfinite_computation_array") :
                controller.getBlockState().getBlock().getName();
        return resolveCpuName(defaultName);
    }

    @Nullable
    private TransfiniteComputationArrayMachine getTransfiniteController() {
        return getTransfiniteControllers().stream().findFirst().orElse(null);
    }

    private String getParallelismText() {
        return Long.toString(this.parallelism);
    }

    private void setParallelismFromText(String text) {
        try {
            setParallelism(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            setParallelism(TransfiniteComputationArrayMachine.DEFAULT_PARALLELISM);
        }
    }

    public long getParallelism() {
        return this.parallelism;
    }

    public void setParallelism(long parallelism) {
        long clamped = Math.max(TransfiniteComputationArrayMachine.MIN_PARALLELISM, parallelism);
        if (this.parallelism == clamped) {
            return;
        }
        this.parallelism = clamped;
        markDirty();
        notifyCraftingCpuChange();
    }

    private void setCustomName(String name) {
        String normalizedName = normalizeCpuName(name);
        if (this.customName.equals(normalizedName)) {
            return;
        }
        this.customName = normalizedName;
        markDirty();
        notifyCraftingCpuChange();
    }

    private static String normalizeCpuName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String normalizedName = name.strip();
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_CPU_NAME_LENGTH) {
            normalizedName = normalizedName.substring(
                    0, normalizedName.offsetByCodePoints(0, MAX_CPU_NAME_LENGTH));
        }
        return normalizedName;
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
