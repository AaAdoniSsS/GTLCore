package org.gtlcore.gtlcore.api.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

/** Differential checks against the original probe/replay search, using mutable match contexts. */
public final class PatternLayoutSearchTest {

    public static void main(String[] args) {
        require(!PatternLayoutSearch.supports(new int[0][], 0), "empty pattern");
        require(!PatternLayoutSearch.supports(new int[][] { null }, 1), "null range");
        require(!PatternLayoutSearch.supports(new int[][] { { 2, 1 } }, 1), "reversed range");
        require(!PatternLayoutSearch.supports(new int[][] { { -1, 1 } }, 1), "negative range");
        require(!PatternLayoutSearch.supports(new int[][] { { 1, 1 } }, 2), "dimension mismatch");

        Random random = new Random(0x47544c5345415243L);
        int successes = 0;
        for (int trial = 0; trial < 10_000; trial++) {
            int[][] ranges = new int[1 + random.nextInt(7)][2];
            for (int[] range : ranges) {
                range[0] = random.nextInt(3);
                range[1] = range[0] + random.nextInt(3);
            }
            int seed = random.nextInt();
            int mode = random.nextInt(5);
            int min = random.nextInt(10);
            int max = random.nextBoolean() ? 100 : random.nextInt(15);
            int unload = random.nextInt(5) == 0 ? random.nextInt(16) - 3 : Integer.MIN_VALUE;
            State old = new State(ranges, seed, mode, min, max, unload);
            State current = new State(ranges, seed, mode, min, max, unload);
            boolean expected = searchStarts(old, false);
            boolean actual = searchStarts(current, true);
            require(expected == actual, "result in trial " + trial);
            require(old.unloaded == current.unloaded, "unloaded in trial " + trial);
            require(old.error.equals(current.error), "error in trial " + trial + ": " + old.error + "/" + current.error);
            if (actual) {
                successes++;
                require(Arrays.equals(old.repetitions, current.repetitions), "selected repetitions in trial " + trial);
                require(old.start == current.start, "selected start in trial " + trial);
                require(old.snapshot().equals(current.snapshot()), "full mutable context in trial " + trial);
            }
        }
        require(successes > 100, "random cases must include successful final context comparisons");

        // A failed maximum probe poisons the context before returning false; the shorter branch must be replayed.
        State failedProbe = new State(new int[][] { { 1, 3 }, { 1, 1 } }, 0, 0, 0, 100, Integer.MIN_VALUE);
        failedProbe.rule = (aisle, z) -> aisle != 0 || z < 2;
        require(run(failedProbe), "failed probe must restore context");
        require(Arrays.equals(failedProbe.repetitions, new int[] { 2, 1 }), "shorter successful probe layout");
        require(!failedProbe.dirty && failedProbe.replays == 1, "failed-probe state was cleaned");

        // The next aisle fails after greedy A=3. A=2 lets that aisle match at the required position.
        State backtrack = new State(new int[][] { { 1, 3 }, { 1, 1 } }, 0, 0, 0, 100, Integer.MIN_VALUE);
        backtrack.rule = (aisle, z) -> aisle == 0 || z == 2;
        require(run(backtrack), "backtracking must find the smaller count");
        require(Arrays.equals(backtrack.repetitions, new int[] { 2, 1 }) && !backtrack.dirty, "backtrack state/layout");

        State globalFailure = new State(new int[][] { { 0, 3 }, { 1, 1 } }, 0, 0, 0, 100, Integer.MIN_VALUE);
        globalFailure.exactTotal = 3;
        require(run(globalFailure), "global check failure must backtrack");
        require(Arrays.equals(globalFailure.repetitions, new int[] { 2, 1 }), "global check selected layout");

        State unload = new State(new int[][] { { 1, 3 }, { 1, 1 } }, 0, 0, 0, 100, 2);
        require(!run(unload) && unload.unloaded && unload.replays == 0, "unloaded maximum probe must abort, not shrink");

        // One variable aisle must no longer force every fixed prefix to be replayed on the successful path.
        int[][] mixed = new int[256][2];
        for (int[] range : mixed) Arrays.fill(range, 1);
        mixed[127][1] = 2;
        State oldMixed = new State(mixed, 0, 0, 0, 1000, Integer.MIN_VALUE);
        State newMixed = new State(mixed, 0, 0, 0, 1000, Integer.MIN_VALUE);
        require(reference(oldMixed, 0, 0, 0) && run(newMixed), "mixed large success");
        require(oldMixed.snapshot().equals(newMixed.snapshot()), "mixed large context");
        require(newMixed.slices == 257 && newMixed.replays == 0 && oldMixed.slices > 30_000,
                "mixed success must be linear");

        // The iterative search also handles deep layouts without consuming one Java stack frame per aisle.
        int[][] deep = new int[20_000][2];
        for (int[] range : deep) Arrays.fill(range, 1);
        deep[10_000][1] = 2;
        State deepState = new State(deep, 0, 0, 0, 30_000, Integer.MIN_VALUE);
        require(run(deepState) && deepState.slices == 20_001, "deep layout without recursive stack growth");

        // Keep exhaustive failure semantics: optimization must not silently truncate ambiguous combinations.
        int[][] ambiguous = new int[11][2];
        for (int[] range : ambiguous) {
            range[0] = 1;
            range[1] = 2;
        }
        ambiguous[10][1] = 1;
        State exhaustive = new State(ambiguous, 0, 0, 0, 1000, Integer.MIN_VALUE);
        exhaustive.rule = (aisle, z) -> aisle != 10;
        require(!run(exhaustive) && exhaustive.finalFailures == 1024, "all ambiguous layouts must be considered");

        System.out.println("PASS: 10000 differential cases (" + successes + " successes); failed probes, backtracking, " +
                "global limits, unload, exhaustive failure and deep layout; mixed slice calls " +
                oldMixed.slices + " -> " + newMixed.slices);
    }

    private static boolean searchStarts(State state, boolean optimized) {
        for (int start = -2; start <= 2; start++) {
            Arrays.fill(state.repetitions, 0);
            boolean matched = optimized ? runAt(state, start) : reference(state, start, 0, start);
            if (matched) {
                state.start = start;
                state.error = "none";
                return true;
            }
            if (state.unloaded) return false;
        }
        return false;
    }

    private static boolean run(State state) {
        return runAt(state, 0);
    }

    private static boolean runAt(State state, int start) {
        return PatternLayoutSearch.match(state.ranges, start, state.repetitions,
                new PatternLayoutSearch.Checks(state::clean, state::slice, state::global,
                        () -> state.unloaded, () -> state.replays++, () -> {}));
    }

    /** Original control flow, including failed probes, descending choices and unconditional prefix replay. */
    private static boolean reference(State state, int start, int aisle, int z) {
        if (aisle == 0) state.clean();
        int valid = 0;
        while (valid < state.ranges[aisle][1] && state.slice(aisle, z + valid)) valid++;
        if (state.unloaded) return false;
        for (int count = valid; count >= state.ranges[aisle][0]; count--) {
            state.repetitions[aisle] = count;
            Arrays.fill(state.repetitions, aisle + 1, state.repetitions.length, 0);
            state.replays++;
            state.clean();
            int replayZ = start;
            boolean replayed = true;
            outer:
            for (int previous = 0; previous <= aisle; previous++) {
                for (int r = 0; r < state.repetitions[previous]; r++, replayZ++) {
                    if (!state.slice(previous, replayZ)) {
                        replayed = false;
                        break outer;
                    }
                }
            }
            if (!replayed) {
                if (state.unloaded) return false;
                continue;
            }
            if (aisle + 1 < state.ranges.length) {
                if (reference(state, start, aisle + 1, z + count)) return true;
                if (state.unloaded) return false;
            } else if (state.global()) return true;
        }
        state.repetitions[aisle] = 0;
        return false;
    }

    private static final class State {

        final int[][] ranges;
        final int[] repetitions;
        final int seed, mode, minimum, maximum, unloadZ;
        final List<String> path = new ArrayList<>();
        final Map<Integer, Integer> counts = new HashMap<>();
        long checksum, slices, replays, finalFailures;
        int start;
        int exactTotal = -1;
        boolean unloaded, dirty;
        String error = "none";
        BiPredicate<Integer, Integer> rule = (aisle, z) -> true;

        State(int[][] ranges, int seed, int mode, int minimum, int maximum, int unloadZ) {
            this.ranges = ranges;
            repetitions = new int[ranges.length];
            this.seed = seed;
            this.mode = mode;
            this.minimum = minimum;
            this.maximum = maximum;
            this.unloadZ = unloadZ;
        }

        void clean() {
            path.clear();
            counts.clear();
            checksum = 0;
            unloaded = dirty = false;
            error = "none";
        }

        boolean slice(int aisle, int z) {
            slices++;
            error = "none";
            if (z == unloadZ) {
                unloaded = true;
                error = "unloaded";
                return false;
            }
            if (dirty) {
                error = "dirty context";
                return false;
            }
            long previous = checksum;
            checksum = checksum * 31 + aisle * 17L + z;
            counts.merge(aisle % 3, 1, Integer::sum);
            path.add(aisle + ":" + z + ":" + previous);
            int cell = seed ^ (aisle * 139) ^ (z * 47);
            boolean accepted = rule.test(aisle, z) && path.size() <= maximum && switch (mode) {
                case 1 -> Math.floorMod(cell, 7) != 0;
                case 2 -> Math.floorMod(cell + previous, 11) != 0;
                case 3 -> Math.floorMod(cell + counts.get(aisle % 3), 13) != 0;
                case 4 -> z < Math.floorMod(seed, 12);
                default -> true;
            };
            if (!accepted) {
                dirty = true;
                error = "predicate";
                if (aisle == ranges.length - 1) finalFailures++;
            }
            return accepted;
        }

        boolean global() {
            boolean accepted = path.size() >= minimum && (exactTotal < 0 || path.size() == exactTotal);
            if (!accepted) {
                error = "global";
                dirty = true;
            }
            return accepted;
        }

        String snapshot() {
            return path + "/" + counts + "/" + checksum + "/" + dirty;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
