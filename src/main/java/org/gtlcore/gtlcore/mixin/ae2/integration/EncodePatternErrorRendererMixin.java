package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.client.gui.EncodePatternErrorModify;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "appeng.integration.modules.jei.transfer.EncodePatternTransferHandler$ErrorRenderer", remap = false)
public class EncodePatternErrorRendererMixin implements EncodePatternErrorModify {

    @Unique
    private boolean gTLCore$virtualIngredientHint;

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
