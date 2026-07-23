package org.gtlcore.gtlcore.mixin.ae2.gui;

import net.minecraft.client.gui.components.AbstractWidget;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WidgetContainer.class)
public interface WidgetContainerAccessor {

    @Accessor(value = "widgets", remap = false)
    Map<String, AbstractWidget> gtlcore$getWidgets();

    @Accessor(value = "compositeWidgets", remap = false)
    Map<String, ICompositeWidget> gtlcore$getCompositeWidgets();
}
