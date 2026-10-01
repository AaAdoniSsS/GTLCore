package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** A terminal snapshot. Ordinary quantities never enter a native variant map. */
public final class TerminalDenseCounter {

    final TerminalInventoryIndex index;
    long[] amounts = new long[16];
    final BitSet used = new BitSet();
    final BitSet touched = new BitSet();
    final Int2ObjectOpenHashMap<BigInteger> exact = new Int2ObjectOpenHashMap<>();
    final Map<TerminalSourceIndex, Integer> sources = new IdentityHashMap<>();

    TerminalDenseCounter(TerminalInventoryIndex index) {
        this.index = index;
    }

    TerminalDenseCounter copy() {
        TerminalDenseCounter copy = new TerminalDenseCounter(index);
        copy.amounts = amounts.clone();
        copy.used.or(used);
        copy.exact.putAll(exact);
        return copy;
    }

    void defer(TerminalSourceIndex source) {
        sources.merge(source, 1, Math::addExact);
    }

    void append(TerminalDenseCounter source) {
        source.sources.forEach((inventory, count) -> sources.merge(inventory, count, Math::addExact));
        for (int id = source.used.nextSetBit(0); id >= 0; id = source.used.nextSetBit(id + 1)) {
            add(index == source.index ? id : index.id(source.index.key(id)), source.amount(id), source.exact.get(id), 1);
        }
    }

    void flush() {
        if (sources.isEmpty()) return;
        sources.forEach((source, count) -> {
            for (int slot = 0; slot < source.size(); slot++) {
                long amount = source.amount(slot);
                BigInteger precise = source.exact(slot);
                if (amount == 0 && precise == null) continue;
                add(index.id(source.key(slot)), amount, precise, count);
            }
        });
        sources.clear();
    }

    public long get(AEKey key) {
        flush();
        int id = index.find(key);
        return id < 0 ? 0 : amount(id);
    }

    public int size() {
        flush();
        return used.cardinality();
    }

    long amount(int id) {
        return id < amounts.length ? amounts[id] : 0;
    }

    void add(AEKey key, long amount) {
        add(index.id(key), amount, null, 1);
    }

    void add(AEKey key, BigInteger amount) {
        add(index.id(key), 0, amount, 1);
    }

    void add(int id, long amount, BigInteger precise, int count) {
        if (count == 0 || (amount == 0 && precise == null)) return;
        if (count != 1) {
            if (precise == null) {
                try {
                    amount = Math.multiplyExact(amount, (long) count);
                } catch (ArithmeticException overflow) {
                    precise = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(count));
                }
            } else precise = precise.multiply(BigInteger.valueOf(count));
        }
        long previous = amount(id);
        BigInteger previousExact = exact.get(id);
        long total = previous + amount;
        if (precise == null && previousExact == null && ((previous ^ total) & (amount ^ total)) >= 0) {
            put(id, total, null);
        } else {
            if (previousExact == null) previousExact = BigInteger.valueOf(previous);
            if (precise == null) precise = BigInteger.valueOf(amount);
            BigInteger sum = previousExact.add(precise);
            if (sum.bitLength() < 64) put(id, sum.longValue(), null);
            else put(id, sum.signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE, sum);
        }
    }

    private void put(int id, long amount, BigInteger precise) {
        if (id >= amounts.length) amounts = Arrays.copyOf(amounts, Math.max(id + 1, amounts.length * 2));
        amounts[id] = amount;
        if (precise == null) exact.remove(id);
        else exact.put(id, precise);
        used.set(id, amount != 0 || precise != null);
        touched.set(id);
    }

    Map<AEKey, BigInteger> precision() {
        flush();
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (var entry : exact.int2ObjectEntrySet()) result.put(index.key(entry.getIntKey()), entry.getValue());
        return result;
    }
}
