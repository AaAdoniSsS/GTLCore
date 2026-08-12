package org.gtlcore.gtlcore.mixin.ldlib;

import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;

import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferHelperImpl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidTransferHelperImpl.class)
public abstract class FluidTransferHelperImplMixin {

    @Inject(method = "toFluidHandler", at = @At("RETURN"), remap = false)
    private static void gtlcore$preserveLongFluidTransfer(IFluidTransfer transfer,
                                                          CallbackInfoReturnable<IFluidHandler> cir) {
        IFluidHandler handler = cir.getReturnValue();
        if (handler.getTanks() == transfer.getTanks()) {
            LongStorageAdapterRegistry.attachFluidStorage(handler,
                    tank -> transfer.getFluidInTank(tank).getAmount());
        }
    }

    @Inject(method = "getFluidTransfer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lcom/lowdragmc/lowdraglib/side/fluid/IFluidTransfer;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void gtlcore$skipBlockLookupAfterServerStop(Level level, BlockPos pos, Direction side,
                                                               CallbackInfoReturnable<IFluidTransfer> cir) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        if (!server.isRunning() || server.isStopped()) {
            cir.setReturnValue(null);
        }
    }
}
