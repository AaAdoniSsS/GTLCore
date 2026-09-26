package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Independent primitive-arc prefix checker; deliberately does not call SequenceSummary. */
public final class GraphFixtureRegression {
    record Summary(Map<String, BigInteger> need, Map<String, BigInteger> delta) {}

    static Summary summary(PlanStep step, Map<String, GraphRecipe<String>> recipes, Map<PlanStep, Summary> memo) {
        if (memo.containsKey(step)) return memo.get(step);
        Map<String, BigInteger> need = new LinkedHashMap<>(), delta = new LinkedHashMap<>();
        if (step instanceof PlanStep.Batch batch) {
            GraphRecipe<String> recipe = Objects.requireNonNull(recipes.get(batch.recipe()));
            BigInteger times = BigInteger.valueOf(batch.runs());
            Set<String> keys = new HashSet<>(recipe.inputs().keySet());
            keys.addAll(recipe.outputs().keySet());
            if (times.signum() > 0) for (String key : keys) {
                BigInteger input = BigInteger.valueOf(recipe.inputs().getOrDefault(key, 0L));
                BigInteger change = BigInteger.valueOf(recipe.outputs().getOrDefault(key, 0L)).subtract(input);
                need.put(key, input.add(change.negate().max(BigInteger.ZERO).multiply(times.subtract(BigInteger.ONE))));
                delta.put(key, change.multiply(times));
            }
        } else if (step instanceof PlanStep.Repeat repeat) {
            Summary body = summary(repeat.body(), recipes, memo);
            BigInteger times = BigInteger.valueOf(repeat.times());
            if (times.signum() > 0) for (String key : body.need.keySet()) {
                BigInteger change = body.delta.getOrDefault(key, BigInteger.ZERO);
                need.put(key, body.need.get(key).add(change.negate().max(BigInteger.ZERO).multiply(times.subtract(BigInteger.ONE))));
                delta.put(key, change.multiply(times));
            }
        } else for (PlanStep child : ((PlanStep.Sequence) step).children()) {
            Summary body = summary(child, recipes, memo);
            body.need.forEach((key, value) -> need.merge(key, value.subtract(delta.getOrDefault(key, BigInteger.ZERO)).max(BigInteger.ZERO), BigInteger::max));
            body.delta.forEach((key, value) -> delta.merge(key, value, BigInteger::add));
        }
        Summary result = new Summary(need, delta);
        memo.put(step, result);
        return result;
    }

    static String string(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 1_000_000) throw new IOException("Invalid fixture string");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static Map<String, Long> amounts(DataInputStream input) throws IOException {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int remaining = input.readInt(); remaining > 0; remaining--) result.put(string(input), input.readLong());
        return result;
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(args[0]))))) {
            int cases = input.readInt();
            for (int index = 0; index < cases; index++) {
                String name = string(input), target = string(input);
                long amount = input.readLong();
                String truth = string(input);
                Map<String, Long> stock = amounts(input);
                List<GraphRecipe<String>> recipes = new ArrayList<>();
                Map<String, GraphRecipe<String>> primitives = new LinkedHashMap<>();
                for (int remaining = input.readInt(); remaining > 0; remaining--) {
                    String id = string(input);
                    Map<String, Long> in = amounts(input), out = amounts(input);
                    var recipe = new GraphRecipe<>(id, id, in.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), out);
                    recipes.add(recipe);
                    if (primitives.put(id, recipe) != null) throw new AssertionError(name + ": duplicate primitive " + id);
                }
                PlanningBudget budget = new PlanningBudget(Long.parseLong(args[1]), Long.parseLong(args[2]), 256L << 20, () -> false, System::nanoTime);
                CountProof.Journal journal = args.length > 3 ? new CountProof.Journal(16L << 20) : null;
                if (journal != null) budget.proofJournal(journal);
                long start = System.nanoTime();
                GraphPlan<String> plan = new GraphPlanner<>(new GraphCompiler<>(recipes)).plan(target, amount, stock, false, true, budget);
                if (!Set.of(GraphPlan.Result.FEASIBLE, GraphPlan.Result.FEASIBLE_NOT_PROVEN_OPTIMAL, GraphPlan.Result.MISSING_INPUT,
                        GraphPlan.Result.MISSING_SEED, GraphPlan.Result.INFEASIBLE).contains(plan.result()))
                    throw new AssertionError(name + ": unresolved " + plan.result() + " " + budget.diagnostics());
                if (truth.equals("UNSAT") && plan.feasible() || truth.equals("SAT") && !plan.feasible())
                    throw new AssertionError(name + ": false conclusion " + plan.result());
                if (plan.feasible() || !plan.missingExact().isEmpty()) {
                    Summary s = summary(plan.steps(), primitives, new IdentityHashMap<>());
                    Map<String, BigInteger> funded = new HashMap<>();
                    stock.forEach((key, value) -> funded.put(key, BigInteger.valueOf(value)));
                    plan.missingExact().forEach((key, value) -> funded.merge(key, value, BigInteger::add));
                    for (var e : s.need.entrySet()) if (funded.getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(e.getValue()) < 0)
                        throw new AssertionError(name + ": unfunded prefix " + e);
                    for (var e : s.need.entrySet()) if (plan.initialExact().getOrDefault(e.getKey(), BigInteger.ZERO).compareTo(e.getValue()) < 0)
                        throw new AssertionError(name + ": initial reservation smaller than primitive prefix " + e);
                    if (s.delta.getOrDefault(target, BigInteger.ZERO).compareTo(BigInteger.valueOf(amount)) < 0)
                        throw new AssertionError(name + ": force order reused existing goal inventory");
                    if (funded.getOrDefault(target, BigInteger.ZERO).add(s.delta.getOrDefault(target, BigInteger.ZERO)).compareTo(BigInteger.valueOf(amount)) < 0)
                        throw new AssertionError(name + ": missing goal");
                    for (var e : plan.seeds().entrySet()) if (funded.getOrDefault(e.getKey(), BigInteger.ZERO).add(s.delta.getOrDefault(e.getKey(), BigInteger.ZERO)).compareTo(BigInteger.valueOf(e.getValue())) < 0)
                        throw new AssertionError(name + ": missing returned seed " + e);
                }
                passed++;
                if (journal != null) {
                    journal.write(Path.of(args[3]).resolve(name.replaceAll("[^A-Za-z0-9_-]", "_") + ".cgp"));
                    if (journal.truncated()) throw new AssertionError(name + ": truncated proof archive");
                    for (var proof : journal.entries()) if (CountProof.verify(proof, 20_000_000) != CountProof.Verdict.VERIFIED)
                        throw new AssertionError(name + ": invalid count certificate " + proof.scope());
                    for (var proof : journal.executions()) if (ExecutionProof.verify(proof, 20_000_000) != CountProof.Verdict.VERIFIED)
                        throw new AssertionError(name + ": invalid execution certificate " + proof.scope());
                }
                System.out.println(name + "\t" + plan.result() + "\tms=" + (System.nanoTime() - start) / 1e6 + "\twork=" + budget.nodes());
            }
            if (input.read() != -1) throw new AssertionError("Trailing fixture bytes");
        }
        System.out.println("PASS fixtures=" + passed);
    }
}
