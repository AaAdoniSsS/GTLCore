package org.gtlcore.gtlcore.mixin.mc;

import org.gtlcore.gtlcore.common.player.NoClipManager;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyReturnValue(method = "onClimbable()Z", at = @At("RETURN"))
    private boolean gtlcore$ignoreClimbableBlocksWhileNoClip(boolean onClimbable) {
        return onClimbable &&
                (!((Object) this instanceof Player player) || !NoClipManager.isEnabled(player));
    }
}
