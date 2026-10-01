package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.stacks.AEKey;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Menu-local projection of maintained sources through a verified key-only filter. */
final class TerminalFilteredView {

    private Object rules;
    private final Map<TerminalSourceIndex, Source> sources = new IdentityHashMap<>();

    void append(TerminalDenseCounter target, TerminalDenseCounter input, Object signature,
                Predicate<AEKey> accepts, long read) {
        if (!Objects.equals(rules, signature)) {
            sources.clear();
            rules = signature;
        }
        input.sources.forEach((source, count) -> {
            Source view = sources.computeIfAbsent(source, ignored -> new Source());
            view.seen = read;
            TerminalSourceIndex filtered = view.update(source, accepts);
            target.sources.merge(filtered, count, Math::addExact);
        });
        // Unknown storages are freshly polled and filtered, never trusted as journalled sources.
        for (int id = input.used.nextSetBit(0); id >= 0; id = input.used.nextSetBit(id + 1)) {
            AEKey key = input.index.key(id);
            if (accepts.test(key)) target.add(target.index == input.index ? id : target.index.id(key),
                    input.amount(id), input.exact.get(id), 1);
        }
    }

    boolean prune(long read) {
        sources.values().removeIf(source -> source.seen != read);
        return sources.isEmpty();
    }

    private static final class Source {

        private TerminalSourceIndex result = new TerminalSourceIndex();
        private final BitSet checked = new BitSet();
        private final BitSet accepted = new BitSet();
        private long epoch = -1;
        private long revision;
        private long seen;

        TerminalSourceIndex update(TerminalSourceIndex input, Predicate<AEKey> accepts) {
            boolean reset = epoch != input.epoch();
            if (reset) {
                // Source compaction can reassign slots. New output identity removes the old
                // contribution through the same topology handling as unplugging a cell.
                result = new TerminalSourceIndex();
                checked.clear();
                accepted.clear();
            }
            BitSet dirty = new BitSet();
            if (reset || !input.changesSince(revision, dirty::set)) {
                for (int slot = 0; slot < input.size(); slot++) updateSlot(input, slot, accepts);
            } else {
                for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
                    updateSlot(input, slot, accepts);
                }
            }
            epoch = input.epoch();
            revision = input.revision();
            return result;
        }

        private void updateSlot(TerminalSourceIndex input, int slot, Predicate<AEKey> accepts) {
            if (!checked.get(slot)) {
                accepted.set(slot, accepts.test(input.key(slot)));
                checked.set(slot);
            }
            if (accepted.get(slot)) result.set(input.key(slot), input.amount(slot), input.exact(slot));
        }
    }
}
