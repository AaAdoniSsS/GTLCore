package org.gtlcore.gtlcore.api.machine.trait;

/**
 * Exposes each slot's actual long-backed quantity when the Forge item-handler API can only expose an int-sized stack.
 */
public interface ILongItemStorage {

    long gtlcore$getStoredAmount(int slot);
}
