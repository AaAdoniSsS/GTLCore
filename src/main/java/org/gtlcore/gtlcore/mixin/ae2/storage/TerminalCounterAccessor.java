package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalVariantAccess;

import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = KeyCounter.class, remap = false)
public interface TerminalCounterAccessor {

    @Accessor("lists")
    Reference2ObjectMap<Object, TerminalVariantAccess> gtlcore$terminalGroups();
}
