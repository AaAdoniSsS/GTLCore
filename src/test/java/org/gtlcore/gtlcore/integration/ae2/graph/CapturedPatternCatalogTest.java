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
                return java.util.Arrays.stream(actual.getInputs()).map(input -> new IInput() {

                    @Override
                    public GenericStack[] getPossibleInputs() {
                        guard();
                        return input.getPossibleInputs();
                    }

                    @Override
                    public long getMultiplier() {
                        guard();
                        return input.getMultiplier();
                    }

                    @Override
                    public boolean isValid(AEKey key, Level level) {
                        guard();
                        return input.isValid(key, level);
                    }

                    @Override
                    public AEKey getRemainingKey(AEKey key) {
                        guard();
                        return input.getRemainingKey(key);
                    }
                }).toArray(IInput[]::new);
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
        var capture = new GtlPatternCatalog.CandidateCapture(guarded, values, new KeyCounter(), null, new PlanningBudget(0, 10000, () -> false));
        while (!capture.step()) {}
        var entry = new CapturedPatternCatalog.Entry(guarded, values, 10, capture.result());
        var entries = new ArrayList<>(List.of(entry, entry));
        var catalog = new CapturedPatternCatalog(entries, 2);
        entries.clear();
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
        // Large enough to enter the parallel encoder, including duplicate
        // bindings and simultaneous builders. Single-worker output is the oracle.
        var many = new ArrayList<CapturedPatternCatalog.Entry>();
        for (int i = 0; i < 1024; i++) {
            var identity = new CompoundTag();
            identity.putInt("catalog_order", i / 2);
            var distinct = new PatternFingerprint.Values(values.type(), AEItemKey.of(Items.PAPER, identity), values.external(), values.inputs(), values.outputs());
            many.add(new CapturedPatternCatalog.Entry(guarded, distinct, i % 7, capture.result()));
        }
        var serialCatalog = new CapturedPatternCatalog(many, many.size());
        var parallelCatalog = new CapturedPatternCatalog(many, many.size());
        try (var serial = new PlanningScheduler(1, 4, 32, 100_000L);
                var parallel = new PlanningScheduler(4, 4, 32, 100_000L)) {
            var sb = new PlanningBudget(0, 100_000, () -> false);
            var pb = new PlanningBudget(0, 100_000, () -> false);
            var work = parallelCatalog.build(pb);
            var expected = serial.submit(serialCatalog.build(sb), sb).get(10, TimeUnit.SECONDS);
            var concurrent = parallel.submit(work, pb);
            var otherBudget = new PlanningBudget(0, 100_000, () -> false);
            var otherWork = parallelCatalog.build(otherBudget);
            var other = parallel.submit(otherWork, otherBudget);
            var result = concurrent.get(10, TimeUnit.SECONDS);
            if (other.get(10, TimeUnit.SECONDS) != result || !result.compiler().producers(output).equals(expected.compiler().producers(output)) ||
                    !result.bindings().equals(expected.bindings()) || work.parallelBatches() + otherWork.parallelBatches() == 0)
                throw new AssertionError("Parallel expansion changed ordered catalog publication");
            var refusedCatalog = new CapturedPatternCatalog(many, many.size());
            var memory = new PlanningBudget(0, 100_000, 1024, () -> false, System::nanoTime);
            try {
                parallel.submit(refusedCatalog.build(memory), memory).get(10, TimeUnit.SECONDS);
                throw new AssertionError("Parallel expansion ignored shared memory budget");
            } catch (java.util.concurrent.ExecutionException expectedFailure) {
                if (!(expectedFailure.getCause() instanceof PlanningBudget.Exhausted)) throw expectedFailure;
            }
            var retry = new PlanningBudget(0, 100_000, () -> false);
            if (!parallel.submit(refusedCatalog.build(retry), retry).get(10, TimeUnit.SECONDS).compiler().producers(output).equals(expected.compiler().producers(output)))
                throw new AssertionError("Failed parallel wave published an incomplete cache");
            var cancelledCatalog = new CapturedPatternCatalog(many, many.size());
            var checks = new java.util.concurrent.atomic.AtomicInteger();
            var cancelled = new PlanningBudget(0, 100_000, () -> checks.incrementAndGet() > 80);
            try {
                parallel.submit(cancelledCatalog.build(cancelled), cancelled).get(10, TimeUnit.SECONDS);
                throw new AssertionError("Parallel expansion ignored cancellation");
            } catch (java.util.concurrent.CancellationException expectedCancellation) {
                // Cancellation reaches workers without waiting on the server tick.
            } catch (java.util.concurrent.ExecutionException expectedCancellation) {
                if (!(expectedCancellation.getCause() instanceof java.util.concurrent.CancellationException)) throw expectedCancellation;
            }
            var fresh = new PlanningBudget(0, 100_000, () -> false);
            if (!parallel.submit(cancelledCatalog.build(fresh), fresh).get(10, TimeUnit.SECONDS).compiler().producers(output).equals(expected.compiler().producers(output)))
                throw new AssertionError("Cancelled expansion contaminated cached data");
        }
        System.out.println("Deferred capture: no worker pattern callbacks; mutation, exact IDs/long amounts, deduplication, work limit and concurrent publication passed");
    }
}
