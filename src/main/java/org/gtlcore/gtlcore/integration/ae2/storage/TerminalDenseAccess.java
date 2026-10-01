package org.gtlcore.gtlcore.integration.ae2.storage;

/** Optional backing for counters owned by a terminal read. */
public interface TerminalDenseAccess {

    TerminalDenseCounter gtlcore$dense();

    void gtlcore$dense(TerminalDenseCounter counter);
}
