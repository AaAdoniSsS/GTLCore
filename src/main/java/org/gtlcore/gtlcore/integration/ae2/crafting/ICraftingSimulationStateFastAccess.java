package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ICraftingSimulationStateFastAccess {

    void gtlcore$mergeRequiredExtract(AEKey key, long amount);

    void gtlcore$directInsert(AEKey key, long amount);

    long gtlcore$directExtract(AEKey key, long amount);

    void gtlcore$directEmit(AEKey key, long amount);

    void gtlcore$directAddBytes(double bytes);

    void gtlcore$directAddCrafting(IPatternDetails details, long times);

    void gtlcore$collectMaxFastPositiveDiff(Set<Object> changedPrimaryKeys);

    /**
     * Returns the next parent that owns the template cache for this input, or
     * {@code null} when this state is the owner. The routing step is separate
     * from cache lookup so a deep child chain can be walked iteratively.
     */
    @Nullable
    ICraftingSimulationStateFastAccess gtlcore$getMaxFastTemplateDelegate(IPatternDetails.IInput input);

    Iterable<InputTemplate> gtlcore$getMaxFastOwnedTemplates(IPatternDetails.IInput input, Level level, AEKey what,
                                                             long validationEpoch, MaxFastMetrics metrics);

    Iterable<InputTemplate> gtlcore$getMaxFastTemplates(IPatternDetails.IInput input, Level level, AEKey what,
                                                        long validationEpoch, MaxFastMetrics metrics);
}
