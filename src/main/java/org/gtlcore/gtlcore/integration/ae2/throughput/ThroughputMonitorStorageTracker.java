package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

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
    private static final Map<MEStorage, List<WeakReference<AllListener>>> ALL_LISTENERS = new WeakHashMap<>();
    private static final Map<MEStorage, List<WeakReference<MEStorage>>> VISIBLE_PARENTS = new WeakHashMap<>();
    private static final Map<MEStorage, List<WeakReference<MEStorage>>> VISIBLE_CHILDREN = new WeakHashMap<>();
    private static final ThreadLocal<Deque<StorageOperation>> OPERATIONS = new ThreadLocal<>();

    private static volatile boolean trackingActive;

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
            trackingActive = true;
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
            refreshTrackingState();
        }
    }

    public static void registerAll(MEStorage storage, AllListener listener) {
        synchronized (LOCK) {
            var listeners = ALL_LISTENERS.computeIfAbsent(storage, ignored -> new ArrayList<>());
            boolean found = false;
            for (Iterator<WeakReference<AllListener>> iterator = listeners.iterator(); iterator.hasNext();) {
                AllListener registered = iterator.next().get();
                if (registered == null) {
                    iterator.remove();
                } else if (registered == listener) {
                    found = true;
                }
            }
            if (!found) {
                listeners.add(new WeakReference<>(listener));
            }
            trackingActive = true;
        }
    }

    public static void unregisterAll(AllListener listener) {
        synchronized (LOCK) {
            for (Iterator<List<WeakReference<AllListener>>> mapIterator = ALL_LISTENERS.values().iterator(); mapIterator.hasNext();) {
                List<WeakReference<AllListener>> listeners = mapIterator.next();
                removeAllListener(listeners, listener);
                if (listeners.isEmpty()) {
                    mapIterator.remove();
                }
            }
            refreshTrackingState();
        }
    }

    public static boolean isTrackingActive() {
        return trackingActive;
    }

    public static boolean hasPendingOperation() {
        Deque<StorageOperation> stack = OPERATIONS.get();
        return stack != null && !stack.isEmpty();
    }

    public static void beginInsert(MEStorage storage, IActionSource source) {
        beginOperation(storage, OperationKind.INSERT, source);
    }

    public static void endInsert(MEStorage storage, AEKey what, long amount, IActionSource source) {
        endOperation(storage, what, amount, OperationKind.INSERT, source);
    }

    public static void beginExtraction(MEStorage storage, IActionSource source) {
        beginOperation(storage, OperationKind.EXTRACT, source);
    }

    public static void endExtraction(MEStorage storage, AEKey what, long amount, IActionSource source) {
        endOperation(storage, what, amount, OperationKind.EXTRACT, source);
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

    private static void beginOperation(MEStorage storage, OperationKind kind, IActionSource source) {
        Deque<StorageOperation> stack = OPERATIONS.get();
        if (!trackingActive && (stack == null || stack.isEmpty())) {
            return;
        }
        if (stack == null) {
            stack = new ArrayDeque<>();
            OPERATIONS.set(stack);
        }
        StorageOperation parent = stack.peek();
        if (parent != null) {
            linkVisibleStorage(parent.storage, storage);
        }
        stack.push(new StorageOperation(storage, kind, source));
    }

    private static void endOperation(MEStorage storage, AEKey what, long amount, OperationKind kind,
                                     @Nullable IActionSource actionSource) {
        Deque<StorageOperation> stack = OPERATIONS.get();
        if (stack == null) {
            return;
        }
        StorageOperation operation = popOperation(stack, storage, kind);
        if (operation == null) {
            OPERATIONS.remove();
            return;
        }
        long changedAmount = Math.max(0L, amount);
        long residualAmount = operation.residualAmount(what, changedAmount);

        StorageOperation parent = stack.peek();
        if (parent != null && parent.kind == kind) {
            parent.recordNestedAmount(what, changedAmount);
        }

        if (residualAmount > 0L) {
            recordChange(
                    storage,
                    what,
                    kind.applySign(residualAmount),
                    actionSource == null ? operation.source : actionSource);
        }

        if (stack.isEmpty()) {
            OPERATIONS.remove();
        }
    }

    private static @Nullable StorageOperation popOperation(Deque<StorageOperation> stack, MEStorage storage,
                                                           OperationKind kind) {
        StorageOperation operation = stack.poll();
        if (operation != null && operation.storage == storage && operation.kind == kind) {
            return operation;
        }

        stack.clear();
        return null;
    }

    private static void recordChange(MEStorage storage, AEKey what, long amountDelta,
                                     @Nullable IActionSource actionSource) {
        if (!trackingActive || amountDelta == 0 || what == null) {
            return;
        }

        long tick = TickHandler.instance().getCurrentTick();
        if (tick <= 0) {
            return;
        }

        List<Listener> matched = new ArrayList<>();
        List<AllListener> allMatched = new ArrayList<>();
        synchronized (LOCK) {
            if (LISTENERS.isEmpty() && ALL_LISTENERS.isEmpty()) {
                refreshTrackingState();
                return;
            }
            Set<MEStorage> targetStorages = visibleDispatchStorages(storage);
            Set<Listener> deliveredListeners = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<AllListener> deliveredAllListeners = Collections.newSetFromMap(new IdentityHashMap<>());
            for (MEStorage targetStorage : targetStorages) {
                List<WeakReference<Listener>> listeners = LISTENERS.get(targetStorage);
                if (listeners != null) {
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

                List<WeakReference<AllListener>> allListeners = ALL_LISTENERS.get(targetStorage);
                if (allListeners == null) {
                    continue;
                }
                for (Iterator<WeakReference<AllListener>> iterator = allListeners.iterator(); iterator.hasNext();) {
                    AllListener listener = iterator.next().get();
                    if (listener == null) {
                        iterator.remove();
                    } else if (deliveredAllListeners.add(listener)) {
                        allMatched.add(listener);
                    }
                }
                if (allListeners.isEmpty()) {
                    ALL_LISTENERS.remove(targetStorage);
                }
            }
            refreshTrackingState();
        }

        for (Listener listener : matched) {
            listener.recordThroughput(amountDelta, tick);
        }
        if (!allMatched.isEmpty()) {
            SourceLocation source = resolveSource(actionSource);
            for (AllListener listener : allMatched) {
                listener.recordThroughput(what, amountDelta, tick, source);
            }
        }
    }

    private static void refreshTrackingState() {
        trackingActive = !LISTENERS.isEmpty() || !ALL_LISTENERS.isEmpty();
        if (!trackingActive) {
            VISIBLE_PARENTS.clear();
            VISIBLE_CHILDREN.clear();
        }
    }

    private static @Nullable SourceLocation resolveSource(@Nullable IActionSource actionSource) {
        if (actionSource == null) {
            return null;
        }

        IActionHost actionHost = actionSource.machine().orElse(null);
        if (actionHost != null) {
            IGridNode node = actionHost.getActionableNode();
            if (node != null) {
                SourceLocation ownerLocation = resolveOwner(node.getOwner());
                if (ownerLocation != null) {
                    return ownerLocation;
                }
            }
        }

        BlockEntity contextBlockEntity = actionSource.context(BlockEntity.class).orElse(null);
        SourceLocation contextLocation = resolveOwner(contextBlockEntity);
        if (contextLocation != null) {
            return contextLocation;
        }
        MetaMachine contextMachine = actionSource.context(MetaMachine.class).orElse(null);
        contextLocation = resolveOwner(contextMachine);
        if (contextLocation != null) {
            return contextLocation;
        }

        Player player = actionSource.player().orElse(null);
        return player == null ? null : new SourceLocation(
                player.level().dimension().location(),
                player.blockPosition(),
                player.getDirection());
    }

    private static @Nullable SourceLocation resolveOwner(@Nullable Object owner) {
        if (owner instanceof AEBasePart part && part.getLevel() != null) {
            return new SourceLocation(
                    part.getLevel().dimension().location(),
                    part.getHost().getLocation().getPos(),
                    part.getSide());
        }
        if (owner instanceof BlockEntity blockEntity && blockEntity.getLevel() != null) {
            return new SourceLocation(
                    blockEntity.getLevel().dimension().location(),
                    blockEntity.getBlockPos(),
                    null);
        }
        if (owner instanceof MetaMachine machine && machine.getLevel() != null) {
            return new SourceLocation(
                    machine.getLevel().dimension().location(),
                    machine.getPos(),
                    machine.getFrontFacing());
        }
        if (owner instanceof IMachineBlockEntity machineBlockEntity) {
            return resolveOwner(machineBlockEntity.self());
        }
        if (owner instanceof IGridConnectedMachine gridConnectedMachine) {
            return resolveOwner(gridConnectedMachine.self());
        }
        if (owner instanceof MachineTrait machineTrait) {
            return resolveOwner(machineTrait.getMachine());
        }
        return null;
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

    private static void removeAllListener(List<WeakReference<AllListener>> listeners, AllListener listener) {
        for (Iterator<WeakReference<AllListener>> iterator = listeners.iterator(); iterator.hasNext();) {
            AllListener registered = iterator.next().get();
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
        private final IActionSource source;
        private @Nullable Map<AEKey, Long> nestedAmounts;

        private StorageOperation(MEStorage storage, OperationKind kind, @Nullable IActionSource source) {
            this.storage = storage;
            this.kind = kind;
            this.source = source;
        }

        private void recordNestedAmount(AEKey key, long amount) {
            if (key == null || amount <= 0L) {
                return;
            }

            if (this.nestedAmounts == null) {
                this.nestedAmounts = new HashMap<>();
            }
            this.nestedAmounts.merge(key, amount, NumberUtils::saturatedAdd);
        }

        private long residualAmount(AEKey key, long totalAmount) {
            long nestedAmount = key == null || this.nestedAmounts == null ?
                    0L : this.nestedAmounts.getOrDefault(key, 0L);
            return totalAmount > nestedAmount ? totalAmount - nestedAmount : 0L;
        }
    }

    public interface Listener {

        AEKey getTrackedKey();

        void recordThroughput(long amountDelta, long tick);
    }

    public interface AllListener {

        void recordThroughput(AEKey key, long amountDelta, long tick, @Nullable SourceLocation source);
    }

    public record SourceLocation(ResourceLocation dimension, BlockPos pos, @Nullable Direction side) {}
}
