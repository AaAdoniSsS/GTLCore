package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.common.CraftAmountConfirmPayload;
import org.gtlcore.gtlcore.integration.ae2.common.CraftAmountLimits;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftAmountMenu;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftConfirmMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftAmountMenu.class)
public abstract class CraftAmountMenuMixin extends AEBaseMenu implements ILongCraftAmountMenu {

    @Shadow(remap = false)
    private AEKey whatToCraft;

    @Shadow(remap = false)
    @Final
    private ISubMenuHost host;

    protected CraftAmountMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void registerLongAmountConfirmAction(int id, Inventory playerInventory, ISubMenuHost host, CallbackInfo ci) {
        this.registerClientAction(CONFIRM_LONG_AMOUNT_ACTION, CraftAmountConfirmPayload.class, payload -> this.gtlcore$confirmLongAmount(
                payload.amount(), payload.craftMissingAmount(), payload.startImmediately()));
    }

    @Override
    public void gtlcore$confirmLongAmount(long amount, boolean craftMissingAmount, boolean startImmediately) {
        if (this.isClientSide()) {
            this.sendClientAction(CONFIRM_LONG_AMOUNT_ACTION,
                    new CraftAmountConfirmPayload(amount, craftMissingAmount, startImmediately));
            return;
        }

        if (this.whatToCraft == null || amount <= 0) {
            return;
        }

        long amountToCraft = craftMissingAmount ? CraftAmountLimits.missingAmount(amount, gtlcore$getStoredAmount()) : amount;
        var locator = this.getLocator();
        if (locator == null) {
            return;
        }

        var player = this.getPlayer();
        if (amountToCraft > 0) {
            MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
            if (player.containerMenu instanceof CraftConfirmMenu confirmMenu) {
                confirmMenu.setAutoStart(startImmediately);
                ((ILongCraftConfirmMenu) confirmMenu).gtlcore$planLongAmountJob(
                        this.whatToCraft, amountToCraft, CalculationStrategy.REPORT_MISSING_ITEMS);
                this.broadcastChanges();
            }
        } else {
            this.host.returnToMainMenu(player, (ISubMenu) (Object) this);
        }
    }

    @Unique
    private long gtlcore$getStoredAmount() {
        var actionHost = this.getActionHost();
        if (actionHost == null) return 0;

        var gridNode = actionHost.getActionableNode();
        if (gridNode == null) return 0;

        return gridNode.getGrid().getStorageService().getCachedInventory().get(this.whatToCraft);
    }
}
