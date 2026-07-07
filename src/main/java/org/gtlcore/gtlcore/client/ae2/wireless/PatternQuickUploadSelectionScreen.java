package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadSelectionMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PatternQuickUploadSelectionScreen extends AbstractContainerScreen<PatternQuickUploadSelectionMenu> {

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

    private int scrollOffset;
    private boolean draggingScrollbar;

    public PatternQuickUploadSelectionScreen(PatternQuickUploadSelectionMenu menu, Inventory inventory,
                                             Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        clampScrollOffset();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WirelessAeStyle.drawInsetPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        drawTargetRows(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, CONTENT_X, TITLE_Y, WirelessAeStyle.TEXT, false);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                Component.translatable("label.gtlcore.pattern_quick_upload_pattern", this.menu.getPatternStack().getHoverName()),
                CONTENT_X,
                PATTERN_Y,
                this.imageWidth - CONTENT_X * 2,
                WirelessAeStyle.MUTED_TEXT);

        if (this.menu.getEntries().isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("label.gtlcore.pattern_quick_upload_no_targets"),
                    CONTENT_X,
                    LIST_Y + 4,
                    this.imageWidth - CONTENT_X * 2,
                    WirelessAeStyle.WARNING_TEXT);
        }
    }

    private void drawTargetRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<PatternQuickUploadSelectionMenu.Entry> entries = this.menu.getEntries();
        int visibleRows = getVisibleRows();
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(this.scrollOffset, entries.size(), visibleRows);
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(entries.size(), visibleRows);
        int listX = getListX();
        int listY = getListY();
        int listWidth = getListWidth(hasScrollbar);

        int rows = Math.min(visibleRows, Math.max(0, entries.size() - this.scrollOffset));
        for (int row = 0; row < rows; row++) {
            PatternQuickUploadSelectionMenu.Entry entry = entries.get(this.scrollOffset + row);
            int rowY = listY + row * ROW_HEIGHT;
            boolean hovered = isInsideRect(mouseX, mouseY, listX, rowY, listWidth, BUTTON_HEIGHT);
            WirelessAeStyle.drawButtonBackground(graphics, listX, rowY, listWidth, BUTTON_HEIGHT,
                    true, false, false, hovered);

            int textX = listX + ROW_TEXT_INSET;
            int titleY = rowY + ROW_TEXT_GAP;
            int recipeY = titleY + this.font.lineHeight + ROW_TEXT_GAP;
            int textWidth = Math.max(8, listWidth - ROW_TEXT_INSET * 2);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    targetLine(entry),
                    textX,
                    titleY,
                    textWidth,
                    WirelessAeStyle.TEXT);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    recipeTypeLine(entry),
                    textX,
                    recipeY,
                    textWidth,
                    WirelessAeStyle.MUTED_TEXT);
        }

        if (hasScrollbar) {
            WirelessAeStyle.drawAe2Scrollbar(
                    graphics,
                    getScrollbarX(),
                    listY,
                    getScrollbarHeight(),
                    entries.size(),
                    visibleRows,
                    this.scrollOffset);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleTargetClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            updateScrollOffsetFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInsideRect(mouseX, mouseY, getListX(), getListY(), this.imageWidth - CONTENT_X * 2, getScrollbarHeight()) &&
                scrollBy(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY) {
        int visibleRows = getVisibleRows();
        int totalRows = this.menu.getEntries().size();
        if (!WirelessAeStyle.needsScrollbar(totalRows, visibleRows) ||
                !isInsideRect(mouseX, mouseY, getScrollbarX(), getListY(), SCROLLBAR_WIDTH, getScrollbarHeight())) {
            return false;
        }
        this.draggingScrollbar = true;
        updateScrollOffsetFromMouse(mouseY);
        return true;
    }

    private boolean handleTargetClick(double mouseX, double mouseY) {
        List<PatternQuickUploadSelectionMenu.Entry> entries = this.menu.getEntries();
        int visibleRows = getVisibleRows();
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(entries.size(), visibleRows);
        int listX = getListX();
        int listY = getListY();
        int listWidth = getListWidth(hasScrollbar);
        if (!isInsideRect(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) {
            return false;
        }

        double relativeY = mouseY - listY;
        if ((int) relativeY % ROW_HEIGHT >= BUTTON_HEIGHT) {
            return true;
        }
        int index = this.scrollOffset + (int) (relativeY / ROW_HEIGHT);
        if (index >= 0 && index < entries.size()) {
            WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SelectPatternQuickUploadTargetPacket(index));
            this.onClose();
        }
        return true;
    }

    private boolean scrollBy(double delta) {
        int visibleRows = getVisibleRows();
        int totalRows = this.menu.getEntries().size();
        int nextOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset - (int) Math.signum(delta),
                totalRows,
                visibleRows);
        if (nextOffset == this.scrollOffset) {
            return false;
        }
        this.scrollOffset = nextOffset;
        return true;
    }

    private void updateScrollOffsetFromMouse(double mouseY) {
        this.scrollOffset = WirelessAeStyle.ae2ScrollbarOffsetFromMouse(
                mouseY,
                getListY(),
                getScrollbarHeight(),
                this.menu.getEntries().size(),
                getVisibleRows());
    }

    private void clampScrollOffset() {
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset,
                this.menu.getEntries().size(),
                getVisibleRows());
    }

    private int getListX() {
        return this.leftPos + CONTENT_X;
    }

    private int getListY() {
        return this.topPos + LIST_Y;
    }

    private int getListWidth(boolean hasScrollbar) {
        return this.imageWidth - CONTENT_X * 2 - (hasScrollbar ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
    }

    private int getScrollbarX() {
        return this.leftPos + this.imageWidth - CONTENT_X - SCROLLBAR_WIDTH;
    }

    private int getScrollbarHeight() {
        return getVisibleRows() * ROW_HEIGHT - (ROW_HEIGHT - BUTTON_HEIGHT);
    }

    private int getVisibleRows() {
        return Math.max(1, (this.topPos + this.imageHeight - BOTTOM_PADDING - getListY() +
                ROW_HEIGHT - BUTTON_HEIGHT) / ROW_HEIGHT);
    }

    private static Component targetLine(PatternQuickUploadSelectionMenu.Entry entry) {
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
