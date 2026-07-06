package org.gtlcore.gtlcore.integration.ae2.common;

public final class CraftAmountLimits {

    public static final long MAX_MANUAL_CRAFT_AMOUNT = Long.MAX_VALUE;
    public static final int MAX_MANUAL_CRAFT_AMOUNT_DIGITS = Long.toString(MAX_MANUAL_CRAFT_AMOUNT).length();

    private CraftAmountLimits() {}

    public static long missingAmount(long requestedAmount, long storedAmount) {
        if (storedAmount <= 0) return requestedAmount;
        if (storedAmount >= requestedAmount) return 0;
        return requestedAmount - storedAmount;
    }
}
