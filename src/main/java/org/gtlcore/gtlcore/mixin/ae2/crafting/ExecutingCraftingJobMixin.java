package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingJobSuspensionState;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingJobSuspension;

import net.minecraft.nbt.CompoundTag;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExecutingCraftingJob.class)
public abstract class ExecutingCraftingJobMixin implements ICraftingJobSuspension {

    @Unique
    private boolean gtlcore$suspended;

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At("RETURN"),
            remap = false)
    private void gtlcore$readSuspended(CompoundTag data, @Coerce Object listener, CraftingCpuLogic logic, CallbackInfo ci) {
        this.gtlcore$suspended = data.getBoolean(CraftingJobSuspensionState.NBT_SUSPENDED);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void gtlcore$writeSuspended(CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putBoolean(CraftingJobSuspensionState.NBT_SUSPENDED, this.gtlcore$suspended);
    }

    @Override
    @Unique
    public boolean gtlcore$isJobSuspended() {
        return this.gtlcore$suspended;
    }

    @Override
    @Unique
    public void gtlcore$setJobSuspended(boolean suspended) {
        this.gtlcore$suspended = suspended;
    }
}
