package org.gtlcore.gtlcore.integration.ae2.crafting.transfinite;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class TransfiniteCraftingCPU implements ICraftingCPU {

    @Nullable
    private final UUID id;
    private final long bytes;
    private final TransfiniteComputationArrayMachine host;
    private final TransfiniteCraftingLogic craftingLogic;

    public TransfiniteCraftingCPU(TransfiniteComputationArrayMachine host, UUID id, long bytes) {
        this.host = host;
        this.id = id;
        this.bytes = bytes;
        this.craftingLogic = new TransfiniteCraftingLogic(this);
    }

    private TransfiniteCraftingCPU(TransfiniteComputationArrayMachine host) {
        this.host = host;
        this.id = null;
        this.bytes = host.getAvailableStorage();
        this.craftingLogic = new TransfiniteCraftingLogic(this);
    }

    public static TransfiniteCraftingCPU capacityView(TransfiniteComputationArrayMachine host) {
        return new TransfiniteCraftingCPU(host);
    }

    public TransfiniteCraftingLogic getCraftingLogic() {
        return this.craftingLogic;
    }

    public TransfiniteComputationArrayMachine getHost() {
        return this.host;
    }

    public @Nullable UUID getId() {
        return this.id;
    }

    public boolean isCapacityView() {
        return this.id == null;
    }

    @Override
    public boolean isBusy() {
        return this.craftingLogic.hasJob();
    }

    @Override
    public @Nullable CraftingJobStatus getJobStatus() {
        var output = this.craftingLogic.getFinalJobOutput();
        if (output == null) {
            return null;
        }
        var tracker = this.craftingLogic.getElapsedTimeTracker();
        long started = tracker.getStartItemCount();
        long progress = Math.max(0L, started - tracker.getRemainingItemCount());
        return new CraftingJobStatus(output, started, progress, tracker.getElapsedTime());
    }

    @Override
    public void cancelJob() {
        if (!isCapacityView()) {
            this.craftingLogic.cancel();
        }
    }

    @Override
    public long getAvailableStorage() {
        return this.bytes;
    }

    @Override
    public int getCoProcessors() {
        return Ints.saturatedCast(getLongCoProcessors());
    }

    public long getLongCoProcessors() {
        return Math.max(0L, this.host.getParallelism() - 1L);
    }

    @Override
    public Component getName() {
        return this.host.getCpuName();
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return this.host.getSelectionMode();
    }

    public boolean isActive() {
        return this.host.isOperational();
    }

    public long getParallelism() {
        return this.host.getParallelism();
    }

    public Level getLevel() {
        return Objects.requireNonNull(this.host.getLevel());
    }

    @Nullable
    public IGrid getGrid() {
        return this.host.getGrid();
    }

    public IActionSource getActionSource() {
        return this.host.getActionSource();
    }

    public void markDirty() {
        this.host.markCpuDirty();
    }
}
