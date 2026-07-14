package org.gtlcore.gtlcore.mixin.ldlib;

import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.item.forge.ItemTransferHelperImpl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemTransferHelperImpl.class)
public abstract class ItemTransferHelperImplMixin {

    @Inject(method = "getItemTransfer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lcom/lowdragmc/lowdraglib/side/item/IItemTransfer;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void gtlcore$skipBlockLookupAfterServerStop(Level level, BlockPos pos, Direction side,
                                                               CallbackInfoReturnable<IItemTransfer> cir) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        if (!server.isRunning() || server.isStopped()) {
            cir.setReturnValue(null);
        }
    }
}
