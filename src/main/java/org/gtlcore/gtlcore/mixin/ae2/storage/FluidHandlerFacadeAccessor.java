package org.gtlcore.gtlcore.mixin.ae2.storage;

import net.minecraftforge.fluids.capability.IFluidHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$FluidHandlerFacade", remap = false)
public interface FluidHandlerFacadeAccessor {

    @Accessor("handler")
    IFluidHandler gtlcore$getHandler();
}
