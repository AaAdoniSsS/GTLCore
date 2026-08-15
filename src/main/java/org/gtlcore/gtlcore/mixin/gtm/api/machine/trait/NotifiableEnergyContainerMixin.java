package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer;
import org.gtlcore.gtlcore.utils.datastructure.Int128;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;

import org.spongepowered.asm.mixin.*;

@Implements(@Interface(
                       iface = IInt128EnergyContainer.class,
                       prefix = "gTLCore$"))
@Mixin(NotifiableEnergyContainer.class)
public abstract class NotifiableEnergyContainerMixin extends NotifiableRecipeHandlerTrait<Long> {

    @Shadow(remap = false)
    protected long energyStored;

    @Shadow(remap = false)
    private long energyCapacity;

    @Shadow(remap = false)
    public void checkOutputSubscription() {
        throw new AssertionError();
    }

    @Unique
    protected Int128 gTLCore$lastEnergyInputPerSec = Int128.ZERO();
    @Unique
    protected Int128 gTLCore$lastEnergyOutputPerSec = Int128.ZERO();
    @Unique
    protected Int128 gTLCore$energyInputPerSec = Int128.ZERO();
    @Unique
    protected Int128 gTLCore$energyOutputPerSec = Int128.ZERO();

    public NotifiableEnergyContainerMixin(MetaMachine machine) {
        super(machine);
    }

    /**
     * @author Dragons
     * @reason PerSec溢出修复
     */
    @Overwrite(remap = false)
    public void setEnergyStored(long energyStored) {
        if (this.energyStored == energyStored) return;
        gTLCore$addEnergyPerSec(energyStored - this.energyStored);
        this.energyStored = energyStored;
        checkOutputSubscription();
        notifyListeners();
    }

    /**
     * @author Dragons
     * @reason PerSec溢出修复
     */
    @Overwrite(remap = false)
    public void updateTick() {
        if (getMachine().getOffsetTimer() % 20 == 0) {
            gTLCore$lastEnergyOutputPerSec = gTLCore$energyOutputPerSec.copy();
            gTLCore$lastEnergyInputPerSec = gTLCore$energyInputPerSec.copy();
            gTLCore$energyOutputPerSec.set(0, 0);
            gTLCore$energyInputPerSec.set(0, 0);
        }
    }

    /**
     * @author Dragons
     * @reason 兼容旧接口
     */
    @Overwrite(remap = false)
    public long getInputPerSec() {
        return gTLCore$lastEnergyInputPerSec.longValue();
    }

    /**
     * @author Dragons
     * @reason 兼容旧接口
     */
    @Overwrite(remap = false)
    public long getOutputPerSec() {
        return gTLCore$lastEnergyOutputPerSec.longValue();
    }

    @Unique
    public Int128 gTLCore$getInt128InputPerSec() {
        return gTLCore$lastEnergyInputPerSec.copy();
    }

    @Unique
    public Int128 gTLCore$getInt128OutputPerSec() {
        return gTLCore$lastEnergyOutputPerSec.copy();
    }

    @Unique
    public void gTLCore$addEnergyPerSec(long energy) {
        if (energy > 0) {
            gTLCore$energyInputPerSec.add(energy);
        } else if (energy < 0) {
            gTLCore$energyOutputPerSec.add(-energy);
        }
    }

    @Unique
    public Int128 gTLCore$getInt128EnergyStored() {
        return new Int128(energyStored);
    }

    @Unique
    public Int128 gTLCore$getInt128EnergyCapacity() {
        return new Int128(energyCapacity);
    }
}
