package org.gtlcore.gtlcore.api.pattern;

/** Traverses a determined layout without probing or replaying its prefixes. */
public final class FixedPatternLayout {

    private FixedPatternLayout() {}

    /** Returns null when layout selection still requires the existing repetition search. */
    public static int[] repetitions(int[][] ranges) {
        if (ranges.length == 0) return null;
        int[] result = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            if (ranges[i] == null || ranges[i].length != 2 || ranges[i][0] < 0 || ranges[i][0] != ranges[i][1]) return null;
            result[i] = ranges[i][0];
        }
        return result;
    }

    /** The caller cleans state before each start position and checks global counts after success. */
    public static boolean match(int[] repetitions, int startZ, SliceMatcher matcher) {
        int z = startZ;
        for (int aisle = 0; aisle < repetitions.length; aisle++) {
            for (int repeat = 0; repeat < repetitions[aisle]; repeat++, z++) {
                if (!matcher.match(aisle, z)) return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    public interface SliceMatcher {

        boolean match(int aisle, int z);
    }
}
