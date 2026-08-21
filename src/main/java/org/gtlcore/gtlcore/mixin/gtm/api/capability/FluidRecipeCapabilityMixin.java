package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.common.data.source_tooltip.SourceTooltip;
import org.gtlcore.gtlcore.integration.jei.RecipeChanceTooltip;
import org.gtlcore.gtlcore.utils.TextUtil;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.*;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.client.TooltipsHandler;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.google.common.primitives.Ints;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BiConsumer;

import static com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP;

@Mixin(FluidRecipeCapability.class)
public abstract class FluidRecipeCapabilityMixin extends RecipeCapability<FluidIngredient> {

    protected FluidRecipeCapabilityMixin(String name, int color, boolean doRenderSlot, int sortIndex, IContentSerializer<FluidIngredient> serializer) {
        super(name, color, doRenderSlot, sortIndex, serializer);
    }

    /**
     * @author Adonis
     * @reason 流体输入输出上限改为long
     */
    @Overwrite(remap = false)
    public FluidIngredient copyWithModifier(FluidIngredient content, ContentModifier modifier) {
        if (content.isEmpty()) {
            return content.copy();
        } else {
            FluidIngredient copy = content.copy();
            copy.setAmount(modifier.apply(copy.getAmount()).longValue());
            return copy;
        }
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public int limitParallel(GTRecipe recipe, IRecipeCapabilityHolder holder, int multiplier) {
        return Ints.saturatedCast(IParallelLogic.getOutputFluidParallel(holder, recipe, recipe.getOutputContents(CAP), multiplier));
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public int getMaxParallelRatio(IRecipeCapabilityHolder holder, GTRecipe recipe, int parallelAmount) {
        return Ints.saturatedCast(IParallelLogic.getInputFluidParallel(holder, recipe, parallelAmount));
    }

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "applyWidgetInfo",
            at = @At(value = "INVOKE",
                     target = "Lcom/lowdragmc/lowdraglib/gui/widget/TankWidget;setXEIChance(F)Lcom/lowdragmc/lowdraglib/gui/widget/TankWidget;",
                     shift = At.Shift.AFTER),
            remap = false,
            require = 0)
    private void hideAmountInXEI(CallbackInfo ci,
                                 @Local(name = "tank") TankWidget tank,
                                 @Local(name = "isXEI") boolean isXEI) {
        if (isXEI) {
            tank.setShowAmount(false);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @ModifyArg(method = "applyWidgetInfo",
               at = @At(value = "INVOKE",
                        target = "Lcom/lowdragmc/lowdraglib/gui/widget/TankWidget;setOnAddedTooltips(Ljava/util/function/BiConsumer;)Lcom/lowdragmc/lowdraglib/gui/widget/TankWidget;"),
               remap = false)
    public BiConsumer<TankWidget, List<Component>> applyWidgetInfo(BiConsumer<TankWidget, List<Component>> onAddedTooltips,
                                                                   @Local(name = "content") Content content,
                                                                   @Local(name = "recipe") GTRecipe recipe,
                                                                   @Local(name = "index") int index,
                                                                   @Local(name = "io") IO io,
                                                                   @Local(name = "isXEI") boolean isXEI) {
        return (w, tooltips) -> {
            var ingredient = FluidRecipeCapability.CAP.of(content.content);
            if (ingredient.getStacks().length > 0) {
                FluidStack stack = ingredient.getStacks()[0];
                if (!isXEI) {
                    TooltipsHandler.appendFluidTooltips(stack.getFluid(),
                            stack.getAmount(), tooltips::add, TooltipFlag.NORMAL);
                } else {
                    TextUtil.appendChemicalFormulaTooltip(stack.getFluid(), tooltips);
                    // EMI 中的来源提示在其他地方被添加过了
                    if (!LDLib.isEmiLoaded()) {
                        SourceTooltip.append(stack.getFluid(), tooltips::add);
                    }
                    TextUtil.appendIngotConversionTooltip(stack, tooltips, stack.getAmount());
                }
            }

            RecipeChanceTooltip.add(content, ChanceLogic.OR, tooltips, io);
            if (isTickSlot(index, io, recipe)) {
                tooltips.add(Component.translatable("gtceu.gui.content.per_tick"));
            }
        };
    }
}
