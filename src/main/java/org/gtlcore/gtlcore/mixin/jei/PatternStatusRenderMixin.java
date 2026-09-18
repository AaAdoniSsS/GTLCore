package org.gtlcore.gtlcore.mixin.jei;

import org.gtlcore.gtlcore.client.ae2.JeiPatternStatus;

import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/** Only JEI ingredient and bookmark grids show pattern status, not recipe contents. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListRenderer", remap = false)
public abstract class PatternStatusRenderMixin {

    @Inject(method = "renderBatch", at = @At("RETURN"), remap = false)
    private void gtlcore$batch(GuiGraphics graphics,
                               Map.Entry<IIngredientType<?>, List<BatchRenderElement<?>>> entry, CallbackInfo ci) {
        for (var element : entry.getValue()) JeiPatternStatus.draw(graphics, element.ingredient(), element.x(), element.y());
    }
}
