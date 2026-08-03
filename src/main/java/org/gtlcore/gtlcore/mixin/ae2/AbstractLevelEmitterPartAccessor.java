package org.gtlcore.gtlcore.mixin.ae2;

import appeng.parts.automation.AbstractLevelEmitterPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractLevelEmitterPart.class)
public interface AbstractLevelEmitterPartAccessor {

    @Accessor(value = "lastReportedValue", remap = false)
    long gtlcore$getLastReportedValue();
}
