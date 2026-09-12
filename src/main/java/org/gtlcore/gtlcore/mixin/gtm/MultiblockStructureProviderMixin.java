package org.gtlcore.gtlcore.mixin.gtm;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.integration.jade.provider.MultiblockStructureProvider;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.BlockAccessor;

@Mixin(MultiblockStructureProvider.class)
public abstract class MultiblockStructureProviderMixin {

    @Inject(method = "appendServerData(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/BlockAccessor;)V",
            at = @At("TAIL"),
            remap = false)
    private void gtlcore$publishFormedState(CompoundTag tag, BlockAccessor accessor, CallbackInfo ci) {
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity &&
                blockEntity.getMetaMachine() instanceof IMultiController controller) {
            // The matcher clears its working error repeatedly, even when the machine is still unformed.
            tag.putBoolean("hasError", !controller.isFormed());
        }
    }
}
