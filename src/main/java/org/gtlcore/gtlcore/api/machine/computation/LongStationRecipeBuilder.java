package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.recipe.ResearchRecipeBuilder.StationRecipeBuilder;

public interface LongStationRecipeBuilder {

    StationRecipeBuilder CWUt(long rate);

    StationRecipeBuilder CWUt(long rate, long total);

    StationRecipeBuilder CWUt(String rate);

    StationRecipeBuilder CWUt(String rate, String total);
}
