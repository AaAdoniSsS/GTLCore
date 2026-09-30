package org.gtlcore.gtlcore.mixin.ae2.storage;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CompositeStorage.class, remap = false)
public interface CompositeStorageAccessor {

    @Accessor("storages")
    Map<AEKeyType, MEStorage> gtlcore$getStorages();
}
