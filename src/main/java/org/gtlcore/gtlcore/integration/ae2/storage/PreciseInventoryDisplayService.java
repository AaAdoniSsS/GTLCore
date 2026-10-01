package org.gtlcore.gtlcore.integration.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;
import org.gtlcore.gtlcore.mixin.ae2.storage.MEInventoryHandlerDisplayAccessor;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NetworkStorage;

import java.math.BigInteger;
import java.util.*;

/** Explicit amount lookups. Terminal refreshes use TerminalDisplayRead directly, without a second query. */
public final class PreciseInventoryDisplayService {

    private PreciseInventoryDisplayService() {}

    public static Map<AEKey, BigInteger> query(MEStorage root, List<AEKey> keys) {
        Map<AEKey, BigInteger> available = TerminalDisplayRead.collect(root::getAvailableStacks).exact();
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (AEKey key : keys) {
            BigInteger amount = available.get(key);
            if (amount != null) result.put(key, amount);
        }
        return result;
    }

    public static Map<AEKey, BigInteger> query(MEStorage root, List<AEKey> keys, IActionSource source) {
        if (source == null) return query(root, keys);
        if (keys.size() > 256) throw new IllegalArgumentException("Display queries must be batched");
        Query query = new Query(source);
        query.visit(root, keys, 0);
        if (query.exhausted) throw new IllegalStateException("Display query exceeded its traversal budget");
        query.addOtherStorageAmounts();
        query.amounts.values().removeIf(value -> value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
        return query.amounts;
    }

    private static final class Query {

        private final Map<AEKey, BigInteger> amounts = new HashMap<>();
        private final IActionSource source;

        private Query(IActionSource source) {
            this.source = source;
        }

        private final Map<MEStorage, Set<AEKey>> visited = new IdentityHashMap<>();
        private final Map<MEStorage, Set<AEKey>> otherStorages = new IdentityHashMap<>();
        private int remaining = 4096;
        private int operations = 65536;
        private final long deadline = System.nanoTime() + 10_000_000L;
        private boolean exhausted;

        private void visit(MEStorage storage, List<AEKey> requested, int depth) {
            if (storage == null || requested.isEmpty() || exhausted) return;
            if (depth > 64 || --remaining < 0) {
                exhausted = true;
                return;
            }
            Set<AEKey> seen = visited.computeIfAbsent(storage, ignored -> new HashSet<>());
            List<AEKey> keys = new ArrayList<>();
            for (AEKey key : requested) {
                checkBudget();
                if (seen.add(key)) keys.add(key);
            }
            if (keys.isEmpty()) return;
            if (storage instanceof PreciseStorageAmount precise) {
                for (AEKey key : keys) {
                    checkBudget();
                    var amount = precise.getExactStoredAmount(key);
                    if (amount.signum() > 0) {
                        long extractable = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
                        if (extractable < Long.MAX_VALUE) amount = BigInteger.valueOf(Math.max(0, extractable));
                        amounts.merge(key, amount, BigInteger::add);
                    }
                }
                return;
            }
            // Only unwrap known AE2 classes: arbitrary subclasses may impose additional filters.
            Class<?> type = storage.getClass();
            boolean traversable = type == NetworkStorage.class || type == CompositeStorage.class ||
                    type == DelegatingMEInventory.class || type == MEInventoryHandler.class || type == DriveWatcher.class;
            if (traversable && storage instanceof ThroughputStorageView view) {
                if (storage instanceof MEInventoryHandlerDisplayAccessor filter) {
                    if (!filter.gtlcore$allowsDisplayExtraction()) return;
                    if (filter.gtlcore$filtersDisplayExtraction()) keys = keys.stream().filter(filter::gtlcore$canDisplay).toList();
                }
                for (MEStorage child : view.gtlcore$getChildStorages()) visit(child, keys, depth + 1);
            } else {
                otherStorages.computeIfAbsent(storage, ignored -> new HashSet<>()).addAll(keys);
            }
        }

        private void checkBudget() {
            if (--operations < 0 || (operations % 64 == 0 && System.nanoTime() > deadline)) {
                throw new IllegalStateException("Display query exceeded its work budget");
            }
        }

        private void addOtherStorageAmounts() {
            otherStorages.forEach((storage, keys) -> {
                if (keys.isEmpty()) return;
                for (AEKey key : keys) {
                    checkBudget();
                    BigInteger amount = BigInteger.valueOf(storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
                    if (amount.signum() > 0) amounts.merge(key, amount, BigInteger::add);
                }
            });
        }
    }
}
