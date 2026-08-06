package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.gui.widget.IShiftAmountOperations;
import org.gtlcore.gtlcore.integration.ae2.common.CraftAmountLimits;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftAmountMenu;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.me.crafting.CraftAmountMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftAmountScreen.class)
public abstract class CraftAmountScreenMixin extends AEBaseScreen<CraftAmountMenu> {

    @Shadow(remap = false)
    @Final
    private Button next;

    @Shadow(remap = false)
    @Final
    private NumberEntryWidget amountToCraft;

    protected CraftAmountScreenMixin(CraftAmountMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void allowLongAmountInput(CraftAmountMenu menu, Inventory playerInventory, Component title, ScreenStyle style,
                                      CallbackInfo ci) {
        this.amountToCraft.setMaxValue(CraftAmountLimits.MAX_MANUAL_CRAFT_AMOUNT);
        ((NumberEntryWidgetAccessor) this.amountToCraft).getTextField()
                .setMaxLength(CraftAmountLimits.MAX_MANUAL_CRAFT_AMOUNT_DIGITS);
        ((IShiftAmountOperations) this.amountToCraft).gtlcore$enableShiftAmountOperations();
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void keepNextButtonEnabledForLongAmounts(CallbackInfo ci) {
        this.next.active = this.amountToCraft.getLongValue().orElse(0) > 0;
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true, remap = false)
    private void confirmLongAmount(CallbackInfo ci) {
        long amount = this.amountToCraft.getLongValue().orElse(0);
        if (amount > 0) {
            ((ILongCraftAmountMenu) this.menu).gtlcore$confirmLongAmount(
                    amount, this.amountToCraft.startsWithEquals(), Screen.hasShiftDown());
        }
        ci.cancel();
    }
}
