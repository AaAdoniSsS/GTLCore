package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.integration.jei.JeiMissingIngredientBookmarks;

import com.gregtechceu.gtceu.integration.ae2.gui.widget.ConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEConfigSlotWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;

import com.lowdragmc.lowdraglib.gui.ingredient.IIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.renderer.Rect2i;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = AEConfigSlotWidget.class, remap = false)
public abstract class AEConfigSlotWidgetMixin implements IIngredientSlot {

    @Shadow
    protected ConfigWidget parentWidget;

    @Shadow
    protected int index;

    @Shadow
    protected abstract boolean mouseOverConfig(double mouseX, double mouseY);

    @Shadow
    protected abstract boolean mouseOverStock(double mouseX, double mouseY);

    @Override
    public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        boolean configHovered = mouseOverConfig(mouseX, mouseY);
        boolean stockHovered = mouseOverStock(mouseX, mouseY);
        if (!configHovered && !stockHovered) {
            return null;
        }

        IConfigurableSlot slot = parentWidget.getDisplay(index);
        var stack = configHovered ? slot.getConfig() : slot.getStock();
        if (stack == null) {
            return null;
        }

        Widget widget = (Widget) (Object) this;
        int slotHeight = widget.getSizeHeight() / 2;
        int slotY = widget.getPositionY() + (stockHovered ? slotHeight : 0);
        var area = new Rect2i(widget.getPositionX(), slotY, widget.getSizeWidth(), slotHeight);
        return JeiMissingIngredientBookmarks.createClickableIngredient(stack.what(), area).orElse(null);
    }
}
