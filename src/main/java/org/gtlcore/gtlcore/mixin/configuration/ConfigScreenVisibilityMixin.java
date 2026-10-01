package org.gtlcore.gtlcore.mixin.configuration;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.toma.configuration.client.screen.ConfigScreen;
import dev.toma.configuration.config.value.ConfigValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenVisibilityMixin {

    @Shadow
    @Final
    @Mutable
    private Map<String, ConfigValue<?>> valueMap;

    @Inject(method = "<init>(Lnet/minecraft/network/chat/Component;Ljava/lang/String;Ljava/util/Map;Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void gtlcore$hideDiagnosticLogging(Component title, String configId,
                                               Map<String, ConfigValue<?>> values, Screen parent, CallbackInfo ci) {
        if (!GTLCore.MOD_ID.equals(configId) || !valueMap.containsKey("ae2GraphDiagnosticLogging")) {
            return;
        }
        // Filter only this screen's copy so the diagnostic option still loads and saves in the config file.
        valueMap = new LinkedHashMap<>(valueMap);
        valueMap.remove("ae2GraphDiagnosticLogging");
    }
}
