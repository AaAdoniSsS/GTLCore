package org.gtlcore.gtlcore.integration.ae2.throughput;

import appeng.hooks.ticking.TickHandler;

import java.util.ArrayDeque;
import java.util.Deque;

final class ThroughputCache {

    static final int TICKS_PER_SECOND = 20;
    static final int DEFAULT_MAX_SAMPLES = 10 * 60 * TICKS_PER_SECOND;

    private static final long NO_TIMESTAMP = -1L;

    private final int maxSamples;
    private final Deque<CacheEntry> changes = new ArrayDeque<>();
    private long startTick = NO_TIMESTAMP;
    private boolean hasRecordedChanges;

    ThroughputCache() {
        this(DEFAULT_MAX_SAMPLES);
    }

    ThroughputCache(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    void reset(long timestamp) {
        changes.clear();
        startTick = timestamp > 0 ? timestamp : NO_TIMESTAMP;
        hasRecordedChanges = false;
    }

    void recordChange(long amountDelta, long timestamp) {
        if (amountDelta == 0 || timestamp <= 0) {
            return;
        }

        double inserted = amountDelta > 0 ? amountDelta : 0.0D;
        double extracted = amountDelta < 0 ? -(double) amountDelta : 0.0D;

        if (startTick == NO_TIMESTAMP) {
            startTick = timestamp;
        }
        hasRecordedChanges = true;

        CacheEntry first = changes.peekFirst();
        if (first != null && first.timestamp == timestamp) {
            changes.removeFirst();
            changes.addFirst(
                    new CacheEntry(
                            first.inserted + inserted,
                            first.extracted + extracted,
                            timestamp));
            return;
        }

        changes.addFirst(new CacheEntry(inserted, extracted, timestamp));
        while (changes.size() > maxSamples) {
            changes.removeLast();
        }
    }

    void clear() {
        changes.clear();
        startTick = NO_TIMESTAMP;
        hasRecordedChanges = false;
    }

    boolean hasRecordedChanges() {
        return hasRecordedChanges;
    }

    double averagePerTick(int sampleWindowSeconds) {
        ThroughputSample sample = sample(sampleWindowSeconds, TickHandler.instance().getCurrentTick());
        return sample.insertedPerTick - sample.extractedPerTick;
    }

    double averagePerTick(int sampleWindowSeconds, long currentTick) {
        ThroughputSample sample = sample(sampleWindowSeconds, currentTick);
        return sample.insertedPerTick - sample.extractedPerTick;
    }

    ThroughputSample sample(int sampleWindowSeconds) {
        return sample(sampleWindowSeconds, TickHandler.instance().getCurrentTick());
    }

    ThroughputSample sample(int sampleWindowSeconds, long currentTick) {
        if (startTick == NO_TIMESTAMP || currentTick <= 0) {
            return ThroughputSample.EMPTY;
        }

        long windowTicks = secondsToTicks(sampleWindowSeconds);
        long firstTick = Math.max(startTick, currentTick - windowTicks);
        long elapsedTicks = Math.max(1L, currentTick - firstTick);
        double inserted = 0.0D;
        double extracted = 0.0D;

        for (CacheEntry change : changes) {
            if (change.timestamp < firstTick) {
                break;
            }

            if (change.timestamp <= currentTick) {
                inserted += change.inserted;
                extracted += change.extracted;
            }
        }

        return new ThroughputSample(inserted / (double) elapsedTicks, extracted / (double) elapsedTicks);
    }

    private static long secondsToTicks(int seconds) {
        return seconds * (long) TICKS_PER_SECOND;
    }

    record ThroughputSample(double insertedPerTick, double extractedPerTick) {

        private static final ThroughputSample EMPTY = new ThroughputSample(0.0D, 0.0D);
    }

    private record CacheEntry(double inserted, double extracted, long timestamp) {}
}
