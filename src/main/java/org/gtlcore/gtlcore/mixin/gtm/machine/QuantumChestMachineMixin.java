package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.trait.IDirectItemStackTransfer;
import org.gtlcore.gtlcore.common.machine.trait.FixedQuantumChestHandler;
import org.gtlcore.gtlcore.common.machine.trait.QuantumChestLongStorage;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(QuantumChestMachine.class)
public abstract class QuantumChestMachineMixin extends TieredMachine implements QuantumChestLongStorage {

    @Unique
    private static final String GTLCORE_LONG_AMOUNT_NBT = "GTLCoreLongStoredAmount";

    @Unique
    private static final long GTLCORE_BASE_CAPACITY = 4_000_000L;

    @Unique
    private static final int GTLCORE_STORAGE_SLOT = 0;

    @Unique
    @Persisted
    @DescSynced
    private long gtlcore$storedAmount = -1L;

    @Shadow(remap = false)
    @Final
    protected NotifiableItemStackHandler cache;

    @Shadow(remap = false)
    protected ItemStack stored;

    public QuantumChestMachineMixin(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
    }

    /**
     * Uses a handler that keeps virtual capacity arithmetic outside the int range.
     *
     * @author GTLCore
     * @reason The upstream handler can overflow while calculating the remaining capacity.
     */
    @Overwrite(remap = false)
    protected NotifiableItemStackHandler createCacheItemHandler(Object... args) {
        return new FixedQuantumChestHandler((QuantumChestMachine) (Object) this);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        QuantumChestMachineAccessor accessor = (QuantumChestMachineAccessor) this;
        if (tag.contains(GTLCORE_LONG_AMOUNT_NBT)) {
            gtlcore$storedAmount = Math.max(0L, tag.getLong(GTLCORE_LONG_AMOUNT_NBT));
        } else if (gtlcore$storedAmount < 0L) {
            long legacyAmount = Math.max(0L, accessor.gtlcore$getItemsStoredInside());
            legacyAmount += cache.storage.getStackInSlot(GTLCORE_STORAGE_SLOT).getCount();
            if (legacyAmount == 0L) legacyAmount = Math.max(0L, accessor.gtlcore$getStoredAmount());
            gtlcore$storedAmount = Math.min(gtlcore$getStorageCapacity(), legacyAmount);
        }

        ItemStack template = cache.storage.getStackInSlot(GTLCORE_STORAGE_SLOT);
        if (template.isEmpty() && gtlcore$storedAmount > 0L && !stored.isEmpty()) {
            template = stored.copy();
        }
        if (!template.isEmpty()) {
            template = template.copy();
            template.setCount(1);
            ((IDirectItemStackTransfer) cache.storage).gtlcore$setStackWithoutNotify(GTLCORE_STORAGE_SLOT, template);
            stored = template.copy();
        }
        if (gtlcore$storedAmount == 0L) {
            ((IDirectItemStackTransfer) cache.storage).gtlcore$setStackWithoutNotify(GTLCORE_STORAGE_SLOT, ItemStack.EMPTY);
            stored = ItemStack.EMPTY;
        }
        gtlcore$mirrorLegacyAmount();
    }

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putLong(GTLCORE_LONG_AMOUNT_NBT, Math.max(0L, gtlcore$storedAmount));
    }

    @Inject(method = "createUIWidget", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$useLongAmountInGui(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Widget> cir) {
        if (!(cir.getReturnValue() instanceof WidgetGroup group)) return;

        var labels = group.getWidgetsByType(LabelWidget.class);
        if (labels.size() > 1) {
            labels.get(1).setTextSupplier(() -> com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(
                    Math.max(0L, gtlcore$storedAmount)));
        }
    }

    public ItemStack gtlcore$getStoredStack() {
        ItemStack template = cache.storage.getStackInSlot(GTLCORE_STORAGE_SLOT);
        if (template.isEmpty() || gtlcore$getStoredAmount() <= 0L) return ItemStack.EMPTY;
        template = template.copy();
        template.setCount(1);
        return template;
    }

    public long gtlcore$getStoredAmount() {
        return Math.max(0L, gtlcore$storedAmount);
    }

    @Override
    public long gtlcore$getStorageCapacity() {
        int tier = getTier();
        if (tier >= GTValues.MAX) return Long.MAX_VALUE;
        return GTLCORE_BASE_CAPACITY * (1L << Math.max(0, tier - 1));
    }

    @Override
    public void gtlcore$setStoredAmount(long amount) {
        gtlcore$storedAmount = Math.min(gtlcore$getStorageCapacity(), Math.max(0L, amount));
        gtlcore$mirrorLegacyAmount();
    }

    @Override
    public void gtlcore$changeStoredAmount(long amount) {
        long current = gtlcore$getStoredAmount();
        if (amount > 0L && current > gtlcore$getStorageCapacity() - amount) {
            gtlcore$setStoredAmount(gtlcore$getStorageCapacity());
        } else {
            gtlcore$setStoredAmount(current + amount);
        }
    }

    @Override
    public void gtlcore$markStorageChanged() {
        QuantumChestMachine chest = (QuantumChestMachine) (Object) this;
        chest.markDirty();
        chest.notifyBlockUpdate();
    }

    @Unique
    private void gtlcore$mirrorLegacyAmount() {
        QuantumChestMachineAccessor accessor = (QuantumChestMachineAccessor) this;
        accessor.gtlcore$setItemsStoredInside(0);
        accessor.gtlcore$setStoredAmount((int) Math.min(Integer.MAX_VALUE, gtlcore$getStoredAmount()));
    }
}
