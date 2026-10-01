package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalVariantAccess;

import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "appeng.api.stacks.VariantCounter", remap = false)
public abstract class TerminalVariantMixin implements TerminalVariantAccess {

    @Shadow
    public abstract long get(AEKey key);

    @Override
    public long gtlcore$terminalAmount(AEKey key) {
        return get(key);
    }
}
