package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$FluidHandlerFacade", remap = false)
public abstract class FluidHandlerFacadeMixin extends ExternalStorageFacade {

    @Shadow
    @Final
    private IFluidHandler handler;

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void gtlcore$publishLongFluidAmounts(KeyCounter counter, CallbackInfo ci) {
        LongStorageAdapterRegistry.LongFluidStorage storage = LongStorageAdapterRegistry.findFluidStorage(handler);
        if (storage == null) return;

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack exposed = handler.getFluidInTank(tank);
            if (exposed.isEmpty()) continue;
            if (extractableOnly && handler.drain(exposed, IFluidHandler.FluidAction.SIMULATE).isEmpty()) continue;

            AEFluidKey key = AEFluidKey.of(exposed);
            long amount = storage.getAmount(tank);
            if (key != null && amount > 0L) {
                counter.add(key, amount);
            }
        }
        ci.cancel();
    }
}
