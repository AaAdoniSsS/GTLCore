package org.gtlcore.gtlcore.api.machine.trait;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Public extension point for exposing long item and fluid quantities through Forge's int-sized handler APIs.
 */
public final class LongStorageAdapterRegistry {

    public static final int DEFAULT_PRIORITY = 0;

    private static final AtomicLong NEXT_ORDER = new AtomicLong();
    private static final CopyOnWriteArrayList<ItemAdapterEntry> ITEM_ADAPTERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<FluidAdapterEntry> FLUID_ADAPTERS = new CopyOnWriteArrayList<>();
    private static final Map<IItemHandler, LongItemStorage> ATTACHED_ITEM_STORAGES = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final Map<IFluidHandler, LongFluidStorage> ATTACHED_FLUID_STORAGES = Collections.synchronizedMap(
            new WeakHashMap<>());

    private LongStorageAdapterRegistry() {}

    /** Registers a type-based item adapter. Higher priorities are queried first; returning null declines a handler. */
    public static <T extends IItemHandler> Registration registerItemAdapter(Class<T> handlerType,
                                                                            ItemAdapter<T> adapter) {
        return registerItemAdapter(handlerType, DEFAULT_PRIORITY, adapter);
    }

    public static <T extends IItemHandler> Registration registerItemAdapter(Class<T> handlerType, int priority,
                                                                            ItemAdapter<T> adapter) {
        Objects.requireNonNull(handlerType, "handlerType");
        Objects.requireNonNull(adapter, "adapter");
        ItemAdapterEntry entry = new ItemAdapterEntry(handlerType, priority, NEXT_ORDER.getAndIncrement(),
                handler -> adapter.adapt(handlerType.cast(handler)));
        ITEM_ADAPTERS.add(entry);
        ITEM_ADAPTERS.sort(ItemAdapterEntry.ORDERING);
        return registration(() -> ITEM_ADAPTERS.remove(entry));
    }

    public static <T extends IFluidHandler> Registration registerFluidAdapter(Class<T> handlerType,
                                                                              FluidAdapter<T> adapter) {
        return registerFluidAdapter(handlerType, DEFAULT_PRIORITY, adapter);
    }

    public static <T extends IFluidHandler> Registration registerFluidAdapter(Class<T> handlerType, int priority,
                                                                              FluidAdapter<T> adapter) {
        Objects.requireNonNull(handlerType, "handlerType");
        Objects.requireNonNull(adapter, "adapter");
        FluidAdapterEntry entry = new FluidAdapterEntry(handlerType, priority, NEXT_ORDER.getAndIncrement(),
                handler -> adapter.adapt(handlerType.cast(handler)));
        FLUID_ADAPTERS.add(entry);
        FLUID_ADAPTERS.sort(FluidAdapterEntry.ORDERING);
        return registration(() -> FLUID_ADAPTERS.remove(entry));
    }

    /** Attaches a source to one Forge handler instance, typically while a mod creates or exposes its capability. */
    public static void attachItemStorage(IItemHandler handler, LongItemStorage storage) {
        ATTACHED_ITEM_STORAGES.put(Objects.requireNonNull(handler, "handler"),
                Objects.requireNonNull(storage, "storage"));
    }

    /** Attaches a source to one Forge handler instance, typically while a mod creates or exposes its capability. */
    public static void attachFluidStorage(IFluidHandler handler, LongFluidStorage storage) {
        ATTACHED_FLUID_STORAGES.put(Objects.requireNonNull(handler, "handler"),
                Objects.requireNonNull(storage, "storage"));
    }

    public static @Nullable LongItemStorage findItemStorage(IItemHandler handler) {
        LongItemStorage attached = ATTACHED_ITEM_STORAGES.get(handler);
        if (attached != null) return attached;
        if (handler instanceof ILongItemStorage storage) return storage::gtlcore$getStoredAmount;

        for (ItemAdapterEntry entry : ITEM_ADAPTERS) {
            if (!entry.handlerType().isInstance(handler)) continue;
            LongItemStorage adapted = entry.resolver().adapt(handler);
            if (adapted != null) return adapted;
        }
        return null;
    }

    public static @Nullable LongFluidStorage findFluidStorage(IFluidHandler handler) {
        LongFluidStorage attached = ATTACHED_FLUID_STORAGES.get(handler);
        if (attached != null) return attached;
        if (handler instanceof ILongFluidStorage storage) return storage::gtlcore$getStoredAmount;

        for (FluidAdapterEntry entry : FLUID_ADAPTERS) {
            if (!entry.handlerType().isInstance(handler)) continue;
            LongFluidStorage adapted = entry.resolver().adapt(handler);
            if (adapted != null) return adapted;
        }
        return null;
    }

    private static Registration registration(Runnable unregister) {
        AtomicBoolean registered = new AtomicBoolean(true);
        return () -> {
            if (registered.compareAndSet(true, false)) unregister.run();
        };
    }

    @FunctionalInterface
    public interface LongItemStorage {

        long getAmount(int slot);
    }

    @FunctionalInterface
    public interface LongFluidStorage {

        long getAmount(int tank);
    }

    @FunctionalInterface
    public interface ItemAdapter<T extends IItemHandler> {

        @Nullable
        LongItemStorage adapt(T handler);
    }

    @FunctionalInterface
    public interface FluidAdapter<T extends IFluidHandler> {

        @Nullable
        LongFluidStorage adapt(T handler);
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {

        @Override
        void close();
    }

    @FunctionalInterface
    private interface ItemResolver {

        @Nullable
        LongItemStorage adapt(IItemHandler handler);
    }

    @FunctionalInterface
    private interface FluidResolver {

        @Nullable
        LongFluidStorage adapt(IFluidHandler handler);
    }

    private record ItemAdapterEntry(Class<? extends IItemHandler> handlerType, int priority, long order,
                                    ItemResolver resolver) {

        private static final Comparator<ItemAdapterEntry> ORDERING = Comparator
                .comparingInt(ItemAdapterEntry::priority).reversed()
                .thenComparingLong(ItemAdapterEntry::order);
    }

    private record FluidAdapterEntry(Class<? extends IFluidHandler> handlerType, int priority, long order,
                                     FluidResolver resolver) {

        private static final Comparator<FluidAdapterEntry> ORDERING = Comparator
                .comparingInt(FluidAdapterEntry::priority).reversed()
                .thenComparingLong(FluidAdapterEntry::order);
    }
}
