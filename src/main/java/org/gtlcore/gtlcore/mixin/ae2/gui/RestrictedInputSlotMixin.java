package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellItem;

import net.minecraft.world.item.ItemStack;

import appeng.menu.slot.RestrictedInputSlot;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = RestrictedInputSlot.class, remap = false)
public abstract class RestrictedInputSlotMixin {

    @ModifyExpressionValue(
                           method = "mayPlace",
                           remap = true,
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lappeng/core/definitions/ItemDefinition;isSameAs(Lnet/minecraft/world/item/ItemStack;)Z",
                                    remap = false),
                           slice = @Slice(
                                          from = @At(
                                                     value = "FIELD",
                                                     target = "Lappeng/core/definitions/AEItems;VIEW_CELL:Lappeng/core/definitions/ItemDefinition;",
                                                     remap = false),
                                          to = @At(
                                                   value = "FIELD",
                                                   target = "Lappeng/core/definitions/AEItems;WIRELESS_BOOSTER:Lappeng/core/definitions/ItemDefinition;",
                                                   remap = false)))
    private boolean gtlcore$acceptTagViewCell(boolean original, ItemStack stack) {
        return original || TagViewCellItem.isTagViewCell(stack);
    }
}
