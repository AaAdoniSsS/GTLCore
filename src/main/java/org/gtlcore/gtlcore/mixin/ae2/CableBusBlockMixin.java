package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.common.player.NoClipManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.networking.CableBusBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CableBusBlock.class)
public abstract class CableBusBlockMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void gtlcore$ignoreNoClipPlayerCollision(
                                                     BlockState state,
                                                     BlockGetter level,
                                                     BlockPos pos,
                                                     CollisionContext context,
                                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext &&
                entityContext.getEntity() instanceof Player player &&
                NoClipManager.isEnabled(player)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void gtlcore$ignoreNoClipPlayerInside(
                                                  BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player && NoClipManager.isEnabled(player)) {
            ci.cancel();
        }
    }
}
