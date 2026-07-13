package org.gtlcore.gtlcore.client.gui;

import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;

import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.Level;

public final class PatternBufferProxyPositionConfiguratorClient {

    private static final long HIGHLIGHT_DURATION_MILLIS = 15_000L;
    private static final String TELEPORT_COMMAND_PREFIX = "/tp @s ";
    private static final double TELEPORT_HORIZONTAL_OFFSET = 0.5D;
    private static final int TELEPORT_VERTICAL_OFFSET = 1;
    private static final String TELEPORT_OFFER_KEY = "message.gtlcore.pattern_buffer_proxy_teleport_offer";
    private static final String TELEPORT_BUTTON_KEY = "message.gtlcore.pattern_buffer_proxy_teleport_button";
    private static final String TELEPORT_BUTTON_TOOLTIP_KEY = "tooltip.gtlcore.pattern_buffer_proxy_teleport_button";

    public static void handlePositionClick(String componentData, ClickData clickData) {
        if (!clickData.isRemote) {
            return;
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        BlockPos target = BlockPos.of(Long.parseLong(componentData));
        BlockHighlightHandler.highlight(target, level.dimension(), System.currentTimeMillis() + HIGHLIGHT_DURATION_MILLIS);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    TELEPORT_OFFER_KEY,
                    target.toShortString(),
                    createTeleportButton(target)));
        }
    }

    private static Component createTeleportButton(BlockPos target) {
        return Component.translatable(TELEPORT_BUTTON_KEY)
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, createTeleportCommand(target)))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(TELEPORT_BUTTON_TOOLTIP_KEY))));
    }

    private static String createTeleportCommand(BlockPos target) {
        return TELEPORT_COMMAND_PREFIX + (target.getX() + TELEPORT_HORIZONTAL_OFFSET) + " " +
                (target.getY() + TELEPORT_VERTICAL_OFFSET) + " " +
                (target.getZ() + TELEPORT_HORIZONTAL_OFFSET);
    }
}
