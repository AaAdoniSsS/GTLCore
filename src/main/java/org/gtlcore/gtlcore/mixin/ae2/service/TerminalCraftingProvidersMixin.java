package org.gtlcore.gtlcore.mixin.ae2.service;

import org.gtlcore.gtlcore.integration.ae2.storage.TerminalCraftables;

import appeng.api.stacks.AEKey;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(NetworkCraftingProviders.class)
public abstract class TerminalCraftingProvidersMixin implements TerminalCraftables {

    @Shadow(remap = false)
    public abstract Set<AEKey> getCraftableKeys();

    @Shadow(remap = false)
    public abstract Set<AEKey> getEmittableKeys();

    @Unique
    private Set<AEKey> gtlcore$terminalSnapshot;

    // Invalidate on both sides so a nested read during mounting cannot retain a partial snapshot.
    @Inject(method = { "addProvider", "removeProvider" }, at = @At("HEAD"), remap = false)
    private void gtlcore$beforeProviderChange(CallbackInfo ci) {
        gtlcore$terminalSnapshot = null;
    }

    @Inject(method = { "addProvider", "removeProvider" }, at = @At("RETURN"), remap = false)
    private void gtlcore$afterProviderChange(CallbackInfo ci) {
        gtlcore$terminalSnapshot = null;
    }

    @Override
    public Set<AEKey> gtlcore$terminalCraftables() {
        if (gtlcore$terminalSnapshot == null) {
            gtlcore$terminalSnapshot = ImmutableSet.<AEKey>builder()
                    .addAll(getCraftableKeys()).addAll(getEmittableKeys()).build();
        }
        return gtlcore$terminalSnapshot;
    }
}
