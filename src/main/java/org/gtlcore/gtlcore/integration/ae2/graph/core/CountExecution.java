package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Inventory-scoped necessary conditions at the first/last firing of a recipe. */
final class CountExecution<K> implements AutoCloseable {

    record Proof<K>(int recipe, List<K> materials, boolean last, CountGuard guard) {}

    private record Use(int recipe, BigInteger delta, BigInteger residual) {}

    private record Pool<K>(List<K> materials, BigInteger stock, Map<Integer, BigInteger> gains, List<Use> uses) {}

    private final List<Pool<K>> pools = new ArrayList<>();
    private final PlanningBudget budget;
    private long memory;

    CountExecution(RecipeCountModel<K> model, PlanningBudget budget) {
        this.budget = budget;
        // Reusable tokens are exact read arcs. Consumed configuration inputs
        // depend on push grouping and cannot use a per-firing absence proof.
        if (model.recipes.stream().anyMatch(GraphRecipe::batchSensitiveInputs)) return;
        long incidences = model.recipes.stream().mapToLong(r -> r.inputs().size() + r.outputs().size()).sum();
        long bytes = 256L + 192L * incidences;
        if (!budget.tryReserve(bytes)) return;
        memory = bytes;
        try {
            var keys = model.keys.stream().filter(key -> !model.external.contains(key)).toList();
            Set<K> returned = new LinkedHashSet<>();
            for (var recipe : model.recipes) for (K key : recipe.inputs().keySet()) {
                budget.check();
                if (!model.external.contains(key) && recipe.outputs().containsKey(key)) returned.add(key);
            }
            for (K key : returned) if (!compile(model, List.of(key))) return;
            // Different forms of a catalyst can individually appear obtainable
            // while their shared pool is too small. A nonnegative sum of places
            // obeys the same first/last-firing argument. Keep this optional
            // family small; omitted pools never imply infeasibility.
            if (keys.size() > 2 && keys.size() <= 16) for (K omitted : keys) {
                if (!compile(model, keys.stream().filter(key -> !key.equals(omitted)).toList())) return;
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private boolean compile(RecipeCountModel<K> model, List<K> keys) {
        BigInteger stock = BigInteger.ZERO;
        for (K key : keys) stock = stock.add(BigInteger.valueOf(model.stock.getOrDefault(key, 0L)));
        Map<Integer, BigInteger> gains = new LinkedHashMap<>();
        List<Use> uses = new ArrayList<>();
        for (int i = 0; i < model.recipes.size(); i++) {
            BigInteger input = BigInteger.ZERO, output = BigInteger.ZERO;
            for (K key : keys) {
                budget.check();
                input = input.add(BigInteger.valueOf(model.recipes.get(i).inputs().getOrDefault(key, 0L)));
                output = output.add(BigInteger.valueOf(model.recipes.get(i).outputs().getOrDefault(key, 0L)));
            }
            BigInteger delta = output.subtract(input), residual = input.min(output);
            if (delta.signum() > 0) gains.put(i, delta);
            if (residual.signum() > 0 && (delta.signum() < 0 || stock.compareTo(residual) < 0)) uses.add(new Use(i, delta, residual));
        }
        if (uses.isEmpty()) return true;
        long bytes = 256L + 128L * (gains.size() + uses.size());
        if (!budget.tryReserve(bytes)) return false;
        memory += bytes;
        pools.add(new Pool<>(List.copyOf(keys), stock, Map.copyOf(gains), List.copyOf(uses)));
        return true;
    }

    private Proof<K> proof(Pool<K> pool, Use use) {
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        pool.gains().forEach((key, value) -> { if (key != use.recipe()) terms.put(key, value.negate()); });
        if (use.delta().signum() < 0) terms.put(use.recipe(), use.delta().negate());
        // Before the FIRST use, this recipe cannot fund its own input. Before
        // its LAST use, its earlier net losses must also be funded. All OTHER
        // net losses are ignored and all other gains granted at once.
        // x_i>0 => -sum(other positive delta*x)-min(delta_i,0)*x_i
        // <= stock-min(input_i,output_i).
        var guard = new CountGuard(use.recipe(), new ExactLinearProgram.Constraint(terms, pool.stock().subtract(use.residual())));
        return new Proof<>(use.recipe(), pool.materials(), use.delta().signum() < 0, guard);
    }

    List<Proof<K>> proofs() {
        var result = new ArrayList<Proof<K>>();
        for (var pool : pools) for (var use : pool.uses()) result.add(proof(pool, use));
        return List.copyOf(result);
    }

    CountGuard violated(ExactRational[] point) {
        for (var pool : pools) {
            ExactRational funded = ExactRational.of(pool.stock());
            for (var gain : pool.gains().entrySet()) {
                budget.check();
                funded = funded.add(point[gain.getKey()].multiply(ExactRational.of(gain.getValue())));
            }
            for (var use : pool.uses()) {
                budget.check();
                if (point[use.recipe()].signum() == 0) continue;
                if (funded.subtract(point[use.recipe()].multiply(ExactRational.of(use.delta().abs())))
                        .compareTo(ExactRational.of(use.residual())) >= 0)
                    continue;
                Proof<K> proof = proof(pool, use);
                budget.note("count_execution", (proof.last() ? "last" : "first") + "_firing; recipe=" + proof.recipe() +
                        "; materials=" + proof.materials() + "; condition=" + proof.guard().required());
                return proof.guard();
            }
        }
        return null;
    }

    @Override
    public void close() {
        budget.release(memory);
        memory = 0;
    }
}
