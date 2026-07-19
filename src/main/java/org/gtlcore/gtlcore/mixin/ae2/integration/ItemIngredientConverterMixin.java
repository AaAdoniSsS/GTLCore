package org.gtlcore.gtlcore.mixin.ae2.integration;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.ItemIngredientConverter;
import com.tterrag.registrate.util.entry.RegistryEntry;
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
            var item = itemStack.getItem();
            boolean b1 = Arrays.stream(SHAPE_MOLDS).map(RegistryEntry::get).anyMatch((i) -> i.equals(item));
            boolean b2 = Arrays.stream(SHAPE_EXTRUDERS).filter(Objects::nonNull).map(RegistryEntry::get).anyMatch((i) -> i.equals(item));
            boolean b3 = itemStack.getTag() != null && itemStack.getTag().contains("assembly_line_research");
            if (b1 || b2 || b3) return null;
        }
        return GenericStack.fromItemStack(itemStack);
    }
}
