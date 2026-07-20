package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraftforge.fluids.FluidType;

public final class MEFluidUnits {

    private static final String MILLIBUCKET_SUFFIX = "mB";
    private static final String BUCKET_SUFFIX = "B";

    private MEFluidUnits() {}

    public static String formatDisplayAmount(long amountMb) {
        if (amountMb < FluidType.BUCKET_VOLUME) {
            return NumberUtils.formatLong(amountMb) + MILLIBUCKET_SUFFIX;
        }
        return NumberUtils.formatDouble((double) amountMb / FluidType.BUCKET_VOLUME) + BUCKET_SUFFIX;
    }
}
