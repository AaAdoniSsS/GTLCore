package org.gtlcore.gtlcore.integration.ae2.common;

public final class CraftAmountConfirmPayload {

    private long amount;
    private boolean craftMissingAmount;
    private boolean startImmediately;

    public CraftAmountConfirmPayload() {}

    public CraftAmountConfirmPayload(long amount, boolean craftMissingAmount, boolean startImmediately) {
        this.amount = amount;
        this.craftMissingAmount = craftMissingAmount;
        this.startImmediately = startImmediately;
    }

    public long amount() {
        return amount;
    }

    public boolean craftMissingAmount() {
        return craftMissingAmount;
    }

    public boolean startImmediately() {
        return startImmediately;
    }
}
