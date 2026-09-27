package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.WeightedCache;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PreviewShapeCache {

    private static final WeightedCache<MultiblockMachineDefinition, Shapes> SHAPES = new WeightedCache<>(128L << 20, Shapes::bytes, ignored -> {});
    private static final WeightedCache<MultiblockShapeInfo, SparsePreviewShape> SPARSE = new WeightedCache<>(64L << 20, SparsePreviewShape::estimatedBytes, ignored -> {});

    private PreviewShapeCache() {}

    public static List<MultiblockShapeInfo> get(MultiblockMachineDefinition definition) {
        Shapes cached = SHAPES.get(definition);
        return cached == null ? null : cached.values();
    }

    public static void put(MultiblockMachineDefinition definition, List<MultiblockShapeInfo> shapes) {
        long bytes = 0;
        for (var shape : shapes) {
            if (shape == null) continue;
            for (var aisle : shape.getBlocks()) {
                bytes += 24;
                // Charge for retained BlockInfo objects as well as array references.
                for (var row : aisle) bytes += 24L + row.length * 64L;
            }
        }
        // The upstream widget skips null pages; caching must preserve that contract.
        SHAPES.put(definition, new Shapes(Collections.unmodifiableList(new ArrayList<>(shapes)), bytes));
    }

    public static boolean isLarge(MultiblockShapeInfo shape) {
        if (shape == null) return false;
        long positions = 0;
        int minimum = PreviewSettings.minPositions();
        for (var aisle : shape.getBlocks()) {
            for (var row : aisle) {
                positions += row.length;
                if (positions >= minimum) return true;
            }
        }
        return false;
    }

    public static void invalidate(MultiblockMachineDefinition definition) {
        SHAPES.remove(definition);
        // A mesh may outlive its raw shape cache entry, including oversized/evicted shapes.
        SPARSE.clear();
        PreviewLifecycle.clear();
    }

    public static SparsePreviewShape sparse(MultiblockShapeInfo shape, MultiblockMachineDefinition definition) {
        var cached = SPARSE.get(shape);
        if (cached != null) return cached;
        var result = new SparsePreviewShape(shape, definition);
        SPARSE.put(shape, result);
        return result;
    }

    public static void clear() {
        SHAPES.clear();
        SPARSE.clear();
    }

    private record Shapes(List<MultiblockShapeInfo> values, long bytes) {}
}
