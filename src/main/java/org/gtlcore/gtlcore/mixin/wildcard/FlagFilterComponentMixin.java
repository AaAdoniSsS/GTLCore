package org.gtlcore.gtlcore.mixin.wildcard;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.leodreamer.wildcard_pattern.wildcard.impl.FlagFilterComponent", remap = false)
public abstract class FlagFilterComponentMixin {

    @Shadow
    private Material example;

    @Inject(method = "changeExample", at = @At("HEAD"), cancellable = true)
    private void gtlcore$preserveFlagOnMaterialRestore(Material material, CallbackInfoReturnable<Boolean> cir) {
        if (material != GTMaterials.NULL && material == example) {
            cir.setReturnValue(true);
        }
    }
}
