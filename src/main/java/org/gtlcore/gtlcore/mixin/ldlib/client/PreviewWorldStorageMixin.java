package org.gtlcore.gtlcore.mixin.ldlib.client;

import org.gtlcore.gtlcore.client.preview.PreviewBlockPosHash;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Only LDLib's client-side dummy worlds; never real chunk or machine storage. */
@Mixin(value = TrackedDummyWorld.class, remap = false)
public abstract class PreviewWorldStorageMixin {

    @Shadow
    @Final
    @Mutable
    public Map<BlockPos, BlockInfo> renderedBlocks;
    @Shadow
    @Final
    @Mutable
    public Map<BlockPos, BlockEntity> blockEntities;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtlcore$coordinateHash(CallbackInfo ci) {
        var blocks = new Object2ObjectOpenCustomHashMap<BlockPos, BlockInfo>(PreviewBlockPosHash.INSTANCE);
        blocks.putAll(renderedBlocks);
        renderedBlocks = blocks;
        var entities = new Object2ObjectOpenCustomHashMap<BlockPos, BlockEntity>(PreviewBlockPosHash.INSTANCE);
        entities.putAll(blockEntities);
        blockEntities = entities;
    }
}
