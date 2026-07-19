package org.gtlcore.gtlcore.integration.ae2.crafting;

import org.gtlcore.gtlcore.config.AE2CalculationMode;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Objects;

public final class CraftingTemplateHelper {

    private CraftingTemplateHelper() {}

    public static GenericStack[] snapshotPossibleInputs(IPatternDetails.IInput input) {
        return snapshotPossibleInputs(input.getPossibleInputs());
    }

    public static GenericStack[] snapshotPossibleInputs(GenericStack[] possibleInputs) {
        GenericStack[] snapshot = new GenericStack[possibleInputs.length];
        for (int i = 0; i < possibleInputs.length; i++) {
            GenericStack stack = possibleInputs[i];
            snapshot[i] = new GenericStack(stack.what(), stack.amount());
        }
        return snapshot;
    }

    public static boolean samePossibleInputs(GenericStack[] first, GenericStack[] second) {
        if (first.length != second.length) {
            return false;
        }

        for (int i = 0; i < first.length; i++) {
            GenericStack firstStack = first[i];
            GenericStack secondStack = second[i];
            if (firstStack.amount() != secondStack.amount() ||
                    !Objects.equals(firstStack.what(), secondStack.what()) ||
                    firstStack.what().getPrimaryKey() != secondStack.what().getPrimaryKey()) {
                return false;
            }
        }
        return true;
    }

    public static List<InputTemplate> loadRawCandidates(ICraftingInventory inv, GenericStack[] possibleInputs) {
        List<InputTemplate> substitutes = new ObjectArrayList<>(possibleInputs.length);
        for (GenericStack stack : possibleInputs) {
            for (AEKey fuzz : inv.findFuzzyTemplates(stack.what())) {
                substitutes.add(new InputTemplate(fuzz, stack.amount()));
            }
        }
        return substitutes;
    }

    public static List<InputTemplate> filterValidCandidates(InputTemplate[] rawCandidates,
                                                            IPatternDetails.IInput input, Level level) {
        List<InputTemplate> validTemplates = new ObjectArrayList<>(rawCandidates.length);
        for (InputTemplate candidate : rawCandidates) {
            if (input.isValid(candidate.key(), level)) {
                validTemplates.add(candidate);
            }
        }
        return List.copyOf(validTemplates);
    }

    public static List<InputTemplate> revalidateCandidates(InputTemplate[] rawCandidates,
                                                           List<InputTemplate> previousValidTemplates,
                                                           IPatternDetails.IInput input, Level level) {
        if (rawCandidates.length == 0) {
            return previousValidTemplates.isEmpty() ? previousValidTemplates : List.of();
        }
        if (rawCandidates.length == 1) {
            InputTemplate candidate = rawCandidates[0];
            if (!input.isValid(candidate.key(), level)) {
                return previousValidTemplates.isEmpty() ? previousValidTemplates : List.of();
            }
            if (previousValidTemplates.size() == 1 && previousValidTemplates.get(0) == candidate) {
                return previousValidTemplates;
            }
            return List.of(candidate);
        }

        List<InputTemplate> validTemplates = filterValidCandidates(rawCandidates, input, level);
        if (validTemplates.size() != previousValidTemplates.size()) {
            return validTemplates;
        }
        for (int i = 0; i < validTemplates.size(); i++) {
            if (validTemplates.get(i) != previousValidTemplates.get(i)) {
                return validTemplates;
            }
        }
        return previousValidTemplates;
    }

    public static final class CalculationTemplateCacheKey {

        private final IPatternDetails.IInput input;
        private final ICraftingInventory inventory;
        private final Level level;
        private final AEKey what;
        private final AE2CalculationMode mode;
        private final int hash;

        public CalculationTemplateCacheKey(ICraftingInventory inventory, IPatternDetails.IInput input, Level level,
                                           AEKey what, AE2CalculationMode mode) {
            this.inventory = inventory;
            this.input = input;
            this.level = level;
            this.what = what;
            this.mode = mode;
            this.hash = Objects.hash(
                    System.identityHashCode(inventory),
                    System.identityHashCode(input),
                    System.identityHashCode(level),
                    what,
                    mode);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CalculationTemplateCacheKey other)) return false;
            return this.inventory == other.inventory && this.input == other.input && this.level == other.level &&
                    Objects.equals(this.what, other.what) &&
                    this.mode == other.mode;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    public static final class MaxFastTemplateEntry {

        private final GenericStack[] possibleInputsSnapshot;
        private final InputTemplate[] rawCandidates;
        private List<InputTemplate> validTemplates;
        private final Object[] primaryGroups;
        private final long[] membershipEpochs;
        private long validationEpoch;

        public MaxFastTemplateEntry(GenericStack[] possibleInputsSnapshot, InputTemplate[] rawCandidates,
                                    List<InputTemplate> validTemplates, Object[] primaryGroups,
                                    long[] membershipEpochs, long validationEpoch) {
            this.possibleInputsSnapshot = possibleInputsSnapshot;
            this.rawCandidates = rawCandidates;
            this.validTemplates = validTemplates;
            this.primaryGroups = primaryGroups;
            this.membershipEpochs = membershipEpochs;
            this.validationEpoch = validationEpoch;
        }

        public GenericStack[] possibleInputsSnapshot() {
            return this.possibleInputsSnapshot;
        }

        public InputTemplate[] rawCandidates() {
            return this.rawCandidates;
        }

        public List<InputTemplate> validTemplates() {
            return this.validTemplates;
        }

        public Object[] primaryGroups() {
            return this.primaryGroups;
        }

        public long[] membershipEpochs() {
            return this.membershipEpochs;
        }

        public long validationEpoch() {
            return this.validationEpoch;
        }

        public void updateValidation(List<InputTemplate> validTemplates, long validationEpoch) {
            this.validTemplates = validTemplates;
            this.validationEpoch = validationEpoch;
        }
    }
}
