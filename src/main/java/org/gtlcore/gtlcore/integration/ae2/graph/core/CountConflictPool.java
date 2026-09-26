package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.util.*;

/** Coordinator-owned, inventory-scoped conflict cache; eviction only loses pruning. */
final class CountConflictPool implements AutoCloseable {

    private static final int CAPACITY = 128;

    private static final class Entry {

        double activity = 1;
        long used;
    }

    private final Map<CountConflict, Entry> entries = new LinkedHashMap<>();
    private final PlanningBudget budget;
    private long memory, learned, reused, evicted, clock;

    CountConflictPool(PlanningBudget budget) {
        this.budget = budget;
    }

    void add(Collection<CountConflict> proofs) {
        if (proofs.isEmpty()) return;
        if (memory == 0) {
            if (!budget.tryReserve(256L << 10)) return;
            memory = 256L << 10;
        }
        for (var conflict : proofs) {
            if (entries.containsKey(conflict)) continue;
            // Very large clauses are valid, but need not occupy the shared pool.
            if (conflict.assumptions().stream().mapToLong(row -> row.terms().size()).sum() > 64) continue;
            if (entries.size() == CAPACITY) {
                var victim = entries.entrySet().stream().min(Comparator.<Map.Entry<CountConflict, Entry>>comparingDouble(e -> e.getValue().activity)
                        .thenComparingLong(e -> e.getValue().used)).orElseThrow();
                entries.remove(victim.getKey());
                evicted++;
            }
            entries.put(conflict, new Entry());
            learned++;
        }
    }

    void used(Collection<CountConflict> conflicts) {
        clock++;
        if (clock % 32 == 0) entries.values().forEach(entry -> entry.activity *= 0.5);
        for (var conflict : conflicts) {
            var entry = entries.get(conflict);
            if (entry != null) {
                entry.activity += 4;
                entry.used = clock;
                reused++;
            }
        }
    }

    List<CountConflict> snapshot() {
        return entries.entrySet().stream().sorted(Comparator.<Map.Entry<CountConflict, Entry>>comparingDouble(e -> e.getValue().activity).reversed())
                .map(Map.Entry::getKey).toList();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    void report() {
        if (learned > 0) budget.note("count_conflict_pool", "learned=" + learned + "; used=" + reused +
                "; retained=" + entries.size() + "; evicted=" + evicted);
    }

    @Override
    public void close() {
        entries.clear();
        budget.release(memory);
        memory = 0;
    }
}
