package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WirelessNetworkBookmarkBlockEntity extends BlockEntity {

    public WirelessNetworkBookmarkBlockEntity(BlockPos pos, BlockState state) {
        super(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_BE.get(), pos, state);
    }
}
