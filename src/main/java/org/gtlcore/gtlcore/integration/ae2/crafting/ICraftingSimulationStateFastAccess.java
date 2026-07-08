package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

public interface ICraftingSimulationStateFastAccess {

    void gtlcore$mergeRequiredExtract(AEKey key, long amount);

    void gtlcore$directInsert(AEKey key, long amount);

    long gtlcore$directExtract(AEKey key, long amount);

    void gtlcore$directEmit(AEKey key, long amount);

    void gtlcore$directAddBytes(double bytes);

    void gtlcore$directAddCrafting(IPatternDetails details, long times);
}
