package org.gtlcore.gtlcore.client.ae2;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

public interface PreciseRepoAmounts {

    void gtlcore$setPreciseAmounts(Map<AEKey, BigInteger> amounts);
}
