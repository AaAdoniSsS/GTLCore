package org.gtlcore.gtlcore.utils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public final class FluidBucketUtil {

    private FluidBucketUtil() {}

    public static ItemStack getFilledBucket(FluidStack fluid) {
        ItemStack bucket = FluidUtil.getFilledBucket(fluid);
        if (!bucket.isEmpty()) {
            return bucket;
        }

        Item registeredBucket = fluid.getFluid().getBucket();
        return registeredBucket == Items.AIR ? ItemStack.EMPTY : registeredBucket.getDefaultInstance();
    }
}
