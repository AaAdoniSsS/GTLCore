package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IAdvancedOCResult;

import com.gregtechceu.gtceu.api.recipe.logic.OCResult;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Implements({
        @Interface(
                   iface = IAdvancedOCResult.class,
                   prefix = "gTLCore$")
})
@Mixin(OCResult.class)
public abstract class OCResultMixin {

    @Shadow(remap = false)
    private long eut;
    @Shadow(remap = false)
    private long parallelEUt;
    @Shadow(remap = false)
    private int duration;
    @Shadow(remap = false)
    private int parallel;
    @Shadow(remap = false)
    private int ocLevel;

    @Unique
    private int gTLCore$baseOCLevel;
    @Unique
    private double gTLCore$durationFactor = 0;
    @Unique
    private double gTLCore$voltageFactor = 0;
    @Unique
    private boolean gTLCore$subTickOverclockAvailable = false;

    /**
     * @author Dragons
     * @reason 兼容
     */
    @Overwrite(remap = false)
    public void init(long eut, int duration, int parallel, long parallelEUt, int ocLevel) {
        this.eut = eut;
        this.duration = duration;
        this.parallel = parallel;
        this.parallelEUt = parallelEUt;
        this.ocLevel = ocLevel;
        gTLCore$baseOCLevel = ocLevel;
        gTLCore$durationFactor = 0;
        gTLCore$voltageFactor = 0;
        gTLCore$subTickOverclockAvailable = false;
    }

    @Unique
    public void gTLCore$init(long eut, int duration, int parallel, long parallelEUt, int baseOCLevel, int totalOCLevel, double durationFactor, double voltageFactor) {
        this.eut = eut;
        this.duration = duration;
        this.parallel = parallel;
        this.parallelEUt = parallelEUt;
        this.ocLevel = totalOCLevel;
        gTLCore$baseOCLevel = baseOCLevel;
        gTLCore$durationFactor = durationFactor;
        gTLCore$voltageFactor = voltageFactor;
        gTLCore$subTickOverclockAvailable = false;
    }

    @Unique
    public int gTLCore$getBaseOCLevel() {
        return gTLCore$baseOCLevel;
    }

    @Unique
    public double gTLCore$getDurationFactor() {
        return gTLCore$durationFactor;
    }

    @Unique
    public double gTLCore$getVoltageFactor() {
        return gTLCore$voltageFactor;
    }

    @Unique
    public boolean gTLCore$isSubTickOverclockAvailable() {
        return gTLCore$subTickOverclockAvailable;
    }

    @Unique
    public void gTLCore$setSubTickOverclockAvailable(boolean available) {
        gTLCore$subTickOverclockAvailable = available;
    }

    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void gtlcore$resetSubTickOverclockAvailable(CallbackInfo ci) {
        gTLCore$subTickOverclockAvailable = false;
    }
}
