package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.List;

/** A proven forbidden conjunction, shared only within one immutable order model. */
record CountConflict(List<ExactLinearProgram.Constraint> assumptions) {

    CountConflict {
        assumptions = List.copyOf(assumptions);
    }

    boolean impliedBy(BigInteger[] lower, BigInteger[] upper, PlanningBudget budget) {
        for (var row : assumptions) {
            BigInteger maximum = BigInteger.ZERO;
            for (var term : row.terms().entrySet()) {
                budget.check();
                BigInteger bound = term.getValue().signum() > 0 ? upper[term.getKey()] : lower[term.getKey()];
                if (bound == null) return false;
                maximum = maximum.add(term.getValue().multiply(bound));
            }
            if (maximum.compareTo(row.upper()) > 0) return false;
        }
        return true;
    }
}
