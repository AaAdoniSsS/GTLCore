package org.gtlcore.gtlcore.mixin.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputStorageView;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.List;

@Mixin(DelegatingMEInventory.class)
public abstract class DelegatingMEInventoryMixin implements ThroughputStorageView {

    @Shadow(remap = false)
    protected abstract MEStorage getDelegate();

    @Override
    public Collection<MEStorage> gtlcore$getChildStorages() {
        MEStorage delegate = getDelegate();
        return delegate == null ? List.of() : List.of(delegate);
    }
}
