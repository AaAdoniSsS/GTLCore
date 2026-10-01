package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphPlan;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import java.util.Locale;

/** A completed planning outcome, distinct from a crashed planner. */
public final class GraphPlanningFailure extends RuntimeException {

    private final GraphPlan.Result result;

    public GraphPlanningFailure(GraphPlan.Result result, String detail) {
        super("Graph crafting: " + result + (detail.isEmpty() ? "" : " (" + detail + ")"));
        this.result = result;
    }

    public static String messageKey(Throwable error) {
        while (error != null) {
            if (error instanceof GraphPlanningFailure failure)
                return "gtlcore.ae.graph.failure." + failure.result.name().toLowerCase(Locale.ROOT);
            if (error instanceof PlanningBudget.Exhausted exhausted)
                return "gtlcore.ae.graph.failure." + exhausted.limit().name().toLowerCase(Locale.ROOT);
            if (error.getCause() == error) break;
            error = error.getCause();
        }
        return null;
    }
}
