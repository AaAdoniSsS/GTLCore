package org.gtlcore.gtlcore.integration.ae2.emitter;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Single-slot filter view forwarding to the config inventory of whichever emitter the terminal currently has
 * selected. Like {@link SelectedEmitterUpgrades}, the menu builds its slot once while the backing emitter
 * changes with the selection, so every access resolves the target lazily.
 *
 * <p>
 * Access goes through {@link ConfigMenuInventory} rather than the raw {@link GenericStackInv} so fluids and
 * other non-item keys round-trip as wrapped stacks, exactly as they do in AE2's own filter slots.
 *
 * <p>
 * On the client the resolver yields {@code null} (parts only exist server-side); the menu refreshes the mirror
 * from the selected entry snapshot, and edits are applied server-side from the fake-slot action.
 */
public final class SelectedEmitterConfig implements InternalInventory {

    private final Supplier<@Nullable GenericStackInv> resolver;
    private final boolean client;
    /** Cached wrapper, rebuilt whenever the selection resolves to a different config inventory. */
    private @Nullable GenericStackInv wrappedInv;
    private @Nullable ConfigMenuInventory wrapper;
    /** Client-side mirror; parts only exist on the server, so there is nothing to delegate to. */
    private ItemStack clientView = ItemStack.EMPTY;

    public SelectedEmitterConfig(Supplier<@Nullable GenericStackInv> resolver, boolean client) {
        this.resolver = resolver;
        this.client = client;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (client) {
            return slot == 0 ? clientView : ItemStack.EMPTY;
        }
        ConfigMenuInventory target = resolveWrapper();
        return target == null || slot != 0 ? ItemStack.EMPTY : target.getStackInSlot(slot);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        if (client) {
            return;
        }
        ConfigMenuInventory target = resolveWrapper();
        if (target != null && slot == 0) {
            target.setItemDirect(slot, stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (client) {
            return slot == 0;
        }
        ConfigMenuInventory target = resolveWrapper();
        return target != null && slot == 0 && target.isItemValid(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (client) {
            return 1;
        }
        ConfigMenuInventory target = resolveWrapper();
        return target == null ? 1 : target.getSlotLimit(slot);
    }

    public void setClientKey(@Nullable AEKey key) {
        if (client) {
            clientView = key == null ? ItemStack.EMPTY : key.wrapForDisplayOrFilter();
        }
    }

    private @Nullable ConfigMenuInventory resolveWrapper() {
        GenericStackInv inv = resolver.get();
        if (inv == null) {
            wrappedInv = null;
            wrapper = null;
            return null;
        }
        if (inv != wrappedInv) {
            wrappedInv = inv;
            wrapper = new ConfigMenuInventory(inv);
        }
        return wrapper;
    }
}
