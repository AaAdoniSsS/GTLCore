package org.gtlcore.gtlcore.mixin.ae2.storage;

import net.minecraftforge.items.IItemHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ItemHandlerFacade", remap = false)
public interface ItemHandlerFacadeAccessor {

    @Accessor("handler")
    IItemHandler gtlcore$getHandler();
}
