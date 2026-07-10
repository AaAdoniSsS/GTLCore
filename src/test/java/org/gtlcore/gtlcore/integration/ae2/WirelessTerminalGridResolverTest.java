package org.gtlcore.gtlcore.integration.ae2;

public final class WirelessTerminalGridResolverTest {

    private WirelessTerminalGridResolverTest() {}

    public static void main(String[] args) {
        enforcesDepthAndSlotLimits();
    }

    private static void enforcesDepthAndSlotLimits() {
        var budget = new WirelessTerminalGridResolver.SearchBudget(2, 3);

        require(budget.canEnter(0));
        require(budget.canEnter(2));
        require(!budget.canEnter(3));
        require(budget.tryScanSlot());
        require(budget.tryScanSlot());
        require(budget.tryScanSlot());
        require(!budget.tryScanSlot());
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}
