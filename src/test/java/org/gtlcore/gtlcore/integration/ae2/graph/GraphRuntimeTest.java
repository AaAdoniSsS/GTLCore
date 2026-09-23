package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import java.util.*;

public final class GraphRuntimeTest {

    private static int checks;

    public static void run() {
        batchAndReload();
        rejectionAndUnknown();
        synchronousAndGrowth();
        partialAndCancellation();
        nestedAndZero();
        independentDag();
        cyclicBatchParallelism();
        fullCatalystAccount();
        handoffSnapshot();
        oldReturnDuringRejection();
        emittedInput();
        settlementFaults();
        refundFairness();
        recoveryOwnership();
        configurationBatches();
        closedWaitProof();
        replaceUncommittedSuffix();
        System.out.println("Graph runtime: " + checks + " assertions passed");
    }

    private static void batchAndReload() {
        var recipe = recipe("p", Map.of("R", 1L), Map.of("P", 1L));
        var plan = plan(List.of(recipe), "P", 100, Map.of("R", 100L));
        Fake fake = new Fake(plan);
        fake.limit = 5;
        fake.runtime.tick(fake, 0, 1);
        eq(5L, fake.accepted, "E02 actual accepted batch");
        eq(95L, fake.runtime.pendingRuns().get("p"), "E02 remaining");
        eq(5L, fake.runtime.waiting("P"), "E02 expected");
        var state = fake.runtime.snapshot();
        for (int i = 0; i < 30; i++) eq(3L, fake.runtime.accept("P", 3, true), "R07 simulate accepts");
        eq(state, fake.runtime.snapshot(), "R07 simulation has no changes");
        fake.runtime = new GraphJobRuntime<>(state);
        fake.limit = 0;
        fake.runtime.tick(fake, 10, 100);
        eq(5L, fake.accepted, "R02 no double dispatch after reload");
        fake.returnAll();
        fake.limit = Long.MAX_VALUE;
        fake.complete();
        eq(100L, fake.delivered.get("P"), "E15 delivery");
        eq(2L, fake.pushes, "E01 batch path");
        eq(GraphJobRuntime.State.COMPLETED, fake.runtime.state(), "completed");
    }

    private static void rejectionAndUnknown() {
        var p = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L))), "P", 100, Map.of("R", 100L));
        Fake fake = new Fake(p);
        fake.outcome = GraphJobRuntime.Outcome.REJECTED;
        fake.runtime.tick(fake, 0, 1);
        eq(Map.of("R", 100L), fake.runtime.owned(), "E03 rejected escrow restored");
        eq(100L, fake.runtime.pendingRuns().get("p"), "E03 not dispatched");
        eq(Map.of(), fake.runtime.expected(), "E03 no fake production");
        fake.runtime.tick(fake, 1, 10);
        eq(1L, fake.pushes, "E11 bounded retry");
        fake.outcome = GraphJobRuntime.Outcome.ACCEPTED;
        fake.runtime.tick(fake, 5, 10);
        eq(2L, fake.pushes, "E11 idle eventually retried");
        Fake unknown = new Fake(p);
        unknown.throwAfterTaking = true;
        unknown.runtime.tick(unknown, 0, 1);
        eq(GraphJobRuntime.State.NEEDS_ATTENTION, unknown.runtime.state(), "R08 exception pauses");
        eq(Map.of(), unknown.runtime.owned(), "R08 no phantom refund");
        unknown.runtime = new GraphJobRuntime<>(unknown.runtime.snapshot());
        unknown.runtime.tick(unknown, 1000, 1000);
        eq(1L, unknown.pushes, "R08 unknown dispatch not repeated after reload");
    }

    private static void synchronousAndGrowth() {
        var p = plan(List.of(recipe("grow", Map.of("A", 1L, "R", 1L), Map.of("A", 2L))),
                "A", 100, Map.of("A", 1L, "R", 100L));
        Fake fake = new Fake(p);
        fake.synchronous = true;
        fake.complete();
        eq(List.of(1L, 2L, 4L, 8L, 16L, 32L, 37L), fake.batches, "growth batch schedule");
        eq(100L, fake.delivered.get("A"), "E10 target not delivered as seed");
        eq(1L, fake.refunded.get("A"), "preserved restart seed");
        eq(7L, fake.pushes, "E08 synchronous callback does not lose progress");

        Fake inconsistent = new Fake(p);
        inconsistent.synchronous = true;
        inconsistent.outcome = GraphJobRuntime.Outcome.REJECTED;
        inconsistent.runtime.tick(inconsistent, 0, 1);
        eq(GraphJobRuntime.State.NEEDS_ATTENTION, inconsistent.runtime.state(), "false with callback is ambiguous");
        eq(2L, inconsistent.runtime.owned().get("A"), "actual callback owned, spent seed not restored");
    }

    private static void partialAndCancellation() {
        var p = plan(List.of(recipe("c", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "P", 1L))),
                "P", 100, Map.of("C", 8L, "R", 100L));
        Fake fake = new Fake(p);
        fake.runtime.tick(fake, 0, 1);
        eq(8L, fake.batches.get(0), "C07 eight real catalysts bound the batch");
        eq(1L, fake.runtime.accept("P", 1, false), "E09 partial primary return");
        fake.runtime.tick(fake, 10, 1);
        eq(1L, fake.pushes, "E09 must wait for catalyst");
        eq(Map.of(), fake.delivered, "no early external delivery");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.runtime.cancel();
        fake.refundLimit = 0;
        fake.runtime.tick(fake, 11, 64);
        eq(GraphJobRuntime.State.CANCELLING, fake.runtime.state(), "E14 retains full-network refund");
        eq(0L, fake.runtime.accept("C", 1, false), "late output bypasses cancelled task");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.refundLimit = Long.MAX_VALUE;
        fake.runtime.tick(fake, 12, 64);
        eq(92L, fake.refunded.get("R"), "only actually held unspent R refunded");
        eq(null, fake.refunded.get("C"), "no refund of in-flight catalyst");
        eq(1L, fake.refunded.get("P"), "actual partial output refunded");
        eq(GraphJobRuntime.State.CANCELLED, fake.runtime.state(), "cancelled after settlement");
    }

    private static void nestedAndZero() {
        var r = recipe("p", Map.of("R", 1L), Map.of("P", 1L));
        var steps = new PlanStep.Sequence(List.of(new PlanStep.Repeat(new PlanStep.Sequence(List.of()), 1_000_000_000_000L),
                new PlanStep.Repeat(new PlanStep.Sequence(List.of(new PlanStep.Batch("p", 2), new PlanStep.Repeat(new PlanStep.Batch("p", 1), 3))), 2)));
        var p = new GraphPlan<>("P", 10, false, steps, Map.of("p", r), Map.of("R", 10L), Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        Fake fake = new Fake(p);
        fake.limit = 1;
        for (int tick = 0; tick < 100 && !fake.runtime.finished(); tick++) {
            fake.runtime.tick(fake, tick, 1);
            fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
            fake.returnAll();
        }
        eq(10L, fake.accepted, "nested repeat cursor across every reload");
        eq(10L, fake.delivered.get("P"), "nested repeat delivery");
    }

    private static void independentDag() {
        var p = plan(List.of(recipe("a", Map.of("R", 1L), Map.of("A", 1L)),
                recipe("b", Map.of("S", 1L), Map.of("B", 1L)),
                recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L))), "P", 100, Map.of("R", 100L, "S", 100L));
        Fake fake = new Fake(p);
        fake.runtime.tick(fake, 0, 100);
        eq(2L, fake.pushes, "independent DAG branches dispatch together before outputs arrive");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.runtime.accept("A", 100, false);
        fake.runtime.tick(fake, 5, 100);
        eq(2L, fake.pushes, "DAG cannot spend predicted B");
        fake.runtime.accept("B", 100, false);
        fake.complete();
        eq(3L, fake.pushes, "DAG successor wakes on actual input");
        eq(100L, fake.delivered.get("P"), "DAG final delivery");
    }

    private static void fullCatalystAccount() {
        var recipe = recipe("c", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "P", 1L));
        var p = new GraphPlan<>("P", 1, true, new PlanStep.Sequence(List.of(new PlanStep.Batch("c", 1))),
                Map.of("c", recipe), Map.of("C", Long.MAX_VALUE, "R", 1L), Map.of("C", 1L), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        Fake fake = new Fake(p);
        fake.runtime.tick(fake, 0, 1);
        eq(1L, fake.pushes, "returning catalyst at full long ledger can still dispatch");
        fake.returnAll();
        eq(Long.MAX_VALUE, fake.runtime.held("C"), "catalyst debit precedes credit without overflow");
    }

    private static void cyclicBatchParallelism() {
        var unpack = recipe("unpack", Map.of("block", 1L), Map.of("D", 9L));
        var grow = recipe("grow", Map.of("C", 1L, "D", 1L), Map.of("C", 2L));
        // An ordinary prefix belongs to a cyclic plan: it must still fill several assemblers.
        var p = new GraphPlan<>("C", 8, true, new PlanStep.Sequence(List.of(
                new PlanStep.Batch("unpack", 4), new PlanStep.Batch("grow", 8))),
                Map.of("unpack", unpack, "grow", grow), Map.of("block", 4L, "C", 4L),
                Map.of("C", 4L), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        Fake fake = new Fake(p);
        fake.limit = 1;
        fake.runtime.tick(fake, 0, 2);
        eq(2L, fake.pushes, "cyclic plan ordinary prefix uses CPU work allowance before returns");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.runtime.tick(fake, 1, 8);
        eq(4L, fake.pushes, "partial serial batch keeps parallel window after reload");
        fake.runtime.accept("D", 9, false);
        fake.runtime.tick(fake, 2, 8);
        eq(4L, fake.pushes, "partial previous stage return does not release a different recipe");
        fake.returnAll();
        fake.runtime.tick(fake, 3, 8);
        eq(8L, fake.pushes, "four physically held catalysts fill four separate machines");
        eq(0L, fake.runtime.held("C"), "in-flight catalysts cannot be reused");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.runtime.tick(fake, 10, 8);
        eq(8L, fake.pushes, "reload cannot turn expected catalysts into inventory");
        fake.runtime.accept("C", 2, false);
        fake.runtime.tick(fake, 11, 8);
        eq(10L, fake.pushes, "partial real return immediately permits two new copies");
        fake.complete();
        eq(8L, fake.delivered.get("C"), "parallel cyclic batches preserve delivery");
        eq(4L, fake.refunded.get("C"), "parallel cyclic batches preserve restart catalysts");

        var cancelled = new Fake(p);
        cancelled.limit = 1;
        cancelled.runtime.tick(cancelled, 0, 4);
        cancelled.runtime.cancel();
        cancelled.runtime.tick(cancelled, 1, 64);
        eq(4L, cancelled.refunded.get("C"), "cancel refunds only unspent catalysts");
        eq(null, cancelled.refunded.get("block"), "cancel cannot duplicate four dispatched inputs");
    }

    private static void handoffSnapshot() {
        var p = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L))), "P", 10, Map.of("R", 10L));
        Fake fake = new Fake(p);
        fake.saveDuringPush = true;
        fake.synchronous = true;
        fake.runtime.tick(fake, 0, 1);
        eq(GraphJobRuntime.State.NEEDS_ATTENTION, fake.duringPush.state(), "R09 save in handoff is ambiguous");
        eq(Map.of("R", 10L), fake.duringPush.uncertainInputs(), "R09 escrow is not owned");
        eq(Map.of("P", 10L), fake.duringPush.owned(), "R09 synchronous returns are actual inventory");
        fake.runtime = new GraphJobRuntime<>(fake.duringPush);
        fake.runtime.tick(fake, 100, 100);
        eq(1L, fake.pushes, "R09 never automatically replay interrupted handoff");
        fake.runtime.cancel();
        fake.refundLimit = 0;
        fake.runtime.tick(fake, 101, 100);
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        eq(GraphJobRuntime.State.CANCELLING, fake.runtime.state(), "R04 cancelled ambiguous batch can settle after reload");
        fake.refundLimit = Long.MAX_VALUE;
        fake.runtime.tick(fake, 102, 100);
        eq(Map.of("P", 10L), fake.refunded, "R09 refund real returned output only");
        eq(GraphJobRuntime.State.CANCELLED, fake.runtime.state(), "R09 cancelled ambiguous task completes cleanup");
    }

    private static void oldReturnDuringRejection() {
        var p = plan(List.of(recipe("a", Map.of("R", 1L), Map.of("A", 1L)),
                recipe("b", Map.of("S", 1L), Map.of("B", 1L)),
                recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L))), "P", 10, Map.of("R", 10L, "S", 10L));
        Fake fake = new Fake(p);
        fake.runtime.tick(fake, 0, 1);
        Map<String, Long> old = fake.runtime.expected();
        fake.beforeReturn = () -> old.forEach((key, amount) -> fake.runtime.accept(key, amount, false));
        fake.outcome = GraphJobRuntime.Outcome.REJECTED;
        fake.runtime.tick(fake, 1, 1);
        eq(GraphJobRuntime.State.RUNNING, fake.runtime.state(), "E08 old DAG return during rejected new push is unambiguous");
        eq(Map.of(), fake.runtime.expected(), "E08 old obligations cleared");
        old.forEach((key, amount) -> eq(amount, fake.runtime.held(key), "E08 old return retained"));
        fake.beforeReturn = null;
        fake.outcome = GraphJobRuntime.Outcome.ACCEPTED;
        fake.complete();
        eq(10L, fake.delivered.get("P"), "DAG completes after independent rejection");
    }

    private static void emittedInput() {
        var p = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L))), "P", 10, Map.of("R", 10L));
        Fake fake = new Fake(p);
        fake.runtime = new GraphJobRuntime<>(p, Map.of(), Map.of("R", 10L));
        fake.runtime.tick(fake, 0, 100);
        eq(0L, fake.pushes, "E16 predicted emission is not owned input");
        long checksBefore = fake.runtime.checks();
        for (int tick = 1; tick < 100; tick++) fake.runtime.tick(fake, tick, 100);
        eq(checksBefore + 99, fake.runtime.checks(), "input-blocked DAG only polls its empty queue, not every blocked recipe");
        fake.runtime.accept("R", 10, false);
        fake.complete();
        eq(10L, fake.delivered.get("P"), "E16 actual emitted input wakes dispatch");
    }

    private static void settlementFaults() {
        var p = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L))), "P", 10, Map.of("R", 10L));
        Fake fake = new Fake(p);
        fake.synchronous = true;
        fake.saveDuringDelivery = true;
        fake.runtime.tick(fake, 0, 100);
        fake.runtime.tick(fake, 1, 100);
        eq(Map.of(), fake.duringDelivery.owned(), "delivery handoff is not simultaneously CPU-owned");
        eq(Map.of("P", 10L), fake.duringDelivery.uncertainInputs(), "delivery snapshot retains transfer evidence");
        fake.runtime = new GraphJobRuntime<>(fake.duringDelivery);
        fake.runtime.tick(fake, 100, 100);
        eq(1L, fake.deliveries, "no duplicate delivery after interrupted handoff");
        Fake thrown = new Fake(p);
        thrown.synchronous = true;
        thrown.throwDuringDelivery = true;
        thrown.runtime.tick(thrown, 0, 100);
        thrown.runtime.tick(thrown, 1, 100);
        eq(GraphJobRuntime.State.NEEDS_ATTENTION, thrown.runtime.state(), "throwing requester pauses settlement");
        eq(Map.of(), thrown.runtime.owned(), "throwing requester cannot duplicate ambiguous output");
        thrown.runtime = new GraphJobRuntime<>(thrown.runtime.snapshot());
        thrown.runtime.tick(thrown, 100, 100);
        eq(1L, thrown.deliveries, "throwing requester not retried automatically");
    }

    private static void refundFairness() {
        Map<String, Long> initial = new LinkedHashMap<>();
        initial.put("P", 1L);
        for (int i = 0; i < 100; i++) initial.put("R" + i, 1L);
        var plan = new GraphPlan<>("P", 1, false, new PlanStep.Sequence(List.of()), Map.of(), initial,
                Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        Fake fake = new Fake(plan);
        for (int i = 0; i < 70; i++) fake.blockedRefunds.add("R" + i);
        fake.runtime.cancel();
        for (int tick = 0; tick < 5; tick++) {
            fake.runtime.cancel(); // cancelled link is checked again on every CPU tick
            fake.runtime.tick(fake, tick, 32);
        }
        eq(31, fake.refunded.size(), "full storage for earlier keys cannot starve later refunds");
        eq(70, fake.runtime.owned().size(), "blocked keys stay physically owned");
        fake.runtime = new GraphJobRuntime<>(fake.runtime.snapshot());
        fake.blockedRefunds.clear();
        fake.complete();
        eq(101, fake.refunded.size(), "all refunds finish after storage recovers and reload");
    }

    private static void recoveryOwnership() {
        var p = plan(List.of(recipe("r1", Map.of("C", 1L, "X", 1L), Map.of("I", 1L)),
                recipe("r2", Map.of("I", 1L, "Y", 1L), Map.of("J", 1L)),
                recipe("r3", Map.of("J", 1L, "Z", 1L), Map.of("C", 1L, "P", 1L))),
                "P", 2, Map.of("C", 1L, "X", 2L, "Y", 2L, "Z", 2L));
        Fake f = new Fake(p);
        String owner = f.runtime.recovery().owner();
        f.runtime.tick(f, 0, 1);
        eq(Map.of("I", 1L), f.runtime.inFlight(), "T57 only r1 outputs are in flight");
        eq(Map.of(), f.runtime.externalWaiting(), "T57 no external seed request was invented");
        eq(Map.of("C", 1L), f.runtime.recovery().seeds(), "T57 future C is a restoration obligation");
        eq(RecoveryObligation.Status.IN_FLIGHT, f.runtime.recovery().status(), "T57 current recovery stage");
        eq(1L, f.runtime.recovery().stage(), "T53 one accepted phase transition");
        for (long tick : new long[] { 1, 100, 100_000 }) f.runtime.tick(f, tick, 100);
        eq(1L, f.pushes, "T23 long wait never proves a deadlock or grants another dispatch");
        f.runtime.accept("I", 1, false);
        eq(RecoveryObligation.Status.INTERMEDIATE, f.runtime.recovery().status(), "T15 intermediate ownership");
        f.runtime.suspend(true);
        f.runtime = new GraphJobRuntime<>(f.runtime.snapshot());
        eq(owner, f.runtime.recovery().owner(), "T15 recovery owner survives save and pause");
        f.runtime.tick(f, 100_001, 100);
        eq(1L, f.pushes, "T15 pause prevents the next recovery phase");
        f.runtime.suspend(false);
        f.runtime.tick(f, 100_002, 1);
        f.returnAll();
        f.runtime.tick(f, 100_003, 1);
        f.runtime.accept("P", 1, false);
        eq(Map.of("C", 1L), f.runtime.inFlight(), "T05 early P does not erase missing recovery output");
        f.runtime.tick(f, 100_004, 100);
        eq(Map.of(), f.delivered, "T05 cannot complete before catalyst return");
        f.runtime = new GraphJobRuntime<>(f.runtime.snapshot());
        f.runtime.cancel();
        eq(RecoveryObligation.Status.ABANDONED, f.runtime.recovery().status(), "T58 cancelled restoration is not called completed");
        f.complete();
        eq(null, f.refunded.get("C"), "T58 cancellation cannot refund the unreturned catalyst");
        var obligations = new OutputObligations<String>(Map.of("A", 2L));
        obligations.dispatch("one", 3, Map.of("A", 3L), false);
        obligations.dispatch("two", 4, Map.of("A", 4L), false);
        obligations.returned("A", 4);
        eq(Map.of(), obligations.external(), "External and batch accounts settle without double counting");
        eq(Map.of("A", 5L), obligations.inFlight(), "Only unreturned accepted output remains");
        var loaded = new OutputObligations<>(obligations.snapshot());
        eq(obligations.snapshot(), loaded.snapshot(), "Output owners survive snapshots");
        loaded.returned("A", 5);
        eq(Map.of(), loaded.all(), "Each returned unit consumes one obligation");
    }

    private static void configurationBatches() {
        var recipe = new GraphRecipe<>("configured", "configured", List.of(
                new GraphRecipe.Slot<>("circuit", 1, 0, true), new GraphRecipe.Slot<>("R", 1, 1)), Map.of("P", 1L));
        var p = plan(List.of(recipe), "P", 100, Map.of("circuit", 100L, "R", 100L));
        Fake f = new Fake(p);
        f.limit = 5;
        f.runtime.tick(f, 0, 1);
        eq(5L, f.accepted, "T12 circuits do not force one-operation batches");
        eq(Map.of("circuit", 1L, "R", 5L), f.lastInputs, "T12 one configuration per physical push");
        eq(99L, f.runtime.held("circuit"), "T12 no multiplied circuit debit");
        f.runtime = new GraphJobRuntime<>(f.runtime.snapshot());
        f.complete();
        eq(20L, f.pushes, "T12 provider capacity still controls physical batch count");
        eq(80L, f.refunded.get("circuit"), "T12 unused conservative configuration reserve is refunded");
        eq(100L, f.delivered.get("P"), "T12 requested output unchanged");
    }

    private static void closedWaitProof() {
        var u = new WaitAnalysis.Owner<>("U", Map.of("A", 1L), Map.of("B", 1L), true, false);
        var v = new WaitAnalysis.Owner<>("V", Map.of("B", 1L), Map.of("A", 1L), true, false);
        var closed = WaitAnalysis.inspect(List.of(u, v), Map.of(), new PlanningBudget(0, 10000, () -> false));
        eq(WaitAnalysis.Result.CLOSED_WAIT, closed.result(), "T56 closed wait uses ownership evidence");
        eq(Set.of("U", "V"), closed.owners(), "T56 reports owners");
        eq(Set.of("U"), closed.resourceOwners().get("A"), "T56 reports resource ownership");
        v = new WaitAnalysis.Owner<>("V", Map.of("B", 1L), Map.of("A", 1L), true, true);
        eq(WaitAnalysis.Result.OPEN_OR_UNKNOWN,
                WaitAnalysis.inspect(List.of(u, v), Map.of(), new PlanningBudget(0, 10000, () -> false)).result(),
                "T23 in-flight or external event invalidates closed-wait proof");
        v = new WaitAnalysis.Owner<>("V", Map.of("B", 1L), Map.of("A", 1L), false, false);
        eq(WaitAnalysis.Result.OPEN_OR_UNKNOWN,
                WaitAnalysis.inspect(List.of(u, v), Map.of(), new PlanningBudget(0, 10000, () -> false)).result(),
                "T23 unknown provider internals are not a deadlock proof");
    }

    private static void replaceUncommittedSuffix() {
        var original = plan(List.of(recipe("accepted-r1", Map.of("R", 1L), Map.of("I", 1L)),
                recipe("removed-r2", Map.of("I", 1L), Map.of("P", 1L))), "P", 1, Map.of("R", 1L));
        Fake f = new Fake(original);
        f.runtime.tick(f, 0, 1);
        var boundary = f.runtime.beginReplan();
        eq(Map.of("I", 1L), boundary.forecast(), "T32 accepted output belongs to prefix, not current physical stock");
        var replacement = new GraphPlanner<>(new GraphCompiler<>(List.of(
                recipe("new-r2", Map.of("I", 1L), Map.of("Q", 1L)),
                recipe("new-r3", Map.of("Q", 1L), Map.of("P", 1L)))))
                .plan("P", 1, boundary.forecast(), true, false, new PlanningBudget(0, 10000, () -> false));
        f.runtime.tick(f, 100, 100);
        eq(1L, f.pushes, "T32 planning freezes uncommitted dispatch");
        eq(true, f.runtime.replaceSuffix(boundary.epoch(), replacement, Map.of(), Map.of()), "T32 suffix replaced");
        eq(Map.of("I", 1L), f.runtime.inFlight(), "T32 accepted batch survives replacement");
        eq(Map.of("accepted-r1", 1L), f.runtime.committedRuns(), "T32 accepted authorization is not reset");
        f.runtime = new GraphJobRuntime<>(f.runtime.snapshot());
        f.runtime.tick(f, 101, 100);
        eq(1L, f.pushes, "T32 replacement cannot consume predicted output");
        f.returnAll();
        f.complete();
        eq(3L, f.pushes, "T32 old prefix not dispatched twice");
        eq(Map.of("P", 1L), f.delivered, "T32 new suffix produces exact original target");
        eq(Map.of("accepted-r1", 1L, "new-r2", 1L, "new-r3", 1L), f.runtime.committedRuns(), "T32 complete accepted history across save");

        f = new Fake(original);
        f.runtime.tick(f, 0, 1);
        boundary = f.runtime.beginReplan();
        f.runtime.accept("I", 1, false);
        eq(Map.of("I", 1L), f.runtime.owned(), "T32 existing returns are accepted during planning");
        f.runtime.cancel();
        eq(false, f.runtime.replaceSuffix(boundary.epoch(), replacement, Map.of(), Map.of()), "T32 late planner result cannot resurrect cancelled order");
        f.complete();
        eq(Map.of("I", 1L), f.refunded, "T58 cancellation during replan refunds only actual intermediate");
    }

    private static final class Fake implements GraphJobRuntime.Adapter<String> {

        GraphJobRuntime<String> runtime;
        long limit = Long.MAX_VALUE, refundLimit = Long.MAX_VALUE;
        long pushes, accepted;
        GraphJobRuntime.Outcome outcome = GraphJobRuntime.Outcome.ACCEPTED;
        boolean synchronous, throwAfterTaking, saveDuringPush;
        boolean saveDuringDelivery, throwDuringDelivery;
        long deliveries;
        Runnable beforeReturn;
        GraphJobRuntime.Snapshot<String> duringPush;
        GraphJobRuntime.Snapshot<String> duringDelivery;
        final Map<String, Long> delivered = new LinkedHashMap<>(), refunded = new LinkedHashMap<>();
        final List<Long> batches = new ArrayList<>();
        final Set<String> blockedRefunds = new HashSet<>();
        Map<String, Long> lastInputs = Map.of();

        Fake(GraphPlan<String> plan) {
            runtime = new GraphJobRuntime<>(plan, plan.initial(), Map.of());
        }

        @Override
        public long capacity(GraphRecipe<String> recipe, long requested) {
            return Math.min(limit, requested);
        }

        @Override
        public GraphJobRuntime.Outcome push(GraphRecipe<String> recipe, long runs, Map<String, Long> inputs) {
            pushes++;
            lastInputs = Map.copyOf(inputs);
            if (throwAfterTaking) throw new IllegalStateException("Provider failed after transfer");
            if (synchronous) recipe.outputs().forEach((key, amount) -> eq(amount * runs, runtime.accept(key, amount * runs, false), "sync callback"));
            if (beforeReturn != null) beforeReturn.run();
            if (saveDuringPush) duringPush = runtime.snapshot();
            if (outcome == GraphJobRuntime.Outcome.ACCEPTED) {
                accepted += runs;
                batches.add(runs);
            }
            return outcome;
        }

        @Override
        public long deliver(String key, long amount) {
            deliveries++;
            if (saveDuringDelivery) duringDelivery = runtime.snapshot();
            if (throwDuringDelivery) throw new IllegalStateException("Requester failed after transfer");
            long accepted = Math.min(amount, 17); // requesters may accept partial delivery
            delivered.merge(key, accepted, Long::sum);
            return accepted;
        }

        @Override
        public long refund(String key, long amount) {
            if (blockedRefunds.contains(key)) return 0;
            long accepted = Math.min(amount, refundLimit);
            if (accepted != 0) refunded.merge(key, accepted, Long::sum);
            return accepted;
        }

        void returnAll() {
            runtime.expected().forEach((key, amount) -> runtime.accept(key, amount, false));
        }

        void complete() {
            for (int tick = 0; tick < 1000 && !runtime.finished(); tick++) {
                runtime.tick(this, tick, 100);
                returnAll();
            }
            eq(true, runtime.finished(), "runtime completes");
        }
    }

    private static GraphRecipe<String> recipe(String id, Map<String, Long> input, Map<String, Long> output) {
        return new GraphRecipe<>(id, id, input.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), output);
    }

    private static GraphPlan<String> plan(List<GraphRecipe<String>> recipes, String key, long amount, Map<String, Long> stock) {
        var plan = new GraphPlanner<>(new GraphCompiler<>(recipes)).plan(key, amount, stock, true, true, new PlanningBudget(5000, 100000, () -> false));
        eq(true, plan.feasible(), "test plan feasible");
        return plan;
    }

    private static void eq(Object expected, Object actual, String message) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(message + ": " + expected + " != " + actual);
    }
}
