package org.gtlcore.gtlcore.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

/** Broad phase only: the original widget still tests each candidate's actual outline shape. */
public final class PreviewRayIndex {

    private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
    private int next;
    private boolean usable = true;
    private AABB bounds;

    public void add(BlockGetter world, BlockPos pos, BlockState state) {
        if (!usable || state.isAir()) return;
        try {
            var shape = state.getShape(world, pos);
            if (shape.isEmpty()) return;
            var bounds = shape.bounds().move(pos).inflate(1.0e-7);
            long key = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
            var cell = cells.computeIfAbsent(key, ignored -> new Cell());
            cell.bounds = cell.bounds == null ? bounds : cell.bounds.minmax(bounds);
            cell.entries.add(new Entry(next++, pos));
        } catch (RuntimeException unsupportedShape) {
            // A mod with a context-sensitive shape keeps its original picking path.
            usable = false;
            cells.clear();
        }
    }

    public Iterator<BlockPos> candidates(Vec3 start, Vec3 end) {
        if (!usable) return null;
        var found = new ArrayList<Entry>();
        for (var cell : cells.values()) {
            if (cell.bounds.contains(start) || cell.bounds.contains(end) || cell.bounds.clip(start, end).isPresent()) {
                found.addAll(cell.entries);
            }
        }
        // Preserve the original iteration order, including ties on shared block faces.
        found.sort(Comparator.comparingInt(Entry::order));
        return found.stream().map(Entry::pos).iterator();
    }

    public BlockHitResult trace(BlockGetter world, Vec3 start, Vec3 end, Entity camera) {
        if (!usable) return null;
        if (bounds == null) {
            for (var cell : cells.values()) bounds = bounds == null ? cell.bounds : bounds.minmax(cell.bounds);
            if (bounds != null) bounds = bounds.inflate(1);
        }
        if (bounds == null) return miss(start, end);
        var entry = bounds.contains(start) ? java.util.Optional.of(start) : bounds.clip(start, end);
        var exit = bounds.contains(end) ? java.util.Optional.of(end) : bounds.clip(end, start);
        if (entry.isEmpty() || exit.isEmpty()) return miss(start, end);
        var epsilon = end.subtract(start).normalize().scale(1.0e-5);
        var from = bounds.contains(start) ? start : entry.get().subtract(epsilon);
        var to = bounds.contains(end) ? end : exit.get().add(epsilon);
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        // The widget uses the miss endpoint to construct its secondary picking ray.
        return hit.getType() == HitResult.Type.MISS ? miss(start, end) : hit;
    }

    public static BlockHitResult miss(Vec3 start, Vec3 end) {
        var direction = start.subtract(end);
        return BlockHitResult.miss(end, Direction.getNearest(direction.x, direction.y, direction.z), BlockPos.containing(end));
    }

    public long estimatedBytes() {
        return next * 32L + cells.size() * 192L;
    }

    private static final class Cell {

        private AABB bounds;
        private final ArrayList<Entry> entries = new ArrayList<>();
    }

    private record Entry(int order, BlockPos pos) {}
}
