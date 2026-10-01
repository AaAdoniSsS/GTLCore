package org.gtlcore.gtlcore.client.preview;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

/** Vec3i's linear hash has many collisions in large, dense three-dimensional structures. */
public final class PreviewBlockPosHash implements Hash.Strategy<BlockPos> {

    public static final PreviewBlockPosHash INSTANCE = new PreviewBlockPosHash();

    private PreviewBlockPosHash() {}

    @Override
    public int hashCode(BlockPos pos) {
        return pos == null ? 0 : HashCommon.long2int(HashCommon.mix(pos.asLong()));
    }

    @Override
    public boolean equals(BlockPos first, BlockPos second) {
        return java.util.Objects.equals(first, second);
    }
}
