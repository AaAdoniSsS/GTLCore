package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;

/** Persistent assumptions/learned cores owned by one immutable recipe catalog. */
final class CountSessions {

    private record Entry(List<String> recipes, List<ExactLinearProgram.Constraint> assumptions,
                         List<CountConflict> conflicts, CountProof.Certificate proof, long terms) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    synchronized <K> List<CountConflict> reuse(RecipeCountModel<K> model, PlanningBudget budget) {
        var ids = model.recipes.stream().map(GraphRecipe::id).toList();
        Map<Map<Integer, BigInteger>, BigInteger> bounds = new HashMap<>();
        for (var row : model.constraints) bounds.merge(row.terms(), row.upper(), BigInteger::min);
        for (var entry : entries) {
            if (!entry.recipes.equals(ids)) continue;
            boolean applicable = true;
            for (var row : entry.assumptions) {
                budget.check();
                var current = bounds.get(row.terms());
                if (current == null || current.compareTo(row.upper()) > 0) {
                    applicable = false;
                    break;
                }
            }
            if (!applicable) continue;
            if (budget.proofJournal() != null) budget.proofJournal().add(entry.proof);
            budget.note("count_session", "reused_cores=" + entry.conflicts.size() + "; assumptions_rechecked");
            return entry.conflicts;
        }
        return List.of();
    }

    synchronized <K> void remember(RecipeCountModel<K> model, List<CountConflict> conflicts, PlanningBudget budget) {
        if (model.recipes.size() > 128 || model.constraints.size() > 512 || conflicts.isEmpty() || budget.remainingWork() < 32768) return;
        long terms = model.constraints.stream().mapToLong(row -> row.terms().size()).sum();
        if (terms > 4096) return;
        List<CountProof.Row> axioms = new ArrayList<>(model.constraints.stream().map(CountProof::row).toList());
        for (int i = 0; i < model.recipes.size(); i++) axioms.add(new CountProof.Row(Map.of(i, BigInteger.ONE.negate()), BigInteger.ZERO));
        List<CountConflict> accepted = new ArrayList<>();
        List<List<CountProof.Row>> forbidden = new ArrayList<>();
        int attempts = 0;
        for (var conflict : conflicts) {
            if (attempts++ == 8 || budget.remainingWork() < 8192) break;
            if (conflict.assumptions().stream().mapToLong(row -> row.terms().size()).sum() > 16) continue;
            var trial = new ArrayList<>(forbidden);
            trial.add(conflict.assumptions().stream().map(CountProof::row).toList());
            var proof = new CountProof.Certificate("persistent_count_assumptions", model.recipes.size(), axioms, trial, List.of(), false);
            // Execution-only or unavailable derivations are not transferred.
            // This separate checker certifies the entire retained core chain.
            budget.charge(4096);
            if (CountProof.verify(proof, 4096) != CountProof.Verdict.VERIFIED) continue;
            accepted.add(conflict);
            forbidden = trial;
        }
        if (accepted.isEmpty()) return;
        var proof = new CountProof.Certificate("persistent_count_assumptions", model.recipes.size(), axioms, forbidden, List.of(), false);
        var entry = new Entry(model.recipes.stream().map(GraphRecipe::id).toList(), List.copyOf(model.constraints), List.copyOf(accepted), proof, terms);
        entries.removeIf(old -> old.recipes.equals(entry.recipes) && old.assumptions.equals(entry.assumptions));
        entries.addFirst(entry);
        while (entries.size() > 8 || entries.stream().mapToLong(Entry::terms).sum() > 8192) entries.removeLast();
    }
}
