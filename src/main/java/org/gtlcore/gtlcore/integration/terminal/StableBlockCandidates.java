package org.gtlcore.gtlcore.integration.terminal;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Only explicitly fixed candidate suppliers may be reused during a single terminal action. */
public final class StableBlockCandidates {

    private static final Map<Supplier<?>, Boolean> FIXED = Collections.synchronizedMap(new WeakHashMap<>());

    private StableBlockCandidates() {}

    public static <T extends Supplier<BlockInfo[]>> T mark(T supplier) {
        FIXED.put(supplier, Boolean.TRUE);
        return supplier;
    }

    public static boolean contains(Supplier<?> supplier) {
        return FIXED.containsKey(supplier);
    }
}
