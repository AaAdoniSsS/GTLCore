package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.utils.datastructure.LatestTask;

import com.mojang.blaze3d.vertex.BufferBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** An exclusive lease lasts until upload/release, so native buffers cannot grow with each batch. */
final class PreviewBuffers {

    private static final int LIMIT = 16;
    private static final AtomicInteger ALLOCATED = new AtomicInteger();
    private static final ArrayBlockingQueue<BufferBuilder> FREE = new ArrayBlockingQueue<>(LIMIT);

    private PreviewBuffers() {}

    static BufferBuilder acquire(LatestTask task) throws InterruptedException {
        while (!task.cancelled()) {
            var builder = FREE.poll();
            if (builder != null) return builder;
            int count = ALLOCATED.get();
            if (count < LIMIT && ALLOCATED.compareAndSet(count, count + 1)) {
                try {
                    return new BufferBuilder(128 * 1024);
                } catch (Throwable error) {
                    ALLOCATED.decrementAndGet();
                    throw error;
                }
            }
            builder = FREE.poll(25, TimeUnit.MILLISECONDS);
            if (builder != null) return builder;
        }
        return null;
    }

    static void release(BufferBuilder builder) {
        if (builder.building()) {
            var abandoned = builder.endOrDiscardIfEmpty();
            if (abandoned != null) abandoned.release();
        }
        builder.discard();
        if (!FREE.offer(builder)) throw new IllegalStateException("Preview buffer released twice");
    }
}
