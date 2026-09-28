package org.gtlcore.gtlcore.mixin.gtm.cover;

import org.gtlcore.gtlcore.utils.TagExprFilter;

import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagItemFilter;
import com.gregtechceu.gtceu.utils.OreDictExprFilter;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(TagItemFilter.class)
public abstract class TagItemFilterMixin extends TagFilter<ItemStack, ItemFilter> {

    @Unique
    private final TagExprFilter.CachedExpression gtlcore$tagExpression = new TagExprFilter.CachedExpression();

    @Redirect(method = "test(Lnet/minecraft/world/item/ItemStack;)Z",
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/utils/OreDictExprFilter;matchesOreDict(Ljava/util/List;Lnet/minecraft/world/item/ItemStack;)Z"),
              remap = false)
    private boolean gtlcore$matchTags(List<OreDictExprFilter.MatchRule> rules, ItemStack stack) {
        var tags = stack.getTags().map(TagKey::location).map(Object::toString).collect(Collectors.toSet());
        return gtlcore$tagExpression.matches(oreDictFilterExpression, tags);
    }
}
