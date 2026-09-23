package org.gtlcore.test;

import org.gtlcore.gtlcore.integration.ae2.graph.AeGraphPlan;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphPlanningRequest;
import org.gtlcore.gtlcore.integration.ae2.graph.core.PlanningBudget;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Synthetic topology, real AE patterns, network inventory and production planning paths. Never ships. */
public final class GraphStressProbe {
    private static final java.util.concurrent.ExecutorService OLD = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Graph-Stress-MAX_FAST"); thread.setDaemon(true); return thread;
    });
    private static IGridNode mounted;
    private static NetworkCraftingProviders registry;
    private static List<IPatternDetails> fixture = List.of();
    private static GenericStack initial;
    private static appeng.api.networking.storage.IStorageService fixtureStorage;
    private static appeng.api.storage.IStorageProvider stockProvider;

    public static void install(IGrid grid, List<IPatternDetails> supplied, GenericStack stock) throws Exception {
        install(grid, supplied, stock, 0);
    }

    public static void install(IGrid grid, List<IPatternDetails> supplied, GenericStack stock, int unrelatedVariants) throws Exception {
        remove();
        var patterns = List.copyOf(supplied);
        fixture = patterns;
        initial = stock;
        // A real mounted MEStorage with a long inventory, avoiding a cell-size
        // limit in the 100M / >int fixtures. Optional unrelated NBT variants have
        // the same primary item, exercising exact versus fuzzy input capture.
        fixtureStorage = grid.getStorageService();
        var extraStock = new java.util.HashMap<AEKey, Long>();
        for (int i = 0; i < unrelatedVariants; i++) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.m_128405_("unrelated_graph_stock", i);
            extraStock.put(appeng.api.stacks.AEItemKey.of((net.minecraft.world.item.Item) stock.what().getPrimaryKey(), tag), 100L);
        }
        var storage = new appeng.api.storage.MEStorage() {
            private long held = stock.amount();
            public net.minecraft.network.chat.Component getDescription() { return stock.what().getDisplayName(); }
            public synchronized void getAvailableStacks(KeyCounter out) {
                out.add(stock.what(), held);
                extraStock.forEach(out::add);
            }
            public synchronized long extract(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
                if (!key.equals(stock.what())) {
                    long available = extraStock.getOrDefault(key, 0L);
                    long taken = Math.min(available, amount);
                    if (taken != 0 && mode == appeng.api.config.Actionable.MODULATE) extraStock.put(key, available - taken);
                    return taken;
                }
                long taken = Math.min(held, amount);
                if (mode == appeng.api.config.Actionable.MODULATE) held -= taken;
                return taken;
            }
        };
        stockProvider = mounts -> mounts.mount(storage);
        fixtureStorage.addGlobalStorageProvider(stockProvider);
        ICraftingProvider provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return patterns; }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                throw new AssertionError("Planning benchmark must not submit jobs");
            }
        };
        mounted = (IGridNode) Proxy.newProxyInstance(GraphStressProbe.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getService" -> args[0] == ICraftingProvider.class ? provider : null;
                    case "getGrid" -> grid;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Isolated stress fixture provider";
                    default -> null;
                });
        var field = CraftingService.class.getDeclaredField("craftingProviders"); field.setAccessible(true);
        registry = (NetworkCraftingProviders) field.get(grid.getCraftingService());
        registry.addProvider(mounted);
        System.out.println("[Graph Stress] fixture_registered patterns=" + patterns.size() + " unrelated_stock_variants=" + unrelatedVariants);
    }

    public static void remove() {
        if (registry != null && mounted != null) registry.removeProvider(mounted);
        registry = null; mounted = null;
        if (fixtureStorage != null && stockProvider != null) fixtureStorage.removeGlobalStorageProvider(stockProvider);
        fixtureStorage = null; stockProvider = null;
    }

    public record Sample(ICraftingPlan plan, long wall, long setup, long work, long snapshot, String failure) {}

    public static CompletableFuture<Sample> baseline(IGrid grid, Level level, IActionSource source, AEKey key, long amount) {
        var tickWindow = GraphCaptureTimingProbe.begin("MAX_FAST");
        long start = System.nanoTime();
        CraftingCalculation calculation;
        try {
            calculation = new CraftingCalculation(level, grid, () -> source, new GenericStack(key, amount), CalculationStrategy.REPORT_MISSING_ITEMS);
        } catch (RuntimeException | StackOverflowError error) {
            GraphCaptureTimingProbe.finish(tickWindow);
            return CompletableFuture.completedFuture(new Sample(null, System.nanoTime()-start, System.nanoTime()-start, 0, 0, error.toString()));
        }
        long setup = System.nanoTime()-start;
        CompletableFuture<Sample> result = new CompletableFuture<>();
        var task = OLD.submit(() -> {
            long work = System.nanoTime();
            Sample sample;
            try {
                var plan = calculation.run();
                sample = new Sample(plan, System.nanoTime()-start, setup, System.nanoTime()-work, 0, "");
            } catch (RuntimeException | StackOverflowError error) {
                sample = new Sample(null, System.nanoTime()-start, setup, System.nanoTime()-work, 0, error.toString());
            }
            GraphCaptureTimingProbe.finish(tickWindow);
            result.complete(sample);
        });
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
            GraphCaptureTimingProbe.finish(tickWindow);
            if (result.complete(new Sample(null, System.nanoTime()-start, setup, 0, 0, "TIMEOUT_30S"))) task.cancel(true);
        });
        return result;
    }

    public static CompletableFuture<Sample> graph(IGrid grid, Level level, IActionSource source, AEKey key, long amount) {
        var tickWindow = GraphCaptureTimingProbe.begin("GRAPH");
        long start = System.nanoTime();
        var request = (GraphPlanningRequest) grid.getCraftingService().beginCraftingCalculation(level, () -> source, key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        long setup = System.nanoTime()-start;
        CompletableFuture<Sample> result = request.handle((plan, error) -> {
            GraphCaptureTimingProbe.finish(tickWindow);
            long snapshot = request.budget().metrics().activeNanos().getOrDefault(PlanningBudget.Phase.SNAPSHOT, 0L);
            return new Sample(plan, System.nanoTime()-start, setup, plan instanceof AeGraphPlan graph ? graph.graph().planningNanos() : 0,
                    snapshot, error == null ? "" : error.toString());
        });
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
            if (result.complete(new Sample(null, System.nanoTime()-start, setup, 0, 0, "TIMEOUT_30S"))) request.cancel(true);
        });
        return result;
    }

    public static boolean report(String label, int sample, long amount, Sample old, Sample graph) {
        boolean comparable = old.plan() != null && graph.plan() != null;
        boolean materials = comparable && old.plan().finalOutput().equals(graph.plan().finalOutput()) &&
                old.plan().simulation() == graph.plan().simulation() && equal(old.plan().usedItems(), graph.plan().usedItems()) &&
                equal(old.plan().missingItems(), graph.plan().missingItems()) && equal(old.plan().emittedItems(), graph.plan().emittedItems());
        boolean counts = comparable && old.plan().patternTimes().equals(graph.plan().patternTimes());
        boolean bytes = comparable && old.plan().bytes() == graph.plan().bytes();
        boolean oldValid = old.plan() != null && valid(old.plan()), graphValid = graph.plan() != null && valid(graph.plan());
        if(label.startsWith("chain") && graphValid) {
            int depth=Integer.parseInt(label.substring(5));
            long exact=Math.addExact(Math.multiplyExact(2L*depth+1,amount),8L*(depth+1));
            if(graph.plan().bytes()!=exact) throw new AssertionError("Graph chain CPU charge differs from exact fixture formula");
            if(old.plan()!=null && old.plan().bytes()!=exact)
                System.out.println("[Graph Stress] exact_chain_bytes="+exact+" legacy_bytes="+old.plan().bytes()+" graph_bytes="+graph.plan().bytes());
        }
        System.out.printf(Locale.ROOT,
                "[Graph Stress] label=%s sample=%d amount=%d old_wall_ms=%.4f old_setup_ms=%.4f old_run_ms=%.4f graph_wall_ms=%.4f graph_entry_ms=%.4f graph_solver_ms=%.4f graph_snapshot_ms=%.4f old_bytes=%d graph_bytes=%d materials_equal=%s recipes_equal=%s bytes_equal=%s old_valid=%s graph_valid=%s old_raw=%d graph_raw=%d old_failure=%s graph_failure=%s%n",
                label, sample, amount, old.wall()/1e6, old.setup()/1e6, old.work()/1e6, graph.wall()/1e6, graph.setup()/1e6, graph.work()/1e6, graph.snapshot()/1e6,
                old.plan() == null ? -1 : old.plan().bytes(), graph.plan() == null ? -1 : graph.plan().bytes(), materials, counts, bytes, oldValid, graphValid,
                old.plan() == null ? -1 : old.plan().usedItems().get(initial.what()), graph.plan() == null ? -1 : graph.plan().usedItems().get(initial.what()), old.failure(), graph.failure());
        // Different rounding/aggregation can select different valid plans. Preserve
        // the inequality in the report; never call these samples equivalent.
        // Above 2^53 the legacy double byte accumulator can round an otherwise
        // equivalent plan. Keep that difference visible; independent material
        // validation determines whether another sample is safe to measure.
        return comparable && oldValid && graphValid;
    }

    /** Independent fixture interpreter: recipes are installed in producer-before-consumer order. */
    private static boolean valid(ICraftingPlan plan) {
        if (plan.simulation() || !plan.missingItems().isEmpty() || !plan.emittedItems().isEmpty()) return false;
        KeyCounter held = new KeyCounter();
        for (var entry : plan.usedItems()) {
            if (!initial.what().equals(entry.getKey()) || entry.getLongValue() > initial.amount() || entry.getLongValue() < 0) return false;
            held.add(entry.getKey(), entry.getLongValue());
        }
        int selected = 0;
        for (var pattern : fixture) {
            long runs = plan.patternTimes().getOrDefault(pattern, 0L);
            if (runs == 0) continue;
            if (runs < 0) return false;
            selected++;
            for (var input : pattern.getInputs()) {
                if (input.getPossibleInputs().length != 1) throw new AssertionError("Fixture must have exact inputs");
                var item = input.getPossibleInputs()[0];
                long count = Math.multiplyExact(Math.multiplyExact(item.amount(), input.getMultiplier()), runs);
                if (held.get(item.what()) < count) return false;
                held.add(item.what(), -count);
            }
            for (var output : pattern.getOutputs()) held.add(output.what(), Math.multiplyExact(output.amount(), runs));
        }
        return selected == plan.patternTimes().size() && held.get(plan.finalOutput().what()) >= plan.finalOutput().amount();
    }

    private static boolean equal(KeyCounter a, KeyCounter b) {
        for (var entry : a) if (entry.getLongValue() != b.get(entry.getKey())) return false;
        for (var entry : b) if (entry.getLongValue() != a.get(entry.getKey())) return false;
        return true;
    }
}
