package org.gtlcore.gtlcore.mixin.ldlib.client;

import org.gtlcore.gtlcore.client.preview.PreviewPicking;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.Set;

@Mixin(value = SceneWidget.class, remap = false)
public abstract class PreviewPickingMixin {

    @Redirect(method = "renderBlockOverLay", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private Iterator<BlockPos> gtlcore$nearRay(Set<BlockPos> original) {
        return PreviewPicking.candidates(((SceneWidget) (Object) this).getRenderer(), original);
    }
}
