package org.gtlcore.gtlcore.mixin.mc;

import org.gtlcore.gtlcore.integration.world.WorldLoadPerformanceLogger;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    @ModifyReturnValue(method = "scheduleChunkGeneration", at = @At("RETURN"))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> gtlcore$observeChunkGeneration(
                                                                                                                   CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future,
                                                                                                                   ChunkHolder holder,
                                                                                                                   ChunkStatus status) {
        return WorldLoadPerformanceLogger.observeChunkGeneration(level, holder.getPos(), status, future);
    }
}
