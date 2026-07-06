package org.gtlcore.gtlcore.integration.ae2.common;

public interface ILongCraftAmountMenu {

    String CONFIRM_LONG_AMOUNT_ACTION = "gtlcoreConfirmLongCraftAmount";

    void gtlcore$confirmLongAmount(long amount, boolean craftMissingAmount, boolean startImmediately);
}
