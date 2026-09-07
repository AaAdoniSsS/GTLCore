package org.gtlcore.gtlcore.mixin.configuration;

import net.minecraft.network.chat.Component;

import dev.toma.configuration.config.value.ConfigValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(ConfigValue.class)
public class ConfigValueLocalizationMixin {

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$translateCommentKeys(CallbackInfoReturnable<String[]> cir) {
        String[] descriptions = cir.getReturnValue();
        if (descriptions == null || descriptions.length == 0) {
            return;
        }
        List<String> translated = new ArrayList<>();
        boolean changed = false;
        for (String line : descriptions) {
            if (line != null && line.startsWith("config.")) {
                String value = Component.translatable(line).getString();
                if (!value.equals(line)) {
                    translated.addAll(Arrays.asList(value.split("\n", -1)));
                    changed = true;
                    continue;
                }
            }
            translated.add(line);
        }
        if (changed) {
            cir.setReturnValue(translated.toArray(new String[0]));
        }
    }
}
