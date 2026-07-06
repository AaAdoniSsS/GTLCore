package org.gtlcore.gtlcore.mixin.ae2.pattern;

import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.crafting.pattern.EncodedPatternItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(EncodedPatternItem.class)
public abstract class EncodedPatternItemMixin {

    @Unique
    private static final String GTLCORE$RECIPE_TYPE_SEPARATOR = ", ";

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void gtlcore$appendQuickUploadRecipeTypes(ItemStack stack, Level level, List<Component> tooltip,
                                                      TooltipFlag flag, CallbackInfo ci) {
        Set<ResourceLocation> recipeTypeIds = PatternQuickUploadMetadata.readRecipeTypeIds(stack);
        if (recipeTypeIds.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.pattern_quick_upload_recipe_types",
                gtlcore$formatRecipeTypes(recipeTypeIds)).withStyle(ChatFormatting.GRAY));
    }

    @Unique
    private static Component gtlcore$formatRecipeTypes(Set<ResourceLocation> recipeTypeIds) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (ResourceLocation recipeTypeId : recipeTypeIds) {
            if (!first) {
                result.append(GTLCORE$RECIPE_TYPE_SEPARATOR);
            }
            result.append(PatternQuickUploadMetadata.recipeTypeName(recipeTypeId)
                    .copy()
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            first = false;
        }
        return result;
    }
}
