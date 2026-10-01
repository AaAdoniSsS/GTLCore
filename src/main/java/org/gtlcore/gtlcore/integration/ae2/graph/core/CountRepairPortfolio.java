package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Keep both relaxation faces; neither a valid cut nor a heuristic replaces the other view. */
final class CountRepairPortfolio implements AutoCloseable {

    private static final class View {

        final ExactRational[] point;
        CountLatticeRepair repair;
        int attempt;
        long slice;

        View(ExactRational[] point) {
            this.point = point;
        }
    }

    private final List<ExactLinearProgram.Constraint> rows;
    private final BigInteger[] lower, upper;
    private final PlanningBudget budget;
    private final List<View> views = new ArrayList<>();
    private int cursor;
    private BigInteger[] counts;
    private long memory;
    private boolean complete;

    CountRepairPortfolio(List<ExactLinearProgram.Constraint> rows, BigInteger[] lower, BigInteger[] upper,
                         ExactRational[] primary, ExactRational[] alternative, PlanningBudget budget) {
        this.rows = rows;
        this.lower = lower;
        this.upper = upper;
        this.budget = budget;
        views.add(new View(primary));
        if (alternative != null && !Arrays.equals(primary, alternative)) {
            long bytes = 256 + 64L * alternative.length;
            if (budget.tryReserve(bytes)) {
                memory = bytes;
                views.add(new View(alternative));
            }
        }
    }

    boolean step() {
        if (complete) return true;
        View view = views.get(cursor);
        if (view.attempt == 4) {
            budget.check();
            if (views.stream().allMatch(v -> v.attempt == 4)) {
                complete = true;
                return true;
            }
            cursor = (cursor + 1) % views.size();
            return false;
        }
        long before = views.size() > 1 ? budget.threadWork() : 0;
        try {
            if (view.repair == null) view.repair = new CountLatticeRepair(rows, lower, upper, view.point, view.attempt, budget);
            if (view.repair.step()) {
                counts = view.repair.counts();
                view.repair.close();
                view.repair = null;
                view.attempt++;
                if (counts != null) {
                    complete = true;
                    budget.note("count_repair_view", "witness_view=" + cursor + "; views=" + views.size() + "; attempt=" + view.attempt);
                    return true;
                }
            }
            return false;
        } finally {
            if (views.size() > 1) {
                view.slice += budget.threadWork() - before;
                if (view.slice >= 4096 || view.attempt == 4) {
                    view.slice = 0;
                    cursor = (cursor + 1) % views.size();
                }
            }
        }
    }

    BigInteger[] counts() {
        return counts;
    }

    boolean competingViews() {
        return views.size() > 1 && views.stream().anyMatch(v -> v.attempt > 0 && v.attempt < 4);
    }

    @Override
    public void close() {
        for (View view : views) {
            if (view.repair != null) view.repair.close();
            view.repair = null;
        }
        budget.release(memory);
        memory = 0;
    }
}
