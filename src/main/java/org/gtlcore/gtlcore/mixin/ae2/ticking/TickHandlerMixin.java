package org.gtlcore.gtlcore.mixin.ae2.ticking;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import appeng.crafting.CraftingCalculation;
import appeng.hooks.ticking.TickHandler;
import appeng.me.Grid;
import com.google.common.collect.Multimap;
import org.spongepowered.asm.mixin.*;

@Mixin(TickHandler.class)
public abstract class TickHandlerMixin {

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
     * @reason 使用GTLCore旧后台切片预算推进下单计算
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
     * @reason 使用GTLCore旧后台切片预算注册下单计算
     */
    @Overwrite(remap = false)
    public void registerCraftingSimulation(Level level, CraftingCalculation craftingCalculation) {
        if (level.isClientSide) {
            throw new IllegalArgumentException("Trying to register a crafting job for a client-level");
        }
        synchronized (this.craftingJobs) {
            this.craftingJobs.put(level, craftingCalculation);
        }
    }

    /**
     * @author Dragons
     * @reason 使用GTLCore旧后台切片预算推进下单计算
     */
    @Overwrite(remap = false)
    private void simulateCraftingJobs(LevelAccessor level) {
        synchronized (this.craftingJobs) {
            var jobs = this.craftingJobs.get(level);
            if (jobs.isEmpty()) {
                return;
            }

            for (var iterator = jobs.iterator(); iterator.hasNext();) {
                var job = iterator.next();
                if (!job.simulateFor(Integer.MAX_VALUE)) {
                    iterator.remove();
                }
            }
        }
    }
}
