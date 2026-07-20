package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;
import org.jetbrains.annotations.Nullable;

public interface ICraftingTreeProcess {

    void fastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    void ultraFastRequest(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    IPatternDetails getDetails();

    boolean getPossible();

    void setPossible(boolean b);

    long getOutputCountTest(AEKey what);

    boolean limitsQuantityTest();

    boolean gtlcore$hasContainerItems();

    CraftingTreeNode[] gtlcore$getChildNodes();

    long[] gtlcore$getChildMultipliers();

    void gtlcore$completeMaxFast(CraftingSimulationState inv, long times);

    boolean gtlcore$notRecursive(IPatternDetails details);

    @Nullable
    CraftingTreeNode gtlcore$getMaxFastParentNode();

    void gtlcore$resetFastState();
}
