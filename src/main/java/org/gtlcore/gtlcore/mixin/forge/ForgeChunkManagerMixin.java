package org.gtlcore.gtlcore.mixin.forge;

import org.gtlcore.gtlcore.integration.world.WorldLoadPerformanceLogger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.common.world.ForgeChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeChunkManager.class)
public abstract class ForgeChunkManagerMixin {

    @Inject(
            method = "reinstatePersistentChunks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ForcedChunksSavedData;)V",
            at = @At("TAIL"),
            remap = false)
    private static void gtlcore$recordForcedChunkSources(ServerLevel level, ForcedChunksSavedData saveData,
                                                         CallbackInfo ci) {
        WorldLoadPerformanceLogger.onForcedChunksReinstated(level, saveData);
    }
}
