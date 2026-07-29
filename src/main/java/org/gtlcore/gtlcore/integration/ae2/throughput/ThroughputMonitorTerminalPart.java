package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.storage.MEStorage;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import java.util.List;

public class ThroughputMonitorTerminalPart extends AbstractDisplayPart
                                           implements IViewCellStorage, InternalInventoryHost {

    public static final int VIEW_CELL_SLOT_COUNT = 5;
    private static final String VIEW_CELL_NBT_KEY = "viewCell";
    private static final ResourceLocation MODEL_OFF = GTLCore.id("part/throughput_monitor_terminal_off");
    private static final ResourceLocation MODEL_ON = GTLCore.id("part/throughput_monitor_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static void registerModels() {
        PartModels.registerModels(MODEL_OFF, MODEL_ON);
    }

    private final ThroughputMonitorCollector collector = new ThroughputMonitorCollector();
    private final AppEngInternalInventory viewCells = new AppEngInternalInventory(this, VIEW_CELL_SLOT_COUNT);
    private int openMenuCount;

    public ThroughputMonitorTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        updateStorageTracker();
    }

    @Override
    public void removeFromWorld() {
        unregisterStorageTracker();
        super.removeFromWorld();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack viewCell : viewCells) {
            if (!viewCell.isEmpty()) {
                drops.add(viewCell);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        viewCells.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        viewCells.readFromNBT(data, VIEW_CELL_NBT_KEY);
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        viewCells.writeToNBT(data, VIEW_CELL_NBT_KEY);
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return viewCells;
    }

    @Override
    public void saveChanges() {
        getHost().markForSave();
    }

    @Override
    public void onChangeInventory(InternalInventory inventory, int slot) {
        saveChanges();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        updateStorageTracker();
        super.onMainNodeStateChanged(reason);
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!super.onPartActivate(player, hand, pos) && !isClientSide() &&
                player instanceof ServerPlayer serverPlayer && getMainNode().isActive() &&
                Platform.hasPermissions(getHost().getLocation(), player)) {
            ThroughputMonitorTerminalMenu.open(serverPlayer, this);
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    List<ThroughputMonitorCollector.Snapshot> getSnapshots() {
        registerStorageTracker();
        return collector.getSnapshots();
    }

    void openMonitor() {
        openMenuCount++;
        registerStorageTracker();
    }

    void closeMonitor() {
        if (openMenuCount > 0 && --openMenuCount == 0) {
            unregisterStorageTracker();
        }
    }

    private void updateStorageTracker() {
        if (openMenuCount > 0) {
            registerStorageTracker();
        } else {
            unregisterStorageTracker();
        }
    }

    private void registerStorageTracker() {
        if (isClientSide() || !getMainNode().isActive()) {
            if (!isClientSide()) {
                unregisterStorageTracker();
            }
            return;
        }

        getMainNode().ifPresent((grid, node) -> {
            MEStorage storage = grid.getStorageService().getInventory();
            collector.attach(storage);
        });
    }

    private void unregisterStorageTracker() {
        collector.close();
    }
}
