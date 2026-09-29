package org.gtlcore.gtlcore.integration.ae2.graph;

import appeng.crafting.CraftingPlan;

import java.util.function.Supplier;

/** Identifies the compatibility view of a graph plan while addons build its summary. */
public final class GraphSummaryContext {

    private static final ThreadLocal<CraftingPlan> CURRENT = new ThreadLocal<>();

    private GraphSummaryContext() {}

    public static boolean isGraphPlan(CraftingPlan plan) {
        return plan != null && CURRENT.get() == plan;
    }

    public static <T> T withGraphPlan(CraftingPlan plan, Supplier<T> summary) {
        CraftingPlan previous = CURRENT.get();
        CURRENT.set(plan);
        try {
            return summary.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
