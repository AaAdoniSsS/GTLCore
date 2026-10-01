package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, value = Dist.CLIENT)
public final class PreviewLifecycle {

    private static int reloads;

    private PreviewLifecycle() {}

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            PreviewFrameBudget.beginFrame();
            PreviewSceneState.releaseInvisible();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        PreviewShapeCache.clear();
        PatternPreviewWidget.clearSharedPreviewState();
    }

    public static void clear() {
        PreviewScenes.clear();
        WorldPreview.clear();
        PreviewSceneState.clearAll();
        PreviewMesh.clearAll();
    }

    public static void beginReload() {
        reloads++;
        clear();
    }

    public static void endReload() {
        clear();
        reloads = Math.max(0, reloads - 1);
    }

    public static boolean reloading() {
        return reloads != 0;
    }
}
