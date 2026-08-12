package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.trait.IDirectItemStackTransfer;
import org.gtlcore.gtlcore.api.machine.trait.ILongItemStorage;
import org.gtlcore.gtlcore.mixin.gtm.machine.QuantumChestMachineAccessor;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.world.item.ItemStack;

public final class FixedQuantumChestHandler extends NotifiableItemStackHandler implements ILongItemStorage {

    private static final int STORAGE_SLOT = 0;
    private static final int SLOT_COUNT = 1;

    private final QuantumChestMachine chest;

    public FixedQuantumChestHandler(QuantumChestMachine chest) {
        super(chest, SLOT_COUNT, IO.BOTH, IO.BOTH, ItemStackTransfer::new);
        this.chest = chest;
        setFilter(stack -> !chest.isLocked() || ItemStack.isSameItemSameTags(stack, chest.getLockedItem().getStackInSlot(STORAGE_SLOT)));
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot != STORAGE_SLOT) return ItemStack.EMPTY;
        ItemStack stack = storage.getStackInSlot(slot).copy();
        if (stack.isEmpty() || storage().gtlcore$getStoredAmount() <= 0L) return ItemStack.EMPTY;
        stack.setCount((int) Math.min(Integer.MAX_VALUE, storage().gtlcore$getStoredAmount()));
        return stack;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == STORAGE_SLOT ? Integer.MAX_VALUE : 0;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChanges) {
        if (slot != STORAGE_SLOT || stack.isEmpty() || !canCapInput() || !isItemValid(slot, stack)) return stack;

        ItemStack stored = storage.getStackInSlot(slot);
        if (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, stack)) return stack;

        long available = storage().gtlcore$getStorageCapacity() - storage().gtlcore$getStoredAmount();
        int inserted = (int) Math.min(Math.max(0L, available), stack.getCount());
        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);

        if (!simulate && inserted > 0) {
            if (stored.isEmpty()) {
                stored = stack.copy();
                stored.setCount(1);
                directStorage().gtlcore$setStackWithoutNotify(slot, stored);
            }
            storage().gtlcore$changeStoredAmount(inserted);
            if (notifyChanges) onContentsChanged();
        }
        if (chest.isVoiding()) remainder.setCount(0);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        if (slot != STORAGE_SLOT || amount <= 0 || !canCapOutput()) return ItemStack.EMPTY;

        ItemStack stored = storage.getStackInSlot(slot);
        int extractedAmount = (int) Math.min(storage().gtlcore$getStoredAmount(), amount);
        if (stored.isEmpty() || extractedAmount <= 0) return ItemStack.EMPTY;

        ItemStack extracted = stored.copy();
        extracted.setCount(extractedAmount);
        if (!simulate) {
            storage().gtlcore$changeStoredAmount(-extractedAmount);
            if (storage().gtlcore$getStoredAmount() == 0L) {
                directStorage().gtlcore$setStackWithoutNotify(slot, ItemStack.EMPTY);
            }
            if (notifyChanges) onContentsChanged();
        }
        return extracted;
    }

    @Override
    public Object createSnapshot() {
        return new Snapshot(super.createSnapshot(), storage().gtlcore$getStoredAmount());
    }

    @Override
    public void restoreFromSnapshot(Object snapshot) {
        if (!(snapshot instanceof Snapshot longSnapshot)) {
            super.restoreFromSnapshot(snapshot);
            return;
        }
        super.restoreFromSnapshot(longSnapshot.itemSnapshot());
        storage().gtlcore$setStoredAmount(longSnapshot.amount());
        onContentsChanged();
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        if (!chest.isRemote()) {
            ItemStack stored = storage.getStackInSlot(STORAGE_SLOT).copy();
            accessor().gtlcore$setStored(stored);
            accessor().gtlcore$setStoredAmount((int) Math.min(Integer.MAX_VALUE, storage().gtlcore$getStoredAmount()));
            storage().gtlcore$markStorageChanged();
        }
    }

    @Override
    public long gtlcore$getStoredAmount(int slot) {
        if (slot != STORAGE_SLOT) return 0L;
        return storage().gtlcore$getStoredAmount();
    }

    private QuantumChestMachineAccessor accessor() {
        return (QuantumChestMachineAccessor) chest;
    }

    private QuantumChestLongStorage storage() {
        return (QuantumChestLongStorage) chest;
    }

    private IDirectItemStackTransfer directStorage() {
        return (IDirectItemStackTransfer) storage;
    }

    private record Snapshot(Object itemSnapshot, long amount) {}
}
