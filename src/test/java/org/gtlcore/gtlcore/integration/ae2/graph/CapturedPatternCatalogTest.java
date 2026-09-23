package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.GraphRecipe;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningScheduler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class CapturedPatternCatalogTest {

    static void run() throws Exception {
        var owner = Thread.currentThread();
        boolean[] callbacksAllowed = { true };
        var input = AEItemKey.of(Items.PAPER);
        var output = AEItemKey.of(Items.BOOK);
        var data = new CompoundTag();
        var in = new ListTag();
        var out = new ListTag();
        in.add(GenericStack.writeTag(new GenericStack(input, 3_000_000_000L)));
        out.add(GenericStack.writeTag(new GenericStack(output, 2)));
        data.put("in", in);
        data.put("out", out);
        var actual = new AEProcessingPattern(AEItemKey.of(Items.PAPER, data));
        IPatternDetails guarded = new IPatternDetails() {

            private void guard() {
                if (!callbacksAllowed[0] || Thread.currentThread() != owner)
                    throw new AssertionError("Worker called a live pattern method");
            }

            @Override
            public AEItemKey getDefinition() {
                guard();
                return actual.getDefinition();
            }

            @Override
            public IInput[] getInputs() {
                guard();
                return actual.getInputs();
            }

            @Override
            public GenericStack[] getOutputs() {
                guard();
                return actual.getOutputs();
            }

            @Override
            public boolean supportsPushInputsToExternalInventory() {
                guard();
                return true;
            }
        };
        String binding = PatternFingerprint.of(guarded);
        var values = PatternFingerprint.capture(guarded);
        if (!new PatternFingerprint.Context().of(values).equals(binding)) throw new AssertionError("Detached fingerprint differs");
        var normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class, String.class, KeyCounter.class, Level.class);
        normalize.setAccessible(true);
        @SuppressWarnings("unchecked")
        var originals = (List<GraphRecipe<AEKey>>) normalize.invoke(null, guarded, binding, new KeyCounter(), null);
        var variants = new ArrayList<CapturedPatternCatalog.Recipe>();
        for (var original : originals) variants.add(new CapturedPatternCatalog.Recipe(original.slots(), original.outputs()));
        var entry = new CapturedPatternCatalog.Entry(guarded, values, 10, variants);
        var entries = new ArrayList<>(List.of(entry, entry));
        var catalog = new CapturedPatternCatalog(entries, 2);
        entries.clear();
        variants.clear();
        actual.getOutputs()[0] = new GenericStack(output, 999);
        if (PatternFingerprint.capture(guarded).equals(values)) throw new AssertionError("Provider signature ignored effective output mutation");
        callbacksAllowed[0] = false;
        if (!new PatternFingerprint.Context().of(values).equals(binding)) throw new AssertionError("Snapshot retained mutable output array");
        try (var scheduler = new PlanningScheduler(2, 8, 1, 100_000L)) {
            var limited = new PlanningBudget(0, 1, () -> false);
            try {
                scheduler.submit(catalog.build(limited), limited).get(5, TimeUnit.SECONDS);
                throw new AssertionError("Capture encoding ignored work limit");
            } catch (java.util.concurrent.ExecutionException expected) {
                if (!(expected.getCause() instanceof PlanningBudget.Exhausted)) throw expected;
            }
            var firstBudget = new PlanningBudget(0, 10000, () -> false);
            var secondBudget = new PlanningBudget(0, 10000, () -> false);
            var first = scheduler.submit(catalog.build(firstBudget), firstBudget);
            var second = scheduler.submit(catalog.build(secondBudget), secondBudget);
            var prepared = first.get(5, TimeUnit.SECONDS);
            if (prepared != second.get(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent capture builders published different catalogs");
            if (prepared.bindings().get(binding) != guarded) throw new AssertionError("Original provider handle lost");
            if (!prepared.compiler().producers(output).equals(originals)) throw new AssertionError("Detached catalog changed IDs, amounts or deduplication");
            if (!catalog.mayContainBinding(binding) || catalog.mayContainBinding("absent")) throw new AssertionError("Binding invalidation lookup changed");
        }
        System.out.println("Deferred capture: no worker pattern callbacks; mutation, exact IDs/long amounts, deduplication, work limit and concurrent publication passed");
    }
}
