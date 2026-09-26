package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.math.BigInteger;
import java.util.*;

public final class BoxOracle {
    public static void main(String[] args) throws Exception {
        Random rng = new Random(2609262004L);
        int found = 0, closed = 0, boxes = 0;
        for (int trial = 0; trial < 800; trial++) {
            int n = 2 + rng.nextInt(5), dims = 1 + rng.nextInt(3), states = 1;
            BigInteger[] lower = new BigInteger[n], upper = new BigInteger[n], anchor = new BigInteger[n];
            int[] sizes = new int[n];
            for (int i = 0; i < n; i++) {
                lower[i] = BigInteger.valueOf(rng.nextInt(3));
                if (trial % 5 == 0) lower[i] = lower[i].add(BigInteger.valueOf(Long.MAX_VALUE));
                sizes[i] = 2 + rng.nextInt(3); states *= sizes[i];
                upper[i] = lower[i].add(BigInteger.valueOf(sizes[i] - 1));
                anchor[i] = lower[i].add(BigInteger.valueOf(rng.nextInt(sizes[i])));
            }
            var rows = new ArrayList<ExactLinearProgram.Constraint>();
            for (int d = 0; d < dims; d++) {
                var terms = new LinkedHashMap<Integer, BigInteger>();
                BigInteger sum = BigInteger.ZERO;
                for (int i = 0; i < n; i++) {
                    var k = BigInteger.valueOf(rng.nextInt(11) - 5);
                    if (k.signum() != 0) terms.put(i, k);
                    sum = sum.add(k.multiply(anchor[i]));
                }
                if (trial % 2 == 0) sum = sum.add(BigInteger.valueOf(1 + rng.nextInt(25)));
                int slack = 1 + rng.nextInt(2);
                rows.add(new ExactLinearProgram.Constraint(terms, sum.add(BigInteger.valueOf(slack))));
                var opposite = new LinkedHashMap<Integer, BigInteger>();
                terms.forEach((k, v) -> opposite.put(k, v.negate()));
                rows.add(new ExactLinearProgram.Constraint(opposite, sum.negate()));
            }
            boolean possible = false;
            for (int code = 0; code < states; code++) {
                int value = code;
                BigInteger[] counts = lower.clone();
                for (int i = 0; i < n; i++) { counts[i] = counts[i].add(BigInteger.valueOf(value % sizes[i])); value /= sizes[i]; }
                if (FiniteOracle.valid(rows, counts)) { possible = true; break; }
            }
            var budget = new PlanningBudget(0, 10_000_000, 128L << 20, () -> false, System::nanoTime);
            try (var match = new CountMeetInMiddle(rows, lower, upper, budget)) {
                while (!match.step()) {}
                var field = CountMeetInMiddle.class.getDeclaredField("pointWidths"); field.setAccessible(true);
                if (field.get(match) != null) boxes++;
                if ((match.counts() != null) != possible || match.infeasible() == possible)
                    throw new AssertionError("box result mismatch " + trial);
                if (possible && !FiniteOracle.valid(rows, match.counts())) throw new AssertionError("invalid witness");
                if (possible) found++; else closed++;
            }
            if (budget.reservedBytes() != 0) throw new AssertionError("leaked box workspace");
        }
        System.out.println("BOX_ORACLE cases=800 feasible=" + found + " closed=" + closed + " discrete_boxes=" + boxes + " exact_oracle_passed=true");
    }
}
