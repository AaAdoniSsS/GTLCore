package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.LatestTask;
import org.gtlcore.gtlcore.utils.datastructure.WeightedCache;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class PreviewPerformanceRegression {

    private static int assertions;

    static int run() throws Exception {
        var base = java.nio.file.Path.of("src/main/java/org/gtlcore/gtlcore");
        for (var file : java.util.List.of("client/preview/PreviewScenes.java", "client/preview/WorldPreview.java",
                "mixin/gtm/client/PreviewShapesMixin.java", "api/gui/PatternPreviewWidget.java")) {
            var source = java.nio.file.Files.readString(base.resolve(file));
            check(!source.contains("PreviewScenes.targets") && !source.contains("getNamespace()"),
                    "shared preview cannot depend on a machine whitelist: " + file);
        }
        check(java.nio.file.Files.readString(base.resolve("client/preview/PreviewSceneState.java"))
                .contains("positions.size() < PreviewSettings.minPositions()"), "small scenes retain native renderer");
        check(java.nio.file.Files.readString(base.resolve("client/preview/WorldPreview.java"))
                .contains("!PreviewShapeCache.isLarge(shapes.get(0))"), "world previews use automatic geometry threshold");
        check(java.nio.file.Files.readString(base.resolve("mixin/gtm/client/PreviewShapesMixin.java"))
                .contains("\"setShapes\", \"setPatternFactory\""), "definition changes invalidate cached shapes");
        var shapes = java.nio.file.Files.readString(base.resolve("client/preview/PreviewShapeCache.java"));
        check(shapes.contains("if (shape == null) continue;") && !shapes.contains("List.copyOf(shapes)"),
                "upstream nullable preview pages remain supported");
        check(shapes.contains("row.length * 64L"), "shape cache budgets retained objects, not just references");
        check(java.nio.file.Files.readString(base.resolve("client/preview/SparsePreviewShape.java"))
                .contains("positions.length * 64L"), "sparse cache budgets retained block information");
        check(java.nio.file.Files.readString(base.resolve("api/gui/PatternPreviewWidget.java"))
                .replaceAll("\\s+", " ")
                .contains("!(machine.getDefinition() instanceof MultiblockMachineDefinition)) return null"),
                "controller discovery does not allocate throwaway hatch inventories");
        var released = new ArrayList<Integer>();
        var cache = new WeightedCache<String, Integer>(10, Integer::longValue, released::add);
        cache.put("a", 4);
        cache.put("b", 5);
        check(cache.weight() == 9 && cache.size() == 2, "cache accounting");
        check(cache.get("a") == 4, "cache hit");
        cache.put("c", 4);
        check(cache.get("b") == null && cache.get("a") == 4, "least recently used entry evicted");
        check(released.equals(java.util.List.of(5)), "evicted resource released once");
        check(cache.remove("c") == 4 && cache.weight() == 4, "active resource ownership transfer");
        cache.put("large", 100);
        check(cache.get("large") == null && released.equals(java.util.List.of(5, 100)), "oversized idle mesh released");
        cache.put("a", 3);
        check(cache.weight() == 3 && released.equals(java.util.List.of(5, 100, 4)), "replacement releases old resource");
        cache.clear();
        check(cache.size() == 0 && cache.weight() == 0 && released.size() == 4, "clear releases remaining resources");
        cache.clear();
        check(released.size() == 4, "clear is idempotent");
        var disabled = new WeightedCache<String, Integer>(0, Integer::longValue, released::add);
        disabled.put("x", 1);
        check(disabled.size() == 0 && disabled.weight() == 0, "disabled cache retains nothing");
        var first = new LatestTask();
        var second = new LatestTask();
        var started = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> {
                started.countDown();
                while (!first.cancelled()) Thread.onSpinWait();
                return !second.cancelled();
            });
            check(started.await(2, TimeUnit.SECONDS), "worker started");
            first.cancel();
            check(future.get(2, TimeUnit.SECONDS), "request cancellation cannot cancel another request");
            check(executor.submit(() -> !new LatestTask().cancelled()).get(), "worker reusable after cancellation");
        } finally {
            first.cancel();
            executor.shutdownNow();
        }
        return assertions;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
