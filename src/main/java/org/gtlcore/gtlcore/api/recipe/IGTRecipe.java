package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

public interface IGTRecipe {

    static IGTRecipe of(GTRecipe recipe) {
        return (IGTRecipe) recipe;
    }

    void setHasTick(boolean hasTick);

    int getEuTier();

    long getRealParallels();

    void setRealParallels(long realParallels);

    int getBatchSize();

    void setBatchSize(int batchSize);

    boolean isBatchProcessed();

    void setBatchProcessed(boolean batchProcessed);
}
