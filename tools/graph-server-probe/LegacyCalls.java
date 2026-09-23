package org.gtlcore.test;

import java.util.concurrent.atomic.AtomicLong;

/** Observable old-core entry counters in the opt-in fixture, never shipped with Core. */
public final class LegacyCalls {
    public static final AtomicLong planner = new AtomicLong(), executor = new AtomicLong(), maxFast = new AtomicLong();
    public static void checkZero() {
        String values = "planner=" + planner.get() + " executor=" + executor.get() + " max_fast=" + maxFast.get();
        if (planner.get() != 0 || executor.get() != 0 || maxFast.get() != 0) throw new AssertionError(values);
        System.out.println("[Graph Probe] PASS: old core call counters " + values);
    }
}
