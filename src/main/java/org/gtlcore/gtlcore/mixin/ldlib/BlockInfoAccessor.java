package org.gtlcore.gtlcore.mixin.ldlib;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

@Mixin(value = BlockInfo.class, remap = false)
public interface BlockInfoAccessor {

    @Accessor("tag")
    CompoundTag gtlcore$getTag();

    @Accessor("itemStack")
    ItemStack gtlcore$getItemStack();

    @Accessor("postCreate")
    Consumer<BlockEntity> gtlcore$getPostCreate();
}
