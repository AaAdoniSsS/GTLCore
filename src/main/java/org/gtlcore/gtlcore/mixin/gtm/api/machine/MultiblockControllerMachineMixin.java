package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.trait.ICheckPatternMachine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.Lock;

/**
 * 代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

@Mixin(MultiblockControllerMachine.class)
public abstract class MultiblockControllerMachineMixin extends MetaMachine implements IMultiController, ICheckPatternMachine {

    @Unique
    private int gtlcore$time = 1;

    @Shadow(remap = false)
    protected boolean isFormed;

    @Shadow(remap = false)
    public abstract Lock getPatternLock();

    @Shadow(remap = false)
    public abstract void setFlipped(boolean isFlipped);

    public MultiblockControllerMachineMixin(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean checkPattern() {
        if (gtlcore$time < 1) {
            BlockPattern pattern = getPattern();
            if (pattern != null && pattern.checkPatternAt(getMultiblockState(), false)) {
                gtlcore$time = 0;
                return true;
            } else if (hasButton()) {
                gtlcore$time = 10;
            }
        } else {
            --gtlcore$time;
        }
        return false;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void asyncCheckPattern(long periodID) {
        if ((getMultiblockState().hasError() || !isFormed) && (getHolder().getOffset() + periodID) % 4 == 0 && checkPatternWithTryLock()) {
            if (getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    getPatternLock().lock();
                    try {
                        setFlipped(getMultiblockState().isNeededFlip());
                        onStructureFormed();
                        var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
                        mwsd.addMapping(getMultiblockState());
                        mwsd.removeAsyncLogic(this);
                    } finally {
                        getPatternLock().unlock();
                    }
                });
            }
        }
    }

    @Override
    public void setTime(int time) {
        this.gtlcore$time = time;
    }

    @Override
    public int getTime() {
        return this.gtlcore$time;
    }
}
