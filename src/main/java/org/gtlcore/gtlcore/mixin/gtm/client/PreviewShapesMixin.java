package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.client.preview.PreviewSettings;
import org.gtlcore.gtlcore.client.preview.PreviewShapeCache;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class PreviewShapesMixin {

    @Inject(method = "getMatchingShapes", at = @At("HEAD"), cancellable = true)
    private void gtlcore$cached(CallbackInfoReturnable<List<MultiblockShapeInfo>> cir) {
        if (!Minecraft.getInstance().isSameThread() || !PreviewSettings.enabled()) return;
        var definition = (MultiblockMachineDefinition) (Object) this;
        var shapes = PreviewShapeCache.get(definition);
        if (shapes != null) cir.setReturnValue(shapes);
    }

    @Inject(method = "getMatchingShapes", at = @At("RETURN"))
    private void gtlcore$remember(CallbackInfoReturnable<List<MultiblockShapeInfo>> cir) {
        if (!Minecraft.getInstance().isSameThread() || !PreviewSettings.enabled()) return;
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (PreviewShapeCache.get(definition) == null) PreviewShapeCache.put(definition, cir.getReturnValue());
    }

    @Inject(method = { "setShapes", "setPatternFactory" }, at = @At("HEAD"))
    private void gtlcore$invalidate(CallbackInfo ci) {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.isSameThread()) {
            PreviewShapeCache.invalidate((MultiblockMachineDefinition) (Object) this);
        }
    }
}
