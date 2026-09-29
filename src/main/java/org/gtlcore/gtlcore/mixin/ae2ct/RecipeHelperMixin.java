package org.gtlcore.gtlcore.mixin.ae2ct;

import org.gtlcore.gtlcore.integration.ae2.graph.GraphSummaryContext;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Map;

/** Skip redundant tree construction only for graph plans; preserve legacy addon behavior. */
@Pseudo
@Mixin(targets = "com.neuvillette.ae2ct.api.RecipeHelper", remap = false)
public abstract class RecipeHelperMixin {

    @Coerce
    @WrapMethod(method = "fromCraftingPlan", remap = false)
    private static Object gtlcore$omitTreeRecipes(CraftingPlan plan, Operation<Object> original) {
        if (!GraphSummaryContext.isGraphPlan(plan)) return original.call(plan);
        // Do not enumerate pattern alternatives or multiply amounts here. The ring
        // browser obtains the selected immutable graph lazily through its own pages.
        return original.call(new CraftingPlan(plan.finalOutput(), 0, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of()));
    }
}
