package org.gtlcore.gtlcore.mixin.jei.client;

import org.gtlcore.gtlcore.client.preview.PreviewHeaderBounds;

import mezz.jei.common.util.ImmutableRect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class PreviewHeaderBoundsMixin implements PreviewHeaderBounds {

    @Shadow
    private int headerHeight;

    @Shadow
    public abstract ImmutableRect2i getArea();

    @Override
    public int gtlcore$headerBottom() {
        return getArea().getY() + headerHeight;
    }
}
