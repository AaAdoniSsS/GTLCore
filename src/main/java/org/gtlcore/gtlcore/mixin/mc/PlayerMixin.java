package org.gtlcore.gtlcore.mixin.mc;

import org.gtlcore.gtlcore.common.player.NoClipManager;

import net.minecraft.world.entity.player.Player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyExpressionValue(
                           method = "tick",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
                                    ordinal = 0))
    private boolean gtlcore$applyNoClip(boolean spectator) {
        return spectator || NoClipManager.isEnabled((Player) (Object) this);
    }

    @ModifyExpressionValue(
                           method = "tick",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z",
                                    ordinal = 1))
    private boolean gtlcore$clearNoClipGroundState(boolean spectator) {
        return spectator || NoClipManager.isEnabled((Player) (Object) this);
    }
}
