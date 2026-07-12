package org.gtlcore.gtlcore.api.gui;

import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PatternBufferProxyPositionConfigurator implements IFancyConfigurator {

    private static final int UPDATE_POSITIONS = 0;
    private static final long HIGHLIGHT_DURATION_MILLIS = 15_000L;
    private static final String TELEPORT_COMMAND_PREFIX = "/tp @s ";
    private static final double TELEPORT_HORIZONTAL_OFFSET = 0.5D;
    private static final int TELEPORT_VERTICAL_OFFSET = 1;
    private static final String TELEPORT_OFFER_KEY = "message.gtlcore.pattern_buffer_proxy_teleport_offer";
    private static final String TELEPORT_BUTTON_KEY = "message.gtlcore.pattern_buffer_proxy_teleport_button";
    private static final String TELEPORT_BUTTON_TOOLTIP_KEY = "tooltip.gtlcore.pattern_buffer_proxy_teleport_button";
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 104;
    private static final int PANEL_PADDING = 4;
    private static final int CONTENT_PADDING = 4;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - PANEL_PADDING * 2 - CONTENT_PADDING * 2;

    private final Supplier<List<BlockPos>> positionSupplier;
    private List<BlockPos> positions;

    public PatternBufferProxyPositionConfigurator(Supplier<List<BlockPos>> positionSupplier) {
        this.positionSupplier = positionSupplier;
        this.positions = snapshotPositions();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.gtlcore.pattern_buffer_proxy_positions");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.COMPASS.getDefaultInstance());
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("tooltip.gtlcore.pattern_buffer_proxy_positions"));
    }

    @Override
    public Widget createConfigurator() {
        var group = new WidgetGroup(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        var content = new DraggableScrollableWidgetGroup(
                PANEL_PADDING,
                PANEL_PADDING,
                PANEL_WIDTH - PANEL_PADDING * 2,
                PANEL_HEIGHT - PANEL_PADDING * 2)
                .setBackground(GuiTextures.DISPLAY);
        content.addWidget(new ComponentPanelWidget(CONTENT_PADDING, CONTENT_PADDING, this::appendPositionLines)
                .setMaxWidthLimit(CONTENT_WIDTH)
                .clickHandler(PatternBufferProxyPositionConfigurator::handlePositionClick)
                .setClientSideWidget());
        group.addWidget(content);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public void detectAndSendChange(BiConsumer<Integer, Consumer<FriendlyByteBuf>> sender) {
        List<BlockPos> updatedPositions = snapshotPositions();
        if (!updatedPositions.equals(positions)) {
            positions = updatedPositions;
            sender.accept(UPDATE_POSITIONS, buffer -> writePositions(buffer, updatedPositions));
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == UPDATE_POSITIONS) {
            positions = readPositions(buffer);
        }
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        positions = snapshotPositions();
        writePositions(buffer, positions);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        positions = readPositions(buffer);
    }

    private void appendPositionLines(List<Component> lines) {
        if (positions.isEmpty()) {
            lines.add(Component.translatable("gui.gtlcore.pattern_buffer_proxy_positions.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        lines.add(Component.translatable("gui.gtlcore.pattern_buffer_proxy_positions.count", positions.size())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        for (int index = 0; index < positions.size(); index++) {
            BlockPos position = positions.get(index);
            lines.add(Component.translatable(
                    "gui.gtlcore.pattern_buffer_proxy_positions.entry",
                    index + 1,
                    position.getX(),
                    position.getY(),
                    position.getZ()).withStyle(ChatFormatting.GOLD)
                    .append(ComponentPanelWidget.withButton(
                            Component.translatable("gui.gtlcore.pattern_buffer_proxy_positions.highlight"),
                            Long.toString(position.asLong()))));
        }
    }

    private static void handlePositionClick(String componentData, ClickData clickData) {
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

    private List<BlockPos> snapshotPositions() {
        return List.copyOf(positionSupplier.get());
    }

    private static void writePositions(FriendlyByteBuf buffer, List<BlockPos> positions) {
        buffer.writeVarInt(positions.size());
        for (BlockPos position : positions) {
            buffer.writeBlockPos(position);
        }
    }

    private static List<BlockPos> readPositions(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            positions.add(buffer.readBlockPos());
        }
        return List.copyOf(positions);
    }
}
