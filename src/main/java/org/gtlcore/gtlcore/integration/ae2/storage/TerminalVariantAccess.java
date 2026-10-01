package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Read-only access to a native variant group. */
public interface TerminalVariantAccess extends Iterable<Object2LongMap.Entry<AEKey>> {

    long gtlcore$terminalAmount(AEKey key);
}
