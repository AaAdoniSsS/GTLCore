package org.gtlcore.gtlcore.mixin.ae2.storage;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.NavigableMap;

@Mixin(value = NetworkStorage.class, remap = false)
public interface NetworkStorageAccessor {

    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> gtlcore$getPriorityInventory();
}
