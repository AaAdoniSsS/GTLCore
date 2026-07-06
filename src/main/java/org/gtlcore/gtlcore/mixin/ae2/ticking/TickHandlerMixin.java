package org.gtlcore.gtlcore.mixin.ae2.ticking;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCalculation;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;

import appeng.crafting.CraftingCalculation;
import appeng.hooks.ticking.TickHandler;
import appeng.me.Grid;
import com.google.common.collect.Multimap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickHandler.class)
public abstract class TickHandlerMixin {

    @Unique
    private static final long GTLCORE_NANOS_PER_MICRO = 1000L;
    @Unique
    private static final long GTLCORE_MEDIUM_PRESSURE_MICROS = 25_000L;
    @Unique
    private static final long GTLCORE_HIGH_PRESSURE_MICROS = 40_000L;
    @Unique
    private long gTLCore$serverTickStartNanos;

    @Shadow(remap = false)
    @Final
    private Multimap<LevelAccessor, CraftingCalculation> craftingJobs;

    @Shadow(remap = false)
    public Iterable<Grid> getGridList() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private void readyBlockEntities(ServerLevel level) {
        throw new AssertionError();
    }

    /**
     * @author Dragons
     * @reason 使用GTLCore自适应预算推进下单计算
     */
    @Overwrite(remap = false)
    private void onServerLevelTickEnd(ServerLevel level) {
        this.simulateCraftingJobs(level);
        this.readyBlockEntities(level);

        // tick networks
        for (var g : this.getGridList()) {
            try {
                g.onLevelEndTick(level);
            } catch (Throwable t) {
                CrashReport crashReport = CrashReport.forThrowable(t, "Ticking grid on end of level tick");
                g.fillCrashReportCategory(crashReport.addCategory("Grid being ticked"));
                level.fillReportDetails(crashReport);
                throw new ReportedException(crashReport);
            }
        }
    }

    /**
     * @author Dragons
     * @reason 使用GTLCore自适应预算注册下单计算
     */
    @Overwrite(remap = false)
    public void registerCraftingSimulation(Level level, CraftingCalculation craftingCalculation) {
        if (level.isClientSide) {
            throw new IllegalArgumentException("Trying to register a crafting job for a client-level");
        }
        if (!((ICraftingCalculation) craftingCalculation).gtlcore$isAdaptive()) {
            return;
        }

        synchronized (this.craftingJobs) {
            this.craftingJobs.put(level, craftingCalculation);
        }
    }

    /**
     * @author Dragons
     * @reason 使用GTLCore自适应预算推进下单计算
     */
    @Overwrite(remap = false)
    private void simulateCraftingJobs(LevelAccessor level) {
        synchronized (this.craftingJobs) {
            var jobs = this.craftingJobs.get(level);
            if (jobs.isEmpty()) {
                return;
            }

            int budgetPerJob = this.gTLCore$getBudgetPerJob(jobs.size());
            for (var iterator = jobs.iterator(); iterator.hasNext();) {
                var job = iterator.next();
                if (!job.simulateFor(budgetPerJob)) {
                    iterator.remove();
                }
            }
        }
    }

    @Inject(method = "onServerTick", at = @At("HEAD"), remap = false)
    private void gTLCore$recordServerTickStart(TickEvent.ServerTickEvent event, CallbackInfo ci) {
        if (event.phase == TickEvent.Phase.START) {
            this.gTLCore$serverTickStartNanos = System.nanoTime();
        }
    }

    @Unique
    private int gTLCore$getBudgetPerJob(int activeJobs) {
        int minBudget = Math.max(1, ConfigHolder.INSTANCE.ae2CraftingMinBudgetMicros);
        int maxBudget = Math.max(minBudget, ConfigHolder.INSTANCE.ae2CraftingMaxBudgetMicros);
        int idleBudget = Math.max(maxBudget, ConfigHolder.INSTANCE.ae2CraftingIdleBudgetMicros);
        int totalBudget = idleBudget;

        long elapsedMicros = this.gTLCore$getElapsedServerTickMicros();
        if (elapsedMicros >= GTLCORE_HIGH_PRESSURE_MICROS) {
            totalBudget = minBudget;
        } else if (elapsedMicros >= GTLCORE_MEDIUM_PRESSURE_MICROS) {
            totalBudget = maxBudget;
        }

        return Math.max(minBudget, totalBudget / Math.max(1, activeJobs));
    }

    @Unique
    private long gTLCore$getElapsedServerTickMicros() {
        if (this.gTLCore$serverTickStartNanos == 0) {
            return 0;
        }
        return (System.nanoTime() - this.gTLCore$serverTickStartNanos) / GTLCORE_NANOS_PER_MICRO;
    }
}
