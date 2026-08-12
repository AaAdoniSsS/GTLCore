package org.gtlcore.gtlcore.api.machine.trait;

/** Exposes each tank's actual long-backed amount beyond Forge's int-sized fluid stack. */
public interface ILongFluidStorage {

    long gtlcore$getStoredAmount(int tank);
}
