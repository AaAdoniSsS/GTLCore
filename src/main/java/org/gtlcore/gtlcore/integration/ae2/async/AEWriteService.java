package org.gtlcore.gtlcore.integration.ae2.async;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import com.gregtechceu.gtceu.api.recipe.ingredient.*;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AEWriteService implements AutoCloseable {

    public static final AEWriteService INSTANCE = new AEWriteService();
    public static int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    public static int TIME_OUT = 10;
    /** 存档/卸载时等待异步队列落盘的最长时间(ms) */
    public static long FLUSH_TIMEOUT = 500;

    private volatile ThreadPoolExecutor executor;
    private volatile Thread workerThread;
    /** 已提交但尚未执行完的任务数，用于存档时的快速路径判断 */
    private final AtomicInteger outstanding = new AtomicInteger();

    /**
     * 取当前线程池；已关闭则重建，保证同一 JVM 内重启服务端后依然可用。
     */
    private ThreadPoolExecutor executor() {
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) return current;
        synchronized (this) {
            current = executor;
            if (current == null || current.isShutdown()) {
                current = new ThreadPoolExecutor(
                        1, 1, 30, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(4096),
                        this::createWorker,
                        // 队列满或线程池正在关闭时退回提交线程执行，绝不静默丢弃
                        (task, pool) -> task.run());
                executor = current;
            }
            return current;
        }
    }

    private Thread createWorker(Runnable r) {
        Thread t = new Thread(r, "AE-Writer");
        t.setDaemon(true);
        t.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, THREAD_PRIORITY)));
        workerThread = t;
        return t;
    }

    private void execute(Runnable task) {
        try {
            executor().execute(task);
        } catch (RejectedExecutionException e) {
            // 兜底：任何情况下都执行，避免产物被静默吞掉
            task.run();
        }
    }

    private void executeTracked(Runnable task) {
        outstanding.incrementAndGet();
        execute(() -> {
            try {
                task.run();
            } finally {
                outstanding.decrementAndGet();
            }
        });
    }

    public void submitIngredientLeft(WeakReference<AEAccumulator> accRef, List<Ingredient> left) {
        if (left == null || left.isEmpty()) return;
        executeTracked(() -> {
            var acc = accRef.get();
            if (acc == null) return;
            for (Ingredient ingredient : left) {
                if (ingredient instanceof IntProviderIngredient intProvider) {
                    intProvider.setItemStacks(null);
                    intProvider.setSampledCount(null);
                }

                ItemStack[] items = ingredient.getItems();
                if (items.length != 0) {
                    ItemStack output = items[0];
                    if (!output.isEmpty()) {
                        acc.add(AEItemKey.of(output), ingredient instanceof LongIngredient longIngredient ? longIngredient.getActualAmount() : output.getCount());
                    }
                }
            }
        });
    }

    public void submitFluidIngredientLeft(WeakReference<AEAccumulator> accRef, List<FluidIngredient> left) {
        if (left == null || left.isEmpty()) return;
        executeTracked(() -> {
            var acc = accRef.get();
            if (acc == null) return;
            for (FluidIngredient fluidIngredient : left) {
                if (!fluidIngredient.isEmpty()) {
                    FluidStack[] fluids = fluidIngredient.getStacks();
                    if (fluids.length != 0) {
                        FluidStack output = fluids[0];
                        if (!output.isEmpty()) {
                            acc.add(AEFluidKey.of(output.getFluid()), output.getAmount());
                        }
                    }
                }
            }
        });
    }

    public void prepareDrainedData(WeakReference<AEAccumulator> accRef,
                                   Queue<Object2LongOpenHashMap<AEKey>> targetQueue,
                                   AtomicBoolean drainRequested) {
        execute(() -> {
            try {
                drainNow(accRef, targetQueue);
            } finally {
                drainRequested.set(false);
            }
        });
    }

    /**
     * 把 accumulator 排空并投递到交接队列。
     * <p>
     * 用队列而不是单个槽位，避免"上一批还没被主线程取走"时新的排空结果无处安放而丢失。
     */
    private void drainNow(WeakReference<AEAccumulator> accRef,
                          Queue<Object2LongOpenHashMap<AEKey>> targetQueue) {
        var acc = accRef.get();
        if (acc == null) return;

        Object2LongOpenHashMap<AEKey> drainedData = new Object2LongOpenHashMap<>();
        acc.drainTo(drainedData);
        if (!drainedData.isEmpty()) targetQueue.add(drainedData);
    }

    /**
     * 阻塞式落盘：等待所有已提交任务执行完毕，并把 accumulator 排空到 targetQueue。
     * <p>
     * 只应在主线程存档/卸载时调用。线程池是单线程 FIFO，屏障任务排在所有已提交任务之后，
     * 因此它完成时，所有更早提交的产物都已经进入 targetQueue，主线程随后合并进 buffer 即可。
     *
     * @return 是否在超时前完成
     */
    public boolean flushBlocking(WeakReference<AEAccumulator> accRef,
                                 Queue<Object2LongOpenHashMap<AEKey>> targetQueue,
                                 AtomicBoolean drainRequested,
                                 long timeoutMs) {
        var acc = accRef.get();
        if (acc == null) return true;
        if (outstanding.get() == 0 && targetQueue.isEmpty() && acc.isEmpty()) return true;

        CountDownLatch latch = new CountDownLatch(1);
        Runnable barrier = () -> {
            try {
                drainNow(accRef, targetQueue);
            } finally {
                drainRequested.set(false);
                latch.countDown();
            }
        };

        if (Thread.currentThread() == workerThread) {
            // 防御：不应发生，避免工作线程自等
            barrier.run();
            return true;
        }

        execute(barrier);
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return true;
            GTLCore.LOGGER.warn("AE-Writer flush timed out after {}ms, unflushed async output may be lost", timeoutMs);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void shutDownGracefully() {
        ThreadPoolExecutor pool = executor;
        if (pool == null) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(TIME_OUT, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    @Override
    public void close() {
        shutDownGracefully();
    }
}
