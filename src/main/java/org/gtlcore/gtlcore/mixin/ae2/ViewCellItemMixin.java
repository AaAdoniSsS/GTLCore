package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellFilter;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.AEKeyFilter;
import appeng.items.storage.ViewCellItem;
import appeng.util.prioritylist.IPartitionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(value = ViewCellItem.class, remap = false)
public abstract class ViewCellItemMixin {

    @Inject(method = "createFilter", at = @At("RETURN"), cancellable = true)
    private static void gtlcore$addTagViewCellFilters(AEKeyFilter keyFilter, Collection<ItemStack> viewCells,
                                                      CallbackInfoReturnable<IPartitionList> cir) {
        if (TagViewCellFilter.containsTagViewCell(viewCells)) {
            cir.setReturnValue(TagViewCellFilter.create(keyFilter, viewCells));
        }
    }
}
