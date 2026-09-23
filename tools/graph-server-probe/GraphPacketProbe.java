package org.gtlcore.test;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingPlanSummaryEntry;
import org.gtlcore.gtlcore.integration.ae2.graph.CraftingTreeCycleNode;
import org.gtlcore.gtlcore.integration.ae2.graph.GtlPatternCatalog;
import org.gtlcore.gtlcore.integration.ae2.graph.PatternFingerprint;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphRequestTracker;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphPlanSummaryView;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphRingView;
import org.gtlcore.gtlcore.integration.ae2.graph.AeGraphPlan;
import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.neuvillette.ae2ct.Config;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import com.neuvillette.ae2ct.api.ICraftingPlanSummary;
import com.neuvillette.ae2ct.api.RecipeHelper;
import io.netty.buffer.Unpooled;

import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import org.leodreamer.wildcard_pattern.gui.GenericGTTag;
import org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic;
import org.leodreamer.wildcard_pattern.wildcard.impl.TagIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.SimpleIOComponent;
import org.leodreamer.wildcard_pattern.wildcard.impl.SimpleFilterComponent;

/** Opt-in test mod for an isolated production Forge server. Never included in Core. */
@Mod("gtlgraphprobe")
public final class GraphPacketProbe {

    public GraphPacketProbe() {
        System.out.println("[Graph Probe] Core code_source=" + AeGraphPlan.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("[Graph Probe] node_fee_class=" + PlanNodeCost.class.getProtectionDomain().getCodeSource().getLocation());
    }

    public static void inspectBindings(IGrid grid, Level level, appeng.api.networking.crafting.ICraftingPlan supplied) {
        var plan = (AeGraphPlan) supplied;
        var host = (org.gtlcore.gtlcore.integration.ae2.graph.GraphCpuHost) java.lang.reflect.Proxy.newProxyInstance(
                GraphPacketProbe.class.getClassLoader(), new Class<?>[]{org.gtlcore.gtlcore.integration.ae2.graph.GraphCpuHost.class},
                (proxy, method, args) -> method.getName().equals("level") ? level : null);
        var adapter = new org.gtlcore.gtlcore.integration.ae2.graph.GtlExecutionAdapter(host, null);
        adapter.services((CraftingService) grid.getCraftingService(), grid.getEnergyService());
        for (var entry : plan.graph().patternTimes().entrySet()) {
            var recipe = plan.graph().recipes().get(entry.getKey());
            var resolved = adapter.resolve(recipe);
            System.out.println("[Graph Reuse] binding=" + recipe.binding() + " runs=" + entry.getValue() + " resolved=" + (resolved != null) + " slots=" + recipe.slots() + " outputs=" + recipe.outputs());
            if (resolved == null) for (var output : recipe.outputs().keySet()) for (var pattern : grid.getCraftingService().getCraftingFor(output)) {
                System.out.println("[Graph Reuse] candidate=" + PatternFingerprint.of(pattern) + " definition=" + pattern.getDefinition());
                for (int i = 0; i < pattern.getInputs().length; i++) {
                    var input = pattern.getInputs()[i];
                    System.out.println("[Graph Reuse] input=" + i + " multiplier=" + input.getMultiplier() + " possible=" + java.util.Arrays.toString(input.getPossibleInputs()));
                }
            }
        }
    }

    /** Fault injection: an addon changes effective data without posting a provider revision. */
    public static void rejectStaleBinding(IGrid grid, Level level, IActionSource source,
            appeng.api.networking.crafting.ICraftingCPU cpu, appeng.api.networking.crafting.ICraftingPlan supplied) throws Exception {
        var plan = (AeGraphPlan) supplied;
        check(!plan.simulation() && !cpu.isBusy(), "Fault test requires a feasible plan and idle CPU");
        var service = (CraftingService) grid.getCraftingService();
        var stock = new KeyCounter(); grid.getStorageService().getInventory().getAvailableStacks(stock);
        var tracker = (GraphRequestTracker) service;
        long version = tracker.gtlcore$graphProviderGeneration();
        var recipe = plan.graph().recipes().get(plan.graph().patternTimes().keySet().iterator().next());
        var pattern = plan.bindings().get(recipe.binding());
        var outputs = pattern.getOutputs();
        var original = outputs[0];
        try {
            outputs[0] = new GenericStack(original.what(), original.amount() + 1);
            var rejected = service.submitJob(plan, null, cpu, false, source);
            check(rejected.errorCode() == appeng.api.networking.crafting.CraftingSubmitErrorCode.INCOMPLETE_PLAN,
                    "Changed pattern was not rejected");
            check(!cpu.isBusy(), "Rejected plan took ownership of CPU");
        } finally { outputs[0] = original; }
        check(tracker.gtlcore$graphProviderGeneration() == version, "Fault unexpectedly posted provider event");
        var after = new KeyCounter(); grid.getStorageService().getInventory().getAvailableStacks(after);
        check(countersEqual(stock, after), "Rejected preflight transferred inventory");
        java.lang.reflect.Field catalogField = null;
        for (var field : service.getClass().getDeclaredFields()) if (field.getType() == GtlPatternCatalog.class) catalogField = field;
        check(catalogField != null, "Missing service catalog"); catalogField.setAccessible(true);
        var catalog = (GtlPatternCatalog) catalogField.get(service);
        var retry = catalog.capture(grid, service, level, source, plan.graph().target(), new PlanningBudget(0, 1_000_000, () -> false));
        check(!retry.cacheHit(), "Rejected binding still reused by next calculation");
        var pending = catalog.begin(grid, service, level, source, plan.graph().target(), new PlanningBudget(0, 1_000_000, () -> false));
        catalog.invalidateBinding(recipe.binding());
        try {
            while (!pending.step()) {}
            throw new AssertionError("Invalidated in-progress snapshot republished stale catalog");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("GRAPH_BINDING_INVALIDATED_DURING_SNAPSHOT"), "Wrong invalidation failure");
        }
        System.out.println("[Graph Reuse] PASS: unannounced pattern mutation rejected, no inventory transfer, retry evicts catalog, pending snapshot cannot republish it");
    }

    public static java.util.concurrent.CompletableFuture<String> benchmark(IGrid grid, Level level, IActionSource source,
            AEKey target, long amount) {
        long start = System.nanoTime();
        var old = new appeng.crafting.CraftingCalculation(level, grid, () -> source, new GenericStack(target, amount),
                appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
        long setup = System.nanoTime() - start;
        long graphStart = System.nanoTime();
        var graph = (java.util.concurrent.CompletableFuture<appeng.api.networking.crafting.ICraftingPlan>) grid.getCraftingService()
                .beginCraftingCalculation(level, () -> source, target, amount,
                        appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
        var graphTime = graph.thenApply(after -> new Object[]{after, System.nanoTime() - graphStart});
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            long run = System.nanoTime();
            var before = old.run();
            long nanos = System.nanoTime() - run;
            return graphTime.thenApply(timed -> {
                var after = (appeng.api.networking.crafting.ICraftingPlan) timed[0];
                check(before.simulation() == after.simulation(), "A/B feasibility differs");
                check(before.finalOutput().equals(after.finalOutput()), "A/B target differs");
                check(countersEqual(before.usedItems(), after.usedItems()), "A/B physical input differs");
                check(countersEqual(before.missingItems(), after.missingItems()), "A/B missing input differs");
                check(before.patternTimes().equals(after.patternTimes()), "A/B recipe counts differ");
                check(before.bytes() == after.bytes(), "A/B CPU capacity differs: " + before.bytes() + " / " + after.bytes());
                return "[Graph A/B] amount=" + amount + " baseline_setup_ms=" + setup / 1e6 + " max_fast_ms=" + nanos / 1e6 +
                        " graph_wall_ms=" + (Long) timed[1] / 1e6 + " graph_solver_ms=" + ((AeGraphPlan) after).graph().planningNanos() / 1e6 +
                        " baseline_bytes=" + before.bytes() + " graph_bytes=" + after.bytes() + " physical_inputs_and_recipe_counts=equal";
            });
        }).thenCompose(result -> result);
    }

    private static boolean countersEqual(KeyCounter a, KeyCounter b) {
        for (var entry : a) if (b.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : b) if (a.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }

    public static void installWildcard(org.gtlcore.gtlcore.integration.wildcard.MEWildcardPatternBufferPartMachine machine,
            ItemStack stack, AEKey fuel, int output) {
        var logic = WildcardPatternLogic.on(stack);
        logic.setIOComponents(WildcardPatternLogic.IO.IN, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.dust), 1),
                new SimpleIOComponent(new GenericStack(fuel, 1))));
        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.ingot), output)));
        machine.getTerminalPatternInventory().setItemDirect(0, stack);
        check(machine.getAvailablePatterns().size() > 10, "Real buffer did not expand wildcard");
        System.out.println("[Graph Probe] real wildcard buffer expanded=" + machine.getAvailablePatterns().size() + " output_each=" + output);
    }

    public static void wildcardSlots(org.gtlcore.gtlcore.integration.wildcard.MEWildcardPatternBufferPartMachine machine,
            IPatternDetails expected, long runs) throws Exception {
        var field = machine.getClass().getDeclaredField("internalSlots"); field.setAccessible(true);
        var slots = (List<?>) field.get(machine);
        int index = machine.getAvailablePatterns().indexOf(expected);
        check(index > 0, "Fixture must target an expanded slot beyond physical slot zero");
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            var method = slot.getClass().getMethod("getItemInventory"); method.setAccessible(true);
            var inventory = (it.unimi.dsi.fastutil.objects.Object2LongMap<?>) method.invoke(slot);
            if (i != index || runs == 0) check(inventory.isEmpty(), "Inputs entered wrong expanded slot");
            else for (var input : expected.getInputs()) {
                var possible = input.getPossibleInputs()[0];
                check(inventory.getLong(possible.what()) == possible.amount() * input.getMultiplier() * runs, "Expanded slot quantity mismatch");
            }
        }
        System.out.println("[Graph Probe] PASS: actual wildcard buffer expanded slot=" + index + " runs=" + runs + " physical_pattern_slots=1");
    }

    public static void tunnelOutput(appeng.parts.p2p.P2PTunnelPart<?> part, boolean output) throws Exception {
        var method = appeng.parts.p2p.P2PTunnelPart.class.getDeclaredMethod("setOutput", boolean.class);
        method.setAccessible(true); method.invoke(part, output);
    }

    public static void catalog(IGrid grid, Level level, IActionSource source, IGridNode provider, AEKey target) throws Exception {
        var service = (CraftingService) grid.getCraftingService();
        var catalog = new GtlPatternCatalog();
        var first = catalog.capture(grid, service, level, source, target, new PlanningBudget(0, 1_000_000, () -> false));
        var warm = catalog.capture(grid, service, level, source, target, new PlanningBudget(0, 1_000_000, () -> false));
        check(warm.cacheHit() && catalogIdentity(first.structure()) == catalogIdentity(warm.structure()), "Warm structure was not shared");
        var field = CraftingService.class.getDeclaredField("craftingProviders");
        field.setAccessible(true);
        var providers = (NetworkCraftingProviders) field.get(service);
        var tracker = (GraphRequestTracker) service;
        long revision = tracker.gtlcore$graphProviderGeneration();
        providers.removeProvider(provider);
        providers.addProvider(provider);
        check(tracker.gtlcore$graphProviderGeneration() == revision + 2, "Two same-tick provider edits lost a revision");
        var after = catalog.capture(grid, service, level, source, target, new PlanningBudget(0, 1_000_000, () -> false));
        check(after.cacheHit() && catalogIdentity(after.structure()) == catalogIdentity(first.structure()), "Unchanged dependencies discarded compiled graph");
        check(first.stock().equals(after.stock()), "Structure reuse changed stock snapshot");
        System.out.println("[Graph Probe] PASS: actual provider remove/add in one tick advances twice; dependency-identical structure cache retained");
    }

    private static Object catalogIdentity(Object structure) throws Exception {
        try { return structure.getClass().getMethod("catalog").invoke(structure); }
        catch (NoSuchMethodException oldBuild) { return structure.getClass().getMethod("compiler").invoke(structure); }
    }

    /** Real addon expansion, not a hand-written replacement for wildcard semantics. */
    public static void wildcards(Level level, ItemStack wildcard, AEKey fuel, AEKey product) throws Exception {
        var logic = WildcardPatternLogic.on(wildcard);
        logic.setIOComponents(WildcardPatternLogic.IO.IN, List.of(
                new TagIOComponent(GenericGTTag.item(TagPrefix.dust), 1),
                new SimpleIOComponent(new GenericStack(fuel, 1))));
        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(
                new TagIOComponent(GenericGTTag.item(TagPrefix.ingot), 1)));
        var outward = logic.generateAllPatterns(level).toList();
        check(outward.size() > 10, "Wildcard did not expand registered materials");
        var normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class,
                String.class, KeyCounter.class, Level.class);
        normalize.setAccessible(true);
        var fingerprints = new HashSet<String>();
        var recipes = new ArrayList<GraphRecipe<AEKey>>();
        for (var pattern : outward) {
            String id = PatternFingerprint.of(pattern);
            check(fingerprints.add(id), "Different wildcard materials collapsed into one identity");
            @SuppressWarnings("unchecked")
            var variants = (List<GraphRecipe<AEKey>>) normalize.invoke(null, pattern, id, new KeyCounter(), level);
            check(variants.size() == 1, "Concrete wildcard processing pattern was not normalized");
            recipes.addAll(variants);
        }
        logic.setFilterComponents(List.of(new SimpleFilterComponent(GTMaterials.Iron, true)));
        var ironForward = logic.generateAllPatterns(level).findFirst().orElseThrow();
        AEKey dust = ironForward.getInputs()[0].getPossibleInputs()[0].what();
        // Input condensation need not preserve slot order: find the material input.
        for (var input : ironForward.getInputs()) for (var possible : input.getPossibleInputs())
            if (!possible.what().equals(fuel)) dust = possible.what();
        AEKey ingot = ironForward.getOutputs()[0].what();
        logic.setIOComponents(WildcardPatternLogic.IO.IN, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.ingot), 1)));
        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(
                new TagIOComponent(GenericGTTag.item(TagPrefix.dust), 1), new SimpleIOComponent(new GenericStack(product, 1))));
        var back = logic.generateAllPatterns(level).findFirst().orElseThrow();
        String oldBinding = PatternFingerprint.of(back);
        @SuppressWarnings("unchecked")
        var returning = (List<GraphRecipe<AEKey>>) normalize.invoke(null, back, oldBinding, new KeyCounter(), level);
        recipes.addAll(returning);
        var compiler = new GraphCompiler<>(recipes);
        var graph = compiler.compile(product, Map.of(), java.util.Set.of(), new PlanningBudget(0, 1_000_000, () -> false));
        check(graph.regions().stream().anyMatch(region -> region.cyclic() && region.recipes().size() == 2), "Expanded wildcard back-edge was lost");
        var planner = new GraphPlanner<>(compiler);
        var plan = planner.plan(product, 100, Map.of(dust, 1L, fuel, 100L), true, true, new PlanningBudget(0, 1_000_000, () -> false));
        check(plan.feasible(), "Productive wildcard recovery loop rejected: " + plan.result());
        PlanVerifier.verify(plan);
        check(plan.initial().get(dust) == 1 && plan.initial().get(fuel) == 100, "Wildcard initial inventory changed");
        check(plan.seeds().get(dust) == 1, "Wildcard catalyst was not preserved");
        var unseeded = planner.plan(product, 100, Map.of(fuel, 100L), true, true, new PlanningBudget(0, 1_000_000, () -> false));
        check(!unseeded.feasible(), "Wildcard cycle created a seed from future output");
        logic.setIOComponents(WildcardPatternLogic.IO.OUT, List.of(new TagIOComponent(GenericGTTag.item(TagPrefix.dust), 2)));
        var changed = logic.generateAllPatterns(level).findFirst().orElseThrow();
        check(!oldBinding.equals(PatternFingerprint.of(changed)), "Changing wildcard output reused stale binding");
        System.out.println("[Graph Probe] PASS: real wildcard expansion (" + outward.size() + " materials), distinct fingerprints, two-recipe recovery SCC, exact seed/fuel, no-seed rejection and changed-output identity; ingot=" + ingot);
    }

    public static CraftingPlanSummary roundTrip(CraftingPlanSummary source) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.write(buffer);
            var copy = CraftingPlanSummary.read(buffer);
            check(buffer.readableBytes() == 0, "Unread summary bytes");
            check(source.getUsedBytes() == copy.getUsedBytes(), "CPU bytes changed");
            check(source.isSimulation() == copy.isSimulation(), "Simulation flag changed");
            check(source.getEntries().size() == copy.getEntries().size(), "Entry count changed");
            check(java.util.Objects.equals(((GraphPlanSummaryView) source).gtlcore$graphPlanId(),
                    ((GraphPlanSummaryView) copy).gtlcore$graphPlanId()), "Graph plan identity changed");
            for (int i = 0; i < source.getEntries().size(); i++) {
                var a = source.getEntries().get(i);
                var b = copy.getEntries().get(i);
                check(a.getWhat().equals(b.getWhat()), "Entry key changed");
                check(a.getMissingAmount() == b.getMissingAmount(), "Missing amount changed");
                check(a.getStoredAmount() == b.getStoredAmount(), "Stored amount changed");
                check(a.getCraftAmount() == b.getCraftAmount(), "Craft amount changed");
                var ga = (ICraftingPlanSummaryEntry) a;
                var gb = (ICraftingPlanSummaryEntry) b;
                check(ga.gtlcore$getGraphSeed() == gb.gtlcore$getGraphSeed(), "Seed changed");
                check(ga.gtlcore$isMissingGraphSeed() == gb.gtlcore$isMissingGraphSeed(), "Missing seed changed");
                check(ga.gtlcore$getCraftTimes() == gb.gtlcore$getCraftTimes(), "Runs changed");
            }
            var originalTree = ((ICraftingPlanSummary) source).getJob();
            var copiedTree = ((ICraftingPlanSummary) copy).getJob();
            check(originalTree != null && copiedTree != null, "Addon tree data absent");
            check(originalTree.output.equals(copiedTree.output), "Addon target changed");
            check(originalTree.recipes.equals(copiedTree.recipes), "Addon recipes changed");
            System.out.println("[Graph Probe] PASS: AE2CT + Core summary packet round trip, simulation=" + copy.isSimulation());
            return copy;
        } finally {
            buffer.release();
        }
    }

    public static void trees(AEKey a, AEKey b, AEKey c, AEKey d) {
        // A dedicated server does not load CLIENT config. Supply default config only
        // in this test fixture so the actual addon can lay out nodes headlessly.
        Config.CONFIG.setConfig(CommentedConfig.inMemory());
        for (boolean compact : new boolean[] {false, true}) {
            Config.USE_COMPACT_TREE.set(compact);
            var entries = List.of(entry(a), entry(b), entry(c), entry(d));
            var self = new CraftingTreeHelper(new RecipeHelper(stack(a, 1_000_000_000_000L), List.of(
                    new RecipeHelper.Recipe(List.of(stack(a, 1)), List.of(stack(a, 2)), 1_000_000_000_000L))), entries);
            checkTree(self.build(false), 2, 1, 1);
            checkTree(self.build(false), 2, 1, 1);
            var pair = new CraftingTreeHelper(new RecipeHelper(stack(a, 10), List.of(
                    new RecipeHelper.Recipe(List.of(stack(b, 1)), List.of(stack(a, 1)), 10L),
                    new RecipeHelper.Recipe(List.of(stack(a, 1)), List.of(stack(b, 1)), 10L))), entries);
            checkTree(pair.build(false), 3, 1, 2);
            var diamond = new CraftingTreeHelper(new RecipeHelper(stack(a, 10), List.of(
                    new RecipeHelper.Recipe(List.of(stack(b, 1), stack(c, 1)), List.of(stack(a, 1)), 10L),
                    new RecipeHelper.Recipe(List.of(stack(d, 1)), List.of(stack(b, 1)), 10L),
                    new RecipeHelper.Recipe(List.of(stack(d, 1)), List.of(stack(c, 1)), 10L))), entries);
            checkTree(diamond.build(false), 5, 0, 0);
        }
        System.out.println("[Graph Probe] PASS: actual AE2CT self-cycle (10^12), two-node cycle, repeated builds and shared DAG, compact/loose layouts");
    }

    public static void ring(AeGraphPlan plan) {
        var view = plan.display();
        check(view == plan.display(), "Read-only display was rebuilt");
        int next = 0, total = view.page(0).total();
        long bytes = 0;
        do {
            var page = view.page(next);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                GraphRingView.write(page, buffer);
                bytes += buffer.readableBytes();
                check(page.equals(GraphRingView.read(buffer)), "Crafting Ring page changed in packet");
                check(buffer.readableBytes() == 0, "Ring unread bytes");
            } finally { buffer.release(); }
            next += page.rows().size();
        } while (next < total);
        check(next == total, "Graph rows lost");
        System.out.println("[Graph Probe] PASS: Crafting Ring selected plan pages and cached view, rows=" + total + " bytes=" + bytes);
    }

    private static CraftingPlanSummaryEntry entry(AEKey key) {
        return new CraftingPlanSummaryEntry(key, 0, 0, 1_000_000_000_000L);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static void checkTree(CraftingTreeHelper.NodeManager tree, int nodes, int cycles, int distance) {
        var queue = new ArrayDeque<CraftingTreeHelper.Node>();
        queue.add(tree.root);
        int count = 0, references = 0;
        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            check(++count <= nodes, "Unexpected tree expansion");
            var marked = (CraftingTreeCycleNode) node;
            if (marked.gtlcore$cycleDistance() > 0) {
                references++;
                check(marked.gtlcore$cycleDistance() == distance, "Wrong cycle ancestor");
                check(node.subNodes.isEmpty(), "Cycle reference expanded children");
            }
            queue.addAll(node.subNodes);
        }
        check(count == nodes && references == cycles, "Tree shape/cycle labels changed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Probe assertion failed: " + message);
    }
}
