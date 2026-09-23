package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.api.crafting.IAutoExpandSettings;
import org.gtlcore.gtlcore.integration.ae2.crafting.IMaxFastCraftingProviderVersion;
import org.gtlcore.gtlcore.integration.ae2.crafting.IPatternProviderAutoExpand;
import org.gtlcore.gtlcore.integration.ae2.graph.core.*;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.service.CraftingService;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Real AE patterns/services and GTL adapter; provider/energy edges are controlled fault doubles. */
final class GraphAeAdapterTest {

    static void run() throws Exception {
        AEKey water = AEFluidKey.of(Fluids.WATER), lava = AEFluidKey.of(Fluids.LAVA);
        CompoundTag data = new CompoundTag();
        ListTag in = new ListTag(), out = new ListTag();
        in.add(GenericStack.writeTag(new GenericStack(water, 1000)));
        out.add(GenericStack.writeTag(new GenericStack(lava, 1000)));
        data.put("in", in);
        data.put("out", out);
        ItemStack encoded = new ItemStack(Items.PAPER);
        encoded.setTag(data);
        IPatternDetails pattern = new AEProcessingPattern(AEItemKey.of(encoded));
        var recipe = new GraphRecipe<AEKey>("fluid", PatternFingerprint.of(pattern),
                List.of(new GraphRecipe.Slot<>(water, 1000, 0)), Map.of(lava, 1000L));
        TestProvider provider = new TestProvider(pattern);
        double[] consumed = { 0 }, energyLimit = { Double.MAX_VALUE };
        IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[] { IEnergyService.class }, (p, method, args) -> {
                    if (method.getName().equals("extractAEPower")) {
                        double amount = Math.min((double) args[0], energyLimit[0]);
                        if (args[1] == Actionable.MODULATE) consumed[0] += amount;
                        return amount;
                    }
                    return null;
                });
        IStorageService storage = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[] { IStorageService.class }, (p, method, args) -> null);
        GraphCpuHost host = (GraphCpuHost) Proxy.newProxyInstance(GraphCpuHost.class.getClassLoader(),
                new Class<?>[] { GraphCpuHost.class }, (p, method, args) -> null);
        TestService service = new TestService(storage, energy, provider);
        var adapter = new GtlExecutionAdapter(host, null);
        adapter.services(service, energy);
        check(adapter.resolve(recipe) == pattern && adapter.bindingFailure().isEmpty(), "initial binding resolves");
        var savedOutput = pattern.getOutputs()[0];
        pattern.getOutputs()[0] = new GenericStack(lava, 999);
        check(adapter.resolve(recipe) == null && adapter.bindingFailure().equals("PATTERN_FINGERPRINT_CHANGED"),
                "unannounced effective-pattern mutation rejects even a cached binding");
        check(adapter.bindingDetail().contains("current="), "failure records current fingerprint");
        pattern.getOutputs()[0] = savedOutput;
        check(adapter.resolve(recipe) == pattern && adapter.bindingFailure().isEmpty() && adapter.bindingDetail().isEmpty(),
                "restored binding resolves and clears stale diagnostic");
        var wrongSlot = new GraphRecipe<AEKey>("wrong-slot", recipe.binding(),
                List.of(new GraphRecipe.Slot<>(water, 1000, 1)), recipe.outputs());
        check(adapter.resolve(wrongSlot) == null && adapter.bindingFailure().equals("INPUT_SLOT_CHANGED"),
                "slot mismatch is distinguished from fingerprint drift");
        check(adapter.capacity(recipe, 100) == 5, "actual adapter respects provider operation capacity");
        check(consumed[0] == 0, "energy simulation is read-only");
        check(adapter.push(recipe, 5, Map.of(water, 5000L)) == GraphJobRuntime.Outcome.ACCEPTED, "actual adapter accepted");
        check(provider.received[0].get(water) == 5000, "exact fluid amount and input slot");
        check(consumed[0] == 1000.0 / water.getAmountPerOperation(), "GTL expanded energy rule charges one pattern, using AE's fluid operation unit");
        check(service.requested, "expected output advertised before push");
        provider.toolsPresent = false;
        check(adapter.capacity(recipe, 100) == 0 && adapter.reason().equals("MISSING_TOOL"), "missing-tool preflight blocks before handoff");
        check(provider.calls == 1, "missing tools cannot reach true/refund path");
        provider.toolsPresent = true;
        energyLimit[0] = 0;
        check(adapter.capacity(recipe, 100) == 0 && adapter.reason().equals("WAIT_ENERGY"), "insufficient energy blocks");
        energyLimit[0] = Double.MAX_VALUE;
        provider.reject = true;
        check(adapter.capacity(recipe, 100) == 5, "energy recovery wakes capacity");
        check(adapter.push(recipe, 5, Map.of(water, 5000L)) == GraphJobRuntime.Outcome.REJECTED, "unmodified false is refundable");
        provider.mutateOnReject = true;
        check(adapter.capacity(recipe, 100) == 5, "prepare mutation fault");
        check(adapter.push(recipe, 5, Map.of(water, 5000L)) == GraphJobRuntime.Outcome.IN_DOUBT, "mutated false must never clone escrow");
        provider.room = 2999;
        check(adapter.capacity(recipe, 100) == 2, "provider buffer headroom further limits negotiated batch");
        provider.reject = provider.mutateOnReject = false;
        provider.room = Long.MAX_VALUE;
        provider.maxOperations = 3_000_000;
        check(adapter.capacity(recipe, Long.MAX_VALUE) == 3_000_000, "expanded capacity remains long and provider-bounded");
        var big = new GraphPlan<AEKey>(lava, 3_000_000_000L, false, new PlanStep.Sequence(List.of(new PlanStep.Batch(recipe.id(), 3_000_000))),
                Map.of(recipe.id(), recipe), Map.of(water, 3_000_000_000L), Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 0, 0);
        var runtime = new GraphJobRuntime<>(big, big.initial(), Map.of());
        runtime.tick(adapter, 0, 1);
        check(provider.received[0].get(water) == 3_000_000_000L, "expanded real slot counter does not truncate above int");
        check(runtime.waiting(lava) == 3_000_000_000L && runtime.state() == GraphJobRuntime.State.RUNNING,
                "provider acceptance is not output completion");
        check(runtime.accept(lava, 1_000_000_000L, false) == 1_000_000_000L && runtime.waiting(lava) == 2_000_000_000L,
                "partial long fluid return discharges only actual production");
        var context = new PatternFingerprint.Context();
        String fingerprint = context.of(pattern);
        check(fingerprint.equals(PatternFingerprint.of(pattern)), "request-local key memoization preserves canonical signature");
        pattern.getOutputs()[0] = new GenericStack(lava, 2000);
        check(!context.of(pattern).equals(fingerprint), "same context still observes effective FOA-style output changes");
        check(adapter.resolve(recipe) == null, "effective multiplier changes cannot reuse the prior execution binding");
        pattern.getOutputs()[0] = savedOutput;
        System.out.println("Actual AE/GTL adapter: batch, slots, fluid units, energy, preflight and rejection tests passed");
    }

    private static final class TestService extends CraftingService implements IMaxFastCraftingProviderVersion, GraphRequestTracker {

        final TestProvider provider;
        boolean requested;

        TestService(IStorageService storage, IEnergyService energy, TestProvider provider) {
            super(null, storage, energy);
            this.provider = provider;
        }

        @Override
        public long gtlcore$getMaxFastCraftingProviderVersionTick() {
            return 1;
        }

        @Override
        public void gtlcore$expectGraphOutput(AEKey key) {
            requested = true;
        }

        @Override
        public Collection<IPatternDetails> getCraftingFor(AEKey key) {
            return List.of(provider.pattern);
        }

        @Override
        public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) {
            return List.of(provider);
        }
    }

    private static final class TestProvider implements ICraftingProvider, IAutoExpandSettings, IPatternProviderAutoExpand, GraphDispatchPreflight {

        final IPatternDetails pattern;
        boolean toolsPresent = true, reject, mutateOnReject;
        int calls;
        long room = Long.MAX_VALUE;
        long maxOperations = 5;
        KeyCounter[] received;

        TestProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean pushPattern(IPatternDetails actual, KeyCounter[] inputs) {
            check(actual == pattern, "registered effective pattern identity retained");
            calls++;
            received = inputs;
            if (mutateOnReject) inputs[0].clear();
            return !reject;
        }

        @Override
        public boolean gtlcore$canProduceGraphPattern(IPatternDetails pattern) {
            return toolsPresent;
        }

        @Override
        public long gtlcore$graphCapacity(IPatternDetails pattern, Map<AEKey, Long> inputPerRun, long requested) {
            for (long perRun : inputPerRun.values()) requested = Math.min(requested, room / perRun);
            return requested;
        }

        @Override
        public boolean isPatternAutoExpand() {
            return true;
        }

        @Override
        public void setPatternAutoExpand(boolean enabled) {}

        @Override
        public long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requested) {
            return Math.min(maxOperations, requested);
        }

        @Override
        public long gtlcore$findMaxOperationsForTarget(PatternProviderTarget target, BlockEntity be, Direction side, KeyCounter inputs, long requested) {
            return Math.min(5, requested);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
