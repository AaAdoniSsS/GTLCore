package org.gtlcore.gtlcore.integration.ae2.storage;

import org.gtlcore.gtlcore.mixin.ae2.storage.TerminalCounterAccessor;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Carries exact amounts alongside counters created by one native terminal inventory read. */
public final class TerminalDisplayRead {

    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final ThreadLocal<Read> ACTIVE = new ThreadLocal<>();

    private TerminalDisplayRead() {}

    private static final class Read {

        // Only counters constructed during this read belong to it. Reading a live or cached
        // counter never enrolls that source in precision tracking.
        private final Map<KeyCounter, Map<AEKey, BigInteger>> counters = new IdentityHashMap<>();
        private Map<Object, KeyCounter> filteredSources;
        private boolean writing;
        private Merge merge;
        private final TerminalInventoryIndex index;

        private Read(TerminalInventoryIndex index) {
            this.index = index;
        }
    }

    private record Merge(KeyCounter counter, Reference2ObjectMap<Object, TerminalVariantAccess> target,
                         Reference2ObjectMap<Object, TerminalVariantAccess> source, Map<AEKey, BigInteger> corrections) {}

    public record Snapshot(KeyCounter available, Map<AEKey, BigInteger> exact) {}

    public static Snapshot collect(Supplier<KeyCounter> read) {
        return collect(new TerminalInventoryIndex(), read);
    }

    public static Snapshot collect(TerminalInventoryIndex index, Supplier<KeyCounter> read) {
        Read previous = ACTIVE.get();
        Read current = new Read(index);
        index.beginRead();
        ACTIVE.set(current);
        try {
            KeyCounter available = read.get();
            TerminalDenseCounter dense = dense(available);
            Map<AEKey, BigInteger> overflow;
            if (dense != null && dense.index == index) {
                dense = index.finish(available, dense);
                ((TerminalDenseAccess) (Object) available).gtlcore$dense(dense);
                overflow = dense.precision();
            } else {
                index.invalidate();
                overflow = current.counters.get(available);
            }
            Map<AEKey, BigInteger> exact = new HashMap<>();
            if (overflow != null) {
                overflow.forEach((key, amount) -> {
                    if (amount.compareTo(MAX) > 0 && available.get(key) == Long.MAX_VALUE) exact.put(key, amount);
                });
            }
            return new Snapshot(available, Map.copyOf(exact));
        } catch (RuntimeException | Error failure) {
            index.invalidate();
            throw failure;
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static void created(KeyCounter counter) {
        Read read = ACTIVE.get();
        if (read != null) {
            read.counters.put(counter, Map.of());
            ((TerminalDenseAccess) (Object) counter).gtlcore$dense(new TerminalDenseCounter(read.index));
        }
    }

    public static TerminalDenseCounter dense(KeyCounter counter) {
        return counter == null ? null : ((TerminalDenseAccess) (Object) counter).gtlcore$dense();
    }

    /** Native operations outside the display hot path retain the complete KeyCounter API. */
    public static void materialize(KeyCounter counter) {
        TerminalDenseCounter dense = dense(counter);
        if (dense == null) return;
        dense.flush();
        ((TerminalDenseAccess) (Object) counter).gtlcore$dense(null);
        runNative(() -> {
            for (int id = dense.used.nextSetBit(0); id >= 0; id = dense.used.nextSetBit(id + 1)) {
                counter.set(dense.index.key(id), dense.amount(id));
            }
        });
        Read read = ACTIVE.get();
        if (read != null && read.counters.containsKey(counter)) read.counters.put(counter, dense.precision());
    }

    public static void append(KeyCounter counter, KeyCounter nativeSource, Map<AEKey, BigInteger> exact,
                              TerminalSourceIndex source) {
        TerminalDenseCounter dense = dense(counter);
        if (dense != null) dense.defer(source);
        else append(counter, nativeSource, exact);
    }

    public static boolean tracks(KeyCounter counter) {
        Read read = ACTIVE.get();
        return read != null && !read.writing && read.counters.containsKey(counter);
    }

    /** Run a native counter merge without routing every ordinary value through precision tracking. */
    private static void runNative(Runnable operation) {
        Read read = ACTIVE.get();
        if (read == null) {
            operation.run();
            return;
        }
        boolean previous = read.writing;
        read.writing = true;
        try {
            operation.run();
        } finally {
            read.writing = previous;
        }
    }

    /**
     * Copies a cell's maintained native counter and exact overflow index once per read.
     * Merge ordinary amounts through native sub-maps, correcting only exact/overflowing keys.
     * Compute corrections before mutation: native counters may either wrap or saturate on overflow.
     */
    public static void append(KeyCounter counter, KeyCounter source, Map<AEKey, BigInteger> exact) {
        Read read = ACTIVE.get();
        TerminalDenseCounter dense = dense(counter);
        materialize(source);
        if (dense != null) {
            for (var entry : source) {
                BigInteger precise = exact.get(entry.getKey());
                if (precise == null) dense.add(entry.getKey(), entry.getLongValue());
                else dense.add(entry.getKey(), precise);
            }
            return;
        }
        var before = ((TerminalCounterAccessor) (Object) counter).gtlcore$terminalGroups();
        var incomingGroups = ((TerminalCounterAccessor) (Object) source).gtlcore$terminalGroups();
        if (counter.isEmpty() && read.counters.get(counter).isEmpty()) {
            runNative(() -> counter.addAll(source));
            if (!exact.isEmpty()) read.counters.put(counter, new HashMap<>(exact));
            return;
        }
        Map<AEKey, BigInteger> overflow = read.counters.get(counter);
        Map<AEKey, BigInteger> corrections = new HashMap<>();
        for (var entry : exact.entrySet()) {
            AEKey key = entry.getKey();
            BigInteger previous = overflow.get(key);
            if (previous == null) previous = BigInteger.valueOf(counter.get(key));
            corrections.put(key, previous.add(entry.getValue()));
        }
        for (var entry : overflow.entrySet()) {
            AEKey key = entry.getKey();
            if (exact.containsKey(key)) continue;
            long incoming = source.get(key);
            if (incoming != 0) corrections.put(key, entry.getValue().add(BigInteger.valueOf(incoming)));
        }
        // Native addAll already resolves each primary group. Inspect overlapping values there,
        // instead of scanning and looking up every source group a second time here.
        Merge previous = read.merge;
        read.merge = new Merge(counter, before, incomingGroups, corrections);
        try {
            runNative(() -> counter.addAll(source));
        } finally {
            read.merge = previous;
        }
        corrections.forEach((key, amount) -> store(read, counter, key, amount));
    }

    /** Inspect each overlapping group before native addition can wrap or saturate its values. */
    public static void beforeGroupMerge(KeyCounter counter, Reference2ObjectMap<?, ?> groups, Object primaryKey,
                                        TerminalVariantAccess target) {
        Read read = ACTIVE.get();
        Merge merge = read == null ? null : read.merge;
        if (merge == null || merge.counter() != counter || merge.target() != groups) return;
        TerminalVariantAccess source = merge.source().get(primaryKey);
        if (source == null) return;
        Map<AEKey, BigInteger> corrections = merge.corrections();
        for (var entry : source) {
            AEKey key = entry.getKey();
            long stored = target.gtlcore$terminalAmount(key);
            long incoming = entry.getLongValue();
            long total = stored + incoming;
            if (((stored ^ total) & (incoming ^ total)) < 0 && !corrections.containsKey(key)) {
                corrections.put(key, BigInteger.valueOf(stored).add(BigInteger.valueOf(incoming)));
            }
        }
    }

    public static void add(KeyCounter counter, AEKey key, long amount) {
        TerminalDenseCounter dense = dense(counter);
        if (dense != null) {
            dense.add(key, amount);
            return;
        }
        Read read = ACTIVE.get();
        Map<AEKey, BigInteger> overflow = read.counters.get(counter);
        BigInteger previous = overflow.isEmpty() ? null : overflow.get(key);
        long stored = counter.get(key);
        long total = stored + amount;
        if (previous == null && ((stored ^ amount) < 0 || (stored ^ total) >= 0)) {
            write(read, counter, key, total);
        } else {
            addExact(counter, key, BigInteger.valueOf(amount));
        }
    }

    public static void addExact(KeyCounter counter, AEKey key, BigInteger amount) {
        TerminalDenseCounter dense = dense(counter);
        if (dense != null) {
            dense.add(key, amount);
            return;
        }
        Read read = ACTIVE.get();
        Map<AEKey, BigInteger> overflow = read.counters.get(counter);
        BigInteger previous = overflow.get(key);
        if (previous == null) previous = BigInteger.valueOf(counter.get(key));
        store(read, counter, key, previous.add(amount));
    }

    public static void filteredSource(Object handler, KeyCounter source) {
        Read read = ACTIVE.get();
        if (read == null) return;
        if (read.filteredSources == null) read.filteredSources = new IdentityHashMap<>();
        read.filteredSources.put(handler, source);
    }

    /** Called inside the native handler's recursion/allowExtraction guard, instead of its loop. */
    public static boolean filter(KeyCounter target, KeyCounter source, Object handler, Object rules,
                                 java.util.function.Predicate<AEKey> accepts) {
        if (!tracks(target)) return false;
        TerminalDenseCounter out = dense(target);
        TerminalDenseCounter input = dense(source);
        if (out == null || input == null || out == input) return false;
        if (rules != null) {
            out.index.filter(handler, rules, input, out, accepts);
        } else {
            // A dynamic addon predicate must see the current aggregated inventory each time.
            input.flush();
            for (int id = input.used.nextSetBit(0); id >= 0; id = input.used.nextSetBit(id + 1)) {
                AEKey key = input.index.key(id);
                if (accepts.test(key)) out.add(out.index == input.index ? id : out.index.id(key),
                        input.amount(id), input.exact.get(id), 1);
            }
        }
        return true;
    }

    /** Transfer only native-filter-approved keys, retaining the temporary counter's precision. */
    public static void addFiltered(KeyCounter counter, Object handler, AEKey key, long nativeAmount) {
        Read read = ACTIVE.get();
        KeyCounter source = read.filteredSources == null ? null : read.filteredSources.get(handler);
        Map<AEKey, BigInteger> overflow = read.counters.get(source);
        BigInteger exact = overflow == null ? null : overflow.get(key);
        if (exact == null) add(counter, key, nativeAmount);
        else addExact(counter, key, exact);
    }

    public static void merge(KeyCounter counter, KeyCounter other, boolean subtract) {
        Read read = ACTIVE.get();
        TerminalDenseCounter target = dense(counter);
        TerminalDenseCounter incoming = dense(other);
        if (!subtract && counter != other && target != null && incoming != null) {
            target.append(incoming);
            return;
        }
        materialize(other);
        Map<AEKey, BigInteger> source = read.counters.get(other);
        if (!subtract && counter != other) {
            append(counter, other, source == null ? Map.of() : source);
            return;
        }
        for (var entry : other) {
            AEKey key = entry.getKey();
            BigInteger exact = source == null ? null : source.get(key);
            if (exact != null) {
                addExact(counter, key, subtract ? exact.negate() : exact);
            } else if (subtract && entry.getLongValue() == Long.MIN_VALUE) {
                addExact(counter, key, MIN.negate());
            } else {
                add(counter, key, subtract ? -entry.getLongValue() : entry.getLongValue());
            }
        }
    }

    public static void forget(KeyCounter counter, AEKey key) {
        Map<AEKey, BigInteger> overflow = ACTIVE.get().counters.get(counter);
        if (!overflow.isEmpty()) overflow.remove(key);
    }

    public static void clear(KeyCounter counter) {
        ACTIVE.get().counters.put(counter, Map.of());
    }

    private static void store(Read read, KeyCounter counter, AEKey key, BigInteger amount) {
        Map<AEKey, BigInteger> overflow = read.counters.get(counter);
        if (overflow.isEmpty() && (amount.compareTo(MAX) > 0 || amount.compareTo(MIN) < 0)) {
            overflow = new HashMap<>();
            read.counters.put(counter, overflow);
        }
        if (amount.compareTo(MAX) > 0) {
            overflow.put(key, amount);
            write(read, counter, key, Long.MAX_VALUE);
        } else if (amount.compareTo(MIN) < 0) {
            overflow.put(key, amount);
            write(read, counter, key, Long.MIN_VALUE);
        } else {
            if (!overflow.isEmpty()) overflow.remove(key);
            write(read, counter, key, amount.longValue());
        }
    }

    private static void write(Read read, KeyCounter counter, AEKey key, long amount) {
        read.writing = true;
        try {
            counter.set(key, amount);
        } finally {
            read.writing = false;
        }
    }
}
