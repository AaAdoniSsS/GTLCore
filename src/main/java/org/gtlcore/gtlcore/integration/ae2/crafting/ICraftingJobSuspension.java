package org.gtlcore.gtlcore.integration.ae2.crafting;

public interface ICraftingJobSuspension {

    boolean gtlcore$isJobSuspended();

    void gtlcore$setJobSuspended(boolean suspended);

    default void gtlcore$toggleJobSuspended() {
        gtlcore$setJobSuspended(CraftingJobSuspensionState.toggled(gtlcore$isJobSuspended()));
    }
}
