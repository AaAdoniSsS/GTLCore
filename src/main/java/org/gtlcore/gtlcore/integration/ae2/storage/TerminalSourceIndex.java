package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Maintained at cell mutation time. Readers have independent cursors; the journal is bounded. */
public final class TerminalSourceIndex {

    private static final int JOURNAL_SIZE = 4096;
    private final Object2IntOpenHashMap<AEKey> positions = new Object2IntOpenHashMap<>();
    private final int[] journal = new int[JOURNAL_SIZE];
    private AEKey[] keys = new AEKey[16];
    private long[] amounts = new long[16];
    private final Map<Integer, BigInteger> exact = new HashMap<>();
    private int size;
    private int live;
    private long revision;
    private long epoch;

    public TerminalSourceIndex() {
        positions.defaultReturnValue(-1);
    }

    public void set(AEKey key, long amount, BigInteger overflow) {
        int id = positions.getInt(key);
        if (id < 0) {
            if (amount == 0 && overflow == null) return;
            if (size == keys.length) {
                keys = Arrays.copyOf(keys, size * 2);
                amounts = Arrays.copyOf(amounts, size * 2);
            }
            id = size++;
            positions.put(key, id);
            keys[id] = key;
        }
        BigInteger oldExact = exact.get(id);
        if (amounts[id] == amount && Objects.equals(oldExact, overflow)) return;
        boolean oldLive = amounts[id] != 0 || oldExact != null;
        boolean newLive = amount != 0 || overflow != null;
        if (oldLive != newLive) live += newLive ? 1 : -1;
        amounts[id] = amount;
        if (overflow == null) exact.remove(id);
        else exact.put(id, overflow);
        journal[(int) (revision++ % JOURNAL_SIZE)] = id;
        if (size > 1024 && live * 2 < size) compact();
    }

    private void compact() {
        int next = 0;
        Map<Integer, BigInteger> moved = new HashMap<>();
        positions.clear();
        for (int i = 0; i < size; i++) {
            if (amounts[i] == 0 && !exact.containsKey(i)) continue;
            keys[next] = keys[i];
            amounts[next] = amounts[i];
            positions.put(keys[next], next);
            if (exact.containsKey(i)) moved.put(next, exact.get(i));
            next++;
        }
        Arrays.fill(keys, next, size, null);
        Arrays.fill(amounts, next, size, 0);
        exact.clear();
        exact.putAll(moved);
        size = next;
        epoch++;
    }

    int size() {
        return size;
    }

    AEKey key(int id) {
        return keys[id];
    }

    long amount(int id) {
        return amounts[id];
    }

    BigInteger exact(int id) {
        return exact.get(id);
    }

    long revision() {
        return revision;
    }

    long epoch() {
        return epoch;
    }

    /** Repeated IDs are coalesced by the caller. False requests a sequential full scan. */
    boolean changesSince(long previous, IntConsumer changed) {
        long count = revision - previous;
        if (count < 0 || count > JOURNAL_SIZE || count > size / 2) return false;
        for (long r = previous; r < revision; r++) changed.accept(journal[(int) (r % JOURNAL_SIZE)]);
        return true;
    }
}
