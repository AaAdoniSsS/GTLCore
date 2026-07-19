package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.BoundaryFailureDependencies;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastExecutor.CompilationCache;
import org.gtlcore.gtlcore.integration.ae2.crafting.compiled.MaxFastMetrics;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;

import java.util.function.Supplier;

public interface ICraftingCalculation {

    void gtlcore$handlePausing() throws InterruptedException;

    AE2CalculationMode gtlcore$getCalculationMode();

    MaxFastMetrics gtlcore$getMaxFastMetrics();

    CompilationCache gtlcore$getMaxFastCompilationCache();

    void gtlcore$beginMaxFastStrictBoundaryProbe();

    void gtlcore$endMaxFastStrictBoundaryProbe();

    boolean gtlcore$isMaxFastStrictBoundaryProbeActive();

    void gtlcore$beginMaxFastDeferredMissingScope();

    void gtlcore$commitMaxFastDeferredMissingScope();

    void gtlcore$discardMaxFastDeferredMissingScope();

    void gtlcore$beginMaxFastDeferredMissingCapture();

    void gtlcore$endMaxFastDeferredMissingCapture();

    boolean gtlcore$isMaxFastDeferredMissingCaptureActive();

    void gtlcore$recordMaxFastDeferredMissing(AEKey key, long amount);

    void gtlcore$beginMaxFastBoundaryFailureScope(BoundaryFailureDependencies failureDependencies);

    void gtlcore$endMaxFastBoundaryFailureScope();

    void gtlcore$propagateMaxFastBoundaryFailure(BoundaryFailureDependencies failureDependencies);

    void gtlcore$recordMaxFastBoundaryFailure(AEKey key, long templateAmount,
                                              IPatternDetails.IInput input, long missingTemplates);

    void gtlcore$markMaxFastBoundaryFailureUnknown();

    Iterable<InputTemplate> gtlcore$getCachedTemplates(ICraftingInventory inv,
                                                       IPatternDetails.IInput input,
                                                       Level level,
                                                       AEKey what,
                                                       Supplier<Iterable<InputTemplate>> loader);

    void gtlcore$clearTemplateCache();
}
