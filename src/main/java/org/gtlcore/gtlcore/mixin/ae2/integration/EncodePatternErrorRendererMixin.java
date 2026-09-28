package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.client.gui.EncodePatternErrorModify;
import org.gtlcore.gtlcore.client.preview.PreviewMaterialHighlights;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "appeng.integration.modules.jei.transfer.EncodePatternTransferHandler$ErrorRenderer", remap = false)
public class EncodePatternErrorRendererMixin implements EncodePatternErrorModify {

    @Unique
    private boolean gTLCore$virtualIngredientHint;

    @Redirect(method = "showError",
              at = @At(value = "INVOKE",
                       target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotView;drawHighlight(Lnet/minecraft/client/gui/GuiGraphics;I)V"))
    private void gTLCore$highlightMaterial(IRecipeSlotView slot, GuiGraphics graphics, int color) {
        PreviewMaterialHighlights.draw(slot, graphics, color);
    }

    @Override
    public void gTLCore$setVirtualIngredientHint(boolean hint) {
        this.gTLCore$virtualIngredientHint = hint;
    }

    /**
     * AE draws this tooltip itself rather than through {@code IRecipeTransferError#getTooltip}, so appending there
     * would open a second box on top of this one instead of extending it.
     */
    @ModifyArg(method = "showError",
               at = @At(value = "INVOKE",
                        target = "Lappeng/integration/modules/jei/JEIPlugin;drawHoveringText(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;II)V"),
               index = 1,
               remap = false)
    private List<Component> gTLCore$appendVirtualIngredientHint(List<Component> tooltip) {
        if (!gTLCore$virtualIngredientHint) return tooltip;
        List<Component> lines = new ArrayList<>(tooltip);
        lines.add(Component.translatable("gtlcore.jei.virtual_ingredient_hint").withStyle(ChatFormatting.AQUA));
        return lines;
    }
}
