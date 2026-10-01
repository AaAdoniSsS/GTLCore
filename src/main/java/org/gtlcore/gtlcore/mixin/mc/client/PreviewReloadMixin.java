package org.gtlcore.gtlcore.mixin.mc.client;

import org.gtlcore.gtlcore.client.preview.PreviewLifecycle;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class PreviewReloadMixin {

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void gtlcore$cancelBeforeReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        PreviewLifecycle.beginReload();
    }

    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void gtlcore$resumeAfterReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().whenComplete((ignored, error) -> Minecraft.getInstance().execute(PreviewLifecycle::endReload));
    }
}
