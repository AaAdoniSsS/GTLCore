package org.gtlcore.gtlcore.integration.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Arrays;
import java.util.Objects;

public final class AE2CraftingRequestMergeKey {

    private final AEKey what;
    private final long amount;
    private final Class<?> inputClass;
    private final GenericStack[] possibleInputs;
    private final AEKey[] remainingKeys;
    private final int hash;

    public AE2CraftingRequestMergeKey(AEKey what, long amount, IPatternDetails.IInput input) {
        this.what = what;
        this.amount = amount;

        if (input == null) {
            this.inputClass = null;
            this.possibleInputs = new GenericStack[0];
            this.remainingKeys = new AEKey[0];
        } else {
            this.inputClass = input.getClass();
            this.possibleInputs = input.getPossibleInputs().clone();
            this.remainingKeys = new AEKey[this.possibleInputs.length];
            for (int i = 0; i < this.possibleInputs.length; i++) {
                this.remainingKeys[i] = input.getRemainingKey(this.possibleInputs[i].what());
            }
        }

        this.hash = Objects.hash(
                this.what,
                this.amount,
                this.inputClass,
                Arrays.hashCode(this.possibleInputs),
                Arrays.hashCode(this.remainingKeys));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AE2CraftingRequestMergeKey other)) return false;
        return this.amount == other.amount &&
                Objects.equals(this.what, other.what) &&
                Objects.equals(this.inputClass, other.inputClass) &&
                Arrays.equals(this.possibleInputs, other.possibleInputs) &&
                Arrays.equals(this.remainingKeys, other.remainingKeys);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }
}
