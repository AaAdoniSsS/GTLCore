package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;

import java.util.function.Supplier;

public interface ICraftingCalculation {

    void gtlcore$handlePausing() throws InterruptedException;

    AE2CalculationMode gtlcore$getCalculationMode();

    Iterable<InputTemplate> gtlcore$getCachedTemplates(ICraftingInventory inv,
                                                       IPatternDetails.IInput input,
                                                       Level level,
                                                       AEKey what,
                                                       Supplier<Iterable<InputTemplate>> loader);

    boolean gtlcore$shouldSkipBranch(AEKey output, IPatternDetails details, long requestedAmount);

    void gtlcore$recordBranchFailure(AEKey output, IPatternDetails details, long requestedAmount);

    void gtlcore$recordBranchSuccess(AEKey output, IPatternDetails details);

    int gtlcore$getBranchSuccesses(AEKey output, IPatternDetails details);

    void gtlcore$clearTemplateCache();

    boolean gtlcore$isAdaptive();
}
