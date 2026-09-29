package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingPlanSummaryEntry;
import org.gtlcore.gtlcore.integration.ae2.graph.core.ExactAmounts;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** The confirmation table and graph browser describe the same immutable plan. */
public final class GraphPlanSummary {

    private GraphPlanSummary() {}

    public static List<CraftingPlanSummaryEntry> entries(AeGraphPlan plan) {
        var graph = plan.graph();
        Map<AEKey, BigInteger> crafted = new LinkedHashMap<>(plan.emittedExact());
        Map<AEKey, BigInteger> runs = new LinkedHashMap<>();
        graph.patternTimesExact().forEach((id, count) -> graph.recipes().get(id).outputs().forEach((key, amount) -> {
            crafted.merge(key, count.multiply(BigInteger.valueOf(amount)), BigInteger::add);
            runs.merge(key, count, BigInteger::add);
        }));
        var keys = new LinkedHashSet<>(graph.initialExact().keySet());
        keys.addAll(crafted.keySet());
        List<CraftingPlanSummaryEntry> entries = new ArrayList<>(keys.size());
        for (AEKey key : keys) {
            BigInteger missing = graph.missingExact().getOrDefault(key, BigInteger.ZERO);
            BigInteger stored = graph.initialExact().getOrDefault(key, BigInteger.ZERO)
                    .subtract(missing).subtract(plan.emittedExact().getOrDefault(key, BigInteger.ZERO));
            // Clamp only at the UI boundary, after all exact arithmetic. Adding
            // separately capped used/missing amounts can wrap even a tiny stock
            // into a negative extraction request for a Long.MAX_VALUE order.
            var entry = new CraftingPlanSummaryEntry(key, ExactAmounts.capped(missing), ExactAmounts.capped(stored),
                    ExactAmounts.capped(crafted.getOrDefault(key, BigInteger.ZERO)));
            ((ICraftingPlanSummaryEntry) entry).gtlcore$setCraftTimes(ExactAmounts.capped(runs.getOrDefault(key, BigInteger.ZERO)));
            entries.add(entry);
        }
        return entries;
    }
}
