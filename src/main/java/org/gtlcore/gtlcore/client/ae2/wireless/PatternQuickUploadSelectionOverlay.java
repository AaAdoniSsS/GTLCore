package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadSelectionMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PatternQuickUploadSelectionOverlay {

    private static final int IMAGE_WIDTH = 240;
    private static final int IMAGE_HEIGHT = 158;
    private static final int CONTENT_X = 4;
    private static final int TITLE_Y = 5;
    private static final int PATTERN_Y = 24;
    private static final int LIST_Y = 47;
    private static final int BOTTOM_PADDING = 8;
    private static final int ROW_HEIGHT = 26;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_TEXT_INSET = 6;
    private static final int ROW_TEXT_GAP = 2;
    private static final int SCROLLBAR_WIDTH = WirelessAeStyle.AE2_SCROLLBAR_WIDTH;
    private static final int SCROLLBAR_GAP = 4;
    private static final float OVERLAY_Z = 400.0F;

    private static ItemStack patternStack = ItemStack.EMPTY;
    private static List<PatternQuickUploadSelectionMenu.Entry> entries = List.of();
    private static int scrollOffset;
    private static boolean draggingScrollbar;

    private PatternQuickUploadSelectionOverlay() {}

    public static void open(ItemStack stack, List<PatternQuickUploadSelectionMenu.Entry> targetEntries) {
        patternStack = stack.copy();
        entries = List.copyOf(targetEntries);
        scrollOffset = 0;
        draggingScrollbar = false;
    }

    public static void close() {
        patternStack = ItemStack.EMPTY;
        entries = List.of();
        scrollOffset = 0;
        draggingScrollbar = false;
    }

    public static boolean isOpen() {
        return !patternStack.isEmpty() || !entries.isEmpty();
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }

        int left = getLeft(screenWidth);
        int top = getTop(screenHeight);
        Font font = Minecraft.getInstance().font;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        try {
            WirelessAeStyle.drawInsetPanel(graphics, left, top, IMAGE_WIDTH, IMAGE_HEIGHT);

            graphics.drawString(
                    font,
                    Component.translatable("screen.gtlcore.pattern_quick_upload_select"),
                    left + CONTENT_X,
                    top + TITLE_Y,
                    WirelessAeStyle.TEXT,
                    false);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    Component.translatable("label.gtlcore.pattern_quick_upload_pattern", patternStack.getHoverName()),
                    left + CONTENT_X,
                    top + PATTERN_Y,
                    IMAGE_WIDTH - CONTENT_X * 2,
                    WirelessAeStyle.MUTED_TEXT);

            if (entries.isEmpty()) {
                WirelessAeStyle.drawTrimmedString(
                        graphics,
                        font,
                        Component.translatable("label.gtlcore.pattern_quick_upload_no_targets"),
                        left + CONTENT_X,
                        top + LIST_Y + 4,
                        IMAGE_WIDTH - CONTENT_X * 2,
                        WirelessAeStyle.WARNING_TEXT);
                return;
            }

            drawTargetRows(graphics, font, left, top, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!isOpen()) {
            return false;
        }
        if (button != 0) {
            return true;
        }

        int left = getLeft(screenWidth);
        int top = getTop(screenHeight);
        if (!isInsideRect(mouseX, mouseY, left, top, IMAGE_WIDTH, IMAGE_HEIGHT)) {
            close();
            return true;
        }
        if (handleScrollbarClick(mouseX, mouseY, left, top)) {
            return true;
        }
        handleTargetClick(mouseX, mouseY, left, top);
        return true;
    }

    public static boolean mouseDragged(double mouseY, int screenWidth, int screenHeight) {
        if (!draggingScrollbar) {
            return false;
        }
        updateScrollOffsetFromMouse(mouseY, getTop(screenHeight));
        return true;
    }

    public static boolean mouseReleased() {
        if (!isOpen()) {
            return false;
        }
        draggingScrollbar = false;
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth,
                                        int screenHeight) {
        if (!isOpen()) {
            return false;
        }

        int left = getLeft(screenWidth);
        int top = getTop(screenHeight);
        if (isInsideRect(mouseX, mouseY, getListX(left), getListY(top), IMAGE_WIDTH - CONTENT_X * 2,
                getScrollbarHeight()) && scrollBy(delta)) {
            return true;
        }
        return isInsideRect(mouseX, mouseY, left, top, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    public static boolean keyPressed(int keyCode) {
        if (!isOpen()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return true;
    }

    private static void drawTargetRows(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY) {
        int visibleRows = getVisibleRows();
        scrollOffset = WirelessAeStyle.clampScrollOffset(scrollOffset, entries.size(), visibleRows);
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(entries.size(), visibleRows);
        int listX = getListX(left);
        int listY = getListY(top);
        int listWidth = getListWidth(hasScrollbar);

        int rows = Math.min(visibleRows, Math.max(0, entries.size() - scrollOffset));
        for (int row = 0; row < rows; row++) {
            PatternQuickUploadSelectionMenu.Entry entry = entries.get(scrollOffset + row);
            int rowY = listY + row * ROW_HEIGHT;
            boolean hovered = isInsideRect(mouseX, mouseY, listX, rowY, listWidth, BUTTON_HEIGHT);
            WirelessAeStyle.drawButtonBackground(graphics, listX, rowY, listWidth, BUTTON_HEIGHT,
                    true, false, false, hovered);

            int textX = listX + ROW_TEXT_INSET;
            int titleY = rowY + ROW_TEXT_GAP;
            int recipeY = titleY + font.lineHeight + ROW_TEXT_GAP;
            int textWidth = Math.max(8, listWidth - ROW_TEXT_INSET * 2);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    targetLine(entry),
                    textX,
                    titleY,
                    textWidth,
                    WirelessAeStyle.TEXT);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    recipeTypeLine(entry),
                    textX,
                    recipeY,
                    textWidth,
                    WirelessAeStyle.MUTED_TEXT);
        }

        if (hasScrollbar) {
            WirelessAeStyle.drawAe2Scrollbar(
                    graphics,
                    getScrollbarX(left),
                    listY,
                    getScrollbarHeight(),
                    entries.size(),
                    visibleRows,
                    scrollOffset);
        }
    }

    private static boolean handleScrollbarClick(double mouseX, double mouseY, int left, int top) {
        int visibleRows = getVisibleRows();
        if (!WirelessAeStyle.needsScrollbar(entries.size(), visibleRows) ||
                !isInsideRect(mouseX, mouseY, getScrollbarX(left), getListY(top), SCROLLBAR_WIDTH,
                        getScrollbarHeight())) {
            return false;
        }
        draggingScrollbar = true;
        updateScrollOffsetFromMouse(mouseY, top);
        return true;
    }

    private static void handleTargetClick(double mouseX, double mouseY, int left, int top) {
        int visibleRows = getVisibleRows();
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(entries.size(), visibleRows);
        int listX = getListX(left);
        int listY = getListY(top);
        int listWidth = getListWidth(hasScrollbar);
        if (!isInsideRect(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) {
            return;
        }

        double relativeY = mouseY - listY;
        if ((int) relativeY % ROW_HEIGHT >= BUTTON_HEIGHT) {
            return;
        }
        int index = scrollOffset + (int) (relativeY / ROW_HEIGHT);
        if (index >= 0 && index < entries.size()) {
            WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SelectPatternQuickUploadTargetPacket(index));
            close();
        }
    }

    private static boolean scrollBy(double delta) {
        int nextOffset = WirelessAeStyle.clampScrollOffset(
                scrollOffset - (int) Math.signum(delta),
                entries.size(),
                getVisibleRows());
        if (nextOffset == scrollOffset) {
            return false;
        }
        scrollOffset = nextOffset;
        return true;
    }

    private static void updateScrollOffsetFromMouse(double mouseY, int top) {
        scrollOffset = WirelessAeStyle.ae2ScrollbarOffsetFromMouse(
                mouseY,
                getListY(top),
                getScrollbarHeight(),
                entries.size(),
                getVisibleRows());
    }

    private static int getLeft(int screenWidth) {
        return (screenWidth - IMAGE_WIDTH) / 2;
    }

    private static int getTop(int screenHeight) {
        return (screenHeight - IMAGE_HEIGHT) / 2;
    }

    private static int getListX(int left) {
        return left + CONTENT_X;
    }

    private static int getListY(int top) {
        return top + LIST_Y;
    }

    private static int getListWidth(boolean hasScrollbar) {
        return IMAGE_WIDTH - CONTENT_X * 2 - (hasScrollbar ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
    }

    private static int getScrollbarX(int left) {
        return left + IMAGE_WIDTH - CONTENT_X - SCROLLBAR_WIDTH;
    }

    private static int getScrollbarHeight() {
        return getVisibleRows() * ROW_HEIGHT - (ROW_HEIGHT - BUTTON_HEIGHT);
    }

    private static int getVisibleRows() {
        return Math.max(1, (IMAGE_HEIGHT - BOTTOM_PADDING - LIST_Y + ROW_HEIGHT - BUTTON_HEIGHT) / ROW_HEIGHT);
    }

    private static Component targetLine(PatternQuickUploadSelectionMenu.Entry entry) {
        if (!entry.showPosition()) {
            return Component.translatable(
                    "label.gtlcore.pattern_quick_upload_target_without_position",
                    entry.targetName(),
                    Component.literal(entry.levelKey().location().toString()));
        }
        return Component.translatable(
                "label.gtlcore.pattern_quick_upload_target",
                entry.targetName(),
                Component.literal(entry.levelKey().location().toString()),
                positionLine(entry.bufferPos()));
    }

    private static Component recipeTypeLine(PatternQuickUploadSelectionMenu.Entry entry) {
        return Component.translatable("label.gtlcore.pattern_quick_upload_recipe_type", entry.recipeTypeName());
    }

    private static Component positionLine(BlockPos pos) {
        return Component.translatable(
                "label.gtlcore.pattern_quick_upload_position",
                pos.getX(),
                pos.getY(),
                pos.getZ());
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
