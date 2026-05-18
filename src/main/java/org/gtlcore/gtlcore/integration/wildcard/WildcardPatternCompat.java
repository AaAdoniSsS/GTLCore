package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraftforge.fml.ModList;

/**
 * Compatibility layer for wildcard_pattern mod.
 * This class is safe to load even when wildcard_pattern is not present.
 */
public class WildcardPatternCompat {

    private static final String MOD_ID = "wildcard_pattern";
    private static final boolean IS_LOADED = ModList.get().isLoaded(MOD_ID);

    public static boolean isLoaded() {
        return IS_LOADED;
    }

    /**
     * Initialize wildcard pattern integration.
     * Only call this during mod initialization.
     */
    public static void init() {
        if (IS_LOADED) {
            try {
                WildcardPatternCompatImpl.init();
                GTLCore.LOGGER.info("Wildcard Pattern integration enabled");
            } catch (Exception e) {
                GTLCore.LOGGER.error("Failed to initialize Wildcard Pattern integration", e);
            }
        }
    }

    /**
     * Register machines for wildcard pattern integration.
     * Only call this during machine registration phase.
     */
    public static void registerMachines() {
        if (IS_LOADED) {
            try {
                WildcardPatternCompatImpl.registerMachines();
            } catch (Exception e) {
                GTLCore.LOGGER.error("Failed to register Wildcard Pattern machines", e);
            }
        }
    }
}
