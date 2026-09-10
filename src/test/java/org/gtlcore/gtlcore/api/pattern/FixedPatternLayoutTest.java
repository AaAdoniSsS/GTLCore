package org.gtlcore.gtlcore.api.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Standalone regression checks, following the repository's existing main-based test convention. */
public final class FixedPatternLayoutTest {

    public static void main(String[] args) {
        require(FixedPatternLayout.repetitions(new int[][] { { 1, 2 } }) == null, "variable layout must fall back");
        require(FixedPatternLayout.repetitions(new int[0][]) == null, "empty pattern must fall back");
        require(FixedPatternLayout.repetitions(new int[][] { { -1, -1 } }) == null, "invalid range must fall back");
        require(Arrays.equals(new int[] { 0, 2, 1 },
                FixedPatternLayout.repetitions(new int[][] { { 0, 0 }, { 2, 2 }, { 1, 1 } })), "fixed counts");

        List<String> visits = new ArrayList<>();
        require(FixedPatternLayout.match(new int[] { 0, 2, 1 }, -3, (aisle, z) -> {
            visits.add(aisle + ":" + z);
            return true;
        }), "layout must match");
        require(visits.equals(List.of("1:-3", "1:-2", "2:-1")), "logical traversal order and zero repetitions");

        Random random = new Random(0x47544c);
        for (int trial = 0; trial < 2000; trial++) {
            int[] repetitions = new int[1 + random.nextInt(12)];
            for (int i = 0; i < repetitions.length; i++) repetitions[i] = random.nextInt(4);
            int total = Arrays.stream(repetitions).sum();
            int target = random.nextInt(5) - 2;
            // Include cell rejection, global minimum/maximum failure, unloaded chunks and both orientations.
            int badCell = random.nextBoolean() ? -1 : random.nextInt(total + 1);
            int unloadedCell = random.nextInt(5) == 0 ? random.nextInt(total + 1) : -1;
            int globalMin = random.nextInt(total + 2);
            int globalMax = random.nextBoolean() ? total + 1 : random.nextInt(total + 1);
            int direction = random.nextBoolean() ? 1 : -1;
            State linear = new State(repetitions, target, badCell, unloadedCell, globalMin, globalMax, direction);
            State reference = new State(repetitions, target, badCell, unloadedCell, globalMin, globalMax, direction);
            boolean expected = search(reference, false);
            boolean actual = search(linear, true);
            require(expected == actual, "result differs in trial " + trial);
            require(linear.unloaded == reference.unloaded, "unload handling differs");
            require(linear.error.equals(reference.error), "failure category differs");
            if (actual) {
                require(linear.visits.equals(reference.visits), "final match context differs");
                require(linear.successfulStart == reference.successfulStart, "chosen start differs");
            }
        }

        int[] large = new int[256];
        Arrays.fill(large, 1);
        State reference = new State(large, -2, -1, -1, 256, 256, 1);
        State linear = new State(large, -2, -1, -1, 256, 256, 1);
        require(search(reference, false) && search(linear, true), "large fixed layout");
        require(linear.sliceCalls == 256, "linear path must visit each slice once");
        require(reference.sliceCalls == 256L + 256L * 257 / 2, "reference prefix replay workload");
        System.out.println("PASS: 2000 differential cases; large layout slice calls " + reference.sliceCalls + " -> " + linear.sliceCalls);
    }

    private static boolean search(State state, boolean linear) {
        for (int start = -2; start <= 2; start++) {
            boolean matched;
            if (linear) {
                state.clean();
                matched = FixedPatternLayout.match(state.repetitions, start, state::slice) && state.globalCounts();
            } else {
                matched = referenceSearch(state, start, 0, start);
            }
            if (matched) {
                state.successfulStart = start;
                state.error = "none";
                return true;
            }
            if (state.unloaded) return false;
        }
        return false;
    }

    /** The pre-optimization probe/replay recursion, restricted to fixed ranges. */
    private static boolean referenceSearch(State state, int start, int aisle, int z) {
        if (aisle == 0) state.clean();
        int valid = 0;
        while (valid < state.repetitions[aisle] && state.slice(aisle, z + valid)) valid++;
        if (state.unloaded || valid < state.repetitions[aisle]) return false;
        state.clean();
        int replayZ = start;
        for (int previous = 0; previous <= aisle; previous++) {
            for (int r = 0; r < state.repetitions[previous]; r++, replayZ++) {
                if (!state.slice(previous, replayZ)) return false;
            }
        }
        if (aisle + 1 < state.repetitions.length) return referenceSearch(state, start, aisle + 1, z + valid);
        return state.globalCounts();
    }

    private static final class State {

        final int[] repetitions;
        final int target, badCell, unloadedCell, globalMin, globalMax, direction;
        final List<String> visits = new ArrayList<>();
        long sliceCalls;
        int successfulStart;
        boolean unloaded;
        String error = "none";

        State(int[] repetitions, int target, int badCell, int unloadedCell, int globalMin, int globalMax, int direction) {
            this.repetitions = repetitions;
            this.target = target;
            this.badCell = badCell;
            this.unloadedCell = unloadedCell;
            this.globalMin = globalMin;
            this.globalMax = globalMax;
            this.direction = direction;
        }

        void clean() {
            visits.clear();
            unloaded = false;
            error = "none";
        }

        boolean slice(int aisle, int z) {
            sliceCalls++;
            int index = z - target;
            if (index == unloadedCell && unloadedCell >= 0) {
                unloaded = true;
                error = "unloaded";
                return false;
            }
            int begin = 0;
            for (int i = 0; i < aisle; i++) begin += repetitions[i];
            if (index < begin || index >= begin + repetitions[aisle] ||
                    index == badCell || visits.size() >= globalMax) {
                error = "predicate";
                return false;
            }
            visits.add(aisle + ":" + (direction * z));
            return true;
        }

        boolean globalCounts() {
            if (visits.size() < globalMin) {
                error = "global minimum";
                return false;
            }
            return true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
