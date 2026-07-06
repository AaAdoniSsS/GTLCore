package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

final class MEPatternBufferJadeMerger {

    private MEPatternBufferJadeMerger() {}

    static void mergeItemKeys(Object2LongOpenHashMap<Item> target, Object2LongMap<AEItemKey> source) {
        for (var entry : Object2LongMaps.fastIterable(source)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                target.addTo(entry.getKey().getItem(), amount);
            }
        }
    }

    static void mergeFluidKeys(Object2LongOpenHashMap<Fluid> target, Object2LongMap<AEFluidKey> source) {
        for (var entry : Object2LongMaps.fastIterable(source)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                target.addTo(entry.getKey().getFluid(), amount);
            }
        }
    }

    static void mergeItemStacks(Object2LongOpenHashMap<Item> target, Iterable<?> source) {
        for (Object value : source) {
            if (value instanceof ItemStack stack && !stack.isEmpty()) {
                target.addTo(stack.getItem(), stack.getCount());
            }
        }
    }

    static void mergeFluidStacks(Object2LongOpenHashMap<Fluid> target, Iterable<?> source) {
        for (Object value : source) {
            if (value instanceof com.lowdragmc.lowdraglib.side.fluid.FluidStack stack && !stack.isEmpty()) {
                target.addTo(stack.getFluid(), stack.getAmount());
            }
        }
    }
}
