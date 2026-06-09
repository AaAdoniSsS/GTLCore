package org.gtlcore.gtlcore.integration.ae2.wireless;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import org.gtlcore.gtlcore.client.ae2.wireless.WirelessNetworkCoreRenderer;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class WirelessNetworkCoreBlock extends MetaMachineBlock {
    public static final DirectionProperty FACING = RotationState.NON_Y_AXIS.property;

    public WirelessNetworkCoreBlock() {
        super(prepareProperties(), WirelessNetworkCoreBlockEntity.definition());
        RotationState.clear();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessNetworkCoreBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public IRenderer getRenderer(BlockState state) {
        return WirelessNetworkCoreRenderer.INSTANCE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!ToolHelper.getToolTypes(heldStack).isEmpty()) {
            return super.use(state, level, pos, player, hand, hit);
        }

        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            WirelessNetworkCoreMenu.open(serverPlayer, core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            core.setOwner(player);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity core) {
            core.removeNetwork(serverLevel.getServer());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static BlockBehaviour.Properties prepareProperties() {
        RotationState.set(RotationState.NON_Y_AXIS);
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0F)
                .requiresCorrectToolForDrops();
    }
}
