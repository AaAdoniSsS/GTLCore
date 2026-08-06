package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.gui.widget.IShiftAmountOperations;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.Point;
import appeng.client.gui.widgets.NumberEntryWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.OptionalLong;

@Mixin(NumberEntryWidget.class)
public abstract class NumberEntryWidgetMixin implements IShiftAmountOperations {

    @Unique
    private static final Component[] GTLCORE$SHIFT_BUTTON_LABELS = {
            Component.literal("\u00d72"),
            Component.literal("\u00d75"),
            Component.literal("int"),
            Component.literal("long"),
            Component.literal("\u00f72"),
            Component.literal("\u00f75"),
            Component.literal("=1"),
            Component.literal("=1")
    };

    @Shadow(remap = false)
    @Final
    private static long[] STEPS;

    @Shadow(remap = false)
    private List<Button> buttons;

    @Shadow(remap = false)
    private long maxValue;

    @Unique
    private boolean gtlcore$shiftAmountOperationsEnabled;

    @Unique
    private boolean gtlcore$showingShiftButtonLabels;

    @Unique
    private Component[] gtlcore$normalButtonLabels;

    @Shadow(remap = false)
    public abstract OptionalLong getLongValue();

    @Shadow(remap = false)
    public abstract void setMaxValue(long value);

    @Shadow(remap = false)
    public abstract void setLongValue(long value);

    @Override
    public void gtlcore$enableShiftAmountOperations() {
        this.gtlcore$shiftAmountOperationsEnabled = true;
        this.setMaxValue(Long.MAX_VALUE);
    }

    @Inject(method = "drawBackgroundLayer", at = @At("HEAD"), remap = false)
    private void gtlcore$updateShiftButtonLabels(GuiGraphics guiGraphics, Rect2i bounds, Point mouse,
                                                 CallbackInfo ci) {
        if (!this.gtlcore$shiftAmountOperationsEnabled || this.buttons == null ||
                this.buttons.size() < GTLCORE$SHIFT_BUTTON_LABELS.length) {
            return;
        }

        boolean showShiftLabels = Screen.hasShiftDown();
        if (this.gtlcore$normalButtonLabels == null) {
            this.gtlcore$normalButtonLabels = new Component[GTLCORE$SHIFT_BUTTON_LABELS.length];
            for (int i = 0; i < this.gtlcore$normalButtonLabels.length; i++) {
                this.gtlcore$normalButtonLabels[i] = this.buttons.get(i).getMessage();
            }
        }
        if (showShiftLabels == this.gtlcore$showingShiftButtonLabels) {
            return;
        }

        Component[] labels = showShiftLabels ? GTLCORE$SHIFT_BUTTON_LABELS : this.gtlcore$normalButtonLabels;
        for (int i = 0; i < labels.length; i++) {
            this.buttons.get(i).setMessage(labels[i]);
        }
        this.gtlcore$showingShiftButtonLabels = showShiftLabels;
    }

    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$applyShiftAmountOperation(long delta, CallbackInfo ci) {
        if (!this.gtlcore$shiftAmountOperationsEnabled || !Screen.hasShiftDown()) {
            return;
        }

        long currentValue = this.getLongValue().orElse(0);
        if (delta == STEPS[0]) {
            this.gtlcore$multiply(currentValue, 2);
        } else if (delta == STEPS[1]) {
            this.gtlcore$multiply(currentValue, 5);
        } else if (delta == STEPS[2]) {
            this.setLongValue(Integer.MAX_VALUE);
        } else if (delta == STEPS[3]) {
            this.setLongValue(Long.MAX_VALUE);
        } else if (delta == -STEPS[0]) {
            this.setLongValue(currentValue / 2);
        } else if (delta == -STEPS[1]) {
            this.setLongValue(currentValue / 5);
        } else if (delta == -STEPS[2] || delta == -STEPS[3]) {
            this.setLongValue(1);
        } else {
            return;
        }
        ci.cancel();
    }

    @Unique
    private void gtlcore$multiply(long currentValue, long factor) {
        long result = currentValue > this.maxValue / factor ? this.maxValue : currentValue * factor;
        this.setLongValue(result);
    }
}
