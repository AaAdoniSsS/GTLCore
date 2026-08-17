package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.locks.Lock;

/**
 * 代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

@Mixin(MultiblockControllerMachine.class)
public abstract class MultiblockControllerMachineMixin extends MetaMachine implements IMultiController {

    @Shadow(remap = false)
    protected boolean isFormed;

    @Shadow(remap = false)
    public abstract Lock getPatternLock();

    @Shadow(remap = false)
    public abstract void setFlipped(boolean isFlipped);

    public MultiblockControllerMachineMixin(IMachineBlockEntity holder) {
        super(holder);
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
}
