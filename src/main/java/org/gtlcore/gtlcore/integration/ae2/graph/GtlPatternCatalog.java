package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.crafting.ManualCraftingInventoryLock;
import org.gtlcore.gtlcore.integration.ae2.graph.core.CatalogIndex;
import org.gtlcore.gtlcore.integration.ae2.graph.core.CheckedAmounts;
import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphCompiler;
import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphRecipe;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.me.service.CraftingService;

import java.util.*;

/** Snapshot creation and pattern methods run on the server thread, never the solver thread. */
public final class GtlPatternCatalog {

    private static final int MAX_VARIANTS = 256;

    private record Roots(AEKey target, Set<AEKey> recovery) {}

    private final Map<Roots, Structure> cache = new LinkedHashMap<>(16, 0.75f, true);
    private Object recipeManager;
    private static long dataGeneration;
    private long cachedDataGeneration = -1;
    private long invalidationGeneration;

    /** A failed execution preflight disproves the cached catalog, even without a provider event. */
    public void invalidateBinding(String binding) {
        cache.values().removeIf(structure -> structure.bindings().containsKey(binding));
        invalidationGeneration++;
    }

    public Snapshot capture(IGrid grid, CraftingService service, Level level, IActionSource source, AEKey target, PlanningBudget budget) {
        Capture capture = begin(grid, service, level, source, target, budget);
        while (!capture.step()) { /* Explicit synchronous utility; production uses the tick-budgeted queue. */ }
        return capture.result();
    }

    public static void dataReloaded() {
        dataGeneration++;
    }

    public Capture begin(IGrid grid, CraftingService service, Level level, IActionSource source, AEKey target, PlanningBudget budget) {
        return begin(grid, service, level, source, target, Set.of(), budget);
    }

    public Capture begin(IGrid grid, CraftingService service, Level level, IActionSource source, AEKey target,
                         Set<AEKey> recovery, PlanningBudget budget) {
        return new Capture(grid, service, level, source, new Roots(target, Set.copyOf(recovery)), budget);
    }

    /** World access is split between ticks; each request keeps its frontier and captured bindings. */
    public final class Capture {

        private final IGrid grid;
        private final CraftingService service;
        private final IStorageService storage;
        private final Level level;
        private final IActionSource source;
        private final Roots roots;
        private final PlanningBudget budget;
        private KeyCounter available;
        private long revision;
        private final long dataRevision;
        private final long invalidationRevision;
        private final boolean simulate;
        private final Map<String, IPatternDetails> bindings = new LinkedHashMap<>();
        private final Map<String, Integer> priorities = new HashMap<>();
        private final Map<AEKey, List<String>> dependencies = new LinkedHashMap<>();
        private final Set<AEKey> seen = new LinkedHashSet<>();
        // IGNORE_ALL fuzzy scans use only the primary key. Cache one representative
        // instead of revisiting thousands of NBT variants on every warm request.
        private final Map<Object, AEKey> templates = new LinkedHashMap<>();
        private final Set<IPatternDetails> seenPatterns = Collections.newSetFromMap(new IdentityHashMap<>());
        private final PatternFingerprint.Context fingerprints = new PatternFingerprint.Context();
        private final Deque<AEKey> pending = new ArrayDeque<>();
        private final List<GraphRecipe<AEKey>> recipes = new ArrayList<>();
        private final Set<String> seenVariants = new HashSet<>();
        private final NavigableMap<Integer, List<GraphRecipe<AEKey>>> priorityBuckets = new TreeMap<>(Comparator.reverseOrder());
        private Iterator<List<GraphRecipe<AEKey>>> sortedBuckets;
        private Iterator<GraphRecipe<AEKey>> sorting;
        private final Map<AEKey, Long> stock = new LinkedHashMap<>();
        private final Set<AEKey> emitted = new LinkedHashSet<>(), fuzzy = new HashSet<>();
        private final Set<Object> fuzzyPrimary = new HashSet<>();
        private Structure structure;
        private boolean hit, bounded;
        private int phase;
        private Iterator<AEKey> keys;
        private Iterator<IPatternDetails> patterns;
        private AEKey key;
        private List<String> versions;
        private Snapshot result;
        private CatalogIndex<AEKey> indexing;
        private Normalization normalizing;
        private Iterator<GraphRecipe<AEKey>> normalized;

        Capture(IGrid grid, CraftingService service, Level level, IActionSource source, Roots roots, PlanningBudget budget) {
            if (!level.getServer().isSameThread()) throw new IllegalStateException("Graph snapshot requires server thread");
            this.grid = grid;
            this.service = service;
            this.storage = grid.getStorageService();
            this.level = level;
            this.source = source;
            this.roots = roots;
            this.budget = budget;
            available = source == null ? new KeyCounter() : storage.getCachedInventory();
            revision = ((GraphRequestTracker) service).gtlcore$graphProviderGeneration();
            dataRevision = dataGeneration;
            invalidationRevision = invalidationGeneration;
            if (recipeManager != level.getRecipeManager() || cachedDataGeneration != dataRevision) {
                cache.clear();
                recipeManager = level.getRecipeManager();
                cachedDataGeneration = dataRevision;
            }
            simulate = source != null && (AEConfig.instance().isCraftingSimulatedExtraction() ||
                    ManualCraftingInventoryLock.hasReservations(storage.getInventory()));
            structure = cache.get(roots);
            if (structure != null) {
                hit = true;
                if (structure.providerRevision() != revision) keys = structure.resources().iterator();
                else {
                    phase = 2;
                    keys = structure.inputTemplates().iterator();
                }
            } else resetBuild();
        }

        public boolean step() {
            return step(Long.MAX_VALUE);
        }

        public boolean step(long deadline) {
            if (!level.getServer().isSameThread()) throw new IllegalStateException("Graph snapshot escaped server thread");
            budget.check();
            budget.phase(PlanningBudget.Phase.SNAPSHOT);
            switch (phase) {
                case 0 -> { // Revalidate only this target's dependency signatures after a provider edit.
                    if (patterns != null && patterns.hasNext()) {
                        versions.add(signature(patterns.next()));
                        return false;
                    }
                    if (patterns != null) {
                        patterns = null;
                        if (!versions.equals(structure.dependencies().get(key))) {
                            resetBuild();
                            return false;
                        }
                    }
                    if (keys.hasNext()) {
                        key = keys.next();
                        versions = new ArrayList<>();
                        patterns = List.copyOf(service.getCraftingFor(key)).iterator();
                    } else {
                        phase = 2;
                        keys = structure.inputTemplates().iterator();
                    }
                }
                case 1 -> {
                    if (normalizing != null) {
                        if (!normalizing.step()) return false;
                        bounded |= normalizing.bounded;
                        normalized = normalizing.variants.iterator();
                        normalizing = null;
                    }
                    if (normalized != null) {
                        if (normalized.hasNext()) {
                            var variant = normalized.next();
                            if (!seenVariants.add(variant.id())) return false;
                            budget.reserve(256L + 64L * (variant.slots().size() + variant.outputs().size()));
                            recipes.add(variant);
                            priorityBuckets.computeIfAbsent(priorities.get(variant.binding()), ignored -> new ArrayList<>()).add(variant);
                            pending.addAll(variant.inputs().keySet());
                            return false;
                        }
                        normalized = null;
                    }
                    if (patterns != null && patterns.hasNext()) {
                        IPatternDetails pattern = patterns.next();
                        String binding = fingerprints.of(pattern);
                        versions.add(signature(pattern, binding));
                        if (!seenPatterns.add(pattern)) return false;
                        bindings.putIfAbsent(binding, pattern);
                        normalizing = new Normalization(pattern, binding, available, level, budget, fingerprints);
                        for (var input : pattern.getInputs()) for (var possible : input.getPossibleInputs()) {
                            templates.putIfAbsent(possible.what().getPrimaryKey(), possible.what());
                            pending.add(possible.what());
                        }
                        return false;
                    }
                    if (patterns != null) {
                        dependencies.put(key, List.copyOf(versions));
                        patterns = null;
                    }
                    if (pending.isEmpty()) {
                        phase = 2;
                        keys = templates.values().iterator();
                        return false;
                    }
                    key = pending.removeFirst();
                    if (!seen.add(key)) return false;
                    if (seen.size() > 100_000 || recipes.size() > 100_000) throw new PlanningBudget.Exhausted(PlanningBudget.Limit.GRAPH_LIMIT);
                    budget.reserve(96);
                    versions = new ArrayList<>();
                    patterns = List.copyOf(service.getCraftingFor(key)).iterator();
                }
                case 2 -> {
                    if (keys.hasNext()) {
                        AEKey template = keys.next();
                        if (fuzzyPrimary.add(template.getPrimaryKey()))
                            for (var entry : available.findFuzzy(template, FuzzyMode.IGNORE_ALL)) if (entry.getLongValue() > 0) fuzzy.add(entry.getKey());
                    } else if (hit && !fuzzy.equals(structure.fuzzyKeys())) resetBuild();
                    else {
                        if (!hit) {
                            sortedBuckets = priorityBuckets.values().iterator();
                            recipes.clear();
                            phase = 6;
                            return false;
                        } else if (structure.providerRevision() != revision) structure = new Structure(structure.compiler(), structure.bindings(),
                                structure.resources(), structure.inputTemplates(), structure.fuzzyKeys(), structure.boundedAlternatives(),
                                structure.dependencies(), revision);
                        keys = structure.resources().iterator();
                        phase = 3;
                        if (source != null) available = storage.getCachedInventory();
                    }
                }
                case 3 -> {
                    // Amortize state-machine/phase bookkeeping over a bounded
                    // stock scan. World calls stay on the server thread; every
                    // key still checks cancellation and the shared tick deadline.
                    int scanned = 0;
                    while (keys.hasNext() && scanned < 32) {
                        if (scanned > 0) {
                            if (deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0) return false;
                            budget.check();
                        }
                        scanned++;
                        AEKey resource = keys.next();
                        if (source != null) {
                            long count = available.get(resource);
                            // AE may defer a just-inserted stack's cache update to
                            // its end-tick event. Query an empty cached key before
                            // diagnosing absence; this never transfers material.
                            if (simulate || count == 0) count = storage.getInventory().extract(resource,
                                    count == 0 ? Long.MAX_VALUE : count, Actionable.SIMULATE, source);
                            if (count > 0) stock.put(resource, count);
                        }
                        if (service.canEmitFor(resource)) emitted.add(resource);
                    }
                    if (!keys.hasNext()) {
                        if (dataRevision != dataGeneration) throw new IllegalStateException("GRAPH_DATA_CHANGED_DURING_SNAPSHOT");
                        if (invalidationRevision != invalidationGeneration) throw new IllegalStateException("GRAPH_BINDING_INVALIDATED_DURING_SNAPSHOT");
                        long latest = ((GraphRequestTracker) service).gtlcore$graphProviderGeneration();
                        if (revision != latest) {
                            revision = latest;
                            phase = 7;
                            keys = structure.resources().iterator();
                            patterns = null;
                            return false;
                        }
                        if (structure.providerRevision() != revision) structure = new Structure(structure.compiler(), structure.bindings(), structure.resources(),
                                structure.inputTemplates(), structure.fuzzyKeys(), structure.boundedAlternatives(), structure.dependencies(), revision);
                        cache.put(roots, structure);
                        long weight = cache.values().stream().mapToLong(value -> value.resources().size() + value.compiler().catalog().size()).sum();
                        while (cache.size() > 32 || weight > 65_536) {
                            Structure removed = cache.remove(cache.keySet().iterator().next());
                            weight -= removed.resources().size() + removed.compiler().catalog().size();
                        }
                        result = new Snapshot(structure, Map.copyOf(stock), Set.copyOf(emitted), revision, hit);
                        phase = 4;
                    }
                }
                case 5 -> {
                    if (!indexing.step(budget)) return false;
                    structure = new Structure(indexing.result(), Map.copyOf(bindings), Set.copyOf(seen),
                            Set.copyOf(templates.values()), Set.copyOf(fuzzy), bounded, Map.copyOf(dependencies), revision);
                    indexing = null;
                    keys = structure.resources().iterator();
                    phase = 3;
                    if (source != null) available = storage.getCachedInventory();
                }
                case 6 -> {
                    if (sorting != null && sorting.hasNext()) recipes.add(sorting.next());
                    else if (sortedBuckets.hasNext()) sorting = sortedBuckets.next().iterator();
                    else {
                        indexing = new CatalogIndex<>(recipes);
                        phase = 5;
                    }
                }
                case 7 -> {
                    if (patterns != null && patterns.hasNext()) {
                        versions.add(signature(patterns.next()));
                        return false;
                    }
                    if (patterns != null) {
                        patterns = null;
                        if (!versions.equals(structure.dependencies().get(key))) throw new IllegalStateException("GRAPH_PATTERN_CHANGED_DURING_SNAPSHOT");
                    }
                    if (keys.hasNext()) {
                        key = keys.next();
                        versions = new ArrayList<>();
                        patterns = List.copyOf(service.getCraftingFor(key)).iterator();
                    } else {
                        phase = 3;
                        keys = Collections.emptyIterator();
                    }
                }
                default -> {
                    return true;
                }
            }
            return result != null;
        }

        private String signature(IPatternDetails pattern) {
            return signature(pattern, fingerprints.of(pattern));
        }

        private String signature(IPatternDetails pattern, String id) {
            int priority = Integer.MIN_VALUE;
            for (var provider : service.getProviders(pattern)) priority = Math.max(priority, provider.getPatternPriority());
            priorities.put(id, priority);
            return id + ':' + priority;
        }

        private void resetBuild() {
            hit = false;
            structure = null;
            phase = 1;
            patterns = null;
            fuzzy.clear();
            fuzzyPrimary.clear();
            pending.add(roots.target());
            pending.addAll(roots.recovery());
        }

        public Snapshot result() {
            if (result == null) throw new IllegalStateException("Snapshot incomplete");
            return result;
        }
    }

    private static Set<AEKey> fuzzyKeys(KeyCounter available, Set<AEKey> templates) {
        Set<AEKey> result = new HashSet<>();
        Set<Object> primary = new HashSet<>();
        for (AEKey key : templates) {
            if (!primary.add(key.getPrimaryKey())) continue;
            for (var entry : available.findFuzzy(key, FuzzyMode.IGNORE_ALL)) if (entry.getLongValue() > 0) result.add(entry.getKey());
        }
        return Set.copyOf(result);
    }

    private static List<GraphRecipe<AEKey>> normalize(IPatternDetails pattern, String binding, KeyCounter available, Level level) {
        return normalize(pattern, binding, available, level, new PlanningBudget(0, 200_000, () -> false));
    }

    private static List<GraphRecipe<AEKey>> normalize(IPatternDetails pattern, String binding, KeyCounter available, Level level, PlanningBudget budget) {
        var work = new Normalization(pattern, binding, available, level, budget, new PatternFingerprint.Context());
        while (!work.step()) {}
        return work.variants;
    }

    private static final class Normalization {

        final IPatternDetails pattern;
        final String binding;
        final KeyCounter available;
        final Level level;
        final PlanningBudget budget;
        final PatternFingerprint.Context fingerprints;
        final List<List<List<Picked>>> choices = new ArrayList<>();
        final List<GraphRecipe<AEKey>> variants = new ArrayList<>();
        final Map<AEKey, GenericStack> candidates = new LinkedHashMap<>();
        final IPatternDetails.IInput[] inputs;
        int inputSlot, phase;
        int[] indices;
        boolean bounded;
        Iterator<GenericStack> possibilities;
        Iterator<? extends it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey>> fuzzy;
        GenericStack possible;

        Normalization(IPatternDetails pattern, String binding, KeyCounter available, Level level, PlanningBudget budget, PatternFingerprint.Context fingerprints) {
            this.pattern = pattern;
            this.binding = binding;
            this.available = available;
            this.level = level;
            this.budget = budget;
            this.fingerprints = fingerprints;
            inputs = pattern.getInputs();
            if (inputs.length > 256 || pattern.getOutputs().length > 256) throw new PlanningBudget.Exhausted(PlanningBudget.Limit.GRAPH_LIMIT);
        }

        boolean step() {
            budget.check();
            if (phase == 2) return true;
            if (phase == 0) {
                if (inputSlot == inputs.length) {
                    indices = new int[choices.size()];
                    phase = 1;
                    return false;
                }
                var input = inputs[inputSlot];
                if (fuzzy != null) {
                    if (fuzzy.hasNext() && candidates.size() < MAX_VARIANTS) {
                        var entry = fuzzy.next();
                        if (entry.getLongValue() > 0 && input.isValid(entry.getKey(), level))
                            candidates.putIfAbsent(entry.getKey(), new GenericStack(entry.getKey(), possible.amount()));
                        return false;
                    }
                    bounded |= fuzzy.hasNext();
                    fuzzy = null;
                }
                if (possibilities == null) possibilities = List.of(input.getPossibleInputs()).iterator();
                if (possibilities.hasNext() && candidates.size() < MAX_VARIANTS) {
                    possible = possibilities.next();
                    if (input.isValid(possible.what(), level)) candidates.put(possible.what(), possible);
                    fuzzy = available.findFuzzy(possible.what(), FuzzyMode.IGNORE_ALL).iterator();
                    return false;
                }
                bounded |= possibilities.hasNext();
                if (candidates.isEmpty()) {
                    phase = 2;
                    return true;
                }
                List<List<Picked>> selections = new ArrayList<>();
                for (GenericStack candidate : candidates.values()) selections.add(List.of(new Picked(candidate, input.getMultiplier())));
                if (!pattern.supportsPushInputsToExternalInventory() && input.getMultiplier() <= 9 && candidates.size() > 1) {
                    mixed(new ArrayList<>(candidates.values()), 0, input.getMultiplier(), new ArrayList<>(), selections, budget);
                    bounded |= selections.size() >= MAX_VARIANTS;
                }
                choices.add(List.copyOf(selections));
                inputSlot++;
                possibilities = null;
                candidates.clear();
                return false;
            }
            List<List<Picked>> selected = new ArrayList<>();
            for (int i = 0; i < indices.length; i++) selected.add(choices.get(i).get(indices[i]));
            variants.add(variant(pattern, binding, selected, fingerprints));
            int at = indices.length - 1;
            while (at >= 0 && ++indices[at] == choices.get(at).size()) {
                indices[at] = 0;
                at--;
            }
            if (at < 0) phase = 2;
            else if (variants.size() >= MAX_VARIANTS) {
                bounded = true;
                phase = 2;
            }
            return phase == 2;
        }
    }

    private static void mixed(List<GenericStack> candidates, int at, long left, List<Picked> selected,
                              List<List<Picked>> out, PlanningBudget budget) {
        budget.check();
        if (out.size() >= MAX_VARIANTS) return;
        if (left == 0) {
            if (selected.size() > 1) out.add(List.copyOf(selected));
            return;
        }
        if (at >= candidates.size()) return;
        for (long count = left; count >= 0 && out.size() < MAX_VARIANTS; count--) {
            if (count > 0) selected.add(new Picked(candidates.get(at), count));
            mixed(candidates, at + 1, left - count, selected, out, budget);
            if (count > 0) selected.remove(selected.size() - 1);
        }
    }

    private static GraphRecipe<AEKey> variant(IPatternDetails pattern, String binding, List<List<Picked>> selected, PatternFingerprint.Context fingerprints) {
        List<GraphRecipe.Slot<AEKey>> slots = new ArrayList<>();
        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        StringBuilder identity = new StringBuilder(binding);
        for (var output : pattern.getOutputs()) outputs.merge(output.what(), output.amount(), CheckedAmounts::add);
        for (int i = 0; i < selected.size(); i++) {
            for (Picked picked : selected.get(i)) {
                var choice = picked.template();
                long amount = CheckedAmounts.multiply(choice.amount(), picked.copies());
                boolean configuration = pattern.supportsPushInputsToExternalInventory() && GtlDispatchPolicy.configuration(choice.what());
                slots.add(new GraphRecipe.Slot<>(choice.what(), amount, i, configuration));
                identity.append('|').append(i).append(':').append(fingerprints.key(choice.what())).append(':').append(amount);
                AEKey remainder = pattern.getInputs()[i].getRemainingKey(choice.what());
                if (remainder != null) outputs.merge(remainder, picked.copies(), CheckedAmounts::add);
            }
        }
        return new GraphRecipe<>(fingerprints.hash(identity.toString()), binding, slots, outputs);
    }

    private record Picked(GenericStack template, long copies) {}

    public record Structure(GraphCompiler<AEKey> compiler, Map<String, IPatternDetails> bindings, Set<AEKey> resources,
                            Set<AEKey> inputTemplates, Set<AEKey> fuzzyKeys, boolean boundedAlternatives,
                            Map<AEKey, List<String>> dependencies, long providerRevision) {}

    public record Snapshot(Structure structure, Map<AEKey, Long> stock, Set<AEKey> emitable, long epoch, boolean cacheHit) {}
}
