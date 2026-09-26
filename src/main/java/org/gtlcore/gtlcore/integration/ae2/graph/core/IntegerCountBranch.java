package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** One owned, resumable subtree. No mutable solver state is shared between workers. */
final class IntegerCountBranch<K> implements AutoCloseable {

    enum State {
        OPEN,
        SPLIT,
        DEAD,
        UNRESOLVED,
        FOUND,
        PRUNED
    }

    final RecipeCountModel<K> model;
    final CountExecution<K> execution;
    final K target;
    final long amount, started;
    final Map<K, Long> stock, seeds;
    final Set<K> external;
    final boolean preserve, force;
    boolean preprocessingOnly;
    final PlanningBudget budget;
    final List<ExactLinearProgram.Constraint> current;
    final List<List<ExactLinearProgram.Constraint>> children = new ArrayList<>();
    final Set<ExactLinearProgram.Constraint> learnedMaterials = new LinkedHashSet<>();
    final List<CountGuard> supportConflicts = new ArrayList<>();
    final Set<CountConflict> learnedChoices = new LinkedHashSet<>();
    private List<CountConflict> knownChoices = List.of();
    private List<ExactLinearProgram.Constraint> knownMaterials = List.of();
    List<ExactLinearProgram.Constraint> linearConstraints;
    CountBounds propagating;
    CountBounds.Seed inheritedBounds, sharedBounds;
    CountReduction reduction;
    boolean compiled, reducedLinear;
    CountPartition partition;
    CountMeetInMiddle matching;
    CountLatticeRepair repair;
    ExactRational[] repairPoint;
    int repairAttempts;
    CountBoolean binary;
    ExactLinearProgram linear;
    ExactLinearProgram.Basis inheritedBasis, sharedBasis;
    CountReduction.Coordinates inheritedCoordinates;
    CountSchedule<K> scheduling;
    CountSupportSearch<K> supportSearch;
    CountProgram<K> program;
    AllocationSearch.Candidate<K> assembling;
    PlanVerification<K> verifying;
    BigInteger[] counts, lower, upper;
    GraphPlan<K> plan;
    PlanPreference<K> preference, lowerCost;
    State state = State.OPEN;
    PlanningBudget.Exhausted limit;
    boolean initialized, partitioned, rescue, retried, choicePruned, needsRepair;
    long memory, workspace, work, schedulingWork;
    int globalRows, checkedChoices;
    final Set<CountConflict> usedChoices = new LinkedHashSet<>();

    IntegerCountBranch(RecipeCountModel<K> model, CountExecution<K> execution, K target, long amount, Map<K, Long> stock,
                       Map<K, Long> seeds, Set<K> external, boolean preserve, boolean force,
                       PlanningBudget budget, long started, List<ExactLinearProgram.Constraint> current) {
        this.model = model;
        this.execution = execution;
        this.target = target;
        this.amount = amount;
        this.stock = stock;
        this.seeds = seeds;
        this.external = external;
        this.preserve = preserve;
        this.force = force;
        preprocessingOnly = model.recipes.size() > 64 || model.keys.size() > 96;
        this.budget = budget;
        this.started = started;
        this.current = List.copyOf(current);
        long bytes = 512L + current.stream().mapToLong(row -> 192L + 48L * row.terms().size()).sum();
        if (budget.tryReserve(bytes)) memory = bytes;
        else state = State.UNRESOLVED;
    }

    void run(long quantum, List<ExactLinearProgram.Constraint> materials, List<CountGuard> support, List<CountConflict> choices,
             PlanPreference<K> incumbent, BooleanSupplier stopped) {
        long before = budget.threadWork();
        try {
            if (state != State.OPEN) return;
            if (!knownChoices.equals(choices)) checkedChoices = 0;
            knownChoices = choices;
            knownMaterials = materials;
            if (!initialized && rejectsChoices()) {
                choicePruned = true;
                state = State.DEAD;
                return;
            }
            if (workspace == 0) {
                if (!budget.tryReserve(2L << 20)) {
                    state = State.UNRESOLVED;
                    return;
                }
                workspace = 2L << 20;
            }
            if (!initialized) {
                initialized = true;
                supportConflicts.addAll(support);
                linearConstraints = new ArrayList<>(model.constraints);
                for (int i = 0; i < model.recipes.size(); i++) {
                    var recipe = model.recipes.get(i);
                    budget.check();
                    // A deterministic identity firing leaves the marking
                    // unchanged. Delete it from any witness without changing
                    // the enabledness of the surrounding program. Canonicalize
                    // its count to zero so no-good partitioning cannot spend
                    // branches adding arbitrarily many useless firings.
                    if (recipe.configurationInputs().isEmpty() && recipe.reusableInputs().isEmpty() &&
                            recipe.inputs().equals(recipe.outputs()))
                        linearConstraints.add(bound(i, BigInteger.ZERO, false));
                }
                linearConstraints.addAll(materials);
                globalRows = linearConstraints.size();
                linearConstraints.addAll(current);
                propagating = new CountBounds(model.recipes.size(), linearConstraints, budget, globalRows, inheritedBounds);
                if (inheritedBounds != null) {
                    inheritedBounds.close();
                    inheritedBounds = null;
                }
            }
            if (propagating != null) propagating.learn(choices);
            do {
                if (stopped.getAsBoolean()) return;
                budget.check();
                if (incumbent != null && lowerCost != null && lowerCost.cannotImprove(incumbent)) {
                    state = State.PRUNED;
                    return;
                }
                long scheduleBefore = budget.threadWork();
                boolean inSchedule = scheduling != null;
                advance();
                if (inSchedule) schedulingWork += budget.threadWork() - scheduleBefore;
                if (scheduling != null && !partitioned && schedulingWork >= 8192) {
                    // Keep this exact scheduling continuation, while other counts
                    // get disjoint sibling subspaces and their own work slices.
                    partitionCounts();
                    return;
                }
            } while (state == State.OPEN && budget.threadWork() - before < quantum);
        } catch (ExactRational.PrecisionLimit precision) {
            state = State.UNRESOLVED;
        } catch (PlanningBudget.Exhausted exhausted) {
            limit = exhausted;
            state = State.UNRESOLVED;
        } finally {
            work += budget.threadWork() - before;
        }
    }

    private void advance() {
        if (upper != null) while (checkedChoices < knownChoices.size()) {
            var conflict = knownChoices.get(checkedChoices++);
            if (conflict.impliedBy(lower, upper, budget)) {
                usedChoices.add(conflict);
                choicePruned = true;
                state = State.DEAD;
                return;
            }
        }
        if (verifying != null) {
            if (!verifying.step()) return;
            for (BigInteger initial : plan.initialExact().values()) {
                budget.check();
                if (initial.compareTo(ExactAmounts.LONG_MAX) > 0) {
                    unresolved();
                    return;
                }
            }
            preference = PlanPreference.of(plan, assembling.summary,
                    Arrays.stream(counts).reduce(BigInteger.ZERO, BigInteger::add));
            if (lowerCost != null && lowerCost.cannotImprove(preference)) {
                partitioned = true;
                budget.note("count_cost_bound", "witness_attains_componentwise_lower_bound; no_improvement_branches");
            } else partitionCounts();
            state = State.FOUND;
            return;
        }
        if (assembling != null) {
            if (!assembling.step()) return;
            plan = assembling.plan;
            if (plan == null) {
                unresolved();
                return;
            }
            verifying = new PlanVerification<>(plan, budget);
            return;
        }
        if (program != null) {
            if (!program.step()) return;
            if (!program.available()) {
                state = State.UNRESOLVED;
                return;
            }
            beginAssembly(program.program());
            program.close();
            program = null;
            return;
        }
        if (scheduling != null) {
            if (!scheduling.step()) return;
            CountSchedule.Result status = scheduling.result();
            if (status == CountSchedule.Result.WITNESS) beginAssembly(scheduling.witness());
            scheduling.close();
            scheduling = null;
            if (status == CountSchedule.Result.DEAD) {
                // This fixed vector was exhausted or rejected by a checked
                // optimistic startup proof. Share the exact exclusion; an
                // UNKNOWN scheduling result must never create this clause.
                var fixed = new ArrayList<ExactLinearProgram.Constraint>();
                for (int i = 0; i < counts.length; i++) {
                    fixed.add(bound(i, counts[i], false));
                    if (counts[i].signum() > 0) fixed.add(bound(i, counts[i], true));
                }
                learnedChoices.add(new CountConflict(fixed));
                needsRepair = true;
                supportSearch = new CountSupportSearch<>(model, counts, budget);
            } else if (status == CountSchedule.Result.UNKNOWN) unresolved();
            return;
        }
        if (supportSearch != null) {
            if (!supportSearch.step()) return;
            var status = supportSearch.result();
            if (status == CountSupportSearch.Result.WITNESS) {
                // This witness may use different counts than the dead candidate.
                // It is checked against the immutable order, not accepted as a
                // solution of this branch's incidental count assumptions.
                counts = supportSearch.counts();
                lowerCost = null;
                beginAssembly(supportSearch.witness());
            } else if (status == CountSupportSearch.Result.CLOSED) {
                var cut = supportSearch.cut();
                learnedMaterials.add(cut);
                // The checked closed set rules out the entire old support.
                // All remaining solutions introduce at least one outside source.
                enqueue(current, cut);
                partitioned = true;
                state = State.SPLIT;
            } else {
                partitionCounts();
                state = preprocessingOnly ? State.UNRESOLVED : State.DEAD;
            }
            supportSearch.close();
            supportSearch = null;
            return;
        }
        if (rescue) {
            program = new CountProgram<>(model.recipes, counts, budget);
            return;
        }
        if (partition != null) {
            if (!partition.step()) return;
            counts = reduction.expand(partition.counts());
            partition.close();
            partition = null;
            if (counts != null) {
                if (!preprocessingOnly && refineSupport()) state = State.SPLIT;
                else scheduling = new CountSchedule<>(model, counts, budget);
            } else if (weightedChoices()) matching = new CountMeetInMiddle(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            else binary = new CountBoolean(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            return;
        }
        if (matching != null) {
            if (!matching.step()) return;
            counts = reduction.expand(matching.counts());
            boolean impossible = matching.infeasible();
            matching.close();
            matching = null;
            if (counts != null) {
                if (!preprocessingOnly && refineSupport()) state = State.SPLIT;
                else scheduling = new CountSchedule<>(model, counts, budget);
            } else if (impossible) {
                learnedChoices.add(new CountConflict(current));
                state = State.DEAD;
            } else binary = new CountBoolean(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            return;
        }
        if (binary != null) {
            if (!binary.step()) return;
            counts = reduction.expand(binary.counts());
            boolean impossible = binary.infeasible();
            binary.close();
            binary = null;
            if (counts != null) {
                if (!preprocessingOnly && refineSupport()) state = State.SPLIT;
                else scheduling = new CountSchedule<>(model, counts, budget);
            } else if (impossible) {
                learnedChoices.add(new CountConflict(current));
                state = State.DEAD;
            } else if (preprocessingOnly) state = State.UNRESOLVED;
            else beginLinear();
            return;
        }
        if (repair != null) {
            if (!repair.step()) return;
            counts = reduction.expand(repair.counts());
            repair.close();
            repair = null;
            if (counts != null) {
                repairPoint = null;
                if (refineSupport()) state = State.SPLIT;
                else scheduling = new CountSchedule<>(model, counts, budget);
            } else if (++repairAttempts < 4) {
                beginRepair();
            } else {
                branchPoint(repairPoint);
                repairPoint = null;
            }
            return;
        }
        if (propagating != null) {
            if (!propagating.step()) return;
            boolean blocked = propagating.blocked();
            lower = propagating.lowerBounds();
            upper = propagating.upperBounds();
            BitSet conflict = propagating.conflictingAssumptions();
            var tightened = propagating.tightened();
            usedChoices.addAll(propagating.usedConflicts());
            if (!blocked) sharedBounds = propagating.snapshot();
            propagating.close();
            propagating = null;
            if (blocked) {
                if (conflict != null) {
                    List<ExactLinearProgram.Constraint> used = new ArrayList<>();
                    for (int i = conflict.nextSetBit(0); i >= 0; i = conflict.nextSetBit(i + 1)) used.add(current.get(i));
                    learnedChoices.add(new CountConflict(used));
                }
                state = State.DEAD;
                return;
            }
            linearConstraints.addAll(tightened);
            reduction = new CountReduction(linearConstraints, lower, upper, budget);
            return;
        }
        if (!compiled) {
            if (!reduction.step()) return;
            compiled = true;
            preprocessingOnly = reduction.variables() > 64 || reduction.rows().size() > 512;
            lowerCost = PlanPreference.compiledLowerBound(model, reduction, lower, seeds, budget);
            if (current.isEmpty()) partition = new CountPartition(reduction.rows(), reduction.lower(), reduction.upper(), budget);
            else if (preprocessingOnly) state = State.UNRESOLVED;
            else beginLinear();
            return;
        }
        if (!linear.step()) return;
        var status = linear.result();
        ExactRational[] point = reducedLinear ? reduction.expand(linear.point()) : linear.point();
        if (status == ExactLinearProgram.Result.INFEASIBLE && !reducedLinear && !linear.hot()) learnMaterialConflict(linear.certificate());
        sharedBasis = linear.takeBasis();
        linear.close();
        linear = null;
        if (status == ExactLinearProgram.Result.INFEASIBLE) {
            learnedChoices.add(new CountConflict(current));
            state = State.DEAD;
            return;
        }
        if (status != ExactLinearProgram.Result.OPTIMAL) {
            state = State.UNRESOLVED;
            return;
        }
        ExactRational operations = ExactRational.ZERO;
        for (ExactRational value : point) operations = operations.add(value);
        lowerCost = PlanPreference.lowerBound(model, lower, seeds, operations.ceil(), budget);
        if (refineMaterials(point)) {
            state = State.SPLIT;
            return;
        }
        for (CountGuard conflict : supportConflicts) if (conflict.violated(point, budget)) {
            branch(conflict);
            state = State.SPLIT;
            return;
        }
        if (current.isEmpty() && fractionalChoice(point) >= 0) {
            repairPoint = point;
            beginRepair();
            return;
        }
        branchPoint(point);
    }

    private void beginRepair() {
        ExactRational[] reduced = Arrays.stream(reduction.representatives()).mapToObj(i -> repairPoint[i]).toArray(ExactRational[]::new);
        repair = new CountLatticeRepair(reduction.rows(), reduction.lower(), reduction.upper(), reduced, repairAttempts, budget);
    }

    private void branchPoint(ExactRational[] point) {
        int chosen = fractionalChoice(point);
        if (chosen >= 0) {
            int i = chosen;
            enqueue(current, bound(i, point[i].floor(), false));
            enqueue(current, bound(i, point[i].ceil(), true));
            state = State.SPLIT;
            return;
        }
        counts = Arrays.stream(point).map(ExactRational::numerator).toArray(BigInteger[]::new);
        if (refineSupport()) {
            state = State.SPLIT;
            return;
        }
        scheduling = new CountSchedule<>(model, counts, budget);
    }

    private void beginLinear() {
        // Tiny tableaux are cheap to rebuild. Keep their canonical recipe
        // ordering instead of paying for basis reuse and changing degenerate ties.
        boolean reuse = (model.recipes.size() + 2L) * (model.constraints.size() + 2L) > 256;
        reducedLinear = reuse && reduction.variables() != model.recipes.size();
        linear = new ExactLinearProgram(reducedLinear ? reduction.variables() : model.recipes.size(),
                reducedLinear ? reduction.rows() : linearConstraints,
                reducedLinear ? reduction.objective(model.objective(true)) : model.objective(true), budget,
                reuse && reduction.coordinates().equals(inheritedCoordinates) ? inheritedBasis : null, reuse);
        if (inheritedBasis != null) {
            inheritedBasis.close();
            inheritedBasis = null;
        }
    }

    private boolean weightedChoices() {
        // Cardinality/clause systems are handled directly by Boolean
        // propagation. Building a multidimensional equality index adds no
        // useful structure there; reserve it for genuinely weighted choices.
        for (var row : reduction.rows()) for (var weight : row.terms().values()) {
            budget.check();
            if (weight.abs().compareTo(BigInteger.ONE) > 0) return true;
        }
        return false;
    }

    private int fractionalChoice(ExactRational[] point) {
        int best = -1, bestActivity = -1;
        for (int id : reduction.representatives()) if (!point[id].integral()) {
            int activity = 0;
            int weight = knownChoices.size();
            for (var conflict : knownChoices) {
                for (var row : conflict.assumptions()) {
                    budget.check();
                    if (row.terms().containsKey(id)) activity += weight;
                }
                weight--;
            }
            if (activity > bestActivity) {
                best = id;
                bestActivity = activity;
            }
        }
        return best;
    }

    private boolean rejectsChoices() {
        if (knownChoices.isEmpty()) return false;
        BigInteger[] low = new BigInteger[model.recipes.size()], high = new BigInteger[model.recipes.size()];
        Arrays.fill(low, BigInteger.ZERO);
        // Cheap decision bounds can reuse a learned core before rebuilding
        // propagation or simplex. General assumptions remain in the full model.
        for (var row : current) {
            budget.check();
            if (row.terms().size() != 1) continue;
            var term = row.terms().entrySet().iterator().next();
            int id = term.getKey();
            if (term.getValue().equals(BigInteger.ONE)) high[id] = high[id] == null ? row.upper() : high[id].min(row.upper());
            else if (term.getValue().equals(BigInteger.ONE.negate())) low[id] = low[id].max(row.upper().negate());
        }
        for (var conflict : knownChoices) if (conflict.impliedBy(low, high, budget)) {
            usedChoices.add(conflict);
            return true;
        }
        return false;
    }

    private void beginAssembly(PlanStep witness) {
        Map<String, GraphRecipe<K>> recipes = new LinkedHashMap<>();
        model.recipes.forEach(recipe -> recipes.put(recipe.id(), recipe));
        assembling = new AllocationSearch.Candidate<>(witness, recipes, target, amount, stock, seeds, external,
                preserve, force, false, budget, started);
    }

    private void unresolved() {
        plan = null;
        partitionCounts();
        state = State.UNRESOLVED;
    }

    boolean resume() {
        if (retried || counts == null || memory == 0 || limit != null) return false;
        releaseWorkspace();
        initialized = true;
        retried = rescue = true;
        state = State.OPEN;
        return true;
    }

    void releaseWorkspace() {
        if (repair != null) repair.close();
        repair = null;
        repairPoint = null;
        if (linear != null) linear.close();
        if (propagating != null) propagating.close();
        if (partition != null) partition.close();
        if (matching != null) matching.close();
        if (binary != null) binary.close();
        if (scheduling != null) scheduling.close();
        if (supportSearch != null) supportSearch.close();
        if (program != null) program.close();
        if (assembling != null) assembling.close();
        if (verifying != null) verifying.close();
        linear = null;
        propagating = null;
        partition = null;
        matching = null;
        binary = null;
        scheduling = null;
        supportSearch = null;
        program = null;
        assembling = null;
        verifying = null;
        budget.release(workspace);
        workspace = 0;
    }

    private boolean refineSupport() {
        var point = Arrays.stream(counts).map(ExactRational::of).toArray(ExactRational[]::new);
        if (refineMaterials(point)) return true;
        CountGuard condition = execution.violated(point);
        if (condition != null) {
            learn(condition);
            branch(condition);
            return true;
        }
        Set<K> reachable = new HashSet<>(external);
        stock.forEach((key, value) -> { if (value > 0) reachable.add(key); });
        BitSet fired = new BitSet();
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < counts.length; i++) {
                budget.check();
                if (counts[i].signum() == 0 || fired.get(i)) continue;
                GraphRecipe<K> recipe = model.recipes.get(i);
                if (consumedKeys(recipe).stream().anyMatch(key -> !reachable.contains(key))) continue;
                reachable.addAll(recipe.outputs().keySet());
                fired.set(i);
                changed = true;
            }
        } while (changed);
        for (int blocked = 0; blocked < counts.length; blocked++) if (counts[blocked].signum() > 0 && !fired.get(blocked)) {
            // If this transition is used, a first producer must introduce an
            // initially absent place without itself requiring an absent place.
            // Keep both possibilities: avoid the transition, or include a repair.
            Map<Integer, BigInteger> repairs = new LinkedHashMap<>();
            for (int i = 0; i < counts.length; i++) {
                budget.check();
                GraphRecipe<K> recipe = model.recipes.get(i);
                if (consumedKeys(recipe).stream().allMatch(reachable::contains) &&
                        recipe.outputs().keySet().stream().anyMatch(key -> model.ids.containsKey(key) && !reachable.contains(key)))
                    repairs.put(i, BigInteger.ONE.negate());
            }
            CountGuard conflict = new CountGuard(blocked, new ExactLinearProgram.Constraint(repairs, BigInteger.ONE.negate()));
            learn(conflict);
            branch(conflict);
            return true;
        }
        return false;
    }

    private boolean refineMaterials(ExactRational[] point) {
        // A sibling can certify a new boundary while this LP is suspended.
        // Apply that shared proof before branching on a stale relaxation.
        for (var row : knownMaterials) {
            if (linearConstraints.contains(row)) continue;
            ExactRational sum = ExactRational.ZERO;
            for (var term : row.terms().entrySet()) {
                budget.check();
                sum = sum.add(point[term.getKey()].multiply(ExactRational.of(term.getValue())));
            }
            if (sum.compareTo(ExactRational.of(row.upper())) > 0) {
                enqueue(current, row);
                return true;
            }
        }
        return false;
    }

    private Set<K> consumedKeys(GraphRecipe<K> recipe) {
        Set<K> keys = new LinkedHashSet<>(recipe.inputs().keySet());
        keys.removeIf(key -> recipe.inputs().get(key).longValue() == recipe.configurationInputs().getOrDefault(key, 0L));
        return keys;
    }

    private void partitionCounts() {
        if (partitioned) return;
        partitioned = true;
        // Large models get optional arithmetic preprocessing, not an enlarged
        // general branch tree. An undecided candidate remains UNKNOWN.
        if (preprocessingOnly) return;
        // Disjoint lexicographic siblings cover every OTHER integer vector.
        // An undecided fixed vector stays owned by this retained branch.
        var prefix = new ArrayList<>(current);
        for (int i : reduction.representatives()) {
            if (counts[i].compareTo(lower[i]) > 0) enqueue(prefix, bound(i, counts[i].subtract(BigInteger.ONE), false));
            if (upper[i] == null || counts[i].compareTo(upper[i]) < 0) enqueue(prefix, bound(i, counts[i].add(BigInteger.ONE), true));
            prefix.add(bound(i, counts[i], false));
            prefix.add(bound(i, counts[i], true));
        }
    }

    private void learn(CountGuard condition) {
        if (!supportConflicts.contains(condition)) supportConflicts.add(condition);
        learnedChoices.add(condition.conflict());
    }

    private void branch(CountGuard conflict) {
        enqueue(current, bound(conflict.recipe(), BigInteger.ZERO, false));
        if (!conflict.required().terms().isEmpty()) {
            var required = new ArrayList<>(current);
            required.add(bound(conflict.recipe(), BigInteger.ONE, true));
            enqueue(required, conflict.required());
        }
    }

    private void learnMaterialConflict(ExactRational[] proof) {
        if (learnedMaterials.size() >= 64) return;
        // Combine only globally valid material rows. The other Farkas terms
        // describe this branch; dropping those terms leaves a reusable resource
        // inequality that is valid for all branches of THIS inventory snapshot.
        BigInteger scale = BigInteger.ONE;
        for (int i = 0; i < globalRows; i++) {
            scale = scale.divide(scale.gcd(proof[i].denominator())).multiply(proof[i].denominator());
            if (scale.bitLength() > 2048) return;
        }
        Map<Integer, BigInteger> terms = new LinkedHashMap<>();
        BigInteger upper = BigInteger.ZERO;
        for (int i = 0; i < globalRows; i++) {
            BigInteger weight = proof[i].numerator().multiply(scale.divide(proof[i].denominator()));
            if (weight.signum() < 0) return;
            if (weight.signum() == 0) continue;
            upper = upper.add(linearConstraints.get(i).upper().multiply(weight));
            for (var term : linearConstraints.get(i).terms().entrySet()) {
                budget.check();
                terms.merge(term.getKey(), term.getValue().multiply(weight), BigInteger::add);
            }
        }
        terms.values().removeIf(value -> value.signum() == 0);
        if (terms.isEmpty()) return;
        BigInteger common = upper.abs();
        for (BigInteger coefficient : terms.values()) common = common.gcd(coefficient);
        BigInteger divisor = common;
        if (divisor.signum() > 0) {
            terms.replaceAll((key, value) -> value.divide(divisor));
            upper = upper.divide(divisor);
        }
        if (upper.bitLength() > 2048 || terms.values().stream().anyMatch(value -> value.bitLength() > 2048)) return;
        learnedMaterials.add(new ExactLinearProgram.Constraint(terms, upper));
    }

    private void enqueue(List<ExactLinearProgram.Constraint> prefix, ExactLinearProgram.Constraint constraint) {
        var next = new ArrayList<>(prefix);
        next.add(constraint);
        children.add(List.copyOf(next));
    }

    private static ExactLinearProgram.Constraint bound(int variable, BigInteger value, boolean lower) {
        return new ExactLinearProgram.Constraint(Map.of(variable, lower ? BigInteger.ONE.negate() : BigInteger.ONE), lower ? value.negate() : value);
    }

    @Override
    public void close() {
        releaseWorkspace();
        if (inheritedBounds != null) {
            inheritedBounds.close();
            inheritedBounds = null;
        }
        if (sharedBounds != null) {
            sharedBounds.close();
            sharedBounds = null;
        }
        if (inheritedBasis != null) {
            inheritedBasis.close();
            inheritedBasis = null;
        }
        if (sharedBasis != null) {
            sharedBasis.close();
            sharedBasis = null;
        }
        if (reduction != null) {
            reduction.close();
            reduction = null;
        }
        budget.release(memory);
        memory = 0;
    }
}
