package org.gtlcore.gtlcore.mixin.gtm.cover;

import org.gtlcore.gtlcore.utils.TagExprFilter;

import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagFluidFilter;
import com.gregtechceu.gtceu.utils.OreDictExprFilter;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.tags.TagKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(TagFluidFilter.class)
public abstract class TagFluidFilterMixin extends TagFilter<FluidStack, FluidFilter> {

    @Unique
    private final TagExprFilter.CachedExpression gtlcore$tagExpression = new TagExprFilter.CachedExpression();

    @Redirect(method = "test(Lcom/lowdragmc/lowdraglib/side/fluid/FluidStack;)Z",
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/utils/OreDictExprFilter;matchesOreDict(Ljava/util/List;Lcom/lowdragmc/lowdraglib/side/fluid/FluidStack;)Z"),
              remap = false)
    private boolean gtlcore$matchTags(List<OreDictExprFilter.MatchRule> rules, FluidStack stack) {
        var tags = stack.getFluid().defaultFluidState().getTags().map(TagKey::location)
                .map(Object::toString).collect(Collectors.toSet());
        return gtlcore$tagExpression.matches(oreDictFilterExpression, tags);
    }
}
