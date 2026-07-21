package org.gtlcore.gtlcore.integration.ae2.throughput;

public enum ThroughputMonitorUpdateInterval {

    TICK(1, "t"),
    SECOND(ThroughputCache.TICKS_PER_SECOND, "s"),
    MINUTE(60 * ThroughputCache.TICKS_PER_SECOND, "1m"),
    FIVE_MINUTES(5 * 60 * ThroughputCache.TICKS_PER_SECOND, "5m"),
    TEN_MINUTES(10 * 60 * ThroughputCache.TICKS_PER_SECOND, "10m"),
    THIRTY_MINUTES(30 * 60 * ThroughputCache.TICKS_PER_SECOND, "30m"),
    HOUR(60 * 60 * ThroughputCache.TICKS_PER_SECOND, "1h");

    private static final ThroughputMonitorUpdateInterval[] VALUES = values();

    private final int ticks;
    private final String label;

    ThroughputMonitorUpdateInterval(int ticks, String label) {
        this.ticks = ticks;
        this.label = label;
    }

    public int ticks() {
        return ticks;
    }

    public String label() {
        return label;
    }

    public ThroughputMonitorUpdateInterval next(boolean backwards) {
        int step = backwards ? -1 : 1;
        return VALUES[Math.floorMod(ordinal() + step, VALUES.length)];
    }
}
