package org.gtlcore.gtlcore.integration.gtmt;

import java.util.List;

public final class WirelessEnergyDisplayTextLimiterTest {

    private WirelessEnergyDisplayTextLimiterTest() {}

    public static void main(String[] args) {
        keepsOnlyTheConfiguredNumberOfEntries();
        reportsNoHiddenEntriesWhenEverythingIsVisible();
    }

    private static void keepsOnlyTheConfiguredNumberOfEntries() {
        List<Integer> entries = List.of(1, 2, 3, 4, 5);
        int maxVisibleEntries = 3;

        List<Integer> visibleEntries = WirelessEnergyDisplayTextLimiter.limit(entries, maxVisibleEntries);

        expectEquals(List.of(1, 2, 3), visibleEntries, "visible entries");
        expectEquals(2, WirelessEnergyDisplayTextLimiter.hiddenEntryCount(entries.size(), visibleEntries.size()),
                "hidden entry count");
    }

    private static void reportsNoHiddenEntriesWhenEverythingIsVisible() {
        List<Integer> entries = List.of(1, 2);
        int maxVisibleEntries = 5;

        List<Integer> visibleEntries = WirelessEnergyDisplayTextLimiter.limit(entries, maxVisibleEntries);

        expectEquals(entries, visibleEntries, "visible entries");
        expectEquals(0, WirelessEnergyDisplayTextLimiter.hiddenEntryCount(entries.size(), visibleEntries.size()),
                "hidden entry count");
    }

    private static void expectEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }
}
