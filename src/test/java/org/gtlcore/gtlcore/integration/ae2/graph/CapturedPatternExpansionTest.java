package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphRecipe;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningScheduler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.*;

/** Differential oracle retains the pre-split Cartesian/zero-branch DFS algorithm. */
final class CapturedPatternExpansionTest {

    static void run() throws Exception {
        int cases = 0;
        for (boolean external : new boolean[] { false, true }) {
            for (int candidates : new int[] { 1, 2, 3, 4, 8, 16, 255, 256 }) {
                for (int copies : new int[] { 1, 2, 3, 9, 10 }) {
                    compare(List.of(input(0, candidates, copies)), external);
                    compare(List.of(input(0, 2, 3), input(1, candidates, copies), input(2, 3, 2)), external);
                    cases += 2;
                }
            }
        }
        Random random = new Random(728901);
        for (int test = 0; test < 200; test++) {
            List<CapturedPattern.Input> inputs = new ArrayList<>();
            for (int slot = 0, count = random.nextInt(6); slot < count; slot++)
                inputs.add(input(slot, 1 + random.nextInt(8), 1 + random.nextInt(9)));
            compare(inputs, random.nextBoolean());
            cases++;
        }
        compare(List.of(), false);
        compare(List.of(input(0, 2, 3), input(1, 0, 1)), false);
        captureCallbacks();
        System.out.println("Detached combinations: " + (cases + 2) + " legacy differential fixtures passed (ordered variants/dependencies, remainders, long counts, caps)");
    }

    private static void captureCallbacks() throws Exception {
        var owner = Thread.currentThread();
        boolean[] frozen = { false };
        int[] remainders = { 0 };
        var variants = input(0, 3, 2).candidates();
        AEKey a = variants.get(0).stack().what(), b = variants.get(1).stack().what(), rejected = variants.get(2).stack().what();
        var book = AEItemKey.of(Items.BOOK);
        IPatternDetails pattern = new IPatternDetails() {

            private void guard() {
                if (frozen[0] || Thread.currentThread() != owner) throw new AssertionError("Live fuzzy callback escaped capture");
            }

            @Override
            public AEItemKey getDefinition() {
                guard();
                return AEItemKey.of(Items.PAPER);
            }

            @Override
            public GenericStack[] getOutputs() {
                guard();
                return new GenericStack[] { new GenericStack(book, 1) };
            }

            @Override
            public boolean supportsPushInputsToExternalInventory() {
                guard();
                return false;
            }

            @Override
            public IInput[] getInputs() {
                guard();
                return new IInput[] { new IInput() {

                    @Override
                    public GenericStack[] getPossibleInputs() {
                        guard();
                        return new GenericStack[] { new GenericStack(a, 3_000_000_000L) };
                    }

                    @Override
                    public long getMultiplier() {
                        guard();
                        return 2;
                    }

                    @Override
                    public boolean isValid(AEKey key, Level level) {
                        guard();
                        return key.equals(a) || key.equals(b);
                    }

                    @Override
                    public AEKey getRemainingKey(AEKey key) {
                        guard();
                        remainders[0]++;
                        return key.equals(a) ? AEItemKey.of(Items.BUCKET) : AEItemKey.of(Items.GLASS_BOTTLE);
                    }
                } };
            }
        };
        var inventory = new KeyCounter();
        inventory.add(b, 10);
        inventory.add(rejected, 10);
        var values = PatternFingerprint.capture(pattern);
        var budget = new PlanningBudget(0, 10_000, () -> false);
        var capture = new GtlPatternCatalog.CandidateCapture(pattern, values, inventory, null, budget);
        while (!capture.step()) {}
        if (remainders[0] != 2) throw new AssertionError("Remainders not captured once per valid candidate");
        var catalog = new CapturedPatternCatalog(List.of(new CapturedPatternCatalog.Entry(pattern, values, 0, capture.result())), 3);
        frozen[0] = true;
        inventory.clear();
        try (var workers = new PlanningScheduler(2, 4, 8, 100_000L)) {
            var recipes = workers.submit(catalog.build(budget), budget).get(5, java.util.concurrent.TimeUnit.SECONDS).compiler().producers(book);
            var mixed = recipes.stream().filter(recipe -> recipe.inputs().equals(Map.of(a, 3_000_000_000L, b, 3_000_000_000L))).findFirst().orElseThrow();
            if (recipes.size() != 3 || mixed.outputs().get(AEItemKey.of(Items.BUCKET)) != 1 ||
                    mixed.outputs().get(AEItemKey.of(Items.GLASS_BOTTLE)) != 1 || recipes.stream().anyMatch(recipe -> recipe.inputs().containsKey(rejected)))
                throw new AssertionError("Frozen fuzzy capture changed validity, quantities or return containers");
        }
    }

    private static CapturedPattern.Input input(int slot, int count, long multiplier) {
        List<CapturedPattern.Candidate> choices = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            var tag = new CompoundTag();
            tag.putInt("slot", slot);
            tag.putInt("candidate", index);
            choices.add(new CapturedPattern.Candidate(new GenericStack(AEItemKey.of(Items.PAPER, tag), 3_000_000_000L + index),
                    index % 2 == 0 ? AEItemKey.of(Items.BUCKET) : AEItemKey.of(Items.GLASS_BOTTLE), slot == 1));
        }
        return new CapturedPattern.Input(multiplier, choices);
    }

    private static void compare(List<CapturedPattern.Input> inputs, boolean external) {
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.BOOK), 2), new GenericStack(AEItemKey.of(Items.BUCKET), 3));
        var reference = legacy(inputs, outputs, external);
        var captured = new CapturedPattern(inputs, outputs, external, false);
        var budget = new PlanningBudget(0, 10_000_000, () -> false);
        var expansion = captured.expand(budget);
        List<CapturedPatternCatalog.Recipe> actual = new ArrayList<>();
        while (expansion.hasNext()) actual.add(expansion.next());
        if (!actual.equals(reference.recipes()) || captured.bounded() != reference.bounded() || captured.size() != actual.size())
            throw new AssertionError("Detached Cartesian expansion differs from old matcher: inputs=" + inputs.size() + " external=" + external);
        Set<AEKey> expectedKeys = new LinkedHashSet<>(), capturedKeys = new LinkedHashSet<>();
        for (var recipe : reference.recipes()) for (var slot : recipe.slots()) expectedKeys.add(slot.key());
        captured.dependencies().forEachRemaining(capturedKeys::add);
        if (!new ArrayList<>(expectedKeys).equals(new ArrayList<>(capturedKeys))) throw new AssertionError("Discovery order or truncated alternatives changed");
    }

    private record Picked(CapturedPattern.Candidate candidate, long copies) {}

    private record Reference(List<CapturedPatternCatalog.Recipe> recipes, boolean bounded) {}

    private static Reference legacy(List<CapturedPattern.Input> inputs, List<GenericStack> outputs, boolean external) {
        List<List<List<Picked>>> choices = new ArrayList<>();
        boolean bounded = false;
        for (var input : inputs) {
            if (input.candidates().isEmpty()) return new Reference(List.of(), bounded);
            List<List<Picked>> selections = new ArrayList<>();
            for (var candidate : input.candidates()) selections.add(List.of(new Picked(candidate, input.multiplier())));
            if (!external && input.multiplier() <= 9 && input.candidates().size() > 1) {
                mixed(input.candidates(), 0, input.multiplier(), new ArrayList<>(), selections);
                bounded |= selections.size() >= 256;
            }
            choices.add(selections);
        }
        int[] indices = new int[inputs.size()];
        List<CapturedPatternCatalog.Recipe> recipes = new ArrayList<>();
        while (true) {
            List<GraphRecipe.Slot<AEKey>> slots = new ArrayList<>();
            Map<AEKey, Long> produced = new LinkedHashMap<>();
            for (var output : outputs) produced.merge(output.what(), output.amount(), Math::addExact);
            for (int slot = 0; slot < choices.size(); slot++) for (var picked : choices.get(slot).get(indices[slot])) {
                var candidate = picked.candidate();
                slots.add(new GraphRecipe.Slot<>(candidate.stack().what(), Math.multiplyExact(candidate.stack().amount(), picked.copies()), slot, candidate.configuration()));
                if (candidate.remaining() != null) produced.merge(candidate.remaining(), picked.copies(), Math::addExact);
            }
            recipes.add(new CapturedPatternCatalog.Recipe(slots, produced));
            int at = indices.length - 1;
            while (at >= 0 && ++indices[at] == choices.get(at).size()) {
                indices[at] = 0;
                at--;
            }
            if (at < 0) break;
            if (recipes.size() >= 256) {
                bounded = true;
                break;
            }
        }
        return new Reference(recipes, bounded);
    }

    private static void mixed(List<CapturedPattern.Candidate> candidates, int at, long left, List<Picked> selected,
                              List<List<Picked>> out) {
        if (out.size() >= 256) return;
        if (left == 0) {
            if (selected.size() > 1) out.add(List.copyOf(selected));
            return;
        }
        if (at >= candidates.size()) return;
        for (long count = left; count >= 0 && out.size() < 256; count--) {
            if (count > 0) selected.add(new Picked(candidates.get(at), count));
            mixed(candidates, at + 1, left - count, selected, out);
            if (count > 0) selected.remove(selected.size() - 1);
        }
    }
}
