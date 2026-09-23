package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import java.util.*;

/** Standalone JVM acceptance/oracle tests: no Minecraft bootstrap or test double of the planner. */
public final class GraphCoreTest {

    private static int assertions;

    public static void main(String[] args) throws Exception {
        amounts();
        preparedCatalog();
        ordinary();
        dagInventoryOracle();
        splitSources();
        externalSupply();
        restorationGoals();
        displayTopology();
        displayLayout();
        catalystPolicy();
        logicalNodeFees();
        cycles();
        summaryOracle();
        ordinarySummaryOracle();
        boundedReachabilityOracle();
        GraphRuntimeTest.run();
        GraphSchedulingTest.run();
        System.out.println("Graph core: " + assertions + " assertions passed");
    }

    private static void preparedCatalog() throws Exception {
        var low = recipe("low", Map.of("A", 1L), Map.of("B", 1L));
        var high = recipe("high", Map.of("A", 2L), Map.of("B", 1L));
        var values = new ArrayList<>(List.of(low, high));
        var priorities = new HashMap<>(Map.of("low", 0, "high", 10));
        var prepared = new PreparedCatalog<>(values, priorities);
        values.clear();
        priorities.clear();
        try (var scheduler = new PlanningScheduler(2, 8, 1, 100_000L)) {
            var first = scheduler.submit(prepared.build(new PlanningBudget(0, 10000, () -> false)), new PlanningBudget(0, 10000, () -> false));
            var second = scheduler.submit(prepared.build(new PlanningBudget(0, 10000, () -> false)), new PlanningBudget(0, 10000, () -> false));
            var compiler = first.get(5, java.util.concurrent.TimeUnit.SECONDS);
            eq(List.of(high, low), compiler.producers("B"), "Worker index preserves provider priority and detached inputs");
            eq(true, compiler == second.get(5, java.util.concurrent.TimeUnit.SECONDS), "Concurrent builders publish one complete index");
        }
        var compiler = new GraphCompiler<>(List.of(low));
        var selected = new HashMap<String, GraphRecipe<String>>();
        for (int i = 0; i < 32769; i++) selected.put("K" + i, low);
        var large = new GraphCompiler.Compiled<>(Map.of("low", low), selected, List.of());
        compiler.publish("large", Map.of(), Set.of(), large);
        eq(large, compiler.cached("large", Map.of(), Set.of()), "Oversized bounded graph must not evict itself");
        compiler.publish("other", Map.of(), Set.of(), large);
        eq(null, compiler.cached("large", Map.of(), Set.of()), "Only one oversized graph may remain cached");
        eq(large, compiler.cached("other", Map.of(), Set.of()), "Newest oversized graph retained");
    }

    private static void displayTopology() {
        var shared = new PlanTopology<>(List.of(recipe("ab", Map.of("D", 1L), Map.of("A", 1L, "B", 1L)),
                recipe("p", Map.of("A", 2L, "B", 1L), Map.of("P", 1L))));
        eq(6, shared.nodes().size(), "Shared graph has one node per resource and recipe");
        eq(0L, shared.groups().stream().filter(PlanTopology.Group::cyclic).count(), "Shared output is not a cycle");
        var self = new PlanTopology<>(List.of(recipe("grow", Map.of("A", 1L), Map.of("A", 2L))));
        eq(1L, self.groups().stream().filter(PlanTopology.Group::cyclic).count(), "Self replication is a resource/recipe ring");
        var cycle = new PlanTopology<>(List.of(recipe("r1", Map.of("C", 1L, "X", 1L), Map.of("I", 1L)),
                recipe("r2", Map.of("I", 1L), Map.of("J", 1L)), recipe("r3", Map.of("J", 1L), Map.of("C", 1L, "P", 1L))));
        eq(6, cycle.groups().stream().filter(PlanTopology.Group::cyclic).findFirst().orElseThrow().nodes().size(),
                "Recovery SCC contains three recipes and three intermediates, not fuel/product");
        var split = new PlanTopology<>(List.of(recipe("x", Map.of("X", 1L), Map.of("A", 1L)),
                recipe("y", Map.of("Y", 1L), Map.of("A", 1L))));
        eq(2L, split.nodes().stream().filter(node -> node.recipe() != null).count(), "Both selected sources stay visible");
        eq(1L, split.nodes().stream().filter(node -> "A".equals(node.resource())).count(), "Shared target has one identity");
        List<GraphRecipe<String>> deep = new ArrayList<>();
        for (int i = 0; i < 15000; i++) deep.add(recipe("r" + i, Map.of("K" + i, 1L), Map.of("K" + (i + 1), 1L)));
        eq(30001, new PlanTopology<>(deep).nodes().size(), "Deep display analysis is iterative");
    }

    private static void displayLayout() {
        var self = new PlanTopology<>(List.of(recipe("grow", Map.of("A", 1L, "R", 1L), Map.of("A", 2L))));
        var layout = new PlanGraphLayout<>(self);
        eq(self.nodes().size(), layout.points().size(), "Graph preserves all resource and recipe nodes");
        eq(self.edges().size(), layout.links().size(), "Graph preserves all edges including the back edge");
        var cycle = layout.links().stream().filter(PlanGraphLayout.Link::cyclic).toList();
        eq(2, cycle.size(), "Self copy has two distinct directed arcs");
        eq(false, cycle.get(0).path().get(8).equals(cycle.get(1).path().get(8)), "Opposite arcs cannot overlap");
        eq(1, layout.rings().size(), "Self-copy SCC is visibly enclosed");
        for (var link : layout.links()) {
            var tip = link.path().get(link.path().size() - 1);
            var target = layout.points().get(link.edge().to());
            eq(true, Math.hypot(tip.x() - target.x(), tip.y() - target.y()) > 12,
                    "Directed arrow tip stays outside the item/recipe slot");
        }
        List<GraphRecipe<String>> deep = new ArrayList<>();
        for (int i = 0; i < 8192; i++) deep.add(recipe("r" + i, Map.of("K" + i, 1L), Map.of("K" + (i + 1), 1L)));
        var topology = new PlanTopology<>(deep);
        var chain = new PlanGraphLayout<>(topology);
        for (var edge : topology.edges()) eq(true, chain.points().get(edge.from()).x() < chain.points().get(edge.to()).x(), "DAG flows forward");
        var view = new PlanGraphLayout.Box(0, 0, 360, 240);
        eq(true, chain.visibleNodes(view).size() < 16, "Deep plan render visits viewport-sized nodes");
        var expected = new HashSet<Integer>();
        for (int i = 0; i < chain.links().size(); i++) if (chain.links().get(i).bounds().intersects(view)) expected.add(i);
        eq(expected, new HashSet<>(chain.visibleLinks(view)), "Spatial edge index agrees with full scan");
    }

    private static void catalystPolicy() {
        var recipes = List.of(recipe("grow", Map.of("C", 1L, "R", 1L), Map.of("C", 2L)));
        var stock = Map.of("C", 8L, "R", 100L);
        var minimal = catalystPlan(recipes, "C", 32, stock, CatalystPolicy.MINIMAL);
        var parallel = catalystPlan(recipes, "C", 32, stock, new CatalystPolicy(4, 0));
        eq(1L, minimal.initial().get("C"), "Configured minimum only borrows one seed");
        eq(4L, parallel.initial().get("C"), "Configured four copies borrows four existing seeds");
        eq(4L, parallel.seeds().get("C"), "Borrowed parallel seeds are visible and returned");
        var made = catalystPlan(recipes, "C", 32, Map.of("C", 1L, "R", 100L), new CatalystPolicy(4, 10));
        eq(4L, made.seeds().get("C"), "Optional growth retains four copies at completion");
        eq(35L, made.patternTimes().get("grow"), "Additional catalysts have explicit material cost");
        var loop = List.of(recipe("ab", Map.of("A", 1L, "R", 1L), Map.of("B", 1L)),
                recipe("ba", Map.of("B", 1L), Map.of("A", 1L, "P", 1L)));
        var lanes = catalystPlan(loop, "P", 40, Map.of("A", 4L, "R", 40L), new CatalystPolicy(4, 0));
        eq(4L, lanes.initial().get("A"), "Multi-recipe loop reserves four lanes");
        eq(40L, lanes.patternTimes().get("ab"), "Parallel grouping keeps total recipe runs");
        var cursor = new PlanCursor(lanes.steps());
        eq(4L, cursor.current().runs(), "Loop stage exposes a multi-run batch to assemblers");
        var bootstrapped = new ArrayList<>(loop);
        bootstrapped.add(recipe("seed", Map.of("Q", 1L), Map.of("A", 1L)));
        var extra = catalystPlan(bootstrapped, "P", 8, Map.of("A", 1L, "Q", 3L, "R", 8L), new CatalystPolicy(4, 10));
        eq(3L, extra.initial().get("Q"), "Extra parallel catalysts require three real external feedstocks");
        eq(4L, extra.seeds().get("A"), "Externally manufactured catalysts are part of the recovery contract");
        var externalCap = catalystPlan(bootstrapped, "P", 9, Map.of("A", 1L, "Q", 20L, "R", 9L), new CatalystPolicy(16, 2));
        eq(2L, externalCap.initial().get("Q"), "Bootstrap must not grant a fresh extra-production allowance");
        eq(3L, externalCap.seeds().get("A"), "Original stock plus two externally made seeds stays below the cap");
        var capped = catalystPlan(recipes, "C", 100, Map.of("C", 1L, "R", 200L), new CatalystPolicy(16, 10));
        eq(11L, capped.seeds().get("C"), "Ten extra copies permits one existing plus ten manufactured catalysts");
        eq(110L, capped.patternTimes().get("grow"), "Every additionally retained catalyst has a real production cost");
        var noExtra = catalystPlan(recipes, "C", 100, Map.of("C", 1L, "R", 200L), new CatalystPolicy(16, 0));
        eq(1L, noExtra.seeds().get("C"), "Zero extra copies retains the existing minimum starter");
        var stocked = catalystPlan(recipes, "C", 100, Map.of("C", 20L, "R", 200L), new CatalystPolicy(16, 2));
        eq(16L, stocked.initial().get("C"), "Manufacturing cap does not limit borrowing existing catalysts");
        var small = catalystPlan(recipes, "C", 2, Map.of("C", 1L, "R", 200L), new CatalystPolicy(16, 10));
        eq(2L, small.seeds().get("C"), "Tiny job does not manufacture all ten allowed extras");
        var budget = new PlanningBudget(5000, 1000000, () -> false);
        var compiler = new GraphCompiler<>(recipes);
        var adaptive = new CatalystPlanningWork<>(new CatalystPolicy(16, 10), budget,
                policy -> new GraphPlanningWork<>(compiler, "C", 100, Map.of("C", 1L, "R", 105L), true, true, budget).catalysts(policy));
        while (!adaptive.step()) {}
        eq(true, adaptive.result().feasible(), "Unfundable upper cap does not reject the original order");
        eq(6L, adaptive.result().seeds().get("C"), "Five affordable extras are retained instead of falling all the way back to one seed");
        eq(105L, adaptive.result().patternTimes().get("grow"), "Partial acceleration spends only available material");
        PlanVerifier.verify(adaptive.result());
    }

    private static GraphPlan<String> catalystPlan(List<GraphRecipe<String>> recipes, String target, long amount, Map<String, Long> stock, CatalystPolicy policy) {
        var work = new GraphPlanningWork<>(new GraphCompiler<>(recipes), target, amount, stock, true, true,
                new PlanningBudget(5000, 1000000, () -> false)).catalysts(policy);
        while (!work.step()) {}
        eq(true, work.result().feasible(), "Configured catalyst plan feasible");
        PlanVerifier.verify(work.result());
        return work.result();
    }

    private static void logicalNodeFees() {
        var shared = plan(List.of(recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L)),
                recipe("a", Map.of("X", 1L), Map.of("A", 1L)), recipe("b", Map.of("X", 1L), Map.of("B", 1L)),
                recipe("x", Map.of("ORE", 1L), Map.of("X", 1L))), "P", 1, Map.of("ORE", 2L), true, true);
        eq(java.math.BigInteger.valueOf(7), PlanNodeCost.count(shared), "Shared ancestors still pay logical AE node fee");
        var simple = plan(List.of(recipe("p", Map.of("X", 1L), Map.of("P", 4L))), "P", 2000, Map.of("X", 500L), true, true);
        eq(java.math.BigInteger.valueOf(2), PlanNodeCost.count(simple), "Node fee independent of recipe run count");
        var cycle = plan(List.of(recipe("grow", Map.of("A", 1L), Map.of("A", 2L))), "A", 1000, Map.of("A", 1L), true, true);
        eq(java.math.BigInteger.valueOf(2), PlanNodeCost.count(cycle), "Cycle fee terminates at explicit back-reference");
        var recipes = new LinkedHashMap<String, GraphRecipe<String>>();
        var steps = new ArrayList<PlanStep>();
        for (int i = 0; i < 15000; i++) {
            var r = recipe("r" + i, Map.of("K" + i, 1L), Map.of("K" + (i + 1), 1L));
            recipes.put(r.id(), r);
            steps.add(new PlanStep.Batch(r.id(), 1));
        }
        var deep = new GraphPlan<>("K15000", 1, false, new PlanStep.Sequence(steps), recipes, Map.of("K0", 1L),
                Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        eq(java.math.BigInteger.valueOf(15001), PlanNodeCost.count(deep), "Node charging does not recurse or expand ancestors");
        for (int depth : new int[] { 8, 16, 24 }) {
            var diamondRecipes = new LinkedHashMap<String, GraphRecipe<String>>();
            var diamondSteps = new ArrayList<PlanStep>();
            // The reachable cone grows by one branch per layer, then wraps at eight.
            for (int layer = 1; layer <= depth; layer++) {
                for (int branch = 0; branch < Math.min(8, depth - layer + 1); branch++) {
                    var inputs = layer == 1 ? Map.of("RAW", 2L) :
                            Map.of("L" + (layer - 1) + "B" + branch, 1L,
                                    "L" + (layer - 1) + "B" + ((branch + 1) % 8), 1L);
                    String id = "L" + layer + "B" + branch;
                    var recipe = recipe(id, inputs, Map.of(id, 2L));
                    diamondRecipes.put(id, recipe);
                    diamondSteps.add(new PlanStep.Batch(id, 1));
                }
            }
            var diamond = new GraphPlan<>("L" + depth + "B0", 1, false, new PlanStep.Sequence(diamondSteps),
                    diamondRecipes, Map.of(), Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
            // 56 input templates in the narrowing cone + 16 per full extra layer,
            // one root, and 14 (depth 8) or 16 terminal occurrences. These match
            // the node component of real-server MAX_FAST bytes: 71 / 201 / 329.
            eq(java.math.BigInteger.valueOf(57 + 16L * (depth - 8) + (depth == 8 ? 14 : 16)), PlanNodeCost.count(diamond),
                    "Shared DAG node fees match MAX_FAST request/terminal sharing at depth " + depth);
        }
    }

    private static void amounts() {
        eq(4_611_686_018_427_387_904L, CheckedAmounts.ceilDiv(Long.MAX_VALUE, 2), "N03");
        for (long count : new long[] { 2_147_483_648L, 9_007_199_254_740_993L }) {
            GraphPlan<String> p = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L))),
                    "P", count, Map.of("R", count), true, true);
            eq(count, p.initial().get("R"), "exact long input");
            eq(count, p.patternTimes().get("p"), "exact long runs");
        }
        boolean threw = false;
        try {
            CheckedAmounts.multiply(Long.MAX_VALUE, 2);
        } catch (ArithmeticException e) {
            threw = true;
        }
        check(threw, "N04 checked overflow");
        var up = recipe("up", Map.of("R", 1L), Map.of("X", 1L));
        var down = recipe("down", Map.of("X", 1L), Map.of("P", 1L));
        var overflowing = new GraphPlan<>("P", 1, false,
                new PlanStep.Sequence(List.of(new PlanStep.Batch("up", 1), new PlanStep.Batch("down", 1))),
                Map.of("up", up, "down", down), Map.of("R", 1L, "X", Long.MAX_VALUE), Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        threw = false;
        try {
            PlanVerifier.verify(overflowing);
        } catch (ArithmeticException e) {
            threw = true;
        }
        check(threw, "N04 intermediate peak checked even when terminal amount fits");
    }

    private static void ordinary() {
        GraphPlan<String> p = plan(List.of(recipe("plate", Map.of("ore", 3L), Map.of("plate", 2L)),
                recipe("gear", Map.of("plate", 2L), Map.of("gear", 1L))), "gear", 10, Map.of("ore", 30L), true, true);
        eq(30L, p.initial().get("ore"), "A01");
        eq(10L, p.patternTimes().get("plate"), "A01 runs");
        p = plan(List.of(recipe("a", Map.of("R", 2L), Map.of("A", 3L)),
                recipe("b", Map.of("A", 2L), Map.of("B", 1L)), recipe("c", Map.of("A", 1L), Map.of("C", 1L)),
                recipe("d", Map.of("B", 1L, "C", 1L), Map.of("D", 1L))), "D", 10, Map.of("R", 20L), true, true);
        eq(20L, p.initial().get("R"), "A02 shared demand");
        p = plan(List.of(recipe("ab", Map.of("R", 2L), Map.of("A", 1L, "B", 1L)),
                recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L))), "P", 10, Map.of("R", 20L), true, true);
        eq(10L, p.patternTimes().get("ab"), "A03 coupled outputs");
        p = plan(List.of(recipe("a", Map.of("R", 2L), Map.of("A", 3L))), "A", 4, Map.of("R", 4L), true, true);
        eq(2L, p.patternTimes().get("a"), "A04 rounding");
        p = plan(List.of(recipe("preferred", Map.of("R", 1L), Map.of("A", 1L)),
                recipe("alternative", Map.of("S", 1L), Map.of("A", 1L)),
                recipe("b", Map.of("R", 1L), Map.of("B", 1L)),
                recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L))), "P", 3, Map.of("R", 3L, "S", 3L), true, true);
        eq(3L, p.patternTimes().get("alternative"), "A05 shared scarce inventory chooses viable alternative");
        interpret(p, 100);
        var forced = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 4L))),
                "P", 10, Map.of("P", 100L, "R", 3L), true, true);
        eq(3L, forced.patternTimes().get("p"), "Forced DAG order still crafts with target stock present");
        var stocked = plan(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 4L))),
                "P", 10, Map.of("P", 100L), true, false);
        eq(Map.of(), stocked.patternTimes(), "Unforced DAG order can use target stock without running recipes");
    }

    /** Exhaustive tiny DAG reachability, including coupled outputs and partially stocked intermediates. */
    private static void dagInventoryOracle() {
        var random = new Random(271829);
        var keys = List.of("R", "A", "B", "P");
        for (int trial = 0; trial < 100; trial++) {
            var recipes = List.of(recipe("split", Map.of("R", 1L + random.nextInt(3)),
                    Map.of("A", 1L + random.nextInt(3), "B", 1L + random.nextInt(3))),
                    recipe("finish", Map.of("A", 1L + random.nextInt(3), "B", 1L + random.nextInt(3)),
                            Map.of("P", 1L + random.nextInt(2))));
            Map<String, Long> stock = Map.of("R", 8L, "A", (long) random.nextInt(3), "B", (long) random.nextInt(3));
            long wanted = 1 + random.nextInt(5);
            var queue = new ArrayDeque<List<Long>>();
            var visited = new HashSet<List<Long>>();
            queue.add(keys.stream().map(key -> stock.getOrDefault(key, 0L)).toList());
            boolean reachable = false;
            while (!queue.isEmpty()) {
                var state = queue.removeFirst();
                if (!visited.add(state)) continue;
                if (state.get(3) >= wanted) {
                    reachable = true;
                    break;
                }
                for (var recipe : recipes) {
                    var next = new ArrayList<Long>();
                    for (int i = 0; i < keys.size(); i++) {
                        long input = recipe.inputs().getOrDefault(keys.get(i), 0L);
                        if (state.get(i) < input) break;
                        next.add(state.get(i) - input + recipe.outputs().getOrDefault(keys.get(i), 0L));
                    }
                    if (next.size() == keys.size()) queue.add(next);
                }
            }
            var plan = raw(recipes, "P", wanted, stock, true, true);
            eq(reachable, plan.feasible(), "DAG fast propagation agrees with exhaustive inventory search");
            if (plan.feasible()) {
                interpret(plan, 100);
                for (var input : plan.initial().entrySet())
                    check(input.getValue() <= stock.getOrDefault(input.getKey(), 0L), "DAG witness does not invent stock");
            }
        }
    }

    private static void cycles() {
        var growth = List.of(recipe("grow", Map.of("A", 1L, "R", 1L), Map.of("A", 2L)));
        GraphPlan<String> p = plan(growth, "A", 100, Map.of("A", 1L, "R", 100L), true, false);
        eq(100L, p.patternTimes().get("grow"), "C01");
        eq(1L, p.seeds().get("A"), "C01 seed");
        p = plan(growth, "A", 100, Map.of("A", 1L, "R", 100L), false, false);
        eq(99L, p.patternTimes().get("grow"), "C02");
        check(!raw(growth, "A", 100, Map.of("R", 100L), true, false).feasible(), "C03 no seed");
        var two = List.of(recipe("grow", Map.of("A", 2L, "R", 1L), Map.of("A", 3L)));
        check(!raw(two, "A", 10, Map.of("A", 1L, "R", 10L), true, false).feasible(), "C04");
        p = plan(two, "A", 10, Map.of("A", 2L, "R", 10L), true, false);
        eq(10L, p.patternTimes().get("grow"), "C05");
        p = plan(List.of(recipe("return", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "P", 1L))),
                "P", 1_000_000_000_000L, Map.of("C", 1L, "R", 1_000_000_000_000L), true, true);
        eq(1L, p.initial().get("C"), "C06 constant seed");
        var cycle = List.of(recipe("ab", Map.of("A", 1L, "R", 1L), Map.of("B", 1L)),
                recipe("ba", Map.of("B", 1L), Map.of("A", 1L, "P", 1L)));
        p = plan(cycle, "P", 100, Map.of("A", 1L, "R", 100L), true, true);
        eq(1L, p.initial().get("A"), "C08");
        eq(100L, p.initial().get("R"), "C08 R");
        interpret(p, 1000);
        p = plan(List.of(recipe("loss", Map.of("A", 2L), Map.of("A", 1L, "P", 1L))),
                "P", 100, Map.of("A", 101L), false, true);
        eq(101L, p.initial().get("A"), "C09 negative delta");
        check(!raw(List.of(recipe("a", Map.of("A", 1L), Map.of("B", 2L)),
                recipe("b", Map.of("B", 1L), Map.of("A", 2L))), "A", 10, Map.of(), true, true).feasible(), "C10 phantom flow");
        var seeded = new ArrayList<>(growth);
        seeded.add(recipe("seed", Map.of("ore", 1L), Map.of("A", 1L)));
        p = plan(seeded, "A", 10, Map.of("ore", 1L, "R", 10L), true, false);
        eq(1L, p.initial().get("ore"), "C13 bootstrap");
        interpret(p, 1000);
        p = plan(List.of(recipe("fluid", Map.of("catalyst-mB", 250L, "water-mB", 1000L), Map.of("catalyst-mB", 250L, "P", 1L))),
                "P", 3_000_000, Map.of("catalyst-mB", 250L, "water-mB", 3_000_000_000L), true, true);
        eq(3_000_000_000L, p.initial().get("water-mB"), "F02");
        p = plan(cycle, "P", 5, Map.of("B", 1L, "R", 5L), true, true);
        interpret(p, 100);
        eq(1L, p.seeds().get("B"), "C11 alternative starting order");
        p = plan(List.of(recipe("a", Map.of("C", 1L, "R", 1L), Map.of("C", 1L, "A", 1L)),
                recipe("b", Map.of("C", 1L, "S", 1L), Map.of("C", 1L, "B", 1L)),
                recipe("p", Map.of("A", 1L, "B", 1L), Map.of("P", 1L))),
                "P", 3, Map.of("C", 1L, "R", 3L, "S", 3L), true, true);
        interpret(p, 100);
        eq(1L, p.initial().get("C"), "C12 shared catalyst is one physical item");
        var sharedTarget = new ArrayList<>(growth);
        sharedTarget.add(recipe("p", Map.of("A", 2L), Map.of("P", 1L)));
        p = plan(sharedTarget, "P", 50, Map.of("A", 1L, "R", 100L), true, true);
        interpret(p, 200);
        eq(100L, p.patternTimes().get("grow"), "C14 seed survives downstream consumption");
    }

    private static void splitSources() {
        var split = List.of(recipe("x", Map.of("X", 1L), Map.of("A", 1L)),
                recipe("y", Map.of("Y", 1L), Map.of("A", 1L)));
        var p = plan(split, "A", 5, Map.of("X", 3L, "Y", 2L), true, true);
        eq(3L, p.patternTimes().get("x"), "T27 source X count");
        eq(2L, p.patternTimes().get("y"), "T27 source Y count");
        interpret(p, 10);
        var nested = new ArrayList<>(split);
        nested.add(recipe("p", Map.of("A", 5L), Map.of("P", 1L)));
        p = plan(nested, "P", 1, Map.of("X", 3L, "Y", 2L), true, true);
        eq(3L, p.patternTimes().get("x"), "T27 intermediate splits, not only final target");
        eq(2L, p.patternTimes().get("y"), "T27 intermediate second source");
        interpret(p, 10);
        p = plan(split, "A", 1_000_000_000_000L, Map.of("X", 600_000_000_000L, "Y", 400_000_000_000L), true, true);
        eq(600_000_000_000L, p.patternTimes().get("x"), "T26 huge split stays symbolic");
        eq(400_000_000_000L, p.patternTimes().get("y"), "T26 exact huge second allocation");
        check(((PlanStep.Sequence) p.steps()).children().size() == 2, "T26 split plan has two batches");
        // The first source can consume scarce X needed by B. The native allocator
        // must roll that choice back before splitting A across Y and Z.
        var shared = List.of(recipe("a-x", Map.of("X", 1L), Map.of("A", 1L)),
                recipe("a-y", Map.of("Y", 1L), Map.of("A", 1L)),
                recipe("a-z", Map.of("Z", 1L), Map.of("A", 1L)),
                recipe("b", Map.of("X", 1L), Map.of("B", 1L)),
                recipe("p", Map.of("A", 2L, "B", 1L), Map.of("P", 1L)));
        p = plan(shared, "P", 1, Map.of("X", 1L, "Y", 1L, "Z", 1L), true, true);
        check(!p.patternTimes().containsKey("a-x"), "T31 scarce material allocation rolled back");
        interpret(p, 10);
        var ratio = List.of(recipe("r1", Map.of("C", 1L, "F", 1L), Map.of("I", 7L)),
                recipe("r2", Map.of("I", 1L), Map.of("C", 1L, "P", 1L)));
        p = plan(ratio, "P", 7, Map.of("C", 1L, "F", 1L), true, true);
        eq(1L, p.patternTimes().get("r1"), "Native fallback finds ratio outside fast-path 1..4");
        eq(7L, p.patternTimes().get("r2"), "Native fallback exact recovery ratio");
        interpret(p, 20);
        var unknown = raw(List.of(recipe("x", Map.of("X", 1L), Map.of("A", 1L)),
                recipe("y", Map.of("Y", 1L), Map.of("A", 1L))), "A", 10, Map.of("X", 1L, "Y", 1L), true, true);
        eq(GraphPlan.Result.UNKNOWN, unknown.result(), "Bounded multi-source failure is not a false global missing proof");
    }

    private static void externalSupply() {
        var compiler = new GraphCompiler<>(List.of(recipe("p", Map.of("R", 2L), Map.of("P", 1L))));
        var work = new GraphPlanner<>(compiler).begin("P", 100, Map.of("R", 3L), Set.of("R"), true, true,
                new PlanningBudget(0, 10000, () -> false));
        while (!work.step()) {}
        var p = work.result();
        check(p.feasible() && p.missing().isEmpty(), "External supply is a permitted requirement");
        eq(200L, p.initial().get("R"), "External quantity is finite and exact");
        PlanVerifier.verify(p);
        work = new GraphPlanner<>(new GraphCompiler<String>(List.of())).begin("R", Long.MAX_VALUE, Map.of(), Set.of("R"),
                true, false, new PlanningBudget(0, 10000, () -> false));
        while (!work.step()) {}
        p = work.result();
        check(p.feasible(), "Exact Long.MAX_VALUE external request is legal, not infinity");
        eq(Long.MAX_VALUE, p.initial().get("R"), "External maximum stays exact");
        compiler = new GraphCompiler<>(List.of(recipe("cycle", Map.of("C", 1L, "F", 1L), Map.of("C", 1L, "P", 1L))));
        work = new GraphPlanner<>(compiler).begin("P", 100, Map.of("F", 100L), Set.of("C"), true, true,
                new PlanningBudget(0, 10000, () -> false));
        while (!work.step()) {}
        p = work.result();
        check(p.feasible(), "Externally supplied seed permits waiting for its real arrival");
        eq(1L, p.initial().get("C"), "Do not invent stock to widen catalyst batches");
        var runtime = new GraphJobRuntime<>(p, Map.of("F", 100L), Map.of("C", 1L));
        check(runtime.owned().getOrDefault("C", 0L) == 0, "External requirement is not held material");
        eq(1L, runtime.expected().get("C"), "Finite external waiting quantity");
    }

    private static void restorationGoals() {
        var catalog = new GraphCompiler<>(List.of(recipe("new-r2", Map.of("I", 1L, "Y", 1L), Map.of("J", 1L)),
                recipe("r3", Map.of("J", 1L, "Z", 1L), Map.of("C", 1L, "P", 1L))));
        var work = new GraphPlanningWork<>(catalog, "P", 1, Map.of("I", 1L, "Y", 1L, "Z", 1L), Set.of(),
                Map.of("C", 1L), true, false, new PlanningBudget(0, 10000, () -> false));
        while (!work.step()) {}
        var p = work.result();
        check(p.feasible(), "Replanned partial recovery can start from I");
        eq(Map.of("C", 1L), p.seeds(), "Original C recovery obligation is retained");
        interpret(p, 10);
        catalog = new GraphCompiler<>(List.of(recipe("p", Map.of("R", 1L), Map.of("P", 1L)),
                recipe("restore", Map.of("I", 1L), Map.of("C", 1L))));
        work = new GraphPlanningWork<>(catalog, "P", 1, Map.of("R", 1L, "I", 1L), Set.of(), Map.of("C", 1L),
                true, false, new PlanningBudget(0, 10000, () -> false));
        while (!work.step()) {}
        p = work.result();
        check(p.feasible(), "Recovery goal outside the target dependency component is included");
        eq(1L, p.patternTimes().get("restore"), "Disconnected recovery steps cannot be omitted");
        interpret(p, 10);
    }

    private static void summaryOracle() {
        Random random = new Random(49325);
        for (int trial = 0; trial < 1000; trial++) {
            Map<String, GraphRecipe<String>> recipes = new LinkedHashMap<>();
            List<PlanStep> sequence = new ArrayList<>();
            int length = 1 + random.nextInt(5), repeat = random.nextInt(6);
            for (int i = 0; i < length; i++) {
                var recipe = recipe("r" + i, Map.of("x", 1L + random.nextInt(4)), Map.of("x", 1L + random.nextInt(4)));
                recipes.put(recipe.id(), recipe);
                sequence.add(new PlanStep.Batch(recipe.id(), 1));
            }
            PlanStep step = new PlanStep.Repeat(new PlanStep.Sequence(sequence), repeat);
            SequenceSummary<String> summary = SequenceSummary.of(step, recipes);
            var computation = new SummaryComputation<>(step, recipes, new PlanningBudget(0, 10000, () -> false));
            while (!computation.step()) { /* One retained step at a time. */ }
            var resumed = computation.result();
            long minimum = 0;
            while (!canExecute(step, recipes, new HashMap<>(Map.of("x", minimum)))) minimum++;
            eq(minimum, summary.required("x").longValueExact(), "random prefix oracle");
            eq(minimum, resumed.required("x").longValueExact(), "resumed prefix oracle");
            Map<String, Long> inventory = new HashMap<>(Map.of("x", minimum));
            check(canExecute(step, recipes, inventory), "oracle executes");
            eq(inventory.get("x") - minimum, summary.delta("x").longValueExact(), "random net oracle");
            eq(inventory.get("x") - minimum, resumed.delta("x").longValueExact(), "resumed net oracle");
            long held = minimum, peak = held;
            for (int n = 0; n < repeat; n++) for (var recipe : recipes.values()) {
                held -= recipe.inputs().get("x");
                held += recipe.outputs().get("x");
                peak = Math.max(peak, held);
            }
            eq(peak - minimum, summary.peak("x").longValueExact(), "random peak oracle");
            eq(peak - minimum, resumed.peak("x").longValueExact(), "resumed peak oracle");
        }
    }

    private static void ordinarySummaryOracle() {
        var random = new Random(58431);
        for (int trial = 0; trial < 200; trial++) {
            var recipes = new LinkedHashMap<String, GraphRecipe<String>>();
            var children = new ArrayList<PlanStep>();
            int length = 1 + random.nextInt(8), repeat = random.nextInt(5);
            for (int i = 0; i < length; i++) {
                String input = random.nextBoolean() ? "x" : "y", output = input.equals("x") ? "y" : "x";
                var recipe = recipe("r" + i, Map.of(input, 1L + random.nextInt(4)), Map.of(output, 1L + random.nextInt(4)));
                recipes.put(recipe.id(), recipe);
                children.add(new PlanStep.Batch(recipe.id(), random.nextInt(6)));
            }
            PlanStep step = new PlanStep.Repeat(new PlanStep.Sequence(children), repeat);
            var computation = new SummaryComputation<>(step, recipes, new PlanningBudget(0, 10000, () -> false));
            while (!computation.step()) {}
            var net = new HashMap<String, Long>();
            var need = new HashMap<String, Long>();
            var peak = new HashMap<String, Long>();
            for (int n = 0; n < repeat; n++) for (var child : children) {
                var batch = (PlanStep.Batch) child;
                var recipe = recipes.get(batch.recipe());
                for (long run = 0; run < batch.runs(); run++) {
                    recipe.inputs().forEach((key, count) -> {
                        long after = net.merge(key, -count, Long::sum);
                        need.merge(key, Math.max(0, -after), Math::max);
                    });
                    recipe.outputs().forEach((key, count) -> {
                        long after = net.merge(key, count, Long::sum);
                        peak.merge(key, Math.max(0, after), Math::max);
                    });
                }
            }
            for (String key : List.of("x", "y")) {
                eq(need.getOrDefault(key, 0L), computation.result().required(key).longValueExact(), "Batch fold prefix oracle");
                eq(net.getOrDefault(key, 0L), computation.result().delta(key).longValueExact(), "Batch fold delta oracle");
                eq(peak.getOrDefault(key, 0L), computation.result().peak(key).longValueExact(), "Batch fold peak oracle");
            }
        }
    }

    /** Independent finite-state BFS: two intermediates, one finite fuel, one goal. */
    private static void boundedReachabilityOracle() {
        Random random = new Random(92147);
        int feasible = 0;
        for (int trial = 0; trial < 150; trial++) {
            List<GraphRecipe<String>> recipes = List.of(
                    recipe("ab", Map.of("A", 1L, "R", 1L), Map.of("B", 1L + random.nextInt(2))),
                    recipe("ba", Map.of("B", 1L + random.nextInt(2)), Map.of("A", 1L, "P", 1L)));
            Map<String, Long> stock = Map.of("A", (long) random.nextInt(3), "B", (long) random.nextInt(3), "R", 4L);
            GraphPlan<String> plan = raw(recipes, "P", 3, stock, true, true);
            if (!plan.feasible()) continue; // bounded search is intentionally incomplete
            feasible++;
            interpret(plan, 200);
            for (var entry : plan.initial().entrySet()) check(entry.getValue() <= stock.getOrDefault(entry.getKey(), 0L), "BFS no invented material");
            Deque<List<Long>> queue = new ArrayDeque<>();
            Set<List<Long>> seen = new HashSet<>();
            List<String> keys = List.of("A", "B", "R", "P");
            queue.add(keys.stream().map(key -> stock.getOrDefault(key, 0L)).toList());
            boolean reachable = false;
            while (!queue.isEmpty()) {
                List<Long> current = queue.removeFirst();
                if (!seen.add(current)) continue;
                boolean goal = current.get(3) >= 3;
                for (int i = 0; i < keys.size(); i++) goal &= current.get(i) >= plan.seeds().getOrDefault(keys.get(i), 0L);
                if (goal) {
                    reachable = true;
                    break;
                }
                for (var recipe : recipes) {
                    List<Long> next = new ArrayList<>(current);
                    boolean enabled = true;
                    for (int i = 0; i < keys.size(); i++) {
                        long input = recipe.inputs().getOrDefault(keys.get(i), 0L);
                        if (current.get(i) < input) {
                            enabled = false;
                            break;
                        }
                        next.set(i, current.get(i) - input + recipe.outputs().getOrDefault(keys.get(i), 0L));
                    }
                    if (enabled) queue.add(List.copyOf(next));
                }
            }
            check(reachable, "BFS independently reaches target and all preserved seeds");
        }
        check(feasible > 30, "BFS covers enough feasible cases");
    }

    private static boolean canExecute(PlanStep step, Map<String, GraphRecipe<String>> recipes, Map<String, Long> inventory) {
        if (step instanceof PlanStep.Batch batch) {
            var recipe = recipes.get(batch.recipe());
            for (long i = 0; i < batch.runs(); i++) {
                for (var input : recipe.inputs().entrySet()) if (inventory.getOrDefault(input.getKey(), 0L) < input.getValue()) return false;
                recipe.inputs().forEach((key, count) -> inventory.merge(key, -count, Long::sum));
                recipe.outputs().forEach((key, count) -> inventory.merge(key, count, Long::sum));
            }
        } else if (step instanceof PlanStep.Repeat repeat) {
            for (long i = 0; i < repeat.times(); i++) if (!canExecute(repeat.body(), recipes, inventory)) return false;
        } else for (PlanStep child : ((PlanStep.Sequence) step).children()) if (!canExecute(child, recipes, inventory)) return false;
        return true;
    }

    private static void interpret(GraphPlan<String> plan, long limit) {
        check(plan.patternTimes().values().stream().mapToLong(Long::longValue).sum() <= limit, "bounded oracle");
        Map<String, Long> inventory = new HashMap<>(plan.initial());
        check(canExecute(plan.steps(), plan.recipes(), inventory), "independent witness interpreter");
        eq(true, inventory.getOrDefault(plan.target(), 0L) >= plan.amount() + plan.seeds().getOrDefault(plan.target(), 0L), "goal");
    }

    private static GraphRecipe<String> recipe(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new GraphRecipe<>(id, id, inputs.entrySet().stream().map(e -> new GraphRecipe.Slot<>(e.getKey(), e.getValue())).toList(), outputs);
    }

    private static GraphPlan<String> raw(List<GraphRecipe<String>> recipes, String target, long amount,
                                         Map<String, Long> stock, boolean preserve, boolean force) {
        return new GraphPlanner<>(new GraphCompiler<>(recipes)).plan(target, amount, stock, preserve, force,
                new PlanningBudget(5000, 100000, () -> false));
    }

    private static GraphPlan<String> plan(List<GraphRecipe<String>> recipes, String target, long amount,
                                          Map<String, Long> stock, boolean preserve, boolean force) {
        GraphPlan<String> plan = raw(recipes, target, amount, stock, preserve, force);
        check(plan.feasible(), "feasible " + target + ": " + plan.result() + " " + plan.missing());
        PlanVerifier.verify(plan);
        return plan;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String message) {
        check(Objects.equals(expected, actual), message + ": expected " + expected + ", actual " + actual);
    }
}
