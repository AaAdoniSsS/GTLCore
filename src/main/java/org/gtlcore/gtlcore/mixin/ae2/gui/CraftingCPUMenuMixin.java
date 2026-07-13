package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingJobSuspensionState;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspensionMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftingCPUMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin extends AEBaseMenu implements ICraftingJobSuspensionMenu {

    @Shadow(remap = false)
    private CraftingCPUCluster cpu;

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
    }

    @Inject(method = "setCPU", at = @At("RETURN"), remap = false)
    private void gtlcore$refreshJobSuspensionOnCpuChange(ICraftingCPU cpu, CallbackInfo ci) {
        gtlcore$refreshJobSuspensionSync();
    }

    @Inject(method = "broadcastChanges",
            at = @At(value = "INVOKE",
                     target = "Lappeng/menu/AEBaseMenu;broadcastChanges()V"),
            remap = false)
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

        if (this.cpu != null && this.cpu.isBusy()) {
            ((ICraftingJobSuspension) this.cpu.craftingLogic).gtlcore$toggleJobSuspended();
            this.cpu.markDirty();
        }

        gtlcore$refreshJobSuspensionSync();
    }

    @Unique
    private void gtlcore$refreshJobSuspensionSync() {
        this.gtlcore$jobSuspensionAvailable = this.cpu != null && this.cpu.isBusy();
        this.gtlcore$jobSuspended = this.gtlcore$jobSuspensionAvailable &&
                ((ICraftingJobSuspension) this.cpu.craftingLogic).gtlcore$isJobSuspended();
    }
}
