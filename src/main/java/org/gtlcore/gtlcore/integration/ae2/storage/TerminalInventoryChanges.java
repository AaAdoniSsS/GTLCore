package org.gtlcore.gtlcore.integration.ae2.storage;

import org.gtlcore.gtlcore.mixin.ae2.storage.TerminalCounterAccessor;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Menu-local, dense snapshot of the last inventory read. Always polls the current inventory;
 * only the previous snapshot is retained, never a cached view of a live storage network.
 */
public final class TerminalInventoryChanges {

    private final Object2IntOpenHashMap<AEKey> positions = new Object2IntOpenHashMap<>();
    private AEKey[] keys = new AEKey[16];
    private long[] amounts = new long[16];
    private boolean[] seen = new boolean[16];
    private int size;
    private boolean generation;
    private KeyCounter previousCounter;

    public TerminalInventoryChanges() {
        positions.defaultReturnValue(-1);
    }

    public Set<AEKey> between(KeyCounter previous, KeyCounter current) {
        // A replaced baseline (or an interrupted comparison) must be loaded from AE2 again.
        boolean reload = previousCounter != previous;
        previousCounter = null;
        if (reload) {
            positions.clear();
            Arrays.fill(keys, 0, size, null);
            size = 0;
            read(previous, null);
        }
        generation = !generation;
        Set<AEKey> changes = new HashSet<>();
        read(current, changes);
        // Dense arrays make removals one sequential pass, without reopening old variant maps.
        for (int i = size - 1; i >= 0; i--) {
            if (seen[i] == generation) continue;
            changes.add(keys[i]);
            positions.removeInt(keys[i]);
            int last = --size;
            if (i != last) {
                keys[i] = keys[last];
                amounts[i] = amounts[last];
                seen[i] = seen[last];
                positions.put(keys[i], i);
            }
            keys[last] = null;
        }
        previousCounter = current;
        return changes;
    }

    private void read(KeyCounter counter, Set<AEKey> changes) {
        TerminalDisplayRead.materialize(counter);
        var groups = ((TerminalCounterAccessor) (Object) counter).gtlcore$terminalGroups();
        int expectedIndex = 0;
        for (var group : groups.values()) {
            for (var entry : group) {
                long amount = entry.getLongValue();
                // An absent key and a native zero are equivalent for inventory notifications.
                if (amount == 0) continue;
                AEKey key = entry.getKey();
                // Stable inventory iteration usually follows the dense snapshot order. Verify
                // the complete key (including its variant) before bypassing the hash lookup.
                // After an insertion/reordering, a lookup also resynchronizes the next prediction.
                int index = expectedIndex < size && key.equals(keys[expectedIndex]) ? expectedIndex : positions.getInt(key);
                if (index < 0) {
                    if (size == keys.length) {
                        int capacity = keys.length + (keys.length >> 1);
                        keys = Arrays.copyOf(keys, capacity);
                        amounts = Arrays.copyOf(amounts, capacity);
                        seen = Arrays.copyOf(seen, capacity);
                    }
                    index = size++;
                    keys[index] = key;
                    positions.put(key, index);
                    if (changes != null) changes.add(key);
                } else if (amounts[index] != amount && changes != null) {
                    changes.add(key);
                }
                amounts[index] = amount;
                seen[index] = generation;
                expectedIndex = index + 1;
            }
        }
    }
}
