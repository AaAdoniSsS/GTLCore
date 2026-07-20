package org.gtlcore.gtlcore.mixin.ae2.integration;

import org.gtlcore.gtlcore.integration.ae2.pattern.VirtualIngredientEncoding;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.ItemIngredientConverter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.*;

import static com.gregtechceu.gtceu.common.data.GTItems.*;

@Mixin(ItemIngredientConverter.class)
public abstract class ItemIngredientConverterMixin {

    /**
     * @author .
     * @reason 填充样板跳过模头和模具
     */
    @Overwrite(remap = false)
    public @Nullable GenericStack getStackFromIngredient(ItemStack itemStack) {
        if (!itemStack.isEmpty()) {
            if (VirtualIngredientEncoding.isAlwaysDropped(itemStack)) return null;
            // Held control means the transfer wants these as virtual ingredients, so they have to survive this far;
            // the wrapping itself happens once the whole recipe is known, because only the recipe says which inputs
            // are actually non-consumed.
            if (VirtualIngredientEncoding.isDroppedUnlessVirtual(itemStack) && !Screen.hasControlDown()) {
                return null;
            }
        }
        return GenericStack.fromItemStack(itemStack);
    }
}
