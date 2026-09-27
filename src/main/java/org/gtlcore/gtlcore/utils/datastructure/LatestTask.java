package org.gtlcore.gtlcore.utils.datastructure;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellation belongs to the request, not to a reusable worker thread. */
public final class LatestTask {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean cancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }
}
