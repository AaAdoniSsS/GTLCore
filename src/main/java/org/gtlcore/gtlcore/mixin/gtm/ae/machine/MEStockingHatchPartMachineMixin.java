package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(MEStockingHatchPartMachine.class)
public abstract class MEStockingHatchPartMachineMixin extends MEInputHatchPartMachine implements IModifiableSyncOffset {

    @Shadow(remap = false)
    public abstract void setAutoPullTest(Predicate<GenericStack> autoPullTest);

    public MEStockingHatchPartMachineMixin(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtlcore$allowStandaloneAutoPull(IMachineBlockEntity holder, Object[] args, CallbackInfo ci) {
        setAutoPullTest(stack -> true);
    }

    @Inject(method = "removedFromController", at = @At("RETURN"), remap = false)
    private void gtlcore$restoreStandaloneAutoPull(IMultiController controller, CallbackInfo ci) {
        setAutoPullTest(stack -> true);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        gtlcore$rebuildConfigCache();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        gtlcore$restoreStock();
    }

    private void gtlcore$restoreStock() {
        if (getMainNode().isOnline()) {
            gtlcore$rebuildConfigCache();
            syncME();
            updateTankSubscription();
            aeFluidHandler.notifyListeners();
        }
    }

    private void gtlcore$rebuildConfigCache() {
        if (aeFluidHandler instanceof IOptimizedMEList optimized) {
            optimized.onConfigChanged();
        }
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
}
