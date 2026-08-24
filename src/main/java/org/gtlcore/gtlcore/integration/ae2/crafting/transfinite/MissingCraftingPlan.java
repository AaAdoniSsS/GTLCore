package org.gtlcore.gtlcore.integration.ae2.crafting.transfinite;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

/** Makes an AE2 missing-item simulation executable by a transfinite crafting CPU. */
public record MissingCraftingPlan(ICraftingPlan delegate) implements ICraftingPlan {

    @Override
    public GenericStack finalOutput() {
        return this.delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return this.delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return false;
    }

    @Override
    public boolean multiplePaths() {
        return this.delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return this.delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return this.delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return this.delegate.missingItems();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return this.delegate.patternTimes();
    }
}
