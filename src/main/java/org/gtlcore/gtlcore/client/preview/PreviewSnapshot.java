package org.gtlcore.gtlcore.client.preview;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Built on the client thread, then published read-only. Workers never create/tick block entities
 * or consult SceneWidget's mutable render filter. The existing preview entities supply model data.
 */
public final class PreviewSnapshot extends TrackedDummyWorld {

    public final List<BlockPos> positions = new ArrayList<>();
    public final List<BlockPos> entities = new ArrayList<>();
    public final PreviewRayIndex rayIndex = new PreviewRayIndex();
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockEntity> tiles = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<ModelData> modelData = new Long2ObjectOpenHashMap<>();
    private final ThreadLocal<BlockPos> isolatedBlock = new ThreadLocal<>();

    public void add(BlockPos pos, BlockState state, BlockEntity entity) {
        if (state.isAir()) return;
        positions.add(pos);
        states.put(pos.asLong(), state);
        if (entity != null) {
            tiles.put(pos.asLong(), entity);
            modelData.put(pos.asLong(), entity.getModelData());
            entities.add(pos);
        }
    }

    public void isolate(BlockPos pos) {
        if (pos == null) isolatedBlock.remove();
        else isolatedBlock.set(pos);
    }

    public ModelData modelData(BlockPos pos) {
        return modelData.getOrDefault(pos.asLong(), ModelData.EMPTY);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        var isolated = isolatedBlock.get();
        return isolated != null && !isolated.equals(pos) ? Blocks.AIR.defaultBlockState() : states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        var isolated = isolatedBlock.get();
        return isolated != null && !isolated.equals(pos) ? null : tiles.get(pos.asLong());
    }
}
