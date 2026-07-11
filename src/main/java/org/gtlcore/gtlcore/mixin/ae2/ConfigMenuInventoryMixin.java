package org.gtlcore.gtlcore.mixin.ae2;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigMenuInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfigMenuInventory.class)
public abstract class ConfigMenuInventoryMixin {

    @Inject(method = "convertToSuitableStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$preserveWrappedItemStackAmount(ItemStack stack, CallbackInfoReturnable<GenericStack> cir) {
        GenericStack genericStack = GenericStack.unwrapItemStack(stack);
        if (genericStack == null || !(genericStack.what() instanceof AEItemKey)) {
            return;
        }
        ConfigMenuInventory inventory = (ConfigMenuInventory) (Object) this;
        cir.setReturnValue(inventory.getDelegate().isAllowed(genericStack.what()) ? genericStack : null);
    }
}
