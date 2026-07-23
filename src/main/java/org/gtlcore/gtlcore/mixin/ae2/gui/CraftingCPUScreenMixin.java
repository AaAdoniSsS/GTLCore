package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspensionMenu;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class CraftingCPUScreenMixin<T extends AEBaseMenu> extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<T> {

    @Unique
    private static final String GTLCORE$CANCEL_WIDGET = "cancel";
    @Unique
    private static final String GTLCORE$CANCEL_ALL_WIDGET = "cancelAll";
    @Unique
    private static final int GTLCORE$DEFAULT_SUSPEND_BUTTON_GAP = 10;

    @Shadow(remap = false)
    @Final
    protected WidgetContainer widgets;

    @Unique
    private Button gtlcore$suspendButton;

    private CraftingCPUScreenMixin(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gtlcore$initSuspendButton(CallbackInfo ci) {
        if (!gtlcore$isCraftingCpuScreen()) {
            return;
        }

        if (this.gtlcore$suspendButton == null) {
            this.gtlcore$suspendButton = Button.builder(Component.empty(), button -> gtlcore$toggleScheduling())
                    .bounds(0, 0, 0, 0)
                    .build();
        }

        gtlcore$updateSuspendButton();
        this.addRenderableWidget(this.gtlcore$suspendButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$updateSuspendButtonBeforeRender(CallbackInfo ci) {
        if (this.gtlcore$suspendButton != null) {
            gtlcore$updateSuspendButton();
        }
    }

    @Unique
    private boolean gtlcore$isCraftingCpuScreen() {
        return (Object) this instanceof CraftingCPUScreen<?> && this.menu instanceof ICraftingJobSuspensionMenu;
    }

    @Unique
    private void gtlcore$toggleScheduling() {
        if (this.menu instanceof ICraftingJobSuspensionMenu suspensionMenu) {
            suspensionMenu.gtlcore$toggleScheduling();
        }
    }

    @Unique
    private void gtlcore$updateSuspendButton() {
        if (!gtlcore$isCraftingCpuScreen()) {
            this.gtlcore$suspendButton.visible = false;
            return;
        }

        var screenWidgets = ((WidgetContainerAccessor) this.widgets).gtlcore$getWidgets();
        AbstractWidget cancelButton = screenWidgets.get(GTLCORE$CANCEL_WIDGET);
        if (cancelButton != null) {
            int buttonWidth = cancelButton.getWidth();
            this.gtlcore$suspendButton.setWidth(buttonWidth);
            this.gtlcore$suspendButton.setHeight(cancelButton.getHeight());

            AbstractWidget cancelAllButton = screenWidgets.get(GTLCORE$CANCEL_ALL_WIDGET);
            int suspendButtonX;
            if (cancelAllButton != null) {
                int availableLeft = cancelAllButton.getX() + cancelAllButton.getWidth();
                int availableWidth = cancelButton.getX() - availableLeft;
                suspendButtonX = availableLeft + (availableWidth - buttonWidth) / 2;
            } else {
                suspendButtonX = cancelButton.getX() - buttonWidth - GTLCORE$DEFAULT_SUSPEND_BUTTON_GAP;
            }
            this.gtlcore$suspendButton.setX(suspendButtonX);
            this.gtlcore$suspendButton.setY(cancelButton.getY());
        }

        ICraftingJobSuspensionMenu suspensionMenu = (ICraftingJobSuspensionMenu) this.menu;
        boolean suspended = suspensionMenu.gtlcore$isJobSuspended();
        this.gtlcore$suspendButton.setMessage(Component.translatable(suspended ?
                "gui.gtlcore.crafting_resume" :
                "gui.gtlcore.crafting_suspend"));
        this.gtlcore$suspendButton.visible = suspensionMenu.gtlcore$isJobSuspensionAvailable();
        this.gtlcore$suspendButton.active = this.gtlcore$suspendButton.visible;
    }
}
