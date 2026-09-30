package org.gtlcore.gtlcore.mixin.ae2.storage;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.hooks.ticking.TickHandler;
import appeng.me.storage.MEInventoryHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

/**
 * isPreferredStorageFor runs a simulated "contains" extraction against the wrapped storage for
 * EVERY insert into the network. When the wrapped storage is an external facade over a giant
 * composite inventory (LDLib ItemTransferList), that probe walks slots x sub-handlers and can
 * take seconds per call. The answer only influences insert routing preference, never
 * correctness, so a one-tick-stale memo is safe and collapses the per-insert probe storm.
 */
@Mixin(value = MEInventoryHandler.class, remap = false)
public abstract class MEInventoryHandlerPreferredMixin {

    @Unique
    private long gtlcore$prefTick = Long.MIN_VALUE;

    @Unique
    private final Map<AEKey, Boolean> gtlcore$prefCache = new HashMap<>();

    @WrapMethod(method = "isPreferredStorageFor")
    private boolean gtlcore$memoizePreferred(AEKey input, IActionSource source, Operation<Boolean> original) {
        long now = TickHandler.instance().getCurrentTick();
        if (now != gtlcore$prefTick) {
            gtlcore$prefTick = now;
            gtlcore$prefCache.clear();
        }
        Boolean cached = gtlcore$prefCache.get(input);
        if (cached != null) {
            return cached;
        }
        boolean result = original.call(input, source);
        gtlcore$prefCache.put(input, result);
        return result;
    }
}
