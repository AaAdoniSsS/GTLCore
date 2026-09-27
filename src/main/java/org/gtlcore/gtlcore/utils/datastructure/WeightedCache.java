package org.gtlcore.gtlcore.utils.datastructure;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/** Owner-thread LRU. Eviction also releases native resources when the value owns them. */
public final class WeightedCache<K, V> {

    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final long limit;
    private final ToLongFunction<V> weigh;
    private final Consumer<V> dispose;
    private long weight;

    public WeightedCache(long limit, ToLongFunction<V> weigh, Consumer<V> dispose) {
        this.limit = Math.max(0, limit);
        this.weigh = weigh;
        this.dispose = dispose;
    }

    public V remove(K key) {
        V value = entries.remove(key);
        if (value != null) weight -= Math.max(0, weigh.applyAsLong(value));
        return value;
    }

    public V get(K key) {
        return entries.get(key);
    }

    public void put(K key, V value) {
        V previous = remove(key);
        if (previous != null && previous != value) dispose.accept(previous);
        long size = Math.max(0, weigh.applyAsLong(value));
        if (size > limit) {
            dispose.accept(value);
            return;
        }
        while (weight > limit - size && !entries.isEmpty()) {
            var iterator = entries.entrySet().iterator();
            V oldest = iterator.next().getValue();
            iterator.remove();
            weight -= Math.max(0, weigh.applyAsLong(oldest));
            dispose.accept(oldest);
        }
        entries.put(key, value);
        weight += size;
    }

    public void clear() {
        entries.values().forEach(dispose);
        entries.clear();
        weight = 0;
    }

    public long weight() {
        return weight;
    }

    public int size() {
        return entries.size();
    }
}
