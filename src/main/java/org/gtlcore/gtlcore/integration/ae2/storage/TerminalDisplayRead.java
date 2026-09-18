package org.gtlcore.gtlcore.integration.ae2.storage;

import java.util.function.Supplier;

/** Limits saturation to the terminal's list snapshot, not live storage or crafting counters. */
public final class TerminalDisplayRead {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private TerminalDisplayRead() {}

    public static boolean active() {
        return ACTIVE.get();
    }

    public static <T> T collect(Supplier<T> read) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return read.get();
        } finally {
            if (previous) ACTIVE.set(true);
            else ACTIVE.remove();
        }
    }
}
