package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/** Optional display-only access to an immutable, non-negative stored quantity. */
public interface PreciseStorageAmount {

    BigInteger getExactStoredAmount(AEKey key);
}
