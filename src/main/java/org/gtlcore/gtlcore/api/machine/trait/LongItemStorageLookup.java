package org.gtlcore.gtlcore.api.machine.trait;

import com.lowdragmc.lowdraglib.misc.ItemTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;

import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class LongItemStorageLookup {

    private static final Map<IItemHandler, ILongItemStorage> WRAPPED_STORAGES = Collections.synchronizedMap(
            new WeakHashMap<>());

    private LongItemStorageLookup() {}

    public static void register(IItemHandler handler, ILongItemStorage storage) {
        WRAPPED_STORAGES.put(handler, storage);
    }

    public static @Nullable ILongItemStorage find(IItemTransfer transfer) {
        if (transfer instanceof ILongItemStorage storage) return storage;
        if (transfer instanceof ItemTransferList list) {
            return containsLongStorage(list) ? new TransferListStorage(list) : null;
        }
        return null;
    }

    public static @Nullable ILongItemStorage find(IItemHandler handler) {
        if (handler instanceof ILongItemStorage storage) return storage;
        return WRAPPED_STORAGES.get(handler);
    }

    private static boolean containsLongStorage(IItemTransfer transfer) {
        if (transfer instanceof ILongItemStorage) return true;
        if (!(transfer instanceof ItemTransferList list)) return false;
        for (IItemTransfer nested : list.transfers) {
            if (containsLongStorage(nested)) return true;
        }
        return false;
    }

    private record TransferListStorage(ItemTransferList transfer) implements ILongItemStorage {

        @Override
        public long gtlcore$getStoredAmount(int slot) {
            int offset = 0;
            for (IItemTransfer nested : transfer.transfers) {
                int slots = nested.getSlots();
                if (slot < offset + slots) {
                    int nestedSlot = slot - offset;
                    ILongItemStorage longStorage = find(nested);
                    return longStorage == null ? nested.getStackInSlot(nestedSlot).getCount() :
                            longStorage.gtlcore$getStoredAmount(nestedSlot);
                }
                offset += slots;
            }
            return 0L;
        }
    }
}
