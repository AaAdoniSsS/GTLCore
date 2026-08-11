package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.integration.jei.JeiMissingIngredientBookmarks;

import com.gregtechceu.gtceu.integration.ae2.gui.widget.list.AEFluidDisplayWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.list.AEItemDisplayWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.list.AEListGridWidget;

import com.lowdragmc.lowdraglib.gui.ingredient.IIngredientSlot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.renderer.Rect2i;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = { AEItemDisplayWidget.class, AEFluidDisplayWidget.class }, remap = false)
public abstract class AEListDisplayWidgetMixin implements IIngredientSlot {

    @Shadow(remap = false)
    @Final
    private AEListGridWidget gridWidget;

    @Shadow(remap = false)
    @Final
    private int index;

    @Override
    public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        Widget widget = (Widget) (Object) this;
        if (!widget.isMouseOverElement(mouseX, mouseY)) {
            return null;
        }

        var stack = gridWidget.getAt(index);
        if (stack == null) {
            return null;
        }

        var area = new Rect2i(
                widget.getPositionX(), widget.getPositionY(), widget.getSizeWidth(), widget.getSizeHeight());
        return JeiMissingIngredientBookmarks.createClickableIngredient(stack.what(), area).orElse(null);
    }
}
