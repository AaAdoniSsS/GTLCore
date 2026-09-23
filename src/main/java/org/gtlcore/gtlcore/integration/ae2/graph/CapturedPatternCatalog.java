package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphCompiler;
import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphRecipe;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningScheduler;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PreparedCatalog;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import java.util.*;

/** Deferred pure encoding. Pattern handles are opaque here: no provider, world or pattern callbacks. */
public final class CapturedPatternCatalog {

    public record Recipe(List<GraphRecipe.Slot<AEKey>> slots, Map<AEKey, Long> outputs) {

        public Recipe {
            slots = List.copyOf(slots);
            outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }

        GraphRecipe<AEKey> encode(String binding, PatternFingerprint.Context fingerprints) {
            StringBuilder identity = new StringBuilder(binding);
            for (var slot : slots) identity.append('|').append(slot.inputSlot()).append(':')
                    .append(fingerprints.key(slot.key())).append(':').append(slot.amount());
            return new GraphRecipe<>(fingerprints.hash(identity.toString()), binding, slots, outputs);
        }
    }

    public record Entry(IPatternDetails handle, PatternFingerprint.Values values, int priority, List<Recipe> recipes) {

        public Entry {
            recipes = List.copyOf(recipes);
        }
    }

    public record Prepared(GraphCompiler<AEKey> compiler, Map<String, IPatternDetails> bindings) {}

    private volatile List<Entry> entries;
    private final int size;
    private volatile Prepared prepared;

    public CapturedPatternCatalog(List<Entry> entries, int size) {
        this.entries = List.copyOf(entries);
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean mayContainBinding(String binding) {
        Prepared current = prepared;
        return current == null || current.bindings().containsKey(binding);
    }

    public Build build(PlanningBudget budget) {
        return new Build(budget);
    }

    public final class Build implements PlanningScheduler.Work<Prepared> {

        private final PlanningBudget budget;
        private final List<Entry> capturedEntries;
        private final PatternFingerprint.Context fingerprints = new PatternFingerprint.Context();
        private final Map<String, IPatternDetails> bindings = new LinkedHashMap<>();
        private final Map<String, Integer> priorities = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private final List<GraphRecipe<AEKey>> recipes = new ArrayList<>();
        private int cursor;
        private String binding;
        private Iterator<Recipe> variants;
        private PreparedCatalog<AEKey>.Build indexing;
        private Prepared result;

        private Build(PlanningBudget budget) {
            this.budget = budget;
            capturedEntries = entries;
        }

        @Override
        public boolean advance(PlanningScheduler.Slice slice) {
            if (prepared != null) {
                result = prepared;
                return true;
            }
            budget.phase(PlanningBudget.Phase.BUILD);
            while (indexing == null && slice.next()) {
                budget.check();
                if (variants != null && variants.hasNext()) {
                    GraphRecipe<AEKey> recipe = variants.next().encode(binding, fingerprints);
                    if (seen.add(recipe.id())) recipes.add(recipe);
                } else if (cursor < capturedEntries.size()) {
                    Entry entry = capturedEntries.get(cursor++);
                    binding = fingerprints.of(entry.values());
                    bindings.putIfAbsent(binding, entry.handle());
                    priorities.put(binding, entry.priority());
                    variants = entry.recipes().iterator();
                } else indexing = new PreparedCatalog<>(recipes, priorities).build(budget);
            }
            if (indexing == null || !indexing.advance(slice)) return false;
            Prepared complete = new Prepared(indexing.result(), Collections.unmodifiableMap(new LinkedHashMap<>(bindings)));
            synchronized (CapturedPatternCatalog.this) {
                if (prepared == null) {
                    prepared = complete;
                    // The compiled recipes now own their values. Do not retain a
                    // second set of variant maps for the lifetime of this cache.
                    // Active builders keep their own immutable list reference.
                    entries = List.of();
                }
                result = prepared;
            }
            return true;
        }

        @Override
        public Prepared result() {
            if (result == null) throw new IllegalStateException("Captured catalog preparation incomplete");
            return result;
        }
    }
}
