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

    void gtlcore$recordBranchSkip(AEKey output, IPatternDetails details, long requestedAmount);

    void gtlcore$recordBranchSuccess(AEKey output, IPatternDetails details, long craftedAmount);

    Object gtlcore$getPreferredBranchDefinition(AEKey output);

    void gtlcore$clearTemplateCache();

    boolean gtlcore$isCraftingCalculationLogEnabled();

    AE2CraftingCalculationLogger.Counters gtlcore$getCraftingCalculationLogCounters();

    default void gtlcore$recordCraftingLogTemplateExtraction(long extracted) {
        if (gtlcore$isCraftingCalculationLogEnabled()) {
            gtlcore$getCraftingCalculationLogCounters().recordTemplateExtraction(extracted);
        }
    }

    default void gtlcore$recordCraftingLogNodeRequest() {
        if (gtlcore$isCraftingCalculationLogEnabled()) {
            gtlcore$getCraftingCalculationLogCounters().recordNodeRequest();
        }
    }

    default void gtlcore$recordCraftingLogProcessRequest(boolean limitQty, long times, int childEdges) {
        if (gtlcore$isCraftingCalculationLogEnabled()) {
            gtlcore$getCraftingCalculationLogCounters().recordProcessRequest(limitQty, times, childEdges);
        }
    }
}
