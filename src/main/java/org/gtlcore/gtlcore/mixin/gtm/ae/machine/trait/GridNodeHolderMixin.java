package org.gtlcore.gtlcore.mixin.gtm.ae.machine.trait;

import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GridNodeHolder.class)
public abstract class GridNodeHolderMixin {

    @Inject(method = "createMainNode", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$skipNodeCreationAfterServerStop(CallbackInfo ci) {
        Level level = ((MachineTrait) (Object) this).getMachine().getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        if (!server.isRunning() || server.isStopped()) {
            ci.cancel();
        }
    }
}
