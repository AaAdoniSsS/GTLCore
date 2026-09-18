package org.gtlcore.gtlcore.mixin.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.me.storage.MEInventoryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MEInventoryHandler.class)
public interface MEInventoryHandlerDisplayAccessor {

    @Accessor(value = "allowExtraction", remap = false)
    boolean gtlcore$allowsDisplayExtraction();

    @Accessor(value = "filterOnExtraction", remap = false)
    boolean gtlcore$filtersDisplayExtraction();

    @Accessor(value = "filterAvailableContents", remap = false)
    boolean gtlcore$filtersDisplayContents();

    @Invoker(value = "canExtract", remap = false)
    boolean gtlcore$canDisplay(AEKey key);
}
