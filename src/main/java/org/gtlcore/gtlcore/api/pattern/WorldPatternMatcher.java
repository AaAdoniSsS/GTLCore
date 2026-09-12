package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import net.minecraft.server.level.ServerLevel;

/** Selects the world fast path independently of preview scopes and diagnostic logging. */
public final class WorldPatternMatcher {

    private static final boolean FIXED_ENABLED = !Boolean.getBoolean("gtlcore.world.disableFixedMatcher");

    private WorldPatternMatcher() {}

    /** The caller must also verify that every aisle has an exact, valid repetition count. */
    public static boolean useFixedLayout(MultiblockState state) {
        return FIXED_ENABLED && state.world instanceof ServerLevel;
    }
}
