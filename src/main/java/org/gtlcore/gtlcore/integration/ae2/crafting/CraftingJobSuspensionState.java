package org.gtlcore.gtlcore.integration.ae2.crafting;

public final class CraftingJobSuspensionState {

    public static final String ACTION_TOGGLE_SCHEDULING = "toggleScheduling";
    public static final String NBT_SUSPENDED = "suspended";
    public static final short GUI_SYNC_SUSPENDED = 10;
    public static final short GUI_SYNC_AVAILABLE = 11;

    private CraftingJobSuspensionState() {}

    public static boolean toggled(boolean suspended) {
        return !suspended;
    }
}
