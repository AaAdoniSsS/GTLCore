package org.gtlcore.gtlcore.integration.gtmt;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.mojang.datafixers.util.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WirelessEnergyMonitorSnapshot {

    public static final int DEFAULT_MAX_TRACKED_ENTRIES = WirelessEnergyDisplayTextLimiter.DEFAULT_MAX_VISIBLE_ENTRIES * 8;

    private static final LinkedHashMap<Pair<UUID, MetaMachine>, Long> RECENT_ENTRIES = new LinkedHashMap<>(DEFAULT_MAX_TRACKED_ENTRIES, 0.75F, true);

    private static int overflowEntryCount;

    private WirelessEnergyMonitorSnapshot() {}

    public static void record(UUID userId, long energyPerTick, MetaMachine machine) {
        if (userId == null || machine == null || DEFAULT_MAX_TRACKED_ENTRIES <= 0) {
            return;
        }

        RECENT_ENTRIES.put(Pair.of(userId, machine), energyPerTick);
        trimToTrackedEntryLimit();
    }

    public static Snapshot drain() {
        List<Map.Entry<Pair<UUID, MetaMachine>, Long>> entries = new ArrayList<>(RECENT_ENTRIES.size());
        for (Map.Entry<Pair<UUID, MetaMachine>, Long> entry : RECENT_ENTRIES.entrySet()) {
            entries.add(Map.entry(entry.getKey(), entry.getValue()));
        }

        Snapshot snapshot = new Snapshot(List.copyOf(entries), overflowEntryCount);
        RECENT_ENTRIES.clear();
        overflowEntryCount = 0;
        return snapshot;
    }

    private static void trimToTrackedEntryLimit() {
        while (RECENT_ENTRIES.size() > DEFAULT_MAX_TRACKED_ENTRIES) {
            Iterator<Pair<UUID, MetaMachine>> iterator = RECENT_ENTRIES.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }

            iterator.next();
            iterator.remove();
            overflowEntryCount = Math.min(Integer.MAX_VALUE, overflowEntryCount + 1);
        }
    }

    public record Snapshot(List<Map.Entry<Pair<UUID, MetaMachine>, Long>> entries, int overflowEntryCount) {}
}
