package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.api.machine.trait.LongStorageAdapterRegistry;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ItemHandlerFacade", remap = false)
public abstract class ItemHandlerFacadeMixin extends ExternalStorageFacade {

    @Shadow
    @Final
    private IItemHandler handler;

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void gtlcore$publishLongStorage(KeyCounter counter, CallbackInfo ci) {
        LongStorageAdapterRegistry.LongItemStorage storage = LongStorageAdapterRegistry.findItemStorage(handler);
        if (storage == null) return;

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (extractableOnly && handler.extractItem(slot, 1, true).isEmpty() &&
                    handler.extractItem(slot, stack.getCount(), true).isEmpty())
                continue;

            AEItemKey key = AEItemKey.of(stack);
            long amount = storage.getAmount(slot);
            if (key != null && amount > 0L) {
                counter.add(key, amount);
            }
        }
        ci.cancel();
    }
}
