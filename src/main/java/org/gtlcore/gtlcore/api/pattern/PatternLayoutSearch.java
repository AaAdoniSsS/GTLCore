package org.gtlcore.gtlcore.api.pattern;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * Greedy depth-first repetition search with state replay only when a branch needs restoring.
 * Predicate/context objects are never copied: replay uses the original ordered slice checks.
 */
public final class PatternLayoutSearch {

    private PatternLayoutSearch() {}

    public static boolean supports(int[][] ranges, int aisleCount) {
        if (aisleCount == 0 || ranges.length != aisleCount) return false;
        for (int[] range : ranges) {
            if (range == null || range.length != 2 || range[0] < 0 || range[1] < range[0]) return false;
        }
        return true;
    }

    /**
     * Checks a single start/orientation, preserving the original descending repetition choices.
     * Requires repeatable predicates and stable inputs during a check, just as prefix replay does.
     */
    public static boolean match(int[][] ranges, int startZ, int[] repetitions, Checks checks) {
        int[] starts = new int[ranges.length];
        starts[0] = startZ;
        Arrays.fill(repetitions, 0);
        checks.clean.run();
        int aisle = 0;
        boolean entering = true;

        while (aisle >= 0) {
            int minimum = ranges[aisle][0];
            if (entering) {
                int maximum = ranges[aisle][1];
                int valid = 0;
                while (valid < maximum && checks.slice.match(aisle, starts[aisle] + valid)) valid++;
                if (checks.unloaded.getAsBoolean()) return false;
                repetitions[aisle] = valid;
                if (valid < minimum) {
                    repetitions[aisle] = 0;
                    aisle--;
                    entering = false;
                    continue;
                }
                // A failed probe can have changed arbitrary context/counts, even if it returned false.
                // A probe that reached the maximum leaves exactly the desired successful prefix.
                if (valid < maximum && !replay(startZ, aisle, repetitions, checks)) {
                    if (checks.unloaded.getAsBoolean()) return false;
                    entering = false;
                    continue;
                }
            } else {
                // The deeper branch failed. Try the next smaller count in the same order as before.
                if (--repetitions[aisle] < minimum) {
                    repetitions[aisle] = 0;
                    aisle--;
                    continue;
                }
                Arrays.fill(repetitions, aisle + 1, repetitions.length, 0);
                if (!replay(startZ, aisle, repetitions, checks)) {
                    if (checks.unloaded.getAsBoolean()) return false;
                    continue;
                }
            }

            if (aisle + 1 == ranges.length) {
                if (checks.globalCounts.getAsBoolean()) return true;
                entering = false;
            } else {
                starts[aisle + 1] = starts[aisle] + repetitions[aisle];
                aisle++;
                entering = true;
            }
        }
        return false;
    }

    private static boolean replay(int startZ, int lastAisle, int[] repetitions, Checks checks) {
        checks.replayStarted.run();
        checks.clean.run();
        int z = startZ;
        for (int aisle = 0; aisle <= lastAisle; aisle++) {
            for (int repeat = 0; repeat < repetitions[aisle]; repeat++, z++) {
                checks.replayedSlice.run();
                if (!checks.slice.match(aisle, z)) return false;
            }
        }
        return true;
    }

    public record Checks(Runnable clean, FixedPatternLayout.SliceMatcher slice,
                         BooleanSupplier globalCounts, BooleanSupplier unloaded,
                         Runnable replayStarted, Runnable replayedSlice) {}
}
