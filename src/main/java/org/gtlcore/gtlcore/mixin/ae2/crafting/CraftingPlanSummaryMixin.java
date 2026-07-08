package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPlanSummaryCraftTimes;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingPlanSummaryEntry;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(CraftingPlanSummary.class)
public class CraftingPlanSummaryMixin {

    @Inject(method = "fromJob", at = @At(value = "INVOKE", target = "Ljava/util/Collections;sort(Ljava/util/List;)V"), remap = false)
    private static void injectCraftTimes(IGrid grid, IActionSource actionSource, ICraftingPlan job, CallbackInfoReturnable<CraftingPlanSummary> cir, @Local ArrayList<CraftingPlanSummaryEntry> entries) {
        var craftTimesByOutput = CraftingPlanSummaryCraftTimes.aggregateByOutput(job.patternTimes());
        for (var entry : entries) {
            ((ICraftingPlanSummaryEntry) entry).gtlcore$setCraftTimes(craftTimesByOutput.getLong(entry.getWhat()));
        }
    }
}
