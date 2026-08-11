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
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.common.item.TooltipBehavior;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

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

import java.util.Arrays;
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
                            for (var detectionError : result) {
                                showError(player, detectionError, isFlipped);
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

    private List<DetectionError> check(IMultiController controller, BlockPattern pattern, boolean isFlipped) {
        var errors = new ObjectArrayList<DetectionError>();
        if (controller == null) {
            errors.add(new DetectionError(new PatternStringError("no controller found"), null));
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
        for (var direction : facings) {
            var worldState = new MultiblockState(controller.self().getLevel(), controller.self().getPos());
            pattern.checkPatternAt(worldState, centerPos, direction, upwardsFacing, isFlipped, false);
            if (worldState.hasError()) errors.add(new DetectionError(worldState.error, worldState.predicate));
        }
        return errors;
    }

    private void showError(Player player, DetectionError detectionError, boolean flip) {
        var error = detectionError.error();
        var show = new ObjectArrayList<Component>();
        if (error instanceof PatternStringError pe) {
            player.sendSystemMessage(pe.getErrorInfo());
            return;
        }
        var pos = error.getPos();
        var posComponent = Component.translatable("item.gtlcore.structure_detect.error.2", pos.getX(), pos.getY(), pos.getZ(), flip ?
                Component.translatable("item.gtlcore.structure_detect.error.3").withStyle(ChatFormatting.GREEN) :
                Component.translatable("item.gtlcore.structure_detect.error.4").withStyle(ChatFormatting.YELLOW));
        var candidates = getCandidateNames(detectionError.predicate());
        if (error instanceof SinglePredicateError singlePredicateError) {
            var roots = getCandidateNames(singlePredicateError.predicate);
            show.add(Component.translatable("item.gtlcore.structure_detect.error.1", posComponent));
            var detail = Component.literal(" - ");
            if (!roots.isEmpty()) detail.append(roots.get(0));
            show.add(detail.append(error.getErrorInfo()));
        } else {
            show.add(Component.translatable("item.gtlcore.structure_detect.error.0", posComponent));
            for (var candidate : candidates) {
                if (!candidate.isEmpty()) show.add(Component.literal(" - ").append(candidate.get(0)));
            }
        }
        show.forEach(player::sendSystemMessage);
        GTLNetworkHandler.INSTANCE.sendTo(new SStructureDetectHighlight(error.getPos(), error.getWorld().dimension(),
                System.currentTimeMillis() + 15000), (ServerPlayer) player);
    }

    private List<List<Component>> getCandidateNames(TraceabilityPredicate predicate) {
        if (predicate == null) return List.of();
        return java.util.stream.Stream.concat(predicate.common.stream(), predicate.limited.stream())
                .map(this::getCandidateNames)
                .filter(candidates -> !candidates.isEmpty())
                .toList();
    }

    private List<Component> getCandidateNames(SimplePredicate predicate) {
        if (predicate.candidates == null) return List.of();
        return Arrays.stream(predicate.candidates.get())
                .map(this::getCandidateName)
                .filter(component -> component != null)
                .toList();
    }

    private Component getCandidateName(BlockInfo candidate) {
        var fluidState = candidate.getBlockState().getFluidState();
        if (!fluidState.isEmpty()) return fluidState.getType().getFluidType().getDescription();
        var itemStack = candidate.getItemStackForm();
        return itemStack.isEmpty() ? null : itemStack.getHoverName();
    }

    private record DetectionError(PatternError error, TraceabilityPredicate predicate) {}
}
