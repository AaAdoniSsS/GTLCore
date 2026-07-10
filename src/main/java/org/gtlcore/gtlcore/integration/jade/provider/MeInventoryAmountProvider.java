package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.client.ae2.MeInventoryAmountClient;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class MeInventoryAmountProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID = GTLCore.id("me_network_inventory");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        AEKey key = keyFor(accessor);
        if (key != null) {
            MeInventoryAmountClient.getTooltip(key).ifPresent(tooltip::add);
        }
    }

    private static @Nullable AEKey keyFor(BlockAccessor accessor) {
        BlockState state = accessor.getBlockState();
        if (state.getBlock() instanceof LiquidBlock) {
            Fluid fluid = state.getFluidState().getType();
            if (fluid != Fluids.EMPTY) {
                return AEFluidKey.of(fluid);
            }
        }

        ItemStack pickedResult = accessor.getPickedResult();
        return pickedResult.isEmpty() ? null : AEItemKey.of(pickedResult);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
