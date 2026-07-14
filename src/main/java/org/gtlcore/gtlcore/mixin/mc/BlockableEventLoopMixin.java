package org.gtlcore.gtlcore.mixin.mc;

import com.lowdragmc.lowdraglib.async.AsyncThreadData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {

    @Inject(method = "execute(Ljava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"),
            cancellable = true,
            remap = false)
    private void gtlcore$skipLdlibTaskAfterServerStop(Runnable runnable, CallbackInfo ci) {
        if ((Object) this instanceof MinecraftServer server &&
                AsyncThreadData.isThreadService() &&
                (!server.isRunning() || server.isStopped())) {
            ci.cancel();
        }
    }
}
