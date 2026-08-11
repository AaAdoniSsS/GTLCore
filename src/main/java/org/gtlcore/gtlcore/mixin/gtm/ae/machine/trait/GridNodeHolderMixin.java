package org.gtlcore.gtlcore.mixin.gtm.ae.machine.trait;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftingCPUInterfacePartMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteComputationArrayLifecycleLogger;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GridNodeHolder.class)
public abstract class GridNodeHolderMixin {

    @Unique
    private static final String GTLCORE$SERVER_STOPPING_REASON = "server_stopping";

    @Unique
    private long gtlcore$loadStartedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;

    @Unique
    private long gtlcore$unloadStartedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;

    @Unique
    private long gtlcore$nodeCreationQueuedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;

    @Unique
    private long gtlcore$nodeCreationStartedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;

    @Inject(method = "onMachineLoad", at = @At("HEAD"), remap = false)
    private void gtlcore$logGridNodeHolderLoadStarted(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        if (machine == null) return;

        this.gtlcore$loadStartedAtNanos = gtlcore$now();
        TransfiniteComputationArrayLifecycleLogger.logGridNodeHolderLoadStarted(
                machine.getLevel(), machine.getPos(), machine.getMainNode().isOnline(),
                machine.getMainNode().isPowered(), machine.getMainNode().isActive(),
                machine.getMainNode().getGrid() != null);
    }

    @Inject(method = "onMachineLoad", at = @At("RETURN"), remap = false)
    private void gtlcore$logGridNodeHolderLoadCompleted(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        if (machine == null) return;

        long finishedAtNanos = gtlcore$now();
        boolean nodeCreationQueued = machine.getLevel() instanceof ServerLevel;
        this.gtlcore$nodeCreationQueuedAtNanos = nodeCreationQueued ? finishedAtNanos :
                TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
        TransfiniteComputationArrayLifecycleLogger.logGridNodeHolderLoadCompleted(
                machine.getLevel(), machine.getPos(), nodeCreationQueued, machine.getMainNode().isOnline(),
                machine.getMainNode().isPowered(), machine.getMainNode().isActive(),
                machine.getMainNode().getGrid() != null,
                gtlcore$elapsed(this.gtlcore$loadStartedAtNanos, finishedAtNanos));
        if (machine.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            TransfiniteComputationArrayLifecycleLogger.logNodeCreationQueued(
                    serverLevel, machine.getPos(), server.isRunning(), server.isStopped());
        }
    }

    @Inject(method = "createMainNode", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$skipNodeCreationAfterServerStop(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        long startedAtNanos = gtlcore$now();
        if (machine != null) {
            this.gtlcore$nodeCreationStartedAtNanos = startedAtNanos;
            TransfiniteComputationArrayLifecycleLogger.logNodeCreationStarted(
                    machine.getLevel(), machine.getPos(),
                    gtlcore$elapsed(this.gtlcore$nodeCreationQueuedAtNanos, startedAtNanos),
                    machine.getMainNode().isOnline(), machine.getMainNode().isPowered(),
                    machine.getMainNode().isActive(), machine.getMainNode().getGrid() != null);
        }

        Level level = ((MachineTrait) (Object) this).getMachine().getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        if (!server.isRunning() || server.isStopped()) {
            if (machine != null) {
                long finishedAtNanos = gtlcore$now();
                TransfiniteComputationArrayLifecycleLogger.logNodeCreationCanceled(
                        serverLevel, machine.getPos(), GTLCORE$SERVER_STOPPING_REASON,
                        gtlcore$elapsed(this.gtlcore$nodeCreationQueuedAtNanos, startedAtNanos),
                        gtlcore$elapsed(this.gtlcore$nodeCreationQueuedAtNanos, finishedAtNanos));
                this.gtlcore$nodeCreationStartedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
            }
            ci.cancel();
        }
    }

    @Inject(method = "createMainNode", at = @At("RETURN"), remap = false)
    private void gtlcore$logNodeCreationCompleted(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        if (machine == null || this.gtlcore$nodeCreationStartedAtNanos ==
                TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS) {
            return;
        }

        long finishedAtNanos = gtlcore$now();
        TransfiniteComputationArrayLifecycleLogger.logNodeCreationCompleted(
                machine.getLevel(), machine.getPos(),
                gtlcore$elapsed(this.gtlcore$nodeCreationQueuedAtNanos, this.gtlcore$nodeCreationStartedAtNanos),
                gtlcore$elapsed(this.gtlcore$nodeCreationStartedAtNanos, finishedAtNanos),
                gtlcore$elapsed(this.gtlcore$nodeCreationQueuedAtNanos, finishedAtNanos),
                machine.getMainNode().isOnline(), machine.getMainNode().isPowered(),
                machine.getMainNode().isActive(), machine.getMainNode().getGrid() != null);
        this.gtlcore$nodeCreationQueuedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
        this.gtlcore$nodeCreationStartedAtNanos = TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
    }

    @Inject(method = "onMachineUnLoad", at = @At("HEAD"), remap = false)
    private void gtlcore$logGridNodeHolderUnloadStarted(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        if (machine == null) return;

        this.gtlcore$unloadStartedAtNanos = gtlcore$now();
        TransfiniteComputationArrayLifecycleLogger.logGridNodeHolderUnloadStarted(
                machine.getLevel(), machine.getPos(), machine.getMainNode().isOnline(),
                machine.getMainNode().isPowered(), machine.getMainNode().isActive(),
                machine.getMainNode().getGrid() != null);
    }

    @Inject(method = "onMachineUnLoad", at = @At("RETURN"), remap = false)
    private void gtlcore$logGridNodeHolderUnloadCompleted(CallbackInfo ci) {
        MECraftingCPUInterfacePartMachine machine = gtlcore$getTransfiniteInterface();
        if (machine == null) return;

        long finishedAtNanos = gtlcore$now();
        TransfiniteComputationArrayLifecycleLogger.logGridNodeHolderUnloadCompleted(
                machine.getLevel(), machine.getPos(), machine.getMainNode().isOnline(),
                machine.getMainNode().isPowered(), machine.getMainNode().isActive(),
                machine.getMainNode().getGrid() != null,
                gtlcore$elapsed(this.gtlcore$unloadStartedAtNanos, finishedAtNanos));
    }

    @Unique
    private MECraftingCPUInterfacePartMachine gtlcore$getTransfiniteInterface() {
        return ((MachineTrait) (Object) this).getMachine() instanceof MECraftingCPUInterfacePartMachine machine ?
                machine : null;
    }

    @Unique
    private static long gtlcore$now() {
        return TransfiniteComputationArrayLifecycleLogger.isEnabled() ? System.nanoTime() :
                TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
    }

    @Unique
    private static long gtlcore$elapsed(long startedAtNanos, long finishedAtNanos) {
        if (startedAtNanos == TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS ||
                finishedAtNanos == TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS) {
            return TransfiniteComputationArrayLifecycleLogger.UNAVAILABLE_DURATION_NANOS;
        }
        return Math.max(0L, finishedAtNanos - startedAtNanos);
    }
}
