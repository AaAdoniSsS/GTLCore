package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationResearchEntries;
import org.gtlcore.gtlcore.api.machine.computation.LongStationRecipeBuilder;

import com.gregtechceu.gtceu.api.recipe.ResearchRecipeBuilder.StationRecipeBuilder;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StationRecipeBuilder.class)
public abstract class ComputationStationBuilderMixin implements LongStationRecipeBuilder {

    @Shadow(remap = false) private int cwut;
    @Shadow(remap = false) private int totalCWU;
    @Unique private long gtlcore$rate;
    @Unique private long gtlcore$total;

    @Override
    public StationRecipeBuilder CWUt(long rate) {
        return CWUt(rate, Math.multiplyExact(rate, StationRecipeBuilder.DEFAULT_STATION_TOTAL_CWUT));
    }

    @Override
    public StationRecipeBuilder CWUt(long rate, long total) {
        if (rate <= 0 || total < rate) throw new IllegalArgumentException("Research requires 0 < CWU/t <= total CWU");
        gtlcore$rate = rate;
        gtlcore$total = total;
        cwut = ComputationMath.toInt(rate);
        totalCWU = ComputationMath.toInt(total);
        return (StationRecipeBuilder) (Object) this;
    }

    @Override
    public StationRecipeBuilder CWUt(String rate) {
        return CWUt(ComputationAmounts.read(rate));
    }

    @Override
    public StationRecipeBuilder CWUt(String rate, String total) {
        return CWUt(ComputationAmounts.read(rate), ComputationAmounts.read(total));
    }

    @Inject(method = "CWUt(I)Lcom/gregtechceu/gtceu/api/recipe/ResearchRecipeBuilder$StationRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$defaultTotal(int rate, CallbackInfoReturnable<StationRecipeBuilder> cir) {
        cir.setReturnValue(CWUt((long) rate));
    }

    @Inject(method = "CWUt(II)Lcom/gregtechceu/gtceu/api/recipe/ResearchRecipeBuilder$StationRecipeBuilder;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$explicitTotal(int rate, int total, CallbackInfoReturnable<StationRecipeBuilder> cir) {
        cir.setReturnValue(CWUt((long) rate, total));
    }

    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private void gtlcore$longEntry(CallbackInfoReturnable<GTRecipeBuilder.ResearchRecipeEntry> cir) {
        if (gtlcore$rate > 0) ComputationResearchEntries.remember(cir.getReturnValue().dataStack(), gtlcore$rate, gtlcore$total);
    }
}
