package org.gtlcore.gtlcore.api.recipe;

public interface IRecipeChanceDisplay {

    void gtlcore$setChanceDisplayTiers(int recipeTier, int selectedTier);

    int gtlcore$getDisplayChance();

    int gtlcore$getUnclampedDisplayChance();

    int gtlcore$getChanceBoostTierCount();
}
