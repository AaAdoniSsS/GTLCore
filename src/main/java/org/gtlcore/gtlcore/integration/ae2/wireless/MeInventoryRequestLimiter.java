package org.gtlcore.gtlcore.integration.ae2.wireless;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

final class MeInventoryRequestLimiter<K> {

    private final int maxRequests;
    private final long windowTicks;
    private final Map<K, ArrayDeque<Long>> requestTicks = new WeakHashMap<>();

    MeInventoryRequestLimiter(int maxRequests, long windowTicks) {
        if (maxRequests <= 0 || windowTicks <= 0) {
            throw new IllegalArgumentException("Request limit and window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowTicks = windowTicks;
    }

    boolean tryAcquire(K requester, long currentTick) {
        Objects.requireNonNull(requester);
        ArrayDeque<Long> history = requestTicks.computeIfAbsent(requester, ignored -> new ArrayDeque<>());
        if (!history.isEmpty() && currentTick < history.peekLast()) {
            history.clear();
        }

        long cutoff = currentTick - windowTicks;
        while (!history.isEmpty() && history.peekFirst() <= cutoff) {
            history.removeFirst();
        }
        if (history.size() >= maxRequests) {
            return false;
        }
        history.addLast(currentTick);
        return true;
    }
}
