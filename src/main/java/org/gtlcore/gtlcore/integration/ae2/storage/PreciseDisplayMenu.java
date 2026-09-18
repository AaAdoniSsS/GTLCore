package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/** Client state belongs to the menu, so initial packets do not depend on screen construction. */
public interface PreciseDisplayMenu {

    Map<AEKey, BigInteger> gtlcore$displayAmounts();

    void gtlcore$acceptDisplayChanges(Map<AEKey, BigInteger> changes, boolean complete);
}
