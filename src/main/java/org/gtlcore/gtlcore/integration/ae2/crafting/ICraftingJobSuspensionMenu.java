package org.gtlcore.gtlcore.integration.ae2.crafting;

public interface ICraftingJobSuspensionMenu {

    boolean gtlcore$isJobSuspensionAvailable();

    boolean gtlcore$isJobSuspended();

    void gtlcore$toggleScheduling();
}
