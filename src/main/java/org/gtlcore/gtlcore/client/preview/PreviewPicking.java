package org.gtlcore.gtlcore.client.preview;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Set;

public final class PreviewPicking {

    private PreviewPicking() {}

    public static Iterator<BlockPos> candidates(WorldSceneRenderer renderer, Set<BlockPos> original) {
        if (renderer instanceof PreviewRendererAccess access && access.gtlcore$previewKey() != null) {
            var hit = renderer.getLastTraceResult();
            if (hit != null) {
                var eye = new Vec3(renderer.getEyePos());
                // Match the widget's float conversion before doubling the ray endpoint.
                var point = hit.getLocation().toVector3f().mul(2.0f);
                var end = new Vec3(point.x - eye.x, point.y - eye.y, point.z - eye.z);
                var candidates = access.gtlcore$rayCandidates(eye, end);
                if (candidates != null) return candidates;
            }
        }
        return original.iterator();
    }
}
