package org.gtlcore.gtlcore.api.machine.computation;

/** A physical source. All routes to the same object spend the same ledger. */
public interface ComputationSource {

    long gtlcore$computationCapacity();

    long gtlcore$availableComputation();

    boolean gtlcore$canBridgeComputation();

    /** Reserves and pays an exact amount, or returns null without changing resources. */
    Receipt gtlcore$withdrawComputation(long amount);

    record Receipt(long amount, Runnable refund) {

        public Receipt {
            if (amount <= 0 || refund == null) throw new IllegalArgumentException("Invalid computation receipt");
        }
    }
}
