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
@Mixin(targets = "org.leodreamer.wildcard_pattern.wildcard.impl.PropertyFilterComponent", remap = false)
public abstract class PropertyFilterComponentMixin {

    @Shadow
    private Material example;

    @Inject(method = "changeExample", at = @At("HEAD"), cancellable = true)
    private void gtlcore$preservePropertyOnMaterialRestore(Material material, CallbackInfoReturnable<Boolean> cir) {
        // Filling the example slot also notifies changeExample during UI construction and synchronization.
        if (material != GTMaterials.NULL && material == example) {
            cir.setReturnValue(true);
        }
    }
}
