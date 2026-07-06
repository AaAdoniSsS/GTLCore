package org.gtlcore.gtlcore.integration.gtmt;

import java.util.List;

public final class WirelessEnergyDisplayTextLimiter {

    public static final int DEFAULT_MAX_VISIBLE_ENTRIES = 64;

    private WirelessEnergyDisplayTextLimiter() {}

    public static <T> List<T> limit(List<T> entries, int maxVisibleEntries) {
        if (maxVisibleEntries < 0) {
            throw new IllegalArgumentException("maxVisibleEntries must not be negative");
        }
        if (entries.size() <= maxVisibleEntries) {
            return entries;
        }
        return entries.subList(0, maxVisibleEntries);
    }

    public static int hiddenEntryCount(int totalEntryCount, int visibleEntryCount) {
        return Math.max(0, totalEntryCount - visibleEntryCount);
    }
}
