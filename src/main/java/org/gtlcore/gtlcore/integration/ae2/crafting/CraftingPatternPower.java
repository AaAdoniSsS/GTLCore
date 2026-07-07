package org.gtlcore.gtlcore.integration.ae2.crafting;

public final class CraftingPatternPower {

    private CraftingPatternPower() {}

    public static double forCpu(double calculatedPower, boolean autoExpand, long expandedOperations) {
        if (!autoExpand || expandedOperations <= 0) {
            return calculatedPower;
        }
        return calculatedPower / expandedOperations;
    }
}
