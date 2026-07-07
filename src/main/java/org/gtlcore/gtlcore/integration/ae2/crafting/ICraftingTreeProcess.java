package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;

public interface ICraftingTreeProcess {

    void adaptiveRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    void fastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    void ultraFastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    IPatternDetails getDetails();

    boolean getPossible();

    void setPossible(boolean b);

    long getOutputCountTest(AEKey what);

    boolean limitsQuantityTest();

    void gtlcore$resetAdaptiveState();
}
