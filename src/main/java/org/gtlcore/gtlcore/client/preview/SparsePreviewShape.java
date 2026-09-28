package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.mixin.ldlib.BlockInfoAccessor;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Geometry can be shared; mutable BlockInfo entity caches cannot. */
public final class SparsePreviewShape {

    public final long[] positions;
    public final BlockInfo[] blocks;
    public final BlockPos controller;
    public final int height;

    public SparsePreviewShape(MultiblockShapeInfo shape, MultiblockMachineDefinition definition) {
        var positions = new LongArrayList();
        var infos = new ObjectArrayList<BlockInfo>();
        var source = shape.getBlocks();
        BlockPos controller = null;
        int height = 0;
        for (int x = 0; x < source.length; x++) {
            height = Math.max(height, source[x].length);
            for (int y = 0; y < source[x].length; y++) {
                for (int z = 0; z < source[x][y].length; z++) {
                    var info = source[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    positions.add(BlockPos.asLong(x, y, z));
                    infos.add(info);
                    if (info.getBlockState().getBlock() instanceof IMachineBlock machine && machine.getDefinition() == definition) controller = new BlockPos(x, y, z);
                }
            }
        }
        this.positions = positions.toLongArray();
        this.blocks = infos.toArray(BlockInfo[]::new);
        this.controller = controller;
        this.height = height;
    }

    public long estimatedBytes() {
        return 64L + positions.length * 64L;
    }

    public static BlockInfo copy(BlockInfo source) {
        var extra = (BlockInfoAccessor) source;
        var item = extra.gtlcore$getItemStack();
        var copy = new BlockInfo(source.getBlockState(), source.hasBlockEntity(),
                item == null ? null : item.copy(), extra.gtlcore$getPostCreate());
        if (extra.gtlcore$getTag() != null) copy.setTag(extra.gtlcore$getTag().copy());
        return copy;
    }
}
