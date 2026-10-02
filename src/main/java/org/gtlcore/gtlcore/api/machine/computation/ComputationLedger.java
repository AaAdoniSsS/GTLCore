package org.gtlcore.gtlcore.api.machine.computation;

/** One budget per world tick, independent of the order in which block entities tick. */
public final class ComputationLedger {

    private long epoch = Long.MIN_VALUE;
    private long used;
    private long paidEnergy;
    private long previousUsed;

    public void advance(long tick) {
        if (epoch == tick) return;
        previousUsed = epoch != Long.MIN_VALUE && tick == epoch + 1 ? used : 0;
        epoch = tick;
        used = 0;
        paidEnergy = 0;
    }

    public long remaining(long capacity) {
        return capacity <= used ? 0 : capacity - used;
    }

    public long used() {
        return used;
    }

    public long previousUsed() {
        return previousUsed;
    }

    public long paidEnergy() {
        return paidEnergy;
    }

    public void debit(long amount, long energy) {
        if (amount < 0 || energy < 0) throw new IllegalArgumentException("Negative computation debit");
        long nextUsed = Math.addExact(used, amount);
        long nextEnergy = Math.addExact(paidEnergy, energy);
        used = nextUsed;
        paidEnergy = nextEnergy;
    }

    public void refund(long amount, long energy) {
        if (amount < 0 || amount > used || energy < 0 || energy > paidEnergy)
            throw new IllegalArgumentException("Invalid computation refund");
        used -= amount;
        paidEnergy -= energy;
    }

    public void clear() {
        epoch = Long.MIN_VALUE;
        used = 0;
        paidEnergy = 0;
        previousUsed = 0;
    }
}
