package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.WeightedCache;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class PreviewScenes {

    private static WeightedCache<Key, PreviewMesh> idle;
    private static int configuredLimit = -1;
    private static int generation;

    private PreviewScenes() {}

    public static void configure(SceneWidget scene, MultiblockMachineDefinition definition,
                                 int page, int layer, boolean modules) {
        var renderer = scene.getRenderer();
        if (renderer instanceof PreviewRendererAccess access) {
            access.gtlcore$previewKey(PreviewSettings.enabled() ? new Key(generation, Minecraft.getInstance().level, definition.getId(), page, layer, modules) : null);
        }
    }

    private static WeightedCache<Key, PreviewMesh> idle() {
        int limit = PreviewSettings.cacheMb();
        if (idle == null || configuredLimit != limit) {
            if (idle != null) idle.clear();
            idle = new WeightedCache<>((long) limit << 20, PreviewMesh::bytes, PreviewMesh::close);
            configuredLimit = limit;
        }
        return idle;
    }

    public static PreviewMesh take(Key key) {
        return idle().remove(key);
    }

    public static void release(Key key, PreviewMesh mesh) {
        if (key != null && key.generation == generation && mesh.complete()) idle().put(key, mesh);
        else mesh.close();
    }

    public static void invalidate(Key key) {
        if (key == null || idle == null) return;
        var previous = idle.remove(key);
        if (previous != null) previous.close();
    }

    public static void clear() {
        generation++;
        if (idle != null) idle.clear();
    }

    public static int generation() {
        return generation;
    }

    public record Key(int generation, Object world, ResourceLocation machine, int page, int layer, boolean modules) {}
}
