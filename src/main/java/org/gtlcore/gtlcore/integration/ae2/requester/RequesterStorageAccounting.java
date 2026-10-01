package org.gtlcore.gtlcore.integration.ae2.requester;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public interface RequesterStorageAccounting {

    void gtlcore$updateSnapshot(IStorageService service, long version, @Nullable AEKey requestedKey, long amount);

    void gtlcore$prepareExport(IStorageService service, long version, @Nullable AEKey exportedKey);

    void gtlcore$finishExport(@Nullable AEKey requestedKey);

    boolean gtlcore$hasStock(long amount);
}
