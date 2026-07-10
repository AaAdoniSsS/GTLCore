package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;
import org.jetbrains.annotations.Nullable;

public interface ICraftingTreeNode {

    void ultraFastRequest(CraftingSimulationState inv, long requestedAmount,
                          @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void fastRequest(CraftingSimulationState inv, long requestedAmount,
                     @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void legacyRequest(CraftingSimulationState inv, long requestedAmount,
                       @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException;

    void gtlcore$resetFastState();

    Object gtlcore$getRequestMergeKey();
}
