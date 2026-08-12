package org.gtlcore.gtlcore.mixin.ldlib;

import org.gtlcore.gtlcore.api.machine.trait.IDirectItemStackTransfer;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author EasterFG on 2025/4/8
 */
@Mixin(ItemStackTransfer.class)
public abstract class ItemStackTransferMixin implements IDirectItemStackTransfer {

    @Shadow(remap = false)
    protected NonNullList<ItemStack> stacks;

    @Override
    public void gtlcore$setStackWithoutNotify(int slot, ItemStack stack) {
        stacks.set(slot, stack);
    }

    @Inject(method = "insertItem", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/misc/ItemStackTransfer;validateSlotIndex(I)V"), remap = false, cancellable = true)
    public void insertItemHook(int slot, ItemStack stack, boolean simulate, boolean notifyChanges, CallbackInfoReturnable<ItemStack> cir) {
        if (stacks.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "extractItem", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/misc/ItemStackTransfer;validateSlotIndex(I)V"), remap = false, cancellable = true)
    public void extractItemHook(int slot, int amount, boolean simulate, boolean notifyChanges, CallbackInfoReturnable<ItemStack> cir) {
        if (stacks.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "getStackInSlot", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/misc/ItemStackTransfer;validateSlotIndex(I)V"), remap = false, cancellable = true)
    public void getStackInSlotHook(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (stacks.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "setStackInSlot", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/misc/ItemStackTransfer;validateSlotIndex(I)V"), remap = false, cancellable = true)
    public void setStackInSlotHook(int slot, ItemStack stack, CallbackInfo ci) {
        if (stacks.isEmpty()) {
            ci.cancel();
        }
    }
}
