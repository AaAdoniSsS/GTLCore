package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

/** Recipe-generated CWU becomes available on the next tick, independently of machine ticking order. */
public final class ComputationBuffer implements ComputationSource {

    private final MetaMachine machine;
    private long epoch = Long.MIN_VALUE;
    private long generated;
    private long capacity;
    private long used;

    public ComputationBuffer(MetaMachine machine) {
        this.machine = machine;
    }

    private void advance() {
        long tick = ComputationNetwork.tick(machine.getLevel());
        if (epoch == tick) return;
        capacity = epoch != Long.MIN_VALUE && tick == epoch + 1 ? generated : 0;
        generated = 0;
        used = 0;
        epoch = tick;
    }

    public boolean produce(long amount, boolean simulate) {
        advance();
        if (amount < 0 || amount > Long.MAX_VALUE - generated || !ComputationConnections.loaded(machine)) return false;
        if (!simulate) {
            generated += amount;
            ComputationRecipes.undoOnFailure(() -> generated -= amount);
        }
        return true;
    }

    @Override
    public long gtlcore$computationCapacity() {
        advance();
        return ComputationConnections.loaded(machine) ? capacity : 0;
    }

    @Override
    public long gtlcore$availableComputation() {
        return Math.max(0, gtlcore$computationCapacity() - used);
    }

    @Override
    public boolean gtlcore$canBridgeComputation() {
        return true;
    }

    @Override
    public Receipt gtlcore$withdrawComputation(long amount) {
        if (amount <= 0 || amount > gtlcore$availableComputation()) return null;
        used += amount;
        return new Receipt(amount, () -> used -= amount);
    }
}
