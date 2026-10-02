package org.gtlcore.gtlcore.api.machine.computation;

/** Long overloads added to GTM's Java and KubeJS builders, without removing their int ABI. */
public interface LongComputationRecipeBuilder<T> {

    T inputCWU(long amount);

    T outputCWU(long amount);

    T CWUt(long amount);

    T totalCWU(long amount);

    T inputCWU(String amount);

    T outputCWU(String amount);

    T CWUt(String amount);

    T totalCWU(String amount);
}
