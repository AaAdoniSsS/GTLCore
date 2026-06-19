package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.network.GTLNetworkHandler;
import org.gtlcore.gtlcore.network.packet.SStructureDetectHighlight;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.*;
import com.gregtechceu.gtceu.api.pattern.error.*;
import com.gregtechceu.gtceu.common.item.TooltipBehavior;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @author EasterFG on 2024/10/25
 */
public class StructureDetectBehavior extends TooltipBehavior implements IToolBehavior, IInteractionItem {

    private static final ReentrantLock LOCK = new ReentrantLock();

    public static final StructureDetectBehavior INSTANCE = new StructureDetectBehavior(lines -> {
        lines.add(Component.translatable("item.gtlcore.structure_detect.tooltip.0"));
        lines.add(Component.translatable("item.gtlcore.structure_detect.tooltip.1"));
    });

    /**
     * @param tooltips a consumer adding translated tooltips to the tooltip list
     */
    public StructureDetectBehavior(@NotNull Consumer<List<Component>> tooltips) {
        super(tooltips);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        var tag = stack.getTag();
        if (tag == null) {
            tag = new CompoundTag();
            tag.putBoolean("isFlipped", false);
            stack.setTag(tag);
        }
        if (player != null) {
            Level level = context.getLevel();
            if (level.isClientSide) return InteractionResult.PASS;
            BlockPos blockPos = context.getClickedPos();
            if (MetaMachine.getMachine(level, blockPos) instanceof IMultiController controller) {
                if (controller.isFormed()) {
                    player.sendSystemMessage(Component.translatable("message.gtlcore.structure_formed").withStyle(ChatFormatting.GREEN));
                } else {
                    boolean isFlipped = !tag.isEmpty() && tag.getBoolean("isFlipped");
                    ((ServerLevel) level).getServer().execute(() -> {
                        var pattern = controller.getPattern();
                        if (!LOCK.tryLock()) {
                            player.sendSystemMessage(Component.literal("Structure detection is already running."));
                            return;
                        }
                        try {
                            var result = check(controller, pattern, isFlipped);
                            for (var patternError : result) {
                                showError(player, patternError, isFlipped);
                            }
                        } finally {
                            LOCK.unlock();
                        }
                    });
                    return InteractionResult.SUCCESS;
                }
            } else if (player instanceof ServerPlayer serverPlayer) {
                tag.putBoolean("isFlipped", !tag.getBoolean("isFlipped"));
                serverPlayer.displayClientMessage(Component.translatable(!tag.getBoolean("isFlipped") ? "message.gtlcore.detection_mode_normal" : "message.gtlcore.detection_mode_mirrored"), true);
            }
        }
        return InteractionResult.PASS;
    }

    private List<PatternError> check(IMultiController controller, BlockPattern pattern, boolean isFlipped) {
        var errors = new ObjectArrayList<PatternError>();
        if (controller == null) {
            errors.add(new PatternStringError("no controller found"));
            return errors;
        }
        var centerPos = controller.self().getPos();
        var frontFacing = controller.self().getFrontFacing();
        var facings = controller.hasFrontFacing() ? new Direction[] { frontFacing } :
                new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        if (controller.self().getBlockState().getBlock() instanceof MetaMachineBlock machineBlock) {
            if (machineBlock.rotationState == RotationState.NONE) facings = new Direction[] { frontFacing };
        }
        var upwardsFacing = controller.self().getUpwardsFacing();
        var worldState = new MultiblockState(controller.self().getLevel(), controller.self().getPos());
        for (var direction : facings) {
            pattern.checkPatternAt(worldState, centerPos, direction, upwardsFacing, isFlipped, false);
            if (worldState.hasError()) errors.add(worldState.error);
        }
        return errors;
    }

    private void showError(Player player, PatternError error, boolean flip) {
        var show = new ObjectArrayList<Component>();
        if (error instanceof PatternStringError pe) {
            player.sendSystemMessage(pe.getErrorInfo());
            return;
        }
        var pos = error.getPos();
        var posComponent = Component.translatable("item.gtlcore.structure_detect.error.2", pos.getX(), pos.getY(), pos.getZ(), flip ?
                Component.translatable("item.gtlcore.structure_detect.error.3").withStyle(ChatFormatting.GREEN) :
                Component.translatable("item.gtlcore.structure_detect.error.4").withStyle(ChatFormatting.YELLOW));
        var candidates = error.getCandidates();
        if (error instanceof SinglePredicateError) {
            var root = candidates.get(0).get(0).getHoverName();
            show.add(Component.translatable("item.gtlcore.structure_detect.error.1", posComponent));
            show.add(Component.literal(" - ").append(root).append(error.getErrorInfo()));
        } else {
            show.add(Component.translatable("item.gtlcore.structure_detect.error.0", posComponent));
            for (var candidate : candidates) {
                if (!candidate.isEmpty()) {
                    show.add(Component.literal(" - ").append(candidate.get(0).getDisplayName()));
                }
            }
        }
        show.forEach(player::sendSystemMessage);
        GTLNetworkHandler.INSTANCE.sendTo(new SStructureDetectHighlight(error.getPos(), error.getWorld().dimension(),
                System.currentTimeMillis() + 15000), (ServerPlayer) player);
    }
}
