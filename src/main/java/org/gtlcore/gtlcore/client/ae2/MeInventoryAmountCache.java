package org.gtlcore.gtlcore.client.ae2;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;

final class MeInventoryAmountCache<K> {

    private final int maxEntries;
    private final long availableLifetime;
    private final long unavailableLifetime;
    private final long pendingTimeout;
    private final Map<K, Entry> results;
    private final Map<K, Long> pending;

    MeInventoryAmountCache(int maxEntries, long availableLifetime, long unavailableLifetime, long pendingTimeout) {
        if (maxEntries <= 0 || availableLifetime <= 0 || unavailableLifetime <= 0 || pendingTimeout <= 0) {
            throw new IllegalArgumentException("Cache limits and lifetimes must be positive");
        }
        this.maxEntries = maxEntries;
        this.availableLifetime = availableLifetime;
        this.unavailableLifetime = unavailableLifetime;
        this.pendingTimeout = pendingTimeout;
        this.results = createBoundedMap();
        this.pending = createBoundedMap();
    }

    OptionalLong getOrRequest(K key, long now, Consumer<K> requestSender) {
        Entry result = results.get(key);
        OptionalLong displayedAmount = displayedAmount(result);
        if (result != null && now < result.expiresAt()) {
            return displayedAmount;
        }

        Long pendingDeadline = pending.get(key);
        if (pendingDeadline != null && now < pendingDeadline) {
            return displayedAmount;
        }

        pending.put(key, now + pendingTimeout);
        try {
            requestSender.accept(key);
        } catch (RuntimeException ignored) {
            // Keep the stale value and retry after the pending timeout.
        }
        return displayedAmount;
    }

    void receive(K key, boolean available, long amount, long now) {
        pending.remove(key);
        long lifetime = available ? availableLifetime : unavailableLifetime;
        results.put(key, new Entry(available, Math.max(0, amount), now + lifetime));
    }

    void clear() {
        results.clear();
        pending.clear();
    }

    private static OptionalLong displayedAmount(Entry result) {
        return result != null && result.available() ? OptionalLong.of(result.amount()) : OptionalLong.empty();
    }

    private <V> Map<K, V> createBoundedMap() {
        return new LinkedHashMap<>(maxEntries, 0.75F, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    private record Entry(boolean available, long amount, long expiresAt) {}
}
