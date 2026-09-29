package org.gtlcore.gtlcore.integration.ae2.graph;

import appeng.crafting.CraftingPlan;

import java.util.function.Supplier;

/** Identifies the compatibility view of a graph plan while addons build its summary. */
public final class GraphSummaryContext {

    private record Context(CraftingPlan view, AeGraphPlan graph) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private GraphSummaryContext() {}

    public static boolean isGraphPlan(CraftingPlan plan) {
        Context context = CURRENT.get();
        return plan != null && context != null && context.view() == plan;
    }

    public static AeGraphPlan graphPlan(Object plan) {
        Context context = CURRENT.get();
        return context != null && context.view() == plan ? context.graph() : null;
    }

    public static <T> T withGraphPlan(CraftingPlan plan, Supplier<T> summary) {
        return withGraphPlan(plan, null, summary);
    }

    public static <T> T withGraphPlan(CraftingPlan plan, AeGraphPlan graph, Supplier<T> summary) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(plan, graph));
        try {
            return summary.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
