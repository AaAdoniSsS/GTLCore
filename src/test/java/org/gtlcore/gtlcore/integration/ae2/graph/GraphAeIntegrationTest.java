package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.config.AECraftingEngine;
import org.gtlcore.gtlcore.config.AEGraphSeedPolicy;
import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.RegistryBuilder;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import dev.toma.configuration.config.format.YamlFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uses real AE keys and Minecraft NBT classes on the resolved mod classpath. */
public final class GraphAeIntegrationTest {

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The standalone harness installs the same real AE key types in a real
        // Forge registry. This replaces mod startup, not NBT/key implementation.
        var builder = new RegistryBuilder<AEKeyType>().setName(new ResourceLocation("gtlcore", "graph_test_keys"));
        var create = RegistryBuilder.class.getDeclaredMethod("create");
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (net.minecraftforge.registries.IForgeRegistry<AEKeyType>) create.invoke(builder);
        AEKeyTypesInternal.setRegistry(() -> registry);
        AEKeyTypesInternal.register(AEKeyType.items());
        AEKeyTypesInternal.register(AEKeyType.fluids());
        AEKey input = AEItemKey.of(Items.IRON_INGOT), output = AEItemKey.of(Items.IRON_BLOCK);
        long amount = 9_007_199_254_740_993L;
        var recipe = new GraphRecipe<AEKey>("test", "test", List.of(new GraphRecipe.Slot<>(input, 1)), Map.of(output, 1L));
        var plan = new GraphPlanner<>(new GraphCompiler<>(List.of(recipe))).plan(output, amount, Map.of(input, amount),
                true, true, new PlanningBudget(5000, 10000, () -> false));
        if (!plan.feasible()) throw new AssertionError(plan.result());
        var runtime = new GraphJobRuntime<>(plan, plan.initial(), Map.of());
        CompoundTag saved = GraphJobCodec.write(runtime.snapshot());
        var loaded = new GraphJobRuntime<>(GraphJobCodec.read(saved));
        if (!saved.equals(GraphJobCodec.write(loaded.snapshot()))) throw new AssertionError("Actual NBT round trip changed the task");
        System.out.println("AE key/NBT integration round trip passed: " + amount);
        var waterTag = new CompoundTag();
        waterTag.putString("variant", "one");
        var water = AEFluidKey.of(Fluids.WATER, waterTag);
        var otherTag = new CompoundTag();
        otherTag.putString("variant", "two");
        if (water.equals(AEFluidKey.of(Fluids.WATER, otherTag))) throw new AssertionError("Fluid NBT collapsed");
        CompoundTag fluids = new CompoundTag();
        fluids.put("fluids", GraphJobCodec.amounts(Map.of(water, 3_000_000_000L)));
        if (!GraphJobCodec.amounts(fluids, "fluids").equals(Map.of(water, 3_000_000_000L))) throw new AssertionError("Fluid NBT/long changed");

        CompoundTag patternTag = new CompoundTag();
        ListTag inputs = new ListTag(), outputs = new ListTag();
        inputs.add(GenericStack.writeTag(new GenericStack(water, 1000)));
        inputs.add(GenericStack.writeTag(new GenericStack(input, 2)));
        outputs.add(GenericStack.writeTag(new GenericStack(output, 1)));
        patternTag.put("in", inputs);
        patternTag.put("out", outputs);
        ItemStack encoded = new ItemStack(Items.PAPER);
        encoded.setTag(patternTag);
        IPatternDetails actualPattern = new AEProcessingPattern(AEItemKey.of(encoded));
        var normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class, String.class, KeyCounter.class, Level.class);
        normalize.setAccessible(true);
        @SuppressWarnings("unchecked")
        var variants = (List<GraphRecipe<AEKey>>) normalize.invoke(null, actualPattern, PatternFingerprint.of(actualPattern), new KeyCounter(), null);
        if (variants.size() != 1 || !variants.get(0).inputs().equals(Map.of(water, 1000L, input, 2L))) throw new AssertionError("Real processing template units/multipliers changed");
        System.out.println("Actual AE processing-pattern normalization and fluid NBT/long tests passed");
        confirmationViews(variants.get(0), actualPattern, water, input, output);
        graphRingViews(input, output, water);

        var yaml = new YamlFormat();
        yaml.writeEnum("ae2CraftingEngine", AECraftingEngine.GRAPH);
        yaml.writeEnum("ae2GraphSeedPolicy", AEGraphSeedPolicy.ALLOW_CONSUME);
        var file = new java.io.File("graph-config-roundtrip.yml");
        yaml.writeFile(file);
        var read = new YamlFormat();
        read.readFile(file);
        if (read.readEnum("ae2CraftingEngine", AECraftingEngine.class) != AECraftingEngine.GRAPH ||
                read.readEnum("ae2GraphSeedPolicy", AEGraphSeedPolicy.class) != AEGraphSeedPolicy.ALLOW_CONSUME)
            throw new AssertionError("Configuration enum round trip failed");
        System.out.println("Actual YAML enum read/write passed: GRAPH / ALLOW_CONSUME");
        mixedInputsAndReturns();
        PatternFingerprintTest.run();
        CapturedPatternCatalogTest.run();
        GraphAeAdapterTest.run();
        AsyncOutputRegressionTest.run();
    }

    private static void graphRingViews(AEKey input, AEKey output, AEKey water) {
        long amount = 9_007_199_254_740_993L;
        var recipe = new GraphRecipe<AEKey>("recover", "recover", List.of(new GraphRecipe.Slot<>(input, 1)), Map.of(input, 1L, output, 1L));
        var steps = new PlanStep.Repeat(new PlanStep.Batch("recover", 1), amount);
        var plan = new GraphPlan<>(output, amount, true, steps, Map.of("recover", recipe), Map.of(input, 1L),
                Map.of(input, 1L), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        PlanVerifier.verify(plan);
        var id = java.util.UUID.randomUUID();
        var view = new GraphRingView(id, plan, plan.patternTimes());
        var page = view.page(0);
        if (page.total() != 4 || page.rows().size() != 4) throw new AssertionError("Display expanded production count");
        if (page.rows().stream().filter(row -> row.kind() == GraphRingView.Kind.RECIPE).findFirst().orElseThrow().count() != amount)
            throw new AssertionError("Display rounded selected run count");
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            GraphRingView.write(page, buffer);
            if (!page.equals(GraphRingView.read(buffer)) || buffer.readableBytes() != 0) throw new AssertionError("Ring packet roundtrip");
        } finally {
            buffer.release();
        }
        boolean immutable = false;
        try {
            page.rows().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        if (!immutable) throw new AssertionError("Writable plan view");
        java.util.ArrayList<PlanStep> children = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) children.add(new PlanStep.Batch("recover", 1));
        var large = new GraphPlan<>(output, 150, true, new PlanStep.Sequence(children), Map.of("recover", recipe),
                Map.of(input, 1L), Map.of(input, 1L), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        view = new GraphRingView(id, large, large.patternTimes());
        int received = 0;
        while (received < view.page(0).total()) {
            page = view.page(received);
            buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                GraphRingView.write(page, buffer);
                if (!page.equals(GraphRingView.read(buffer))) throw new AssertionError("Display page changed");
            } finally {
                buffer.release();
            }
            received += page.rows().size();
        }
        if (received != 153) throw new AssertionError("Lost paged graph rows");
        try (var scheduler = new PlanningScheduler(1, 2, 1, 2_000_000L)) {
            var scheduled = scheduler.submit(new GraphRingView.Builder(id, large, large.patternTimes()),
                    new PlanningBudget(0, 10000, () -> false)).get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!view.page(0).equals(scheduled.page(0)) || !view.page(128).equals(scheduled.page(128)) || scheduler.slices() < 150)
                throw new AssertionError("Sliced view lost rows or did not yield");
            boolean limited = false;
            try {
                scheduler.submit(new GraphRingView.Builder(id, large, large.patternTimes()),
                        new PlanningBudget(0, 10000, 128, () -> false, System::nanoTime)).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                limited = e.getCause() instanceof PlanningBudget.Exhausted;
            }
            if (!limited) throw new AssertionError("View ignored memory budget");
        } catch (Exception e) {
            throw new AssertionError("Graph view scheduler", e);
        }
        System.out.println("Crafting Ring: immutable selected view, exact >2^53 amounts, compressed recovery and paged packet roundtrips passed");
    }

    private static void confirmationViews(GraphRecipe<AEKey> recipe, IPatternDetails pattern,
                                          AEKey water, AEKey input, AEKey output) {
        var planner = new GraphPlanner<>(new GraphCompiler<>(List.of(recipe)));
        var stock = Map.of(water, 1000L, input, 2L);
        var missing = planner.plan(output, 4, stock, true, true, new PlanningBudget(5000, 10000, () -> false));
        var nativePlan = new AeGraphPlan(missing, Map.of(recipe.binding(), pattern), Set.of(), stock);
        var display = nativePlan.summaryView();
        if (!display.simulation() || display.usedItems().get(water) != 1000 || display.usedItems().get(input) != 2 ||
                display.missingItems().get(water) != 3000 || display.missingItems().get(input) != 6)
            throw new AssertionError("Missing stock was counted twice in confirmation");
        if (display.bytes() != nativePlan.bytes() || !display.finalOutput().equals(nativePlan.finalOutput()) ||
                !display.patternTimes().equals(nativePlan.patternTimes()))
            throw new AssertionError("Summary view lost plan data");
        display.usedItems().add(input, 99);
        if (nativePlan.usedItems().get(input) != 2) throw new AssertionError("Display mutated the execution plan");
        if (nativePlan.graph() != missing || nativePlan.graph().feasible()) throw new AssertionError("Display replaced execution ownership");

        var available = Map.of(water, 4000L, input, 8L);
        var feasible = planner.plan(output, 4, available, true, true, new PlanningBudget(5000, 10000, () -> false));
        var mutableBindings = new java.util.LinkedHashMap<String, IPatternDetails>();
        mutableBindings.put(recipe.binding(), pattern);
        var executable = new AeGraphPlan(feasible, mutableBindings, Set.of(), available);
        mutableBindings.clear();
        if (executable.bindings().get(recipe.binding()) != pattern || executable.patternTimes().get(pattern) != 4L)
            throw new AssertionError("Plan retained mutable caller bindings or lost recipe counts");
        try {
            executable.patternTimes().clear();
            throw new AssertionError("Pattern summary is mutable");
        } catch (UnsupportedOperationException expected) {}
        try {
            executable.bindings().clear();
            throw new AssertionError("Plan bindings are mutable");
        } catch (UnsupportedOperationException expected) {}
        var view = executable.summaryView();
        if (view.simulation() || view.usedItems().get(water) != 4000 || view.usedItems().get(input) != 8 ||
                !view.missingItems().isEmpty())
            throw new AssertionError("Feasible summary changed extraction requirements");
        AEKey lava = AEFluidKey.of(Fluids.LAVA);
        var mixed = new GraphRecipe<AEKey>("byte-units", "byte-units",
                List.of(new GraphRecipe.Slot<>(water, 1), new GraphRecipe.Slot<>(lava, 1), new GraphRecipe.Slot<>(input, 2)), Map.of(output, 1L));
        for (long count : new long[] { 1, 1001, 123456 }) {
            var graph = new GraphPlan<>(output, count, true, new PlanStep.Batch(mixed.id(), count), Map.of(mixed.id(), mixed),
                    Map.of(water, count, lava, count, input, count * 2), Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
            // Binding is not executed here; only the immutable quantity view is tested.
            var charged = new AeGraphPlan(graph, Map.of(mixed.binding(), pattern), Set.of(), graph.initial());
            long expected = (long) Math.ceil(32 + count + 3.0 * count * 8 / input.getType().getAmountPerByte() +
                    2.0 * count * 8 / water.getType().getAmountPerByte());
            if (charged.bytes() != expected) throw new AssertionError("Item/fluid fees were rounded per key instead of once: " + charged.bytes());
        }
        System.out.println("Confirmation views: real CraftingPlan compatibility, missing/used partition and execution isolation passed");
    }

    private static void mixedInputsAndReturns() throws Exception {
        AEKey oak = AEItemKey.of(Items.OAK_PLANKS), birch = AEItemKey.of(Items.BIRCH_PLANKS);
        AEKey bucket = AEItemKey.of(Items.BUCKET), bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        // Controlled custom-pattern fixture using the real AE input API: one
        // condensed slot needs four equivalent units, with choice-dependent returns.
        IPatternDetails pattern = new IPatternDetails() {

            @Override
            public boolean supportsPushInputsToExternalInventory() {
                return false;
            }

            @Override
            public AEItemKey getDefinition() {
                return AEItemKey.of(Items.PAPER);
            }

            @Override
            public GenericStack[] getOutputs() {
                return new GenericStack[] { new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1) };
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[] { new IInput() {

                    @Override
                    public GenericStack[] getPossibleInputs() {
                        return new GenericStack[] { new GenericStack(oak, 1), new GenericStack(birch, 1) };
                    }

                    @Override
                    public long getMultiplier() {
                        return 4;
                    }

                    @Override
                    public boolean isValid(AEKey key, Level level) {
                        return key.equals(oak) || key.equals(birch);
                    }

                    @Override
                    public AEKey getRemainingKey(AEKey key) {
                        return key.equals(oak) ? bucket : bottle;
                    }
                } };
            }
        };
        var normalize = GtlPatternCatalog.class.getDeclaredMethod("normalize", IPatternDetails.class, String.class, KeyCounter.class, Level.class);
        normalize.setAccessible(true);
        @SuppressWarnings("unchecked")
        var variants = (List<GraphRecipe<AEKey>>) normalize.invoke(null, pattern, PatternFingerprint.of(pattern), new KeyCounter(), null);
        var mixed = variants.stream().filter(recipe -> recipe.inputs().equals(Map.of(oak, 2L, birch, 2L))).findFirst().orElseThrow();
        if (mixed.slots().stream().anyMatch(slot -> slot.inputSlot() != 0)) throw new AssertionError("Condensed slot routing changed");
        if (mixed.outputs().get(bucket) != 2 || mixed.outputs().get(bottle) != 2) throw new AssertionError("Returns not bound to selected inputs");
        var inputs = GtlExecutionAdapter.class.getDeclaredMethod("inputs", GraphRecipe.class, long.class, int.class);
        inputs.setAccessible(true);
        KeyCounter[] dispatched = (KeyCounter[]) inputs.invoke(null, mixed, 3L, 1);
        if (dispatched.length != 1 || dispatched[0].get(oak) != 6 || dispatched[0].get(birch) != 6) throw new AssertionError("Mixed input batch incorrectly routed");
        CompoundTag first = new CompoundTag(), second = new CompoundTag();
        first.putInt("a", 1);
        first.putInt("b", 2);
        second.putInt("b", 2);
        second.putInt("a", 1);
        if (!PatternFingerprint.key(AEFluidKey.of(Fluids.WATER, first)).equals(PatternFingerprint.key(AEFluidKey.of(Fluids.WATER, second))))
            throw new AssertionError("Fingerprint depends on compound insertion order");
        System.out.println("AE API mixed-slot/choice-dependent-return fixture and canonical NBT fingerprint passed");
    }
}
