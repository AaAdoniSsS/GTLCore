package org.gtlcore.gtlcore.integration.ae2.emitter;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Inventory view that forwards to the upgrade inventory of whichever emitter the terminal currently has
 * selected. The menu's slots are built once, but the backing emitter changes with the selection, so every
 * access resolves the target lazily.
 *
 * <p>
 * On the client the resolver yields {@code null} (parts only exist server-side); the menu's slots then
 * hold the stacks the server sent, and {@link #isItemValid} only has to be permissive enough for click
 * prediction — the server re-validates through the real upgrade inventory.
 */
public final class SelectedEmitterUpgrades implements InternalInventory {

    private final Supplier<@Nullable IUpgradeInventory> resolver;
    private final int size;
    private final boolean client;
    /**
     * Client-side mirror of the cards. Parts only exist on the server, so the client has nothing to
     * delegate to and instead holds whatever the server's slot sync sent.
     */
    private final ItemStack[] clientView;

    public SelectedEmitterUpgrades(Supplier<@Nullable IUpgradeInventory> resolver, int size, boolean client) {
        this.resolver = resolver;
        this.size = size;
        this.client = client;
        this.clientView = new ItemStack[size];
        Arrays.fill(this.clientView, ItemStack.EMPTY);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (client) {
            return slot < clientView.length ? clientView[slot] : ItemStack.EMPTY;
        }
        IUpgradeInventory target = resolver.get();
        return target == null || slot >= target.size() ? ItemStack.EMPTY : target.getStackInSlot(slot);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        if (client) {
            if (slot < clientView.length) {
                clientView[slot] = stack;
            }
            return;
        }
        IUpgradeInventory target = resolver.get();
        if (target != null && slot < target.size()) {
            target.setItemDirect(slot, stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (client) {
            return Upgrades.isUpgradeCardItem(stack);
        }
        IUpgradeInventory target = resolver.get();
        return target != null && slot < target.size() && target.isItemValid(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (client) {
            return 1;
        }
        IUpgradeInventory target = resolver.get();
        return target == null || slot >= target.size() ? 1 : target.getSlotLimit(slot);
    }
}
