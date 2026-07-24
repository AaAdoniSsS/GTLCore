package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MECraftingCPUInterfacePartMachine extends MEIOPartMachine {

    private static final int MAX_CPU_NAME_LENGTH = 64;
    private static final int UI_WIDTH = 176;
    private static final int UI_HEIGHT = 48;
    private static final int CONTENT_X = 8;
    private static final int NETWORK_STATUS_Y = 3;
    private static final int CPU_NAME_LABEL_Y = 16;
    private static final int CPU_NAME_FIELD_Y = 27;
    private static final int CPU_NAME_FIELD_HEIGHT = 14;
    private static final int CPU_NAME_FIELD_WIDTH = UI_WIDTH - CONTENT_X * 2;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MECraftingCPUInterfacePartMachine.class, MEIOPartMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    @DescSynced
    private String customName = "";

    public MECraftingCPUInterfacePartMachine(IMachineBlockEntity holder) {
        super(holder, IO.NONE);
    }

    @Override
    public boolean canShared() {
        return false;
    }

    public List<TransfiniteComputationArrayMachine> getTransfiniteControllers() {
        return getControllers().stream()
                .filter(TransfiniteComputationArrayMachine.class::isInstance)
                .map(TransfiniteComputationArrayMachine.class::cast)
                .toList();
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
        return group;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        super.onMainNodeStateChanged(reason);
        notifyCraftingCpuChange();
    }

    public void notifyCraftingCpuChange() {
        if (isRemote()) {
            return;
        }
        getMainNode().ifPresent((grid, node) -> grid.postEvent(new GridCraftingCpuChange(node)));
    }

    private Component getDisplayedCpuName() {
        Component defaultName = getTransfiniteControllers().stream()
                .findFirst()
                .map(controller -> controller.getBlockState().getBlock().getName())
                .orElseGet(() -> Component.translatable("block.gtceu.transfinite_computation_array"));
        return resolveCpuName(defaultName);
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
