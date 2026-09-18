package org.gtlcore.gtlcore.integration.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;
import org.gtlcore.gtlcore.mixin.ae2.storage.MEInventoryHandlerDisplayAccessor;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NetworkStorage;

import java.math.BigInteger;
import java.util.*;

/** Read-only, bounded display query. Unknown storage wrappers retain their own visibility rules. */
public final class PreciseInventoryDisplayService {

    private PreciseInventoryDisplayService() {}

    public static Map<AEKey, BigInteger> query(MEStorage root, List<AEKey> keys) {
        return query(root, keys, null);
    }

    public static Map<AEKey, BigInteger> query(MEStorage root, List<AEKey> keys, IActionSource source) {
        Query query = new Query(source);
        query.visit(root, keys, 0);
        if (query.exhausted) return Map.of();
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
        private boolean exhausted;

        private void visit(MEStorage storage, List<AEKey> requested, int depth) {
            if (storage == null || requested.isEmpty() || exhausted) return;
            if (depth > 64 || --remaining < 0) {
                exhausted = true;
                return;
            }
            Set<AEKey> seen = visited.computeIfAbsent(storage, ignored -> new HashSet<>());
            List<AEKey> keys = requested.stream().filter(seen::add).toList();
            if (keys.isEmpty()) return;
            if (storage instanceof PreciseStorageAmount precise) {
                for (AEKey key : keys) {
                    var amount = precise.getExactStoredAmount(key);
                    if (amount.signum() > 0) {
                        if (source != null) {
                            long extractable = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
                            if (extractable < Long.MAX_VALUE) amount = BigInteger.valueOf(Math.max(0, extractable));
                        }
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
                    if (source != null) {
                        if (!filter.gtlcore$allowsDisplayExtraction()) return;
                        if (filter.gtlcore$filtersDisplayExtraction()) keys = keys.stream().filter(filter::gtlcore$canDisplay).toList();
                    } else if (filter.gtlcore$filtersDisplayContents()) {
                        keys = keys.stream().filter(filter::gtlcore$canDisplay).toList();
                    }
                }
                for (MEStorage child : view.gtlcore$getChildStorages()) visit(child, keys, depth + 1);
            } else {
                otherStorages.computeIfAbsent(storage, ignored -> new HashSet<>()).addAll(keys);
            }
        }

        private void addOtherStorageAmounts() {
            otherStorages.forEach((storage, keys) -> {
                if (keys.isEmpty()) return;
                KeyCounter available = source == null ? storage.getAvailableStacks() : null;
                for (AEKey key : keys) {
                    long amount = source == null ? available.get(key) : storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
                    if (amount > 0) amounts.merge(key, BigInteger.valueOf(amount), BigInteger::add);
                }
            });
        }
    }
}
