package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.api.recipe.IAdvancedOCResult;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gregtechceu.gtceu.api.recipe.OverclockingLogic.*;

@Mixin(OverclockingLogic.class)
public abstract class OverclockingLogicMixin {

    /**
     * @author mod_author
     * @reason 原版的高炉太慢
     */
    @SuppressWarnings("DataFlowIssue")
    @Overwrite(remap = false)
    public static void heatingCoilOC(@NotNull OCParams params, @NotNull OCResult result, long maxVoltage,
                                     int providedTemp, int requiredTemp) {
        double duration = params.getDuration() * Math.max(0.5, (double) requiredTemp / providedTemp);
        double eut = params.getEut();
        int ocAmount = params.getOcAmount();
        double parallel = 1.0;
        boolean shouldParallel = false;

        int ocLevel;
        int baseOCLevel = 0;
        double vfPowParallel = 1.0;

        for (ocLevel = 0; ocAmount-- > 0; ++ocLevel) {
            double potentialVoltage = eut * STD_VOLTAGE_FACTOR;
            if (potentialVoltage > (double) maxVoltage) break;

            eut = potentialVoltage;
            if (shouldParallel) {
                parallel *= PERFECT_DURATION_FACTOR_INV;
                vfPowParallel *= STD_VOLTAGE_FACTOR;
            } else {
                double potentialDuration = duration * PERFECT_DURATION_FACTOR;
                if (potentialDuration < 1.0) {
                    parallel *= PERFECT_DURATION_FACTOR_INV;
                    vfPowParallel *= STD_VOLTAGE_FACTOR;
                    shouldParallel = true;
                } else {
                    duration = potentialDuration;
                    ++baseOCLevel;
                }
            }
        }

        eut *= Math.min(1, NumberUtils.pow95(Math.max(0, (providedTemp - requiredTemp) / 900)));

        ((IAdvancedOCResult) (Object) result).init(gTLCore$toRecipeEUt(eut / vfPowParallel), gTLCore$toRecipeDuration(duration),
                (int) parallel, gTLCore$toRecipeEUt(eut), baseOCLevel, ocLevel, PERFECT_DURATION_FACTOR, STD_VOLTAGE_FACTOR);
    }

    @Inject(method = "getOverclockForTier", at = @At("HEAD"), remap = false, cancellable = true)
    protected void getOverclockForTier(long voltage, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(NumberUtils.getFakeVoltageTier(voltage));
    }

    /**
     * @author Dragons
     * @reason 为OCResult提供更多信息
     */
    @SuppressWarnings("DataFlowIssue")
    @Overwrite(remap = false)
    public static void subTickParallelOC(@NotNull OCParams params, @NotNull OCResult result, long maxVoltage, double durationFactor, double voltageFactor) {
        double duration = params.getDuration();
        double eut = (double) params.getEut();
        int ocAmount = params.getOcAmount();
        double parallel = 1.0;
        boolean shouldParallel = false;

        int ocLevel;
        int baseOCLevel = 0;
        double vfPowParallel = 1.0;

        for (ocLevel = 0; ocAmount-- > 0; ++ocLevel) {
            double potentialVoltage = eut * voltageFactor;
            if (potentialVoltage > (double) maxVoltage) break;

            eut = potentialVoltage;
            if (shouldParallel) {
                parallel /= durationFactor;
                vfPowParallel *= voltageFactor;
            } else {
                double potentialDuration = duration * durationFactor;
                if (potentialDuration < 1.0) {
                    parallel /= durationFactor;
                    vfPowParallel *= voltageFactor;
                    shouldParallel = true;
                } else {
                    duration = potentialDuration;
                    ++baseOCLevel;
                }
            }
        }

        ((IAdvancedOCResult) (Object) result).init(gTLCore$toRecipeEUt(eut / vfPowParallel), gTLCore$toRecipeDuration(duration),
                (int) parallel, gTLCore$toRecipeEUt(eut), baseOCLevel, ocLevel, durationFactor, voltageFactor);
    }

    @Unique
    private static int gTLCore$toRecipeDuration(double duration) {
        return duration > 0 ? Math.max(1, (int) duration) : 0;
    }

    @Unique
    private static long gTLCore$toRecipeEUt(double eut) {
        if (eut > 0) return Math.max(1L, (long) eut);
        if (eut < 0) return Math.min(-1L, (long) eut);
        return 0L;
    }
}
