package org.gtlcore.gtlcore.mixin.ldlib.client;

import org.gtlcore.gtlcore.client.preview.PreviewBlockPosHash;
import org.gtlcore.gtlcore.client.preview.PreviewRendererAccess;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

@Mixin(value = SceneWidget.class, remap = false)
public abstract class PreviewSceneWidgetMixin {

    @Shadow
    protected WorldSceneRenderer renderer;
    @Shadow
    protected Set<BlockPos> core;
    @Unique
    private boolean gtlcore$largeScene;

    @Inject(method = "setRenderedCore(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneBlockRenderHook;)Lcom/lowdragmc/lowdraglib/gui/widget/SceneWidget;",
            at = @At("HEAD"))
    private void gtlcore$replaceMutableKey(CallbackInfoReturnable<SceneWidget> cir) {
        if (renderer instanceof PreviewRendererAccess access && access.gtlcore$previewKey() != null) {
            // SceneWidget reuses and mutates a HashSet that is also a map key.
            // Remove it before mutation instead of retaining one bucket per visited layer.
            var others = new HashMap<>(renderer.renderedBlocksMap);
            others.entrySet().removeIf(entry -> entry.getKey() == core);
            renderer.renderedBlocksMap.clear();
            renderer.renderedBlocksMap.putAll(others);
            if (!(core instanceof ObjectOpenCustomHashSet<?>)) {
                core = new ObjectOpenCustomHashSet<>(PreviewBlockPosHash.INSTANCE);
            }
        }
    }

    @Inject(method = "setRenderedCore(Ljava/util/Collection;Lcom/lowdragmc/lowdraglib/client/scene/ISceneBlockRenderHook;)Lcom/lowdragmc/lowdraglib/gui/widget/SceneWidget;",
            at = @At("RETURN"))
    private void gtlcore$frameGiant(CallbackInfoReturnable<SceneWidget> cir) {
        if (renderer instanceof PreviewRendererAccess access && access.gtlcore$previewKey() != null) {
            var scene = (SceneWidget) (Object) this;
            float edge = scene.getZoom() / 3.5f;
            edge *= edge;
            gtlcore$largeScene = edge > 32;
            if (gtlcore$largeScene) scene.setZoom(Math.max(scene.getZoom(), edge * 1.2f));
        }
    }

    @ModifyConstant(method = "mouseWheelMove", constant = { @Constant(doubleValue = 0.5), @Constant(doubleValue = -0.5) })
    private double gtlcore$giantZoomStep(double step) {
        if (!gtlcore$largeScene) return step;
        return Math.copySign(Math.max(Math.abs(step), ((SceneWidget) (Object) this).getZoom() * 0.08), step);
    }

    @ModifyArg(method = "drawInBackground",
               at = @At(value = "INVOKE",
                        target = "Lcom/lowdragmc/lowdraglib/gui/texture/TextTexture;<init>(Ljava/lang/String;)V"),
               index = 0)
    private String gtlcore$localizedProgress(String original) {
        if (renderer instanceof PreviewRendererAccess access && access.gtlcore$previewKey() != null) {
            return Component.translatable("gui.gtlcore.preview.loading",
                    String.format(Locale.ROOT, "%.0f", renderer.getCompileProgress() * 100))
                    .getString().replace("%", "%%"); // TextTexture runs the resolved text through I18n again.
        }
        return original;
    }
}
