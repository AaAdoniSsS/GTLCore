package org.gtlcore.test;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedAsyncOutputPartMachine;
import org.gtlcore.gtlcore.integration.ae2.async.AEWriteService;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Hold the writer to make saving accepted-but-not-yet-processed output deterministic. */
public final class AsyncOutputProbe {
    public static void benchmark(Level level, BlockPos pos, Ingredient product, AEItemKey key) {
        var machine=(MEExtendedAsyncOutputPartMachine)MetaMachine.getMachine(level,pos);
        long timeout=AEWriteService.FLUSH_TIMEOUT;
        AEWriteService.FLUSH_TIMEOUT=10000;
        try {
            for(int sample=0;sample<4;sample++) {
                machine.saveCustomPersistedData(new CompoundTag(),false);
                long before=machine.getBuffer().getLong(key), start=System.nanoTime();
                for(int i=0;i<10000;i++) machine.getItemOutputHandler().meHandleRecipeOutput(List.of(product),false);
                long receipt=System.nanoTime()-start;
                machine.saveCustomPersistedData(new CompoundTag(),false);
                long accepted=machine.getBuffer().getLong(key)-before;
                if(accepted!=1000000) throw new AssertionError("Output benchmark lost material: "+accepted);
                System.out.println("[Async Output] benchmark sample="+sample+" submissions=10000 units="+accepted+" receipt_ms="+receipt/1e6+" saved_ms="+(System.nanoTime()-start)/1e6);
            }
        } finally { AEWriteService.FLUSH_TIMEOUT=timeout; }
    }

    public static void save(Level level, BlockPos pos, Ingredient product, AEItemKey key, boolean drop) throws Exception {
        var machine = (MEExtendedAsyncOutputPartMachine) MetaMachine.getMachine(level, pos);
        if (!key.equals(AEItemKey.of(product.m_43908_()[0]))) throw new AssertionError("Fixture ingredient lost its NBT before submission");
        var method = AEWriteService.class.getDeclaredMethod("executor");
        method.setAccessible(true);
        var executor = (ThreadPoolExecutor) method.invoke(AEWriteService.INSTANCE);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Writer fixture failed to enter");
        long timeout = AEWriteService.FLUSH_TIMEOUT;
        try {
            AEWriteService.FLUSH_TIMEOUT = 1;
            var left = machine.getItemOutputHandler().meHandleRecipeOutput(List.of(product), false);
            var saved = new CompoundTag();
            long start = System.nanoTime();
            machine.saveCustomPersistedData(saved, drop);
            long savedCount=machine.getBuffer().getLong(key);
            System.out.println("[Async Output] save drop="+drop+" left="+left.size()+" saved="+savedCount+" save_ms="+(System.nanoTime()-start)/1e6+" keys="+machine.getBuffer());
        } finally {
            AEWriteService.FLUSH_TIMEOUT = timeout;
            release.countDown();
        }
    }
}
