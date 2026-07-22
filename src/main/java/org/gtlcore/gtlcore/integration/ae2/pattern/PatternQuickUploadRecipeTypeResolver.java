package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.CraftingPatternItem;
import appeng.crafting.pattern.SmithingTablePatternItem;
import appeng.crafting.pattern.StonecuttingPatternItem;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PatternQuickUploadRecipeTypeResolver {

    private static final String LOG_PREFIX = "[PatternQuickUpload]";

    private PatternQuickUploadRecipeTypeResolver() {}

    public static Set<ResourceLocation> findRecipeTypeIds(ServerPlayer player, ItemStack patternStack) {
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(patternStack, player.level());
        ResourceLocation molecularRecipeTypeId = getMolecularRecipeTypeId(patternStack, pattern);
        if (molecularRecipeTypeId != null) {
            return Set.of(molecularRecipeTypeId);
        }
        if (!(pattern instanceof AEProcessingPattern processingPattern)) {
            GTLCore.LOGGER.debug("{} resolver rejected unsupported pattern {}", LOG_PREFIX, patternStack.getHoverName().getString());
            return Set.of();
        }
        return findRecipeTypeIds(player.server.getRecipeManager(), processingPattern);
    }

    /**
     * Translates a vanilla recipe type id into the GT recipe types accepting it as a proxy. A GT recipe type
     * declaring a vanilla proxy (currently only {@code gtceu:electric_furnace} on {@code minecraft:smelting}) runs
     * those recipes through its own lookup, so a pattern carrying the vanilla id belongs on machines of the proxying
     * type. The vanilla id itself is only kept when it still names a target on its own, as it does for the molecular
     * assembler.
     */
    public static Set<ResourceLocation> expandProxiedRecipeTypeIds(ResourceLocation recipeTypeId) {
        if (recipeTypeId == null) {
            return Set.of();
        }
        Set<ResourceLocation> recipeTypeIds = new LinkedHashSet<>();
        for (GTRecipeType gtRecipeType : GTRegistries.RECIPE_TYPES.values()) {
            if (gtRecipeType.registryName == null) {
                continue;
            }
            for (RecipeType<?> proxiedType : gtRecipeType.getProxyRecipes().keySet()) {
                if (recipeTypeId.equals(recipeTypeId(proxiedType))) {
                    recipeTypeIds.add(gtRecipeType.registryName);
                }
            }
        }
        if (recipeTypeIds.isEmpty() || isMolecularRecipeTypeId(recipeTypeId)) {
            recipeTypeIds.add(recipeTypeId);
        }
        return recipeTypeIds;
    }

    public static boolean isMolecularRecipeTypeId(ResourceLocation recipeTypeId) {
        return recipeTypeId != null &&
                (recipeTypeId.equals(recipeTypeId(RecipeType.CRAFTING)) ||
                        recipeTypeId.equals(recipeTypeId(RecipeType.SMITHING)) ||
                        recipeTypeId.equals(recipeTypeId(RecipeType.STONECUTTING)));
    }

    private static ResourceLocation getMolecularRecipeTypeId(ItemStack patternStack, IPatternDetails pattern) {
        if (!(pattern instanceof IMolecularAssemblerSupportedPattern)) {
            return null;
        }
        if (patternStack.getItem() instanceof CraftingPatternItem) {
            return recipeTypeId(RecipeType.CRAFTING);
        }
        if (patternStack.getItem() instanceof SmithingTablePatternItem) {
            return recipeTypeId(RecipeType.SMITHING);
        }
        if (patternStack.getItem() instanceof StonecuttingPatternItem) {
            return recipeTypeId(RecipeType.STONECUTTING);
        }
        return null;
    }

    private static ResourceLocation recipeTypeId(RecipeType<?> recipeType) {
        return BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
    }

    static Set<ResourceLocation> findRecipeTypeIds(RecipeManager recipeManager, AEProcessingPattern pattern) {
        PatternSignature patternSignature = PatternSignature.fromPattern(pattern);
        if (patternSignature.isEmpty()) {
            GTLCore.LOGGER.debug("{} resolver rejected empty pattern signature", LOG_PREFIX);
            return Set.of();
        }

        Set<ResourceLocation> recipeTypeIds = new LinkedHashSet<>();
        int checkedRecipes = 0;
        int matchedRecipes = 0;
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!(recipe instanceof GTRecipe gtRecipe) || gtRecipe.recipeType == null ||
                    gtRecipe.recipeType == GTRecipeTypes.DUMMY_RECIPES ||
                    gtRecipe.recipeType.registryName == null) {
                continue;
            }
            checkedRecipes++;
            PatternSignature recipeSignature = PatternSignature.fromRecipe(gtRecipe);
            if (!recipeSignature.isEmpty() && patternSignature.matchesScaled(recipeSignature)) {
                matchedRecipes++;
                recipeTypeIds.add(gtRecipe.recipeType.registryName);
            }
        }
        // Proxied vanilla recipes are converted on datapack reload and only ever live in their recipe type,
        // never in the recipe manager, so the loop above cannot see them.
        for (GTRecipeType gtRecipeType : GTRegistries.RECIPE_TYPES.values()) {
            if (gtRecipeType.registryName == null || gtRecipeType == GTRecipeTypes.DUMMY_RECIPES) {
                continue;
            }
            for (List<GTRecipe> proxiedRecipes : gtRecipeType.getProxyRecipes().values()) {
                for (GTRecipe proxiedRecipe : proxiedRecipes) {
                    checkedRecipes++;
                    PatternSignature recipeSignature = PatternSignature.fromRecipe(proxiedRecipe);
                    if (!recipeSignature.isEmpty() && patternSignature.matchesScaled(recipeSignature)) {
                        matchedRecipes++;
                        recipeTypeIds.add(gtRecipeType.registryName);
                    }
                }
            }
        }
        GTLCore.LOGGER.debug("{} resolver checkedRecipes={} matchedRecipes={} recipeTypes={}",
                LOG_PREFIX,
                checkedRecipes,
                matchedRecipes,
                recipeTypeIds);
        if (recipeTypeIds.size() > 1) {
            GTLCore.LOGGER.debug("{} resolver rejected ambiguous GT recipe types {}", LOG_PREFIX, recipeTypeIds);
            return Set.of();
        }
        return recipeTypeIds;
    }

    private record PatternSignature(Object2LongOpenHashMap<AEItemKey> inputItems,
                                    Object2LongOpenHashMap<AEFluidKey> inputFluids,
                                    Object2LongOpenHashMap<AEItemKey> outputItems,
                                    Object2LongOpenHashMap<AEFluidKey> outputFluids) {

        private static PatternSignature empty() {
            return new PatternSignature(new Object2LongOpenHashMap<>(), new Object2LongOpenHashMap<>(),
                    new Object2LongOpenHashMap<>(), new Object2LongOpenHashMap<>());
        }

        private static PatternSignature fromPattern(AEProcessingPattern pattern) {
            PatternSignature signature = empty();
            for (GenericStack stack : pattern.getSparseInputs()) {
                signature.addInput(stack);
            }
            for (GenericStack stack : pattern.getSparseOutputs()) {
                signature.addOutput(stack);
            }
            return signature;
        }

        private static PatternSignature fromRecipe(GTRecipe recipe) {
            PatternSignature signature = empty();
            for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                signature.addItemContent(signature.inputItems, content);
            }
            for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                signature.addFluidContent(signature.inputFluids, content);
            }
            for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                signature.addItemContent(signature.outputItems, content);
            }
            for (Content content : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
                signature.addFluidContent(signature.outputFluids, content);
            }
            return signature;
        }

        private boolean isEmpty() {
            return inputItems.isEmpty() && inputFluids.isEmpty() && outputItems.isEmpty() && outputFluids.isEmpty();
        }

        private boolean matchesScaled(PatternSignature recipeSignature) {
            Scale scale = new Scale();
            return matchesPatternEntries(inputItems, recipeSignature.inputItems, scale) &&
                    matchesPatternEntries(inputFluids, recipeSignature.inputFluids, scale) &&
                    matchesPatternEntries(outputItems, recipeSignature.outputItems, scale) &&
                    matchesPatternEntries(outputFluids, recipeSignature.outputFluids, scale) &&
                    scale.isSet();
        }

        private void addInput(GenericStack stack) {
            addStack(inputItems, inputFluids, stack);
        }

        private void addOutput(GenericStack stack) {
            addStack(outputItems, outputFluids, stack);
        }

        private void addStack(Object2LongOpenHashMap<AEItemKey> items,
                              Object2LongOpenHashMap<AEFluidKey> fluids,
                              GenericStack stack) {
            if (stack == null || stack.amount() <= 0) {
                return;
            }
            if (stack.what() instanceof AEItemKey itemKey) {
                items.addTo(itemKey, stack.amount());
            } else if (stack.what() instanceof AEFluidKey fluidKey) {
                fluids.addTo(fluidKey, stack.amount());
            }
        }

        private void addItemContent(Object2LongOpenHashMap<AEItemKey> items, Content content) {
            if (content == null || content.getContent() == null) {
                return;
            }
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.getContent());
            ItemStack stack = firstItem(ingredient);
            long amount = itemAmount(ingredient, stack);
            if (!stack.isEmpty() && amount > 0) {
                items.addTo(AEItemKey.of(stack), amount);
            }
        }

        private void addFluidContent(Object2LongOpenHashMap<AEFluidKey> fluids, Content content) {
            if (content == null || content.getContent() == null) {
                return;
            }
            FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.getContent());
            FluidStack stack = firstFluid(ingredient);
            long amount = ingredient.getAmount();
            if (!stack.isEmpty() && amount > 0) {
                fluids.addTo(AEFluidKey.of(stack.getFluid()), amount);
            }
        }

        private static ItemStack firstItem(Ingredient ingredient) {
            if (ingredient == null || ingredient.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack[] items = ingredient.getItems();
            return items.length == 0 ? ItemStack.EMPTY : items[0];
        }

        private static long itemAmount(Ingredient ingredient, ItemStack stack) {
            if (ingredient instanceof LongIngredient longIngredient) {
                return longIngredient.getActualAmount();
            }
            if (ingredient instanceof SizedIngredient sizedIngredient) {
                return sizedIngredient.getAmount();
            }
            return stack.isEmpty() ? 0 : stack.getCount();
        }

        private static FluidStack firstFluid(FluidIngredient ingredient) {
            if (ingredient == null || ingredient.isEmpty()) {
                return FluidStack.empty();
            }
            FluidStack[] fluids = ingredient.getStacks();
            return fluids.length == 0 ? FluidStack.empty() : fluids[0];
        }

        private static <T> boolean matchesPatternEntries(Object2LongMap<T> pattern, Object2LongMap<T> recipe,
                                                         Scale scale) {
            if (pattern.size() > recipe.size()) {
                return false;
            }
            for (Object2LongMap.Entry<T> patternEntry : Object2LongMaps.fastIterable(pattern)) {
                T key = patternEntry.getKey();
                if (!pattern.containsKey(key) ||
                        !recipe.containsKey(key) ||
                        !scale.accept(patternEntry.getLongValue(), recipe.getLong(key))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Scale {

        private long value;

        private boolean accept(long patternAmount, long recipeAmount) {
            if (patternAmount <= 0 || recipeAmount <= 0 || patternAmount % recipeAmount != 0) {
                return false;
            }
            long candidate = patternAmount / recipeAmount;
            if (candidate <= 0) {
                return false;
            }
            if (value == 0) {
                value = candidate;
                return true;
            }
            return value == candidate;
        }

        private boolean isSet() {
            return value > 0;
        }
    }
}
