package org.gtlcore.gtlcore.mixin.gtm.api.misc;

import org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer;
import org.gtlcore.gtlcore.utils.NumberUtils;
import org.gtlcore.gtlcore.utils.datastructure.Int128;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Implements(@Interface(
                       iface = IInt128EnergyContainer.class,
                       prefix = "gTLCore$"))
@Mixin(EnergyContainerList.class)
public abstract class EnergyContainerListMixin {

    @Shadow(remap = false)
    @Final
    private List<? extends IEnergyContainer> energyContainerList;

    @Mutable
    @Shadow(remap = false)
    @Final
    private long inputVoltage;

    @Mutable
    @Shadow(remap = false)
    @Final
    private long outputVoltage;

    @Mutable
    @Shadow(remap = false)
    @Final
    private long inputAmperage;

    @Mutable
    @Shadow(remap = false)
    @Final
    private long outputAmperage;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void EnergyContainerList(List<? extends IEnergyContainer> energyContainerList, CallbackInfo ci) {
        long totalInputEut = 0;
        long totalOutputEut = 0;

        for (IEnergyContainer container : energyContainerList) {
            totalInputEut = NumberUtils.saturatedAdd(totalInputEut, NumberUtils.saturatedMultiply(container.getInputVoltage(), container.getInputAmperage()));
            totalOutputEut = NumberUtils.saturatedAdd(totalOutputEut, NumberUtils.saturatedMultiply(container.getOutputVoltage(), container.getOutputAmperage()));
        }

        this.inputVoltage = totalInputEut;
        this.inputAmperage = totalInputEut > 0 ? 1 : 0;
        this.outputVoltage = totalOutputEut;
        this.outputAmperage = totalOutputEut > 0 ? 1 : 0;
    }

    @Redirect(method = "<init>",
              remap = false,
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/misc/EnergyContainerList;calculateVoltageAmperage(JJ)[J"))
    private long[] redirectCalculateVoltageAmperage(long voltage, long amperage) {
        return new long[] { voltage, amperage };
    }

    /**
     * @author Dragons
     * @reason Long Saturated
     */
    @Overwrite(remap = false)
    public long getEnergyStored() {
        long energyStored = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyStored = NumberUtils.saturatedAdd(energyStored, iEnergyContainer.getEnergyStored());
        }
        return energyStored;
    }

    /**
     * @author Dragons
     * @reason Long Saturated
     */
    @Overwrite(remap = false)
    public long getEnergyCapacity() {
        long energyCapacity = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyCapacity = NumberUtils.saturatedAdd(energyCapacity, iEnergyContainer.getEnergyCapacity());
        }
        return energyCapacity;
    }

    /**
     * @author Dragons
     * @reason Long Saturated
     */
    @Overwrite(remap = false)
    public long getInputPerSec() {
        long sum = 0;
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum = NumberUtils.saturatedAdd(sum, iEnergyContainer.getInputPerSec());
        }
        return sum;
    }

    /**
     * @author Dragons
     * @reason Long Saturated
     */
    @Overwrite(remap = false)
    public long getOutputPerSec() {
        long sum = 0;
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum = NumberUtils.saturatedAdd(sum, iEnergyContainer.getOutputPerSec());
        }
        return sum;
    }

    @Unique
    public Int128 gTLCore$getInt128InputPerSec() {
        Int128 sum = Int128.ZERO();
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum.add(((IInt128EnergyContainer) iEnergyContainer).getInt128InputPerSec());
        }
        return sum;
    }

    @Unique
    public Int128 gTLCore$getInt128OutputPerSec() {
        Int128 sum = Int128.ZERO();
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum.add(((IInt128EnergyContainer) iEnergyContainer).getInt128OutputPerSec());
        }
        return sum;
    }

    @Unique
    public Int128 gTLCore$getInt128EnergyStored() {
        Int128 sum = Int128.ZERO();
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum.add(iEnergyContainer.getEnergyStored());
        }
        return sum;
    }

    @Unique
    public Int128 gTLCore$getInt128EnergyCapacity() {
        Int128 sum = Int128.ZERO();
        List<? extends IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum.add(iEnergyContainer.getEnergyCapacity());
        }
        return sum;
    }
}
