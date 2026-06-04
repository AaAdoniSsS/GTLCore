package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.hepdd.gtmthings.api.misc.UnlimitedItemStackTransfer;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeBusPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HugeBusPartMachine.class)
public abstract class HugeBusPartMachineMixin extends TieredIOPartMachine {

    public HugeBusPartMachineMixin(IMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
    }

    @Shadow(remap = false)
    protected abstract int getInventorySize();

    @Inject(method = "getInventorySize", at = @At("RETURN"), remap = false, cancellable = true)
    protected void getInventorySize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() - 1);
    }

    @Inject(method = "createInventory", at = @At("HEAD"), remap = false, cancellable = true)
    protected void createInventory(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir) {
        if (io == IO.IN) {
            cir.setReturnValue(new NotifiableItemStackHandler(this, getInventorySize(), IO.IN, IO.IN,
                    UnlimitedItemStackTransfer::new) {

                @Override
                public boolean canCapOutput() {
                    return true;
                }
            }.setFilter(itemStack -> !IntCircuitBehaviour.isIntegratedCircuit(itemStack)));
        }
    }

    @Inject(method = "createCircuitItemHandler", at = @At("HEAD"), remap = false, cancellable = true)
    protected void createCircuitItemHandler(Object[] args, CallbackInfoReturnable<NotifiableItemStackHandler> cir) {
        if (args.length > 0 && args[0] instanceof IO io && io == IO.IN) {
            cir.setReturnValue(new NotifiableCircuitItemStackHandler(this));
        }
    }

    @Inject(method = "setDistinct", at = @At("RETURN"), remap = false)
    public void setDistinct(boolean isDistinct, CallbackInfo ci) {
        for (var controller : this.getControllers()) {
            if (controller instanceof IRecipeCapabilityMachine machine && !machine.isDistinct()) {
                machine.upDate();
            }
        }
    }
}
