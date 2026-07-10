package org.gtlcore.gtlcore.integration.ae2.crafting;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CraftingDispatchReasonState {

    private CraftingDispatchReasonState() {}

    public static <K> Set<K> changedKeys(Map<K, Integer> previous, Map<K, Integer> current) {
        Set<K> changed = new HashSet<>(previous.keySet());
        changed.addAll(current.keySet());
        changed.removeIf(key -> Objects.equals(previous.get(key), current.get(key)));
        return changed;
    }

    public static void applySerialUpdate(
                                         Map<Long, Integer> target,
                                         boolean fullStatus,
                                         Map<Long, Integer> update) {
        if (fullStatus) {
            target.clear();
        }
        for (var entry : update.entrySet()) {
            if (entry.getValue() == 0) {
                target.remove(entry.getKey());
            } else {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
