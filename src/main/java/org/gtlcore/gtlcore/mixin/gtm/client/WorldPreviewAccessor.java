package org.gtlcore.gtlcore.mixin.gtm.client;

import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MultiblockInWorldPreviewRenderer.class, remap = false)
public interface WorldPreviewAccessor {

    @Invoker("rotateByFrontAxis")
    static BlockPos gtlcore$rotate(BlockPos pos, Direction front, Rotation rotation) {
        throw new AssertionError();
    }
}
