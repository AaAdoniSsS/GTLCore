package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.item.TooltipBehavior;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.getBehaviorsTag;

public class MEPatternBufferCutBehavior extends TooltipBehavior implements IInteractionItem {

    private static final String CUT_TAG = "cut";
    private static final String PATTERNS_TAG = "patterns";

    public static final MEPatternBufferCutBehavior INSTANCE = new MEPatternBufferCutBehavior((list -> {
        list.add(Component.translatable("tooltip.gtlcore.cut_pattern_buffer_sneak_right_click"));
        list.add(Component.translatable("tooltip.gtlcore.apply_cut_pattern_buffer_right_click"));
    }));

    public MEPatternBufferCutBehavior(@NotNull Consumer<List<Component>> tooltips) {
        super(tooltips);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        var cut = getBehaviorsTag(stack).getCompound(CUT_TAG);
        if (cut.isEmpty()) {
            return;
        }

        var patterns = cut.getList(PATTERNS_TAG, Tag.TAG_COMPOUND);
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.pattern_buffer_cut_patterns",
                patterns.size()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.pattern_buffer_cut_source",
                cut.getString("name")).withStyle(ChatFormatting.AQUA));
        if (cut.contains("bufferPos", Tag.TAG_LONG)) {
            BlockPos pos = BlockPos.of(cut.getLong("bufferPos"));
            tooltip.add(Component.translatable(
                    "tooltip.gtlcore.pattern_buffer_cut_position",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    cut.getString("dimension")).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.gtlcore.pattern_buffer_cut_position_legacy")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MetaMachineBlockEntity machineBlock) ||
                !(machineBlock.getMetaMachine() instanceof MEPatternBufferPartMachine partMachine)) {
            return InteractionResult.PASS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        var tags = getBehaviorsTag(itemStack);
        if (!serverPlayer.isShiftKeyDown()) {
            if (!tags.contains(CUT_TAG) || tags.getCompound(CUT_TAG).isEmpty()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.gtlcore.pattern_buffer_not_cut"), true);
            } else if (!partMachine.pasteFromTag(tags.getCompound(CUT_TAG))) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.gtlcore.pattern_buffer_paste_failed"), true);
            } else {
                tags.remove(CUT_TAG);
                serverPlayer.getInventory().setChanged();
            }
            return InteractionResult.CONSUME;
        }

        if (hasCutData(itemStack)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.gtlcore.pattern_buffer_cut_already_present"),
                    true);
            return InteractionResult.CONSUME;
        }

        partMachine.cutToTag(tags);
        if (!tags.contains(CUT_TAG) || tags.getCompound(CUT_TAG).isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.gtlcore.pattern_buffer_cut_failed"), true);
        } else {
            serverPlayer.getInventory().setChanged();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.gtlcore.pattern_buffer_cut"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean sneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }

    public static boolean hasCutData(ItemStack stack) {
        var behaviors = stack.getTagElement(ToolHelper.BEHAVIOURS_TAG_KEY);
        return behaviors != null && !behaviors.getCompound(CUT_TAG).isEmpty();
    }
}
