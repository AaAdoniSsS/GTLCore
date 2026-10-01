package org.gtlcore.gtlcore.mixin.ldlib;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockInfo.class, remap = false)
public abstract class MachineBlockInfoMixin {

    @Inject(method = "fromBlockState", at = @At("HEAD"), cancellable = true)
    private static void gtlcore$deferMachineCreation(BlockState state, CallbackInfoReturnable<BlockInfo> cir) {
        // This exact GT class always creates a BE. Do not instantiate a full AE buffer
        // merely to answer that question; BlockInfo still creates it when actually needed.
        // Subclasses and other mods keep LDLib's original nullable-factory behavior.
        if (state.getBlock().getClass() == MetaMachineBlock.class) {
            cir.setReturnValue(new BlockInfo(state, true));
        }
    }
}
