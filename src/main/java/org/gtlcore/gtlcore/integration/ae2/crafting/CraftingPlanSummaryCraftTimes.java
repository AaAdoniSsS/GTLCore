package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.Map;

public final class CraftingPlanSummaryCraftTimes {

    private CraftingPlanSummaryCraftTimes() {}

    public static Object2LongMap<AEKey> aggregateByOutput(Map<IPatternDetails, Long> patternTimes) {
        Object2LongOpenHashMap<AEKey> craftTimes = new Object2LongOpenHashMap<>();
        for (var patternEntry : patternTimes.entrySet()) {
            long timesUsed = patternEntry.getValue();
            if (timesUsed <= 0) {
                continue;
            }

            for (var output : patternEntry.getKey().getOutputs()) {
                craftTimes.mergeLong(output.what(), timesUsed, NumberUtils::saturatedAdd);
            }
        }
        return craftTimes;
    }
}
