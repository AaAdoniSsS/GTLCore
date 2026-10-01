package org.gtlcore.gtlcore.integration.ae2.storage;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKey;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.PrecisePriorityList;

import java.util.HashSet;
import java.util.Set;

/** Only known native key-only rules are cacheable. Unknown rules are evaluated on every read. */
public final class TerminalFilterRules {

    private TerminalFilterRules() {}

    private static final ClassValue<Boolean> NATIVE_FILTER = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    current.getDeclaredMethod("canExtract", AEKey.class);
                    return current == MEInventoryHandler.class;
                } catch (NoSuchMethodException ignored) {
                    // Inherited native predicate is safe; overriding predicates may be dynamic.
                }
            }
            return false;
        }
    };

    private record Rules(IPartitionList identity, IncludeExclude mode, boolean empty, Set<AEKey> keys) {}

    public static Object capture(Object handler, IPartitionList list, IncludeExclude mode) {
        if (!NATIVE_FILTER.get(handler.getClass())) return null;
        Class<?> type = list.getClass();
        if (type != DefaultPriorityList.class && type != PrecisePriorityList.class && type != FuzzyPriorityList.class) return null;
        Set<AEKey> keys = new HashSet<>();
        for (AEKey key : list.getItems()) {
            // Precise lists retain zero entries but match only positive amounts. Fuzzy lists
            // match key presence; their mode is final and covered by the list identity.
            if (type != PrecisePriorityList.class || list.isListed(key)) keys.add(key);
        }
        return new Rules(list, mode, list.isEmpty(), Set.copyOf(keys));
    }
}
