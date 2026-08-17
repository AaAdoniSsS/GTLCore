package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.lookup.AbstractMapIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.Branch;

import com.mojang.datafixers.util.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public interface IRecipeIterator {

    static @Nullable GTRecipe diveIngredientTreeFindRecipe(@NotNull List<AbstractMapIngredient> ingredients, @NotNull Branch branchMap,
                                                           @NotNull Predicate<GTRecipe> canHandle) {
        return diveIngredientTreeFindRecipe(ingredients, branchMap, canHandle,
                new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    private static @Nullable GTRecipe diveIngredientTreeFindRecipe(
                                                                   @NotNull List<AbstractMapIngredient> ingredients,
                                                                   @NotNull Branch branchMap,
                                                                   @NotNull Predicate<GTRecipe> canHandle,
                                                                   @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> normalCache,
                                                                   @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> specialCache) {
        if (ingredients.isEmpty()) return null;
        for (var ingredient : ingredients) {
            var result = lookup(ingredient, branchMap, normalCache, specialCache);
            if (result != null) {
                GTRecipe r = result.map((potentialRecipe) -> canHandle.test(potentialRecipe) ? potentialRecipe : null,
                        (potentialBranch) -> diveIngredientTreeFindRecipe(ingredients, potentialBranch, canHandle, normalCache, specialCache));
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static GTRecipe diveIngredientTreeFindRecipeCollection(@NotNull List<AbstractMapIngredient> ingredients, @NotNull Branch branchMap,
                                                           @NotNull Predicate<GTRecipe> canHandle, Set<GTRecipe> recipeSet) {
        return diveIngredientTreeFindRecipeCollection(ingredients, branchMap, canHandle, recipeSet,
                new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    private static GTRecipe diveIngredientTreeFindRecipeCollection(
                                                                   @NotNull List<AbstractMapIngredient> ingredients,
                                                                   @NotNull Branch branchMap,
                                                                   @NotNull Predicate<GTRecipe> canHandle,
                                                                   Set<GTRecipe> recipeSet,
                                                                   @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> normalCache,
                                                                   @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> specialCache) {
        if (ingredients.isEmpty()) return null;
        for (var ingredient : ingredients) {
            var result = lookup(ingredient, branchMap, normalCache, specialCache);
            if (result != null) {
                GTRecipe r = result.map((potentialRecipe) -> canHandle.test(potentialRecipe) ? potentialRecipe : null,
                        (potentialBranch) -> diveIngredientTreeFindRecipeCollection(ingredients, potentialBranch, canHandle, recipeSet,
                                normalCache, specialCache));
                if (r != null) recipeSet.add(r);
            }
        }
        return null;
    }

    static @NotNull Map<AbstractMapIngredient, Either<GTRecipe, Branch>> determineRootNodes(@NotNull AbstractMapIngredient ingredient, @NotNull Branch branchMap) {
        return ingredient.isSpecialIngredient() ? branchMap.getSpecialNodes() : branchMap.getNodes();
    }

    private static @Nullable Either<GTRecipe, Branch> lookup(
                                                             @NotNull AbstractMapIngredient ingredient,
                                                             @NotNull Branch branchMap,
                                                             @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> normalCache,
                                                             @NotNull Map<Branch, Map<AbstractMapIngredient, Either<GTRecipe, Branch>>> specialCache) {
        var cache = ingredient.isSpecialIngredient() ? specialCache : normalCache;
        var branchCache = cache.computeIfAbsent(branchMap, ignored -> new HashMap<>());
        if (!branchCache.containsKey(ingredient)) {
            branchCache.put(ingredient, determineRootNodes(ingredient, branchMap).get(ingredient));
        }
        return branchCache.get(ingredient);
    }
}
