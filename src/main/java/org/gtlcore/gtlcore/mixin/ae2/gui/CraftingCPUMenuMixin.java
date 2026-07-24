package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingJobSuspensionState;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspensionMenu;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStatusBulkActions;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStatusReasons;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin extends AEBaseMenu implements ICraftingJobSuspensionMenu, ICraftingStatusBulkActions {

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    @Shadow(remap = false)
    private CraftingCPUCluster cpu;

    @Shadow(remap = false)
    @Final
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Shadow(remap = false)
    @Final
    private Consumer<AEKey> cpuChangeListener;

    @Shadow(remap = false)
    public appeng.api.config.CpuSelectionMode schedulingMode;

    @Shadow(remap = false)
    public boolean cantStoreItems;

    @Unique
    private TransfiniteCraftingCPU gtlcore$transfiniteCpu;

    @Unique
    @GuiSync(CraftingJobSuspensionState.GUI_SYNC_SUSPENDED)
    private boolean gtlcore$jobSuspended;

    @Unique
    @GuiSync(CraftingJobSuspensionState.GUI_SYNC_AVAILABLE)
    private boolean gtlcore$jobSuspensionAvailable;

    protected CraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$registerToggleSchedulingAction(MenuType<?> menuType, int id, Inventory playerInventory,
                                                        Object host, CallbackInfo ci) {
        this.registerClientAction(CraftingJobSuspensionState.ACTION_TOGGLE_SCHEDULING, this::gtlcore$toggleScheduling);
        if ((Object) this instanceof CraftingStatusMenu) {
            this.registerClientAction(ICraftingStatusBulkActions.ACTION_SUSPEND_ALL,
                    this::gtlcore$suspendAllCrafting);
            this.registerClientAction(ICraftingStatusBulkActions.ACTION_CANCEL_ALL,
                    this::gtlcore$cancelAllCrafting);
        }
    }

    @Inject(method = "setCPU", at = @At("RETURN"), remap = false)
    private void gtlcore$refreshJobSuspensionOnCpuChange(ICraftingCPU cpu, CallbackInfo ci) {
        gtlcore$refreshJobSuspensionSync();
    }

    @Inject(method = "setCPU", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$setTransfiniteCpu(ICraftingCPU selectedCpu, CallbackInfo ci) {
        if (this.gtlcore$transfiniteCpu != null) {
            this.gtlcore$transfiniteCpu.getCraftingLogic().removeListener(this.cpuChangeListener);
        }
        if (!(selectedCpu instanceof TransfiniteCraftingCPU transfiniteCpu)) {
            this.gtlcore$transfiniteCpu = null;
            return;
        }

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(this.cpuChangeListener);
        }
        this.cpu = null;
        this.incrementalUpdateHelper.reset();
        this.gtlcore$transfiniteCpu = transfiniteCpu;

        KeyCounter allItems = new KeyCounter();
        transfiniteCpu.getCraftingLogic().getAllItems(allItems);
        for (var entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
        }
        transfiniteCpu.getCraftingLogic().addListener(this.cpuChangeListener);
        gtlcore$refreshJobSuspensionSync();
        ci.cancel();
    }

    @Inject(method = "broadcastChanges",
            at = @At(value = "INVOKE",
                     target = "Lappeng/menu/AEBaseMenu;broadcastChanges()V"))
    private void gtlcore$refreshJobSuspensionBeforeSync(CallbackInfo ci) {
        gtlcore$refreshJobSuspensionSync();
    }

    @Override
    @Unique
    public boolean gtlcore$isJobSuspensionAvailable() {
        return this.gtlcore$jobSuspensionAvailable;
    }

    @Override
    @Unique
    public boolean gtlcore$isJobSuspended() {
        return this.gtlcore$jobSuspended;
    }

    @Override
    @Unique
    public void gtlcore$toggleScheduling() {
        if (this.isClientSide()) {
            if (this.gtlcore$jobSuspensionAvailable) {
                this.gtlcore$jobSuspended = CraftingJobSuspensionState.toggled(this.gtlcore$jobSuspended);
            }
            this.sendClientAction(CraftingJobSuspensionState.ACTION_TOGGLE_SCHEDULING);
            return;
        }

        if (this.gtlcore$transfiniteCpu != null && this.gtlcore$transfiniteCpu.isBusy()) {
            this.gtlcore$transfiniteCpu.getCraftingLogic().gtlcore$toggleJobSuspended();
        } else if (this.cpu != null && this.cpu.isBusy()) {
            ((ICraftingJobSuspension) this.cpu.craftingLogic).gtlcore$toggleJobSuspended();
            this.cpu.markDirty();
        }

        gtlcore$refreshJobSuspensionSync();
    }

    @Override
    @Unique
    public void gtlcore$suspendAllCrafting() {
        if (this.isClientSide()) {
            this.sendClientAction(ICraftingStatusBulkActions.ACTION_SUSPEND_ALL);
            return;
        }
        if (this.grid == null) {
            return;
        }

        for (ICraftingCPU craftingCpu : this.grid.getCraftingService().getCpus()) {
            if (!craftingCpu.isBusy()) {
                continue;
            }
            ICraftingJobSuspension suspension = null;
            if (craftingCpu instanceof CraftingCPUCluster cluster) {
                suspension = (ICraftingJobSuspension) cluster.craftingLogic;
            } else if (craftingCpu instanceof TransfiniteCraftingCPU transfiniteCpu) {
                suspension = transfiniteCpu.getCraftingLogic();
            }
            if (suspension != null) {
                if (!suspension.gtlcore$isJobSuspended()) {
                    suspension.gtlcore$setJobSuspended(true);
                    if (craftingCpu instanceof CraftingCPUCluster cluster) {
                        cluster.markDirty();
                    }
                }
            }
        }
        gtlcore$refreshJobSuspensionSync();
    }

    @Override
    @Unique
    public void gtlcore$cancelAllCrafting() {
        if (this.isClientSide()) {
            this.sendClientAction(ICraftingStatusBulkActions.ACTION_CANCEL_ALL);
            return;
        }
        if (this.grid == null) {
            return;
        }

        for (ICraftingCPU craftingCpu : this.grid.getCraftingService().getCpus()) {
            if (craftingCpu.isBusy()) {
                craftingCpu.cancelJob();
            }
        }
        gtlcore$refreshJobSuspensionSync();
    }

    @Unique
    private void gtlcore$refreshJobSuspensionSync() {
        if (this.gtlcore$transfiniteCpu != null) {
            this.gtlcore$jobSuspensionAvailable = this.gtlcore$transfiniteCpu.isBusy();
            this.gtlcore$jobSuspended = this.gtlcore$jobSuspensionAvailable &&
                    this.gtlcore$transfiniteCpu.getCraftingLogic().gtlcore$isJobSuspended();
        } else {
            this.gtlcore$jobSuspensionAvailable = this.cpu != null && this.cpu.isBusy();
            this.gtlcore$jobSuspended = this.gtlcore$jobSuspensionAvailable &&
                    ((ICraftingJobSuspension) this.cpu.craftingLogic).gtlcore$isJobSuspended();
        }
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"), remap = false)
    private void gtlcore$cancelTransfiniteCrafting(CallbackInfo ci) {
        if (this.isServerSide() && this.gtlcore$transfiniteCpu != null) {
            this.gtlcore$transfiniteCpu.cancelJob();
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void gtlcore$removeTransfiniteListener(Player player, CallbackInfo ci) {
        if (this.gtlcore$transfiniteCpu != null) {
            this.gtlcore$transfiniteCpu.getCraftingLogic().removeListener(this.cpuChangeListener);
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void gtlcore$broadcastTransfiniteStatus(CallbackInfo ci) {
        if (!this.isServerSide() || this.gtlcore$transfiniteCpu == null) {
            return;
        }
        TransfiniteCraftingLogic logic = this.gtlcore$transfiniteCpu.getCraftingLogic();
        this.schedulingMode = this.gtlcore$transfiniteCpu.getSelectionMode();
        this.cantStoreItems = logic.isCantStoreItems();
        if (this.incrementalUpdateHelper.hasChanges()) {
            CraftingStatus status = gtlcore$createStatus(this.incrementalUpdateHelper, logic);
            this.incrementalUpdateHelper.commitChanges();
            sendPacketToClient(new CraftingStatusPacket(status));
        }
    }

    @Unique
    private static CraftingStatus gtlcore$createStatus(IncrementalUpdateHelper changes,
                                                       TransfiniteCraftingLogic logic) {
        boolean fullStatus = changes.isFullUpdate();
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();
        Map<Long, Integer> reasonMasks = new LinkedHashMap<>();
        for (AEKey key : changes) {
            AEKey sentKey = !fullStatus && changes.getSerial(key) != null ? null : key;
            CraftingStatusEntry entry = new CraftingStatusEntry(
                    changes.getOrAssignSerial(key), sentKey, logic.getStored(key), logic.getWaitingFor(key),
                    logic.getPendingOutputs(key));
            entries.add(entry);
            reasonMasks.put(entry.getSerial(), entry.isDeleted() ? 0 : logic.gtlcore$getDispatchReasonMask(key));
            if (entry.isDeleted()) {
                changes.removeSerial(key);
            }
        }

        var tracker = logic.getElapsedTimeTracker();
        CraftingStatus status = new CraftingStatus(fullStatus, tracker.getElapsedTime(),
                tracker.getRemainingItemCount(), tracker.getStartItemCount(), entries.build());
        ((ICraftingStatusReasons) status).gtlcore$setReasonMasks(reasonMasks);
        return status;
    }
}
