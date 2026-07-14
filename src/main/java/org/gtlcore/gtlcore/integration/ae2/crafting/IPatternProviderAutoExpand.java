package org.gtlcore.gtlcore.integration.ae2.crafting;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

public interface IPatternProviderAutoExpand {

    long gtlcore$getMaxPatternOperations(IPatternDetails pattern, long requestedOperations);

    long gtlcore$findMaxOperationsForTarget(PatternProviderTarget target, BlockEntity targetBE, Direction side,
                                            KeyCounter baseInputs, long requestedOperations);
}
