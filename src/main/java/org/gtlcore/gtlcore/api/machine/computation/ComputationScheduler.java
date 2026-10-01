package org.gtlcore.gtlcore.api.machine.computation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Server-thread allocation across overlapping source sets, with rotating minimum grants. */
public final class ComputationScheduler {

    private static final long DEMAND_LIFETIME = 12;
    private final Map<Object, Demand> demands = new IdentityHashMap<>();
    private final List<Demand> order = new ArrayList<>();
    private final Map<ComputationSource, Long> reserved = new IdentityHashMap<>();
    private long epoch = Long.MIN_VALUE;
    private int cursor;

    public void advance(long tick) {
        if (epoch == tick) return;
        epoch = tick;
        reserved.clear();
        order.removeIf(demand -> {
            demand.grants.clear();
            if (tick >= demand.lastSeen && tick - demand.lastSeen <= DEMAND_LIFETIME) return false;
            demands.remove(demand.owner);
            return true;
        });
        if (order.isEmpty()) return;
        cursor = (cursor + 1) % order.size();
        // Fund whole minimums before distributing elastic research work.
        for (int i = 0; i < order.size(); i++) {
            Demand demand = order.get((cursor + i) % order.size());
            if (free(demand.sources) >= demand.minimum) reserve(demand, demand.minimum);
        }
        for (int i = 0; i < order.size(); i++) {
            Demand demand = order.get((cursor + i) % order.size());
            if (!demand.grants.isEmpty()) reserve(demand, demand.maximum - total(demand.grants));
        }
    }

    public long available(Object owner, List<ComputationSource> sources) {
        Demand demand = demands.get(owner);
        long amount = 0;
        var seen = Collections.newSetFromMap(new IdentityHashMap<ComputationSource, Boolean>());
        for (ComputationSource source : sources) {
            if (!seen.add(source)) continue;
            long held = demand == null ? 0 : demand.grants.getOrDefault(source, 0L);
            long remaining = Math.max(0, source.gtlcore$availableComputation());
            long others = reserved.getOrDefault(source, 0L) - held;
            amount = ComputationMath.add(amount, Math.max(0, remaining - others));
        }
        return amount;
    }

    public long offer(Object owner, List<ComputationSource> sources, long minimum, long maximum) {
        if (minimum <= 0 || maximum < minimum) return 0;
        var unique = Collections.newSetFromMap(new IdentityHashMap<ComputationSource, Boolean>());
        sources = sources.stream().filter(unique::add).toList();
        Demand demand = demands.get(owner);
        if (demand == null) {
            demand = new Demand(owner);
            demands.put(owner, demand);
            order.add(demand);
        }
        demand.lastSeen = epoch;
        if (!sameSources(demand.sources, sources) || minimum != demand.minimum || maximum != demand.maximum) {
            releaseGrants(demand);
        }
        demand.sources = sources;
        demand.minimum = minimum;
        demand.maximum = maximum;
        long held = total(demand.grants);
        long possible = available(owner, sources);
        if (possible < minimum) {
            releaseGrants(demand);
            return 0;
        }
        if (held < maximum) reserve(demand, maximum - held);
        return Math.min(maximum, Math.min(possible, total(demand.grants)));
    }

    public Transaction withdraw(Object owner, long amount) {
        Demand demand = demands.get(owner);
        if (demand == null || amount <= 0 || total(demand.grants) < amount) return null;
        Transaction transaction = new Transaction(amount);
        long left = amount;
        try {
            for (ComputationSource source : demand.sources) {
                long take = Math.min(left, demand.grants.getOrDefault(source, 0L));
                if (take == 0) continue;
                var receipt = source.gtlcore$withdrawComputation(take);
                if (receipt == null) {
                    transaction.close();
                    releaseGrants(demand);
                    return null;
                }
                transaction.receipts.add(receipt);
                if (receipt.amount() != take) throw new IllegalStateException("Computation source broke reservation");
                left -= take;
                if (left == 0) break;
            }
            if (left != 0) throw new IllegalStateException("Incomplete computation allocation");
            releaseGrants(demand);
            return transaction;
        } catch (RuntimeException | Error failure) {
            transaction.close();
            releaseGrants(demand);
            throw failure;
        }
    }

    public void forget(Object owner) {
        Demand demand = demands.remove(owner);
        if (demand != null) {
            releaseGrants(demand);
            order.remove(demand);
        }
    }

    /** A topology change invalidates reservations as well as adjacency. Owners offer again on their next tick. */
    public void invalidate() {
        reserved.clear();
        demands.clear();
        order.clear();
        cursor = 0;
    }

    private void reserve(Demand demand, long wanted) {
        for (ComputationSource source : demand.sources) {
            long available = Math.max(0, source.gtlcore$availableComputation() - reserved.getOrDefault(source, 0L));
            long take = Math.min(wanted, available);
            if (take > 0) {
                demand.grants.merge(source, take, Math::addExact);
                reserved.merge(source, take, Math::addExact);
                wanted -= take;
            }
            if (wanted == 0) break;
        }
    }

    private long free(List<ComputationSource> sources) {
        long amount = 0;
        for (ComputationSource source : sources) {
            amount = ComputationMath.add(amount,
                    Math.max(0, source.gtlcore$availableComputation() - reserved.getOrDefault(source, 0L)));
        }
        return amount;
    }

    private void releaseGrants(Demand demand) {
        demand.grants.forEach((source, amount) -> {
            long remaining = reserved.getOrDefault(source, 0L) - amount;
            if (remaining <= 0) reserved.remove(source);
            else reserved.put(source, remaining);
        });
        demand.grants.clear();
    }

    private static boolean sameSources(List<ComputationSource> a, List<ComputationSource> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static long total(Map<ComputationSource, Long> amounts) {
        long total = 0;
        for (long amount : amounts.values()) total = ComputationMath.add(total, amount);
        return total;
    }

    private static final class Demand {

        private final Object owner;
        private List<ComputationSource> sources = List.of();
        private final Map<ComputationSource, Long> grants = new IdentityHashMap<>();
        private long minimum;
        private long maximum;
        private long lastSeen;

        private Demand(Object owner) {
            this.owner = owner;
        }
    }

    public static final class Transaction implements AutoCloseable {

        private final long amount;
        private final List<ComputationSource.Receipt> receipts = new ArrayList<>();
        private final List<Runnable> committed = new ArrayList<>();
        private boolean closed;

        private Transaction(long amount) {
            this.amount = amount;
        }

        public long amount() {
            return amount;
        }

        public void commit() {
            if (closed) return;
            closed = true;
            receipts.clear();
            for (Runnable callback : committed) callback.run();
            committed.clear();
        }

        public void onCommit(Runnable callback) {
            if (closed) throw new IllegalStateException("Computation transaction already closed");
            committed.add(callback);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                for (int i = receipts.size() - 1; i >= 0; i--) receipts.get(i).refund().run();
            } finally {
                receipts.clear();
                committed.clear();
            }
        }
    }
}
