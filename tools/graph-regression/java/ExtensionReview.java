package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

public final class ExtensionReview {
    static GraphRecipe<String> recipe(String id, Map<String, Long> in, Map<String, Long> out) {
        return new GraphRecipe<>(id, id, in.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), out);
    }

    static PlanningBudget budget() {
        return new PlanningBudget(0, 10_000_000, 128L << 20, () -> false, System::nanoTime);
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void seeds() {
        var production = recipe("make", Map.of("seed", 1L, "raw", 1L), Map.of("seed", 1L, "goal", 1L));
        var bootstrap = recipe("bootstrap", Map.of("extra", 5L), Map.of("seed", 1L));
        var recipes = List.of(production, bootstrap);
        for (boolean available : List.of(false, true)) {
            var stock = available ? Map.of("seed", 1L, "raw", 1L) : Map.<String, Long>of();
            var original = new GraphPlan<>("goal", 1, true, new PlanStep.Batch("make", 1), Map.of("make", production),
                    Map.of("seed", 1L, "raw", 1L), Map.of("seed", 1L), available ? Map.of() : Map.of("seed", 1L, "raw", 1L),
                    available ? GraphPlan.Result.FEASIBLE : GraphPlan.Result.MISSING_INPUT, 0, 0);
            var b = budget();
            try (var search = new SeedPortfolio<>(new GraphCompiler<>(recipes), original, stock, Map.of(), Set.of(), Set.of(), true, b)) {
                while (!search.step()) {}
                var plan = search.result();
                if (available) check(plan.feasible() && plan.missingExact().isEmpty(), "feasible order became missing");
                else {
                    check(plan.seeds().isEmpty(), "base-material bootstrap was not considered: " + b.diagnostics());
                    check(plan.missingExact().get("extra").equals(BigInteger.valueOf(5)), "purchase cost lost");
                    check(plan.alternatives().stream().anyMatch(p -> p.seeds().containsKey("seed")), "incomparable seed option lost");
                    check(plan.seedOptimality().baseMaterialTradeoff(), "wrong proof scope");
                }
                var s = GraphFixtureRegression.summary(plan.steps(), plan.recipes(), new IdentityHashMap<>());
                for (var e : s.need().entrySet()) check(plan.initialExact().getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(e.getValue()) >= 0, "unfunded tradeoff");
            }
            check(b.reservedBytes() == 0, "seed portfolio memory");
        }
        System.out.println("PASS base-material/seed tradeoff, feasibility and frontier");
    }

    static void recovery() {
        for (int depth : List.of(2, 4, 12)) {
            List<GraphRecipe<String>> recipes = new ArrayList<>();
            recipes.add(recipe("start", Map.of("seed", 1L, "raw", 1L), Map.of("p0", 2L, "side", 1L)));
            for (int i = 0; i < depth; i++) recipes.add(recipe("middle" + i, Map.of("p" + i, 1L, "fuel" + i, 1L), Map.of("p" + (i + 1), 1L)));
            recipes.add(recipe("finish", Map.of("p" + depth, 2L), Map.of("seed", 1L, "goal", 1L)));
            recipes.add(recipe("use_side", Map.of("side", 1L), Map.of("other", 1L)));
            Map<String, Long> stock = new HashMap<>(Map.of("seed", 1L, "raw", 1000L));
            for (int i = 0; i < depth; i++) stock.put("fuel" + i, 2000L);
            var b = budget();
            try (var search = new IntegerCountSearch<>(new GraphCompiler<>(recipes), "goal", 1000, stock, Map.of(), Set.of(), Set.of(), false, true, b, System.nanoTime())) {
                while (!search.step(null)) {}
                var plan = search.result();
                check(plan != null && plan.feasible(), "multistage recovery " + depth + ": " + b.diagnostics());
                var s = GraphFixtureRegression.summary(plan.steps(), plan.recipes(), new IdentityHashMap<>());
                for (var e : s.need().entrySet()) check(BigInteger.valueOf(stock.getOrDefault(e.getKey(), 0L)).compareTo(e.getValue()) >= 0, "unfunded multistage recovery");
                check(s.delta().get("goal").compareTo(BigInteger.valueOf(1000)) >= 0, "missing goal");
            }
            check(b.reservedBytes() == 0, "recovery memory");
        }
        System.out.println("PASS multistage recovery, ratios, joint products");
        var shared = List.of(recipe("start", Map.of("seed", 1L, "raw", 1L), Map.of("pending", 1L, "side", 1L)),
                recipe("return", Map.of("pending", 1L, "fuel", 2L), Map.of("seed", 1L, "part", 1L)),
                recipe("finish", Map.of("pending", 1L, "part", 1L), Map.of("goal", 1L)));
        for (Map<String, Long> stock : List.of(Map.of("pending", 1L, "raw", 1L, "fuel", 2L), Map.of("seed", 1L, "raw", 2L, "fuel", 2L))) {
            var b = budget();
            var plan = new GraphPlanner<>(new GraphCompiler<>(shared)).plan("goal", 1, stock, false, true, b);
            check(plan.feasible(), "open recovery phase lost: " + b.diagnostics());
            var summary = GraphFixtureRegression.summary(plan.steps(), plan.recipes(), new IdentityHashMap<>());
            for (var e : summary.need().entrySet()) check(BigInteger.valueOf(stock.getOrDefault(e.getKey(), 0L)).compareTo(e.getValue()) >= 0, "shared intermediate unfunded");
        }
        System.out.println("PASS stocked intermediate entry and interleaved phase consumption");
    }

    static void proofs() throws Exception {
        Random random = new Random(638506);
        int records = 0;
        for (int trial = 0; trial < 300; trial++) {
            int n = 3 + random.nextInt(6);
            List<ExactLinearProgram.Constraint> rows = new ArrayList<>();
            for (int r = 0; r < n * 4; r++) {
                Map<Integer, BigInteger> terms = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    int value = random.nextInt(7) - 3;
                    if (value != 0) terms.put(i, BigInteger.valueOf(value));
                }
                rows.add(new ExactLinearProgram.Constraint(terms, BigInteger.valueOf(random.nextInt(9) - 3)));
            }
            var b = budget();
            var journal = new CountProof.Journal(8L << 20);
            b.proofJournal(journal);
            BigInteger[] lower = new BigInteger[n], upper = new BigInteger[n];
            Arrays.fill(lower, BigInteger.ZERO);
            Arrays.fill(upper, BigInteger.ONE);
            try (var search = new CountBoolean(rows, lower, upper, b)) {
                while (!search.step()) {}
                for (int bits = 0; bits < 1 << n; bits++) {
                    BigInteger[] values = new BigInteger[n];
                    for (int i = 0; i < n; i++) values[i] = BigInteger.valueOf(bits >> i & 1);
                    boolean valid = true;
                    for (var row : rows) {
                        BigInteger sum = BigInteger.ZERO;
                        for (var term : row.terms().entrySet()) sum = sum.add(values[term.getKey()].multiply(term.getValue()));
                        if (sum.compareTo(row.upper()) > 0) { valid = false; break; }
                    }
                    if (valid) for (var conflict : search.learnedConflicts()) check(!conflict.impliedBy(values, values, b), "learned conflict excluded a valid assignment");
                }
            }
            check(!journal.truncated(), "proof journal truncated");
            for (var proof : journal.entries()) {
                check(CountProof.verify(proof, 2_000_000) == CountProof.Verdict.VERIFIED, "independent Boolean proof failed: " + trial + " " + proof);
                records++;
            }
            if (trial == 0) {
                Path path = Files.createTempFile("graph-proof-", ".cgp");
                try {
                    journal.write(path);
                    var restored = CountProof.read(path);
                    check(restored.entries().equals(journal.entries()), "proof serialization changed scope");
                } finally { Files.delete(path); }
            }
            check(b.reservedBytes() == 0, "proof recording memory");
        }
        var invalid = new CountProof.Certificate("tampered", 1, List.of(new CountProof.Row(Map.of(0, BigInteger.ONE), BigInteger.TEN)),
                List.of(List.of(new CountProof.Row(Map.of(0, BigInteger.ONE), BigInteger.ZERO))), List.of(), false);
        check(CountProof.verify(invalid, 1000) == CountProof.Verdict.INVALID, "forged pruning accepted");
        var b = budget();
        var journal = new CountProof.Journal(1L << 20);
        b.proofJournal(journal);
        try (var lp = new ExactLinearProgram(2, List.of(
                new ExactLinearProgram.Constraint(Map.of(0, BigInteger.ONE, 1, BigInteger.ONE), BigInteger.ONE),
                new ExactLinearProgram.Constraint(Map.of(0, BigInteger.ONE.negate(), 1, BigInteger.ONE.negate()), BigInteger.TWO.negate())),
                new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}, b)) {
            while (!lp.step()) {}
            check(lp.result() == ExactLinearProgram.Result.INFEASIBLE, "LP fixture");
        }
        for (var proof : journal.entries()) check(CountProof.verify(proof, 10000) == CountProof.Verdict.VERIFIED, "Farkas proof failed");
        check(!journal.entries().isEmpty(), "LP certificate not exported");
        var incomplete = new CountProof.Journal(1L << 20);
        incomplete.markIncomplete();
        var tiny = new CountProof.Journal(1);
        tiny.add(journal.entries().get(0));
        for (var partial : List.of(incomplete, tiny)) {
            Path path = Files.createTempFile("graph-incomplete-proof-", ".cgp");
            try {
                partial.write(path);
                check(CountProof.read(path).truncated(), "archive lost incomplete flag");
                boolean rejected = false;
                try { CountProof.main(new String[]{path.toString()}); }
                catch (IllegalStateException expected) { rejected = true; }
                check(rejected, "partial archive accepted as complete");
            } finally { Files.delete(path); }
        }
        System.out.println("PASS independent proof records=" + records + ", Farkas, tampering, serialization, incomplete archives");
    }

    static void coordinates() {
        var b = budget();
        BigInteger shift = BigInteger.ONE.shiftLeft(80);
        var mapping = CountMapping.sums(List.of(List.of(0, 2), List.of(1, 3)), new BigInteger[]{shift, BigInteger.ZERO, BigInteger.TWO, BigInteger.ZERO});
        var original = new CountConflict(List.of(
                new ExactLinearProgram.Constraint(Map.of(0, BigInteger.ONE.negate()), BigInteger.valueOf(-2)),
                new ExactLinearProgram.Constraint(Map.of(1, BigInteger.ONE), BigInteger.ONE)));
        var lifted = mapping.conflict(original, b);
        for (int code = 0; code < 256; code++) {
            BigInteger[] point = {shift.add(BigInteger.valueOf(code & 3)), BigInteger.valueOf(code >> 2 & 3), BigInteger.valueOf(2 + (code >> 4 & 3)), BigInteger.valueOf(code >> 6 & 3)};
            BigInteger[] grouped = {point[0].add(point[2]).subtract(shift).subtract(BigInteger.TWO), point[1].add(point[3])};
            check(original.impliedBy(grouped, grouped, b) == lifted.impliedBy(point, point, b), "affine conflict changed meaning");
        }
        System.out.println("PASS exact conflict lifting including offsets above long");
    }

    static void executionProofs() throws Exception {
        var b = budget();
        var journal = new CountProof.Journal(8L << 20);
        b.proofJournal(journal);
        var cycle = List.of(recipe("ab", Map.of("a", 1L), Map.of("b", 1L)), recipe("ba", Map.of("b", 1L), Map.of("a", 1L)));
        try (var search = new BackwardCoverability<>(cycle, Map.of("a", BigInteger.ONE), Map.of("a", BigInteger.TWO), Set.of(), List.of(), b, 200000)) {
            while (!search.step()) {}
            check(search.result() == BackwardCoverability.Result.CLOSED, "conserved cycle proof");
        }
        var growth = List.of(recipe("growth", Map.of("a", 1L, "raw", 1L), Map.of("a", 2L)));
        try (var search = new BackwardCoverability<>(growth, Map.of("raw", BigInteger.TEN), Map.of("a", BigInteger.ONE), Set.of(), List.of(), b, 200000)) {
            while (!search.step()) {}
            check(search.result() == BackwardCoverability.Result.CLOSED, "startup box proof");
        }
        try (var model = RecipeCountModel.create(new GraphCompiler<>(cycle), "a", 2, Map.of("a", 1L), Map.of(), Set.of(), Set.of(), false, b);
             var search = new CountSupportSearch<>(model, new BigInteger[]{BigInteger.ONE, BigInteger.ONE}, b)) {
            while (!search.step()) {}
            check(search.result() == CountSupportSearch.Result.CLOSED, "forward boundary proof");
        }
        check(journal.executions().size() == 3, "missing execution certificates");
        for (var proof : journal.executions()) check(ExecutionProof.verify(proof, 100000) == CountProof.Verdict.VERIFIED, "invalid execution certificate " + proof);
        Path path = Files.createTempFile("execution-proof-", ".cgp");
        try {
            journal.write(path);
            check(CountProof.read(path).executions().equals(journal.executions()), "execution proof roundtrip");
        } finally { Files.delete(path); }
        var genuine = journal.executions().get(0);
        var invalid = new ExecutionProof.Certificate(genuine.scope(), genuine.kind(), genuine.goal(), genuine.goal(), genuine.inputs(), genuine.outputs(), genuine.states(), genuine.marked());
        check(ExecutionProof.verify(invalid, 100000) == CountProof.Verdict.INVALID, "forged initial marking accepted");
        check(b.reservedBytes() == 0, "execution proof memory");
        System.out.println("PASS startup box, backward closure, forward boundary, independent archive check");
    }

    public static void main(String[] args) throws Exception {
        seeds();
        recovery();
        coordinates();
        proofs();
        executionProofs();
    }
}
