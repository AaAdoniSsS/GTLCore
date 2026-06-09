package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlock;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import javax.annotation.Nullable;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;

public final class WirelessNetworkCoreRenderer extends IModelRenderer {
    public static final ResourceLocation MODEL =
            new ResourceLocation(GTLCore.MOD_ID, "block/wireless_network_core");
    public static final WirelessNetworkCoreRenderer INSTANCE = new WirelessNetworkCoreRenderer();

    private WirelessNetworkCoreRenderer() {
        super(MODEL);
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(MODEL);
    }

    @Override
    @Nullable
    @SuppressWarnings("removal")
    protected BakedModel getBlockBakedModel(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                                            @Nullable BlockState state) {
        Direction facing = Direction.NORTH;
        if (state != null && state.hasProperty(WirelessNetworkCoreBlock.FACING)) {
            facing = state.getValue(WirelessNetworkCoreBlock.FACING);
        }
        return getRotatedModel(facing);
    }
}
