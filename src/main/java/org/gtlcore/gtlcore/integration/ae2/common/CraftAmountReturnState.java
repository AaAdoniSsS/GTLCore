package org.gtlcore.gtlcore.integration.ae2.common;

public final class CraftAmountReturnState {

    public static final long NO_LONG_AMOUNT = 0L;

    private CraftAmountReturnState() {}

    public static long rememberedAmount(long amount) {
        return amount > NO_LONG_AMOUNT ? amount : NO_LONG_AMOUNT;
    }

    public static long displayAmount(long rememberedAmount, int legacyAmount) {
        return rememberedAmount > NO_LONG_AMOUNT ? rememberedAmount : legacyAmount;
    }

    public static int legacyAmount(long amount) {
        if (amount > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (amount < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) amount;
    }
}
