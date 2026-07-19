package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.tterrag.registrate.util.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.gregtechceu.gtceu.common.data.GTItems.SHAPE_EXTRUDERS;
import static com.gregtechceu.gtceu.common.data.GTItems.SHAPE_MOLDS;

/**
 * Decides what a pattern transfer does with inputs that are not really consumed.
 * <p>
 * A plain transfer drops moulds and extruder shapes, because encoding one per pattern only forces the player to stock
 * copies of something they already own. Holding control asks for every non-consumed input to be encoded as a virtual
 * ingredient instead, which a supply machine can then serve to all of them at once.
 * <p>
 * Eligibility is taken from the recipe rather than guessed from the item: a virtual ingredient only ever satisfies an
 * input the recipe declares non-consumed, so wrapping anything else would produce a pattern that matches and then
 * never runs.
 */
public final class VirtualIngredientEncoding {

    private VirtualIngredientEncoding() {}

    /**
     * Research sticks are dropped whatever the player holds down. They carry the recipe's identity rather than being
     * an ingredient, so a virtual stand-in for one is meaningless.
     */
    public static boolean isAlwaysDropped(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().contains("assembly_line_research");
    }

    /**
     * Dropped by a plain transfer, but eligible to be encoded virtually instead.
     */
    public static boolean isDroppedUnlessVirtual(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return Arrays.stream(SHAPE_MOLDS).map(RegistryEntry::get).anyMatch(i -> i.equals(item)) ||
                Arrays.stream(SHAPE_EXTRUDERS).filter(Objects::nonNull).map(RegistryEntry::get)
                        .anyMatch(i -> i.equals(item));
    }

    /**
     * Every key the recipe declares non-consumed, across items and fluids.
     *
     * @return an empty set when the recipe is not a GT recipe, which leaves the transfer untouched
     */
    public static Set<AEKey> notConsumedKeys(@Nullable Object recipeBase) {
        GTRecipe recipe = asGTRecipe(recipeBase);
        if (recipe == null) return Set.of();

        Set<AEKey> keys = new HashSet<>();
        for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            if (content.chance > 0) continue;
            for (ItemStack item : ((Ingredient) content.getContent()).getItems()) {
                if (!item.isEmpty()) keys.add(AEItemKey.of(item));
            }
        }
        for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            if (content.chance > 0) continue;
            for (FluidStack fluid : ((FluidIngredient) content.getContent()).getStacks()) {
                if (!fluid.isEmpty()) keys.add(AEFluidKey.of(fluid.getFluid()));
            }
        }
        return keys;
    }

    /**
     * @return the same stack wrapped into a virtual ingredient, or null when the key is not one of {@code notConsumed}
     */
    @Nullable
    public static GenericStack wrapIfNotConsumed(GenericStack stack, Set<AEKey> notConsumed) {
        if (stack == null || !notConsumed.contains(stack.what())) return null;
        if (stack.what() instanceof AEItemKey itemKey) {
            return GenericStack.fromItemStack(VirtualIngredientBehavior.wrap(itemKey.toStack(1)));
        }
        if (stack.what() instanceof AEFluidKey fluidKey) {
            return GenericStack.fromItemStack(
                    VirtualIngredientBehavior.wrap(FluidStack.create(fluidKey.getFluid(), 1)));
        }
        return null;
    }

    @Nullable
    private static GTRecipe asGTRecipe(@Nullable Object recipeBase) {
        if (recipeBase instanceof GTRecipe recipe) return recipe;
        if (recipeBase instanceof GTRecipeWrapper wrapper) return wrapper.recipe;
        return null;
    }
}
