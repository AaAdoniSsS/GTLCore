package org.gtlcore.gtlcore.mixin.ldlib.client;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** These fields are stable across LDLib 29b and 33b; their public getters are not. */
@Mixin(value = SceneWidget.class, remap = false)
public interface PreviewSceneAccessor {

    @Accessor("dragging")
    boolean gtlcore$dragging();

    @Accessor("useOrtho")
    boolean gtlcore$ortho();

    @Accessor("range")
    float gtlcore$range();
}
