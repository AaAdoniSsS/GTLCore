package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.math.BigInteger;
import java.util.*;

/** Menu-owned stable IDs and independent source cursors. No network result is shared across menus. */
public final class TerminalInventoryIndex {

    private final Object2IntOpenHashMap<AEKey> positions = new Object2IntOpenHashMap<>();
    private final List<AEKey> keys = new ArrayList<>();
    private final Map<TerminalSourceIndex, Observed> observed = new IdentityHashMap<>();
    private TerminalDenseCounter previous;
    private TerminalDenseCounter residual;
    private KeyCounter lastCounter;
    private KeyCounter baseline;
    private Set<AEKey> changes;
    private final Map<Object, TerminalFilteredView> filters = new IdentityHashMap<>();
    private long read;

    void beginRead() {
        read++;
    }

    void filter(Object handler, Object rules, TerminalDenseCounter input, TerminalDenseCounter target,
                java.util.function.Predicate<AEKey> accepts) {
        filters.computeIfAbsent(handler, ignored -> new TerminalFilteredView()).append(target, input, rules, accepts, read);
    }

    public TerminalInventoryIndex() {
        positions.defaultReturnValue(-1);
    }

    int id(AEKey key) {
        Objects.requireNonNull(key);
        int id = positions.getInt(key);
        if (id < 0) {
            id = keys.size();
            positions.put(key, id);
            keys.add(key);
        }
        return id;
    }

    int find(AEKey key) {
        return positions.getInt(Objects.requireNonNull(key));
    }

    AEKey key(int id) {
        return keys.get(id);
    }

    /** Recreate the menu index after substantial key churn, bounding retained dead identities. */
    public boolean shouldCompact() {
        return previous != null && keys.size() > 1024 && previous.used.cardinality() * 2 < keys.size();
    }

    public Set<AEKey> changesFor(KeyCounter before, KeyCounter current) {
        return before == baseline && current == lastCounter ? changes : null;
    }

    TerminalDenseCounter finish(KeyCounter counter, TerminalDenseCounter incoming) {
        try {
            TerminalDenseCounter result = previous == null ? new TerminalDenseCounter(this) : previous.copy();
            // Unknown inventories are polled every time. Only maintained cell sources use cursors.
            replaceResidual(result, residual, incoming);
            var iterator = observed.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!incoming.sources.containsKey(entry.getKey())) {
                    entry.getValue().remove(result);
                    iterator.remove();
                }
            }
            incoming.sources.forEach((source, count) -> observed.computeIfAbsent(source, ignored -> new Observed())
                    .update(source, count, result));
            changes = new HashSet<>();
            // Sparse journals examine dirty IDs only. Dense updates iterate a contiguous quantity array.
            if (result.touched.cardinality() > keys.size() / 2) {
                for (int id = 0; id < keys.size(); id++) compare(result, id);
            } else {
                for (int id = result.touched.nextSetBit(0); id >= 0; id = result.touched.nextSetBit(id + 1)) compare(result, id);
            }
            incoming.sources.clear();
            residual = incoming;
            previous = result;
            baseline = lastCounter;
            lastCounter = counter;
            filters.values().removeIf(filter -> filter.prune(read));
            return result;
        } catch (RuntimeException | Error failure) {
            // A partial source-cursor update must never become the next baseline.
            invalidate();
            throw failure;
        }
    }

    void invalidate() {
        observed.clear();
        previous = null;
        residual = null;
        lastCounter = null;
        baseline = null;
        changes = null;
        filters.clear();
    }

    private void compare(TerminalDenseCounter result, int id) {
        if (result.amount(id) != (previous == null ? 0 : previous.amount(id))) changes.add(key(id));
    }

    private static void replaceResidual(TerminalDenseCounter result, TerminalDenseCounter old, TerminalDenseCounter next) {
        BitSet ids = (BitSet) next.used.clone();
        if (old != null) ids.or(old.used);
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            long oldAmount = old == null ? 0 : old.amount(id);
            BigInteger oldExact = old == null ? null : old.exact.get(id);
            if (oldAmount == next.amount(id) && Objects.equals(oldExact, next.exact.get(id))) continue;
            result.add(id, oldAmount, oldExact, -1);
            result.add(id, next.amount(id), next.exact.get(id), 1);
        }
    }

    private final class Observed {

        private int[] ids = new int[0];
        private long[] amounts = new long[0];
        private final Map<Integer, BigInteger> exact = new HashMap<>();
        private int count;
        private long revision;
        private long epoch = -1;

        void remove(TerminalDenseCounter result) {
            for (int slot = 0; slot < ids.length; slot++) result.add(ids[slot], amounts[slot], exact.get(slot), -count);
        }

        void update(TerminalSourceIndex source, int multiplicity, TerminalDenseCounter result) {
            boolean rebuild = epoch != source.epoch() || count != multiplicity;
            if (rebuild) {
                remove(result);
                ids = new int[0];
                amounts = new long[0];
                exact.clear();
                count = multiplicity;
            }
            int oldSize = ids.length;
            if (oldSize != source.size()) {
                ids = Arrays.copyOf(ids, source.size());
                amounts = Arrays.copyOf(amounts, source.size());
                for (int slot = oldSize; slot < ids.length; slot++) ids[slot] = id(source.key(slot));
            }
            BitSet dirty = new BitSet();
            if (rebuild || !source.changesSince(revision, dirty::set)) {
                for (int slot = 0; slot < ids.length; slot++) updateSlot(source, slot, result);
            } else {
                for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) updateSlot(source, slot, result);
            }
            epoch = source.epoch();
            revision = source.revision();
        }

        private void updateSlot(TerminalSourceIndex source, int slot, TerminalDenseCounter result) {
            long amount = source.amount(slot);
            BigInteger precise = source.exact(slot);
            BigInteger oldExact = exact.get(slot);
            if (amounts[slot] == amount && Objects.equals(oldExact, precise)) return;
            int id = ids[slot];
            result.add(id, amounts[slot], oldExact, -count);
            result.add(id, amount, precise, count);
            amounts[slot] = amount;
            if (precise == null) exact.remove(slot);
            else exact.put(slot, precise);
        }
    }
}
