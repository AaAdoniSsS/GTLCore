package org.gtlcore.gtlcore.integration.ae2;

import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraftforge.fluids.FluidType;

public final class MEFluidUnits {

    private static final String MILLIBUCKET_SUFFIX = "mB";
    private static final String BUCKET_SUFFIX = "B";

    private MEFluidUnits() {}

    public static String formatDisplayAmount(long amountMb) {
        return formatDisplayAmount((double) amountMb);
    }

    public static String formatDisplayAmount(double amountMb) {
        boolean negative = amountMb < 0.0D;
        double absoluteAmount = Math.abs(amountMb);
        String formattedAmount;
        if (absoluteAmount < FluidType.BUCKET_VOLUME) {
            formattedAmount = NumberUtils.formatDouble(absoluteAmount) + MILLIBUCKET_SUFFIX;
        } else {
            formattedAmount = NumberUtils.formatDouble(absoluteAmount / FluidType.BUCKET_VOLUME) + BUCKET_SUFFIX;
        }
        return negative ? "-" + formattedAmount : formattedAmount;
    }
}
