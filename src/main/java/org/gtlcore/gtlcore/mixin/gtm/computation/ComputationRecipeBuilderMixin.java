package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.LongComputationRecipeBuilder;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GTRecipeBuilder.class)
public abstract class ComputationRecipeBuilderMixin implements LongComputationRecipeBuilder<GTRecipeBuilder> {

    @Shadow(remap = false)
    public boolean perTick;

    @Override
    public GTRecipeBuilder inputCWU(long amount) {
        return ((GTRecipeBuilder) (Object) this).input((RecipeCapability) CWURecipeCapability.CAP, ComputationAmounts.box(amount));
    }

    @Override
    public GTRecipeBuilder outputCWU(long amount) {
        return ((GTRecipeBuilder) (Object) this).output((RecipeCapability) CWURecipeCapability.CAP, ComputationAmounts.box(amount));
    }

    @Override
    public GTRecipeBuilder CWUt(long amount) {
        boolean previous = perTick;
        perTick = true;
        try {
            var self = (GTRecipeBuilder) (Object) this;
            if (amount > 0) self.tickInput.remove(CWURecipeCapability.CAP);
            else if (amount < 0) self.tickOutput.remove(CWURecipeCapability.CAP);
            if (amount > 0) inputCWU(amount);
            else if (amount < 0) outputCWU(Math.negateExact(amount));
            return (GTRecipeBuilder) (Object) this;
        } finally {
            perTick = previous;
        }
    }

    @Override
    public GTRecipeBuilder totalCWU(long amount) {
        int duration = ComputationAmounts.duration(amount);
        var self = (GTRecipeBuilder) (Object) this;
        self.durationIsTotalCWU(true).hideDuration(true).addData(ComputationAmounts.TOTAL_CWU, amount);
        self.duration(duration);
        return self;
    }

    @Override
    public GTRecipeBuilder inputCWU(String amount) {
        return inputCWU(ComputationAmounts.read(amount));
    }

    @Override
    public GTRecipeBuilder outputCWU(String amount) {
        return outputCWU(ComputationAmounts.read(amount));
    }

    @Override
    public GTRecipeBuilder CWUt(String amount) {
        return CWUt(new java.math.BigDecimal(amount).longValueExact());
    }

    @Override
    public GTRecipeBuilder totalCWU(String amount) {
        return totalCWU(ComputationAmounts.read(amount));
    }

    @Inject(method = "inputCWU(I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$inputCWU(int amount, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        cir.setReturnValue(inputCWU((long) amount));
    }

    @Inject(method = "outputCWU(I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$outputCWU(int amount, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        cir.setReturnValue(outputCWU((long) amount));
    }

    @Inject(method = "CWUt(I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$CWUt(int amount, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        cir.setReturnValue(CWUt((long) amount));
    }

    @Inject(method = "totalCWU(I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$totalCWU(int amount, CallbackInfoReturnable<GTRecipeBuilder> cir) {
        cir.setReturnValue(totalCWU((long) amount));
    }
}
