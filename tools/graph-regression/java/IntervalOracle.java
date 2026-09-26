package org.gtlcore.gtlcore.integration.ae2.graph.core;
import java.math.BigInteger;
import java.util.*;
public class IntervalOracle {
    static BigInteger b(long n) { return BigInteger.valueOf(n); }
    public static void main(String[] args) {
        Random random = new Random(262009); int found = 0;
        for (int test = 0; test < 1500; test++) {
            int n = 3 + random.nextInt(5);
            BigInteger[] low = new BigInteger[n], high = new BigInteger[n], planted = new BigInteger[n];
            ExactRational[] point = new ExactRational[n];
            for (int i = 0; i < n; i++) {
                low[i] = test % 9 == 0 ? b(Long.MAX_VALUE).add(b(random.nextInt(3))) : b(random.nextInt(3));
                int width = 1 + random.nextInt(4); high[i] = low[i].add(b(width));
                planted[i] = low[i].add(b(random.nextInt(width + 1)));
                point[i] = new ExactRational(low[i].multiply(b(2)).add(b(width)), b(2));
            }
            List<ExactLinearProgram.Constraint> rows = new ArrayList<>();
            for (int r = 0; r < 4; r++) {
                Map<Integer, BigInteger> terms = new LinkedHashMap<>(); BigInteger rhs = BigInteger.ZERO;
                for (int i = 0; i < n; i++) { int v = random.nextInt(19) - 9; if (v != 0) terms.put(i, b(v)); rhs = rhs.add(b(v).multiply(planted[i])); }
                if (r > 1) rhs = rhs.add(b(random.nextInt(5) - (test % 3 == 0 ? 3 : 0)));
                rows.add(new ExactLinearProgram.Constraint(terms, r < 2 ? rhs.add(b(random.nextInt(6))) : rhs));
                if (r < 2) { Map<Integer, BigInteger> opposite = new LinkedHashMap<>(); terms.forEach((i,v) -> opposite.put(i,v.negate())); rows.add(new ExactLinearProgram.Constraint(opposite, rhs.negate().add(b(random.nextInt(6))))); }
            }
            for (int attempt = 0; attempt < 4; attempt++) {
                var budget = new PlanningBudget(0, 20_000_000, 256L << 20, () -> false, System::nanoTime);
                try (var repair = new CountLatticeRepair(rows, low, high, point, attempt, budget)) {
                    while (!repair.step()) {}
                    var counts = repair.counts();
                    if (counts != null) {
                        found++;
                        for (int i = 0; i < n; i++) if (counts[i].compareTo(low[i]) < 0 || counts[i].compareTo(high[i]) > 0) throw new AssertionError("Bounds " + test);
                        if (!FiniteOracle.valid(rows, counts)) throw new AssertionError("Rows " + test);
                    }
                }
            }
        }
        System.out.println("PASS lattice models=1500 attempts=6000 verified_witnesses=" + found);
    }
}
