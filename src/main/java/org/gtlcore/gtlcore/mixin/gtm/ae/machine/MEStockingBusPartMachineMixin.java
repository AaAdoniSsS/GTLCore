package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(MEStockingBusPartMachine.class)
public abstract class MEStockingBusPartMachineMixin extends MEInputBusPartMachine implements IModifiableSyncOffset {

    @Shadow(remap = false)
    private Predicate<GenericStack> autoPullTest;

    public MEStockingBusPartMachineMixin(IMachineBlockEntity holder, IO io, Object... args) {
        super(holder, io, args);
    }

    @Shadow(remap = false)
    public void setAutoPull(boolean autoPull) {
        throw new AssertionError();
    }

    @ModifyConstant(
                    method = "autoIO",
                    constant = @Constant(longValue = 100),
                    remap = false)
    private long replaceOffset(long constant) {
        return getOffset() == 0 ? constant : getOffset();
    }

    @Inject(method = "writeConfigToTag",
            at = @At("RETURN"),
            remap = false)
    public void writesSyncOffset(CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("SyncOffset", getOffset());
    }

    @Inject(method = "readConfigFromTag",
            at = @At("RETURN"),
            remap = false)
    public void readSyncOffset(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("SyncOffset")) {
            this.setOffset(tag.getInt("SyncOffset"));
        }
    }

    /**
     * @author Dragons
     * @reason performance
     */
    @Overwrite(remap = false)
    private void refreshList() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            this.aeItemHandler.clearInventory(0);
        } else {
            MEStorage networkStorage = grid.getStorageService().getInventory();
            var counter = networkStorage.getAvailableStacks();

            int index = 0;
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                if (index >= 16) {
                    break;
                }

                AEKey what = entry.getKey();
                long amount = entry.getLongValue();
                if (amount > 0L && what instanceof AEItemKey itemKey) {
                    long request = networkStorage.extract(what, amount, Actionable.SIMULATE, this.actionSource);
                    if (request != 0L && (this.autoPullTest == null || this.autoPullTest.test(new GenericStack(itemKey, amount)))) {
                        ExportOnlyAEItemSlot slot = this.aeItemHandler.getInventory()[index];
                        ((IMESlot) slot).setConfigWithoutNotify(new GenericStack(what, 1L));
                        slot.setStock(new GenericStack(what, request));
                        ++index;
                    }
                }
            }

            this.aeItemHandler.clearInventory(index);
        }
    }

    /**
     * @author Dragons
     * @reason performance
     */
    @Overwrite(remap = false)
    protected void readConfigFromTag(CompoundTag tag) {
        if (tag.getBoolean("AutoPull")) {
            this.setAutoPull(true);
            this.circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
        } else {
            this.setAutoPull(false);
            if (tag.contains("ConfigStacks")) {
                CompoundTag configStacks = tag.getCompound("ConfigStacks");

                final var inventory = this.aeItemHandler.getInventory();

                for (int i = 0; i < 16; ++i) {
                    String key = Integer.toString(i);
                    if (configStacks.contains(key)) {
                        CompoundTag configTag = configStacks.getCompound(key);
                        ((IMESlot) inventory[i]).setConfigWithoutNotify(GenericStack.readTag(configTag));
                    } else {
                        ((IMESlot) inventory[i]).setConfigWithoutNotify(null);
                    }
                }

                ((IOptimizedMEList) this.aeItemHandler).onConfigChanged();
            }

            if (tag.contains("GhostCircuit")) {
                this.circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(1, () -> ((IOptimizedMEList) this.aeItemHandler).onConfigChanged()));
        }
    }
}
