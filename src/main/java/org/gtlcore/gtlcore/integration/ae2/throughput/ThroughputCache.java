package org.gtlcore.gtlcore.integration.ae2.throughput;

import appeng.hooks.ticking.TickHandler;

import java.util.ArrayDeque;
import java.util.Deque;

final class ThroughputCache {

    static final int DEFAULT_MAX_SAMPLES = 80;

    private static final int TICKS_PER_SECOND = 20;
    private static final long NO_TIMESTAMP = -1L;

    private final int maxSamples;
    private final Deque<CacheEntry> samples = new ArrayDeque<>(DEFAULT_MAX_SAMPLES);

    ThroughputCache() {
        this(DEFAULT_MAX_SAMPLES);
    }

    private ThroughputCache(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    int size() {
        return samples.size();
    }

    void push(long amount, long timestamp) {
        if (timestamp <= 0) {
            return;
        }

        CacheEntry first = samples.peekFirst();
        if (first != null && first.timestamp == timestamp) {
            return;
        }

        samples.addFirst(new CacheEntry(amount, timestamp));
        while (samples.size() > maxSamples) {
            samples.removeLast();
        }
    }

    void clear() {
        samples.clear();
    }

    double averagePerTick(int sampleWindowSeconds) {
        long cutoffTick = TickHandler.instance().getCurrentTick() - secondsToTicks(sampleWindowSeconds);
        long lastAmount = 0L;
        long lastTimestamp = NO_TIMESTAMP;
        double total = 0.0D;
        int count = 0;

        for (CacheEntry sample : samples) {
            if (sample.timestamp < cutoffTick) {
                break;
            }

            if (lastTimestamp != NO_TIMESTAMP) {
                long timestampDelta = lastTimestamp - sample.timestamp;
                if (timestampDelta > 0) {
                    total += (lastAmount - sample.amount) / (double) timestampDelta;
                    count++;
                }
            }

            lastAmount = sample.amount;
            lastTimestamp = sample.timestamp;
        }

        return count == 0 ? 0.0D : total / count;
    }

    private static long secondsToTicks(int seconds) {
        return seconds * (long) TICKS_PER_SECOND;
    }

    private record CacheEntry(long amount, long timestamp) {}
}
