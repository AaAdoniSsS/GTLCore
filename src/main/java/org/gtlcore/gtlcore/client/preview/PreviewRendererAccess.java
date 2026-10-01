package org.gtlcore.gtlcore.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

public interface PreviewRendererAccess {

    float gtlcore$fov();

    void gtlcore$previewKey(PreviewScenes.Key key);

    PreviewScenes.Key gtlcore$previewKey();

    Iterator<BlockPos> gtlcore$rayCandidates(Vec3 start, Vec3 end);
}
