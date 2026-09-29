package org.gtlcore.gtlcore.mixin.gtmt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AdvancedBlockPattern.class, remap = false)
public interface TerminalPatternAccessor {

    @Invoker("setActualRelativeOffset")
    BlockPos gtlcore$offset(int x, int y, int z, Direction facing, Direction up, boolean flipped);
}
