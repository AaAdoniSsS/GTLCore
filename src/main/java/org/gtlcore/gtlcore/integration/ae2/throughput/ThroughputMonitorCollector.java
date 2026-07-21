package org.gtlcore.gtlcore.integration.ae2.throughput;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class ThroughputMonitorCollector implements ThroughputMonitorStorageTracker.AllListener {

    static final int MAX_TRACKED_KEYS = 4096;
    static final int MAX_TRACKED_SOURCES_PER_KEY = 1024;

    private static final int SAMPLE_WINDOW_SECONDS = 20;
    private static final long ACTIVE_RETENTION_TICKS = SAMPLE_WINDOW_SECONDS * (long) ThroughputCache.TICKS_PER_SECOND;

    private final Map<AEKey, KeyStats> statsByKey = new HashMap<>();
    private MEStorage trackedStorage;
    private long trackedStorageTopologyVersion = Long.MIN_VALUE;

    void attach(MEStorage storage) {
        if (storage == null || storage == trackedStorage) {
            return;
        }

        close();
        statsByKey.clear();
        trackedStorage = storage;
        ThroughputMonitorStorageTracker.registerAll(storage, this);
        refreshVisibleStorageLinks();
    }

    void close() {
        ThroughputMonitorStorageTracker.unregisterAll(this);
        trackedStorage = null;
        trackedStorageTopologyVersion = Long.MIN_VALUE;
    }

    List<Snapshot> getSnapshots() {
        refreshVisibleStorageLinks();
        long currentTick = TickHandler.instance().getCurrentTick();
        pruneInactive(currentTick);

        List<Snapshot> snapshots = new ArrayList<>(statsByKey.size());
        for (Map.Entry<AEKey, KeyStats> entry : statsByKey.entrySet()) {
            ThroughputCache.ThroughputSample total = entry.getValue().total.sample(
                    SAMPLE_WINDOW_SECONDS,
                    currentTick);
            pruneInactiveSources(entry.getValue(), currentTick);
            List<SourceSnapshot> sources = new ArrayList<>(entry.getValue().bySource.size());
            for (Map.Entry<ThroughputMonitorStorageTracker.SourceLocation, SourceStats> sourceEntry : entry.getValue().bySource.entrySet()) {
                ThroughputCache.ThroughputSample sourceSample = sourceEntry.getValue().cache.sample(
                        SAMPLE_WINDOW_SECONDS,
                        currentTick);
                if (sourceSample.insertedPerTick() == 0.0D && sourceSample.extractedPerTick() == 0.0D) {
                    continue;
                }
                sources.add(new SourceSnapshot(
                        sourceEntry.getKey(),
                        sourceSample.insertedPerTick() * ThroughputCache.TICKS_PER_SECOND,
                        sourceSample.extractedPerTick() * ThroughputCache.TICKS_PER_SECOND));
            }
            snapshots.add(new Snapshot(
                    entry.getKey(),
                    total.insertedPerTick() * ThroughputCache.TICKS_PER_SECOND,
                    total.extractedPerTick() * ThroughputCache.TICKS_PER_SECOND,
                    List.copyOf(sources)));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public void recordThroughput(AEKey key, long amountDelta, long tick,
                                 ThroughputMonitorStorageTracker.SourceLocation source) {
        KeyStats keyStats = statsByKey.get(key);
        if (keyStats == null) {
            if (statsByKey.size() >= MAX_TRACKED_KEYS) {
                pruneInactive(tick);
                if (statsByKey.size() >= MAX_TRACKED_KEYS) {
                    return;
                }
            }
            keyStats = new KeyStats();
            statsByKey.put(key, keyStats);
        }

        keyStats.total.recordChange(amountDelta, tick);
        keyStats.lastTick = tick;
        if (source != null) {
            SourceStats sourceStats = keyStats.bySource.get(source);
            if (sourceStats == null && keyStats.bySource.size() >= MAX_TRACKED_SOURCES_PER_KEY) {
                pruneInactiveSources(keyStats, tick);
            }
            if (sourceStats == null && keyStats.bySource.size() < MAX_TRACKED_SOURCES_PER_KEY) {
                sourceStats = new SourceStats();
                keyStats.bySource.put(source, sourceStats);
            }
            if (sourceStats != null) {
                sourceStats.cache.recordChange(amountDelta, tick);
                sourceStats.lastTick = tick;
            }
        }
    }

    private void refreshVisibleStorageLinks() {
        if (trackedStorage == null) {
            return;
        }
        long topologyVersion = ThroughputMonitorStorageTracker.topologyVersion(trackedStorage);
        if (topologyVersion != trackedStorageTopologyVersion) {
            ThroughputMonitorStorageTracker.refreshVisibleStorageLinks(trackedStorage);
            trackedStorageTopologyVersion = topologyVersion;
        }
    }

    private void pruneInactive(long currentTick) {
        if (currentTick <= 0L) {
            return;
        }
        for (Iterator<Map.Entry<AEKey, KeyStats>> iterator = statsByKey.entrySet().iterator(); iterator.hasNext();) {
            if (currentTick - iterator.next().getValue().lastTick > ACTIVE_RETENTION_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void pruneInactiveSources(KeyStats keyStats, long currentTick) {
        if (currentTick <= 0L) {
            return;
        }
        keyStats.bySource.entrySet().removeIf(
                entry -> currentTick - entry.getValue().lastTick > ACTIVE_RETENTION_TICKS);
    }

    private static final class KeyStats {

        private final ThroughputCache total = new ThroughputCache();
        private final Map<ThroughputMonitorStorageTracker.SourceLocation, SourceStats> bySource = new HashMap<>();
        private long lastTick;
    }

    private static final class SourceStats {

        private final ThroughputCache cache = new ThroughputCache();
        private long lastTick;
    }

    record Snapshot(AEKey key, double insertedPerSecond, double extractedPerSecond,
                    List<SourceSnapshot> sources) {}

    record SourceSnapshot(ThroughputMonitorStorageTracker.SourceLocation source,
                          double insertedPerSecond, double extractedPerSecond) {}
}
