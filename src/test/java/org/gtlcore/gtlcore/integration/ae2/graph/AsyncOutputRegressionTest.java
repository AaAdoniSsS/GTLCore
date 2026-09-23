package org.gtlcore.gtlcore.integration.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.async.AEAccumulator;
import org.gtlcore.gtlcore.integration.ae2.async.AEWriteService;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real AE identities and a deliberately blocked writer; assertions measure accepted material. */
public final class AsyncOutputRegressionTest {

    public static void run() throws Exception {
        var service = new AEWriteService();
        var accumulator = new AEAccumulator();
        var ref = new WeakReference<>(accumulator);
        var pending = new ConcurrentLinkedQueue<Object2LongOpenHashMap<AEKey>>();
        var requested = new AtomicBoolean();
        var executorMethod = AEWriteService.class.getDeclaredMethod("executor");
        executorMethod.setAccessible(true);
        var executor = (ThreadPoolExecutor) executorMethod.invoke(service);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        check(entered.await(5, TimeUnit.SECONDS), "Fixture could not block writer");
        try {
            var product = Items.DIAMOND.getDefaultInstance();
            product.setCount(37);
            var tag = new CompoundTag();
            tag.putString("batch", "keep");
            product.setTag(tag);
            var key = AEItemKey.of(product);
            var input = Ingredient.of(product);
            var left = new ArrayList<>(List.of(input));
            service.submitIngredientLeft(ref, left);
            left.clear();
            input.getItems()[0].setCount(1);
            service.prepareDrainedData(ref, pending, requested);
            check(service.flushBlocking(ref, pending, requested, 0), "Save still depends on writer progress");
            check(total(pending, key) == 37, "Accepted item quantity/NBT omitted or resampled");
            var fluidTag = new CompoundTag();
            fluidTag.putString("batch", "fluid-keep");
            var fluid = FluidIngredient.of(java.util.stream.Stream.of(Fluids.WATER), 3_000_000_000L, fluidTag);
            service.submitFluidIngredientLeft(ref, List.of(fluid));
            fluid.setAmount(1);
            service.flushBlocking(ref, pending, requested, 0);
            check(total(pending, AEFluidKey.of(Fluids.WATER, fluidTag)) == 3_000_000_000L, "Fluid NBT or long quantity lost");
            check(total(pending, AEFluidKey.of(Fluids.WATER)) == 0, "Tagged fluid converted to untagged fluid");
            pending.clear();
        } finally {
            release.countDown();
        }
        service.close();
        check(total(pending, AEItemKey.of(Items.DIAMOND)) == 0 && pending.isEmpty(), "Delayed drain duplicated saved material");

        // Concurrent producers, drain callbacks and saves must account for every unit exactly once.
        var jobs = Executors.newFixedThreadPool(5);
        var key = AEItemKey.of(Items.IRON_INGOT);
        try {
            List<Future<?>> producers = new ArrayList<>();
            for (int i = 0; i < 4; i++) producers.add(jobs.submit(() -> {
                for (int j = 0; j < 10_000; j++) accumulator.add(key, 3_000_000_000L);
            }));
            var drain = jobs.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    service.prepareDrainedData(ref, pending, requested);
                    service.flushBlocking(ref, pending, requested, 0);
                }
            });
            for (var job : producers) job.get(10, TimeUnit.SECONDS);
            drain.get(10, TimeUnit.SECONDS);
            service.close();
            service.flushBlocking(ref, pending, requested, 0);
            check(total(pending, key) == 120_000_000_000_000L, "Concurrent transfer lost or duplicated material");
        } finally {
            jobs.shutdownNow();
            service.close();
        }
        System.out.println("Async output: blocked-worker saves, mutable input, item/fluid NBT, long counts, concurrent transfer and writer restart passed");
    }

    private static long total(Iterable<Object2LongOpenHashMap<AEKey>> maps, AEKey key) {
        long count = 0;
        for (var map : maps) count = Math.addExact(count, map.getLong(key));
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
