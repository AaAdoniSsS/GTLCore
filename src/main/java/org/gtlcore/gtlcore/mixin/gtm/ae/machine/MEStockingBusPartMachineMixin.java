package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.GenericStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    public abstract void setAutoPullTest(Predicate<GenericStack> autoPullTest);

    public MEStockingBusPartMachineMixin(IMachineBlockEntity holder, IO io, Object... args) {
        super(holder, io, args);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gtlcore$allowStandaloneAutoPull(IMachineBlockEntity holder, Object[] args, CallbackInfo ci) {
        setAutoPullTest(stack -> true);
    }

    @Inject(method = "removedFromController", at = @At("RETURN"), remap = false)
    private void gtlcore$restoreStandaloneAutoPull(IMultiController controller, CallbackInfo ci) {
        setAutoPullTest(stack -> true);
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
