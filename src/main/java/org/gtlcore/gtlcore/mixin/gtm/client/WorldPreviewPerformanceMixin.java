package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.client.preview.WorldPreview;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockInWorldPreviewRenderer.class, remap = false)
public abstract class WorldPreviewPerformanceMixin {

    @Shadow
    private static Thread THREAD;

    @Inject(method = "showPreview", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$show(BlockPos pos, MultiblockControllerMachine controller, int duration, CallbackInfo ci) {
        if (WorldPreview.show(pos, controller, duration)) ci.cancel();
        else WorldPreview.clear();
    }

    @Inject(method = "renderInWorldPreview", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$render(PoseStack pose, Camera camera, float partialTicks, CallbackInfo ci) {
        if (WorldPreview.active()) {
            WorldPreview.render(pose, camera, partialTicks);
            ci.cancel();
        }
    }

    @Inject(method = "cleanPreview", at = @At("HEAD"))
    private static void gtlcore$cancel(CallbackInfo ci) {
        if (THREAD != null) {
            THREAD.interrupt();
            THREAD = null;
        }
        WorldPreview.clear();
    }

    @Inject(method = "removePreview", at = @At("HEAD"))
    private static void gtlcore$remove(BlockPos pos, CallbackInfo ci) {
        WorldPreview.remove(pos);
    }

    @Inject(method = "onClientTick", at = @At("HEAD"))
    private static void gtlcore$tick(CallbackInfo ci) {
        WorldPreview.tick();
    }
}
