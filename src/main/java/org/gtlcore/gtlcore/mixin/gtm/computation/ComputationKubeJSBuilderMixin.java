package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.LongComputationRecipeBuilder;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GTRecipeSchema.GTRecipeJS.class)
public abstract class ComputationKubeJSBuilderMixin implements LongComputationRecipeBuilder<GTRecipeSchema.GTRecipeJS> {

    @Shadow(remap = false)
    public boolean perTick;

    @Override
    public GTRecipeSchema.GTRecipeJS inputCWU(long amount) {
        return ((GTRecipeSchema.GTRecipeJS) (Object) this).input((RecipeCapability) CWURecipeCapability.CAP, ComputationAmounts.box(amount));
    }

    @Override
    public GTRecipeSchema.GTRecipeJS outputCWU(long amount) {
        return ((GTRecipeSchema.GTRecipeJS) (Object) this).output((RecipeCapability) CWURecipeCapability.CAP, ComputationAmounts.box(amount));
    }

    @Override
    public GTRecipeSchema.GTRecipeJS CWUt(long amount) {
        boolean previous = perTick;
        perTick = true;
        try {

            if (amount > 0) inputCWU(amount);
            else if (amount < 0) outputCWU(Math.negateExact(amount));
            return (GTRecipeSchema.GTRecipeJS) (Object) this;
        } finally {
            perTick = previous;
        }
    }

    @Override
    public GTRecipeSchema.GTRecipeJS totalCWU(long amount) {
        int duration = ComputationAmounts.duration(amount);
        var self = (GTRecipeSchema.GTRecipeJS) (Object) this;
        self.durationIsTotalCWU(true).hideDuration(true).addData(ComputationAmounts.TOTAL_CWU, amount);
        self.setValue(GTRecipeSchema.DURATION, (long) duration);
        return self;
    }

    @Override
    public GTRecipeSchema.GTRecipeJS inputCWU(String amount) {
        return inputCWU(ComputationAmounts.read(amount));
    }

    @Override
    public GTRecipeSchema.GTRecipeJS outputCWU(String amount) {
        return outputCWU(ComputationAmounts.read(amount));
    }

    @Override
    public GTRecipeSchema.GTRecipeJS CWUt(String amount) {
        return CWUt(new java.math.BigDecimal(amount).longValueExact());
    }

    @Override
    public GTRecipeSchema.GTRecipeJS totalCWU(String amount) {
        return totalCWU(ComputationAmounts.read(amount));
    }

    @Inject(method = "inputCWU(I)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTRecipeJS;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$inputCWU(int amount, CallbackInfoReturnable<GTRecipeSchema.GTRecipeJS> cir) {
        cir.setReturnValue(inputCWU((long) amount));
    }

    @Inject(method = "outputCWU(I)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTRecipeJS;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$outputCWU(int amount, CallbackInfoReturnable<GTRecipeSchema.GTRecipeJS> cir) {
        cir.setReturnValue(outputCWU((long) amount));
    }

    @Inject(method = "CWUt(I)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTRecipeJS;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$CWUt(int amount, CallbackInfoReturnable<GTRecipeSchema.GTRecipeJS> cir) {
        cir.setReturnValue(CWUt((long) amount));
    }

    @Inject(method = "totalCWU(I)Lcom/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema$GTRecipeJS;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$totalCWU(int amount, CallbackInfoReturnable<GTRecipeSchema.GTRecipeJS> cir) {
        cir.setReturnValue(totalCWU((long) amount));
    }
}
