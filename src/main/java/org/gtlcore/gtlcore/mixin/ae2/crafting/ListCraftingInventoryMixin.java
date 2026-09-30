package org.gtlcore.gtlcore.mixin.ae2.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * KeyCounter is an open-addressed fastutil map: removals leave tombstones that are only cleared
 * on rehash, so lookup degrades towards O(n) after heavy insert/remove churn. Auto-expanded
 * batches amplify that churn enormously (extract/reinject cycles per dispatch attempt), which
 * showed up as multi-minute ticks stalled in Object2LongOpenHashMap.getOrDefault. Rebuild the
 * map periodically to shed tombstones.
 */
@Mixin(value = ListCraftingInventory.class, remap = false)
public abstract class ListCraftingInventoryMixin {

    @Shadow
    @Final
    @Mutable
    public KeyCounter list;

    @Unique
    private int gtlcore$removes;

    @WrapOperation(method = "extract", at = @At(value = "INVOKE", target = "Lappeng/api/stacks/KeyCounter;remove(Lappeng/api/stacks/AEKey;J)V"))
    private void gtlcore$defragOnRemoveAmount(KeyCounter instance, AEKey what, long amount, Operation<Void> original) {
        original.call(instance, what, amount);
        gtlcore$defrag();
    }

    @WrapOperation(method = "extract", at = @At(value = "INVOKE", target = "Lappeng/api/stacks/KeyCounter;remove(Lappeng/api/stacks/AEKey;)J"))
    private long gtlcore$defragOnRemove(KeyCounter instance, AEKey what, Operation<Long> original) {
        long removed = original.call(instance, what);
        gtlcore$defrag();
        return removed;
    }

    @Unique
    private void gtlcore$defrag() {
        if (++gtlcore$removes >= 8192) {
            gtlcore$removes = 0;
            KeyCounter fresh = new KeyCounter();
            for (var entry : list) {
                fresh.add(entry.getKey(), entry.getLongValue());
            }
            list = fresh;
        }
    }
}
