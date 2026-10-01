package org.gtlcore.gtlcore.client.preview;

import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Client-thread scene preparation, followed by asynchronous compilation. */
public final class PreviewSceneState {

    private static final Set<PreviewSceneState> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private PreviewScenes.Key key;
    private PreviewMesh mesh;
    private PreviewSnapshot preparing;
    private Iterator<BlockPos> remaining;
    private int total;
    private int captured;
    private boolean fallback;
    private long lastRendered;

    public boolean render(PreviewScenes.Key requested, WorldSceneRenderer renderer, float partialTicks) {
        lastRendered = PreviewFrameBudget.frame();
        if (PreviewLifecycle.reloading()) return true;
        if (fallback) return false;
        if (requested.generation() != PreviewScenes.generation()) {
            requested = new PreviewScenes.Key(PreviewScenes.generation(), requested.world(), requested.machine(),
                    requested.page(), requested.layer(), requested.modules());
        }
        if (!requested.equals(key)) {
            release();
            key = requested;
            mesh = PreviewScenes.take(key);
        }
        LIVE.add(this);
        if (mesh == null && preparing == null) {
            if (renderer.renderedBlocksMap.size() != 1 || renderer.renderedBlocksMap.values().iterator().next() != null || !(renderer.world instanceof TrackedDummyWorld world) || !world.getAllEntities().isEmpty()) {
                fallback = true;
                return false;
            }
            var positions = renderer.renderedBlocksMap.keySet().iterator().next();
            if (positions.size() < PreviewSettings.minPositions()) {
                fallback = true;
                return false;
            }
            total = positions.size();
            captured = 0;
            remaining = positions.iterator();
            preparing = new PreviewSnapshot();
        }
        if (preparing != null) {
            try (var budget = PreviewFrameBudget.slice()) {
                while (remaining.hasNext() && budget.available()) {
                    var pos = remaining.next();
                    var state = renderer.world.getBlockState(pos);
                    preparing.add(pos, state, state.hasBlockEntity() ? renderer.world.getBlockEntity(pos) : null);
                    preparing.rayIndex.add(renderer.world, pos, state);
                    captured++;
                }
            }
            if (!remaining.hasNext()) {
                mesh = new PreviewMesh(preparing, false);
                preparing = null;
                remaining = null;
            }
        }
        if (mesh != null) {
            mesh.update();
            if (mesh.failed()) {
                mesh.close();
                mesh = null;
                fallback = true;
                return false;
            }
            mesh.draw(RenderSystem.getModelViewMatrix(), partialTicks, new PoseStack());
        }
        return true;
    }

    public boolean compiling() {
        return !fallback && (mesh == null || !mesh.complete());
    }

    public Iterator<BlockPos> rayCandidates(Vec3 start, Vec3 end) {
        // No mesh is visible while its snapshot is being prepared.
        if (preparing != null) return Collections.emptyIterator();
        return mesh == null ? null : mesh.snapshot().rayIndex.candidates(start, end);
    }

    public BlockHitResult trace(BlockGetter world, Vec3 start, Vec3 end, Entity camera) {
        if (fallback) return null;
        return mesh == null ? PreviewRayIndex.miss(start, end) : mesh.snapshot().rayIndex.trace(world, start, end, camera);
    }

    public double progress() {
        return preparing != null ? 0.15 * captured / Math.max(1, total) : mesh == null ? 0 : 0.15 + 0.85 * mesh.progress();
    }

    public void release() {
        if (mesh != null) PreviewScenes.release(key, mesh);
        key = null;
        mesh = null;
        preparing = null;
        remaining = null;
        fallback = false;
        LIVE.remove(this);
    }

    public static void clearAll() {
        for (var scene : List.copyOf(LIVE)) scene.release();
    }

    public static void releaseInvisible() {
        for (var scene : List.copyOf(LIVE)) {
            if (scene.key != null && PreviewFrameBudget.frame() - scene.lastRendered > 2) scene.release();
        }
    }
}
