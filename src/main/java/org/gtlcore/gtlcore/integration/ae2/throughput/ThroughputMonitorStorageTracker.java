package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ThroughputMonitorStorageTracker {

    private static final int MAX_VISIBLE_STORAGE_SCAN = 2048;
    private static final int MAX_PARENT_DISPATCH_DEPTH = 64;
    private static final Object LOCK = new Object();
    private static final Map<MEStorage, List<WeakReference<Listener>>> LISTENERS = new WeakHashMap<>();
    private static final Map<MEStorage, List<WeakReference<MEStorage>>> VISIBLE_PARENTS = new WeakHashMap<>();
    private static final Map<MEStorage, List<WeakReference<MEStorage>>> VISIBLE_CHILDREN = new WeakHashMap<>();
    private static final ThreadLocal<Deque<StorageOperation>> OPERATIONS = ThreadLocal.withInitial(ArrayDeque::new);

    private ThroughputMonitorStorageTracker() {}

    public static void register(MEStorage storage, Listener listener) {
        synchronized (LOCK) {
            var listeners = LISTENERS.computeIfAbsent(storage, ignored -> new ArrayList<>());
            boolean found = false;
            for (Iterator<WeakReference<Listener>> iterator = listeners.iterator(); iterator.hasNext();) {
                Listener registered = iterator.next().get();
                if (registered == null) {
                    iterator.remove();
                } else if (registered == listener) {
                    found = true;
                }
            }
            if (!found) {
                listeners.add(new WeakReference<>(listener));
            }
        }
    }

    public static void unregister(Listener listener) {
        synchronized (LOCK) {
            for (Iterator<List<WeakReference<Listener>>> mapIterator = LISTENERS.values().iterator(); mapIterator.hasNext();) {
                List<WeakReference<Listener>> listeners = mapIterator.next();
                removeListener(listeners, listener);
                if (listeners.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
    }

    public static void beginInsert(MEStorage storage) {
        beginOperation(storage, OperationKind.INSERT);
    }

    public static void endInsert(MEStorage storage, AEKey what, long amount) {
        endOperation(storage, what, amount, OperationKind.INSERT);
    }

    public static void beginExtraction(MEStorage storage) {
        beginOperation(storage, OperationKind.EXTRACT);
    }

    public static void endExtraction(MEStorage storage, AEKey what, long amount) {
        endOperation(storage, what, amount, OperationKind.EXTRACT);
    }

    public static long topologyVersion(MEStorage storage) {
        return storage instanceof ThroughputStorageView view ? view.gtlcore$getTopologyVersion() : 0L;
    }

    public static int refreshVisibleStorageLinks(MEStorage root) {
        if (root == null) {
            return 0;
        }

        int linkedStorages = 0;
        int scannedStorages = 0;
        Deque<MEStorage> pending = new ArrayDeque<>();
        Set<MEStorage> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        seen.add(root);

        while (!pending.isEmpty() && scannedStorages < MAX_VISIBLE_STORAGE_SCAN) {
            MEStorage parent = pending.removeFirst();
            scannedStorages++;
            unlinkVisibleChildren(parent);
            Collection<MEStorage> children = childStorages(parent);
            for (MEStorage child : children) {
                if (child == null || child == parent) {
                    continue;
                }

                linkVisibleStorage(parent, child);
                linkedStorages++;
                if (seen.add(child)) {
                    pending.addLast(child);
                }
            }
        }

        return linkedStorages;
    }

    private static Collection<MEStorage> childStorages(MEStorage storage) {
        return storage instanceof ThroughputStorageView view ? view.gtlcore$getChildStorages() : List.of();
    }

    private static void beginOperation(MEStorage storage, OperationKind kind) {
        Deque<StorageOperation> stack = OPERATIONS.get();
        StorageOperation parent = stack.peek();
        if (parent != null) {
            linkVisibleStorage(parent.storage, storage);
        }
        stack.push(new StorageOperation(storage, kind));
    }

    private static void endOperation(MEStorage storage, AEKey what, long amount, OperationKind kind) {
        Deque<StorageOperation> stack = OPERATIONS.get();
        StorageOperation operation = popOperation(stack, storage, kind);
        long changedAmount = Math.max(0L, amount);
        long residualAmount = operation.residualAmount(what, changedAmount);

        StorageOperation parent = stack.peek();
        if (parent != null && parent.kind == kind) {
            parent.recordNestedAmount(what, changedAmount);
        }

        if (residualAmount > 0L) {
            recordChange(storage, what, kind.applySign(residualAmount));
        }

        if (stack.isEmpty()) {
            OPERATIONS.remove();
        }
    }

    private static StorageOperation popOperation(Deque<StorageOperation> stack, MEStorage storage, OperationKind kind) {
        StorageOperation operation = stack.poll();
        if (operation != null && operation.storage == storage && operation.kind == kind) {
            return operation;
        }

        stack.clear();
        return new StorageOperation(storage, kind);
    }

    private static void recordChange(MEStorage storage, AEKey what, long amountDelta) {
        if (amountDelta == 0 || what == null) {
            return;
        }

        long tick = TickHandler.instance().getCurrentTick();
        if (tick <= 0) {
            return;
        }

        boolean anyListeners;
        List<Listener> matched = new ArrayList<>();
        synchronized (LOCK) {
            anyListeners = !LISTENERS.isEmpty();
            Set<MEStorage> targetStorages = visibleDispatchStorages(storage);
            Set<Listener> deliveredListeners = Collections.newSetFromMap(new IdentityHashMap<>());
            for (MEStorage targetStorage : targetStorages) {
                List<WeakReference<Listener>> listeners = LISTENERS.get(targetStorage);
                if (listeners == null) {
                    continue;
                }

                for (Iterator<WeakReference<Listener>> iterator = listeners.iterator(); iterator.hasNext();) {
                    Listener listener = iterator.next().get();
                    if (listener == null) {
                        iterator.remove();
                    } else if (what.equals(listener.getTrackedKey()) && deliveredListeners.add(listener)) {
                        matched.add(listener);
                    }
                }
                if (listeners.isEmpty()) {
                    LISTENERS.remove(targetStorage);
                }
            }
        }

        if (!anyListeners) {
            return;
        }

        for (Listener listener : matched) {
            listener.recordThroughput(amountDelta, tick);
        }
    }

    private static Set<MEStorage> visibleDispatchStorages(MEStorage storage) {
        Set<MEStorage> storages = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<MEStorage> pending = new ArrayDeque<>();
        storages.add(storage);
        pending.add(storage);

        int depth = 0;
        while (!pending.isEmpty() && depth < MAX_PARENT_DISPATCH_DEPTH) {
            MEStorage child = pending.removeFirst();
            depth++;
            List<WeakReference<MEStorage>> parents = VISIBLE_PARENTS.get(child);
            if (parents == null) {
                continue;
            }

            for (Iterator<WeakReference<MEStorage>> iterator = parents.iterator(); iterator.hasNext();) {
                MEStorage parent = iterator.next().get();
                if (parent == null) {
                    iterator.remove();
                } else if (storages.add(parent)) {
                    pending.addLast(parent);
                }
            }
            if (parents.isEmpty()) {
                VISIBLE_PARENTS.remove(child);
            }
        }

        return storages;
    }

    private static void linkVisibleStorage(MEStorage parent, MEStorage child) {
        if (parent == null || child == null || parent == child) {
            return;
        }

        synchronized (LOCK) {
            List<WeakReference<MEStorage>> parents = VISIBLE_PARENTS.computeIfAbsent(child, ignored -> new ArrayList<>());
            for (Iterator<WeakReference<MEStorage>> iterator = parents.iterator(); iterator.hasNext();) {
                MEStorage existing = iterator.next().get();
                if (existing == null) {
                    iterator.remove();
                } else if (existing == parent) {
                    return;
                }
            }
            parents.add(new WeakReference<>(parent));
            VISIBLE_CHILDREN.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(new WeakReference<>(child));
        }
    }

    private static void unlinkVisibleChildren(MEStorage parent) {
        synchronized (LOCK) {
            List<WeakReference<MEStorage>> children = VISIBLE_CHILDREN.remove(parent);
            if (children == null) {
                return;
            }

            for (WeakReference<MEStorage> childReference : children) {
                MEStorage child = childReference.get();
                if (child != null) {
                    removeVisibleParent(child, parent);
                }
            }
        }
    }

    private static void removeVisibleParent(MEStorage child, MEStorage parent) {
        List<WeakReference<MEStorage>> parents = VISIBLE_PARENTS.get(child);
        if (parents == null) {
            return;
        }

        for (Iterator<WeakReference<MEStorage>> iterator = parents.iterator(); iterator.hasNext();) {
            MEStorage existing = iterator.next().get();
            if (existing == null || existing == parent) {
                iterator.remove();
            }
        }
        if (parents.isEmpty()) {
            VISIBLE_PARENTS.remove(child);
        }
    }

    private static void removeListener(List<WeakReference<Listener>> listeners, Listener listener) {
        for (Iterator<WeakReference<Listener>> iterator = listeners.iterator(); iterator.hasNext();) {
            Listener registered = iterator.next().get();
            if (registered == null || registered == listener) {
                iterator.remove();
            }
        }
    }

    private enum OperationKind {

        INSERT {

            @Override
            long applySign(long amount) {
                return amount;
            }
        },
        EXTRACT {

            @Override
            long applySign(long amount) {
                return -amount;
            }
        };

        abstract long applySign(long amount);
    }

    private static final class StorageOperation {

        private final MEStorage storage;
        private final OperationKind kind;
        private final Map<AEKey, Long> nestedAmounts = new HashMap<>();

        private StorageOperation(MEStorage storage, OperationKind kind) {
            this.storage = storage;
            this.kind = kind;
        }

        private void recordNestedAmount(AEKey key, long amount) {
            if (key == null || amount <= 0L) {
                return;
            }

            nestedAmounts.merge(key, amount, NumberUtils::saturatedAdd);
        }

        private long residualAmount(AEKey key, long totalAmount) {
            long nestedAmount = key == null ? 0L : nestedAmounts.getOrDefault(key, 0L);
            return totalAmount > nestedAmount ? totalAmount - nestedAmount : 0L;
        }
    }

    public interface Listener {

        AEKey getTrackedKey();

        void recordThroughput(long amountDelta, long tick);
    }
}
