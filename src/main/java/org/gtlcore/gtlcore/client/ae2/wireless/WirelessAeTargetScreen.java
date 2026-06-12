package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeTargetMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WirelessAeTargetScreen extends AbstractContainerScreen<WirelessAeTargetMenu> {

    private static final int IMAGE_WIDTH = 248;
    private static final int IMAGE_HEIGHT = 196;
    private static final int CONTENT_X = 14;
    private static final int TITLE_Y = 8;
    private static final int STATUS_Y = 31;
    private static final int LIST_Y = 54;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int DISCONNECT_GAP = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;

    private final WirelessAeTargetMenu.Entry connectedEntry;
    private final WirelessAeTargetMenu.Entry disconnectableEntry;
    private final boolean lockedByCableConnection;
    private int scrollOffset;
    private boolean draggingScrollbar;

    public WirelessAeTargetScreen(WirelessAeTargetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.connectedEntry = findConnectedEntry();
        this.disconnectableEntry = findDisconnectableEntry();
        this.lockedByCableConnection = this.connectedEntry != null && this.disconnectableEntry == null;
    }

    @Override
    protected void init() {
        super.init();
        clampScrollOffset();
        this.addRenderableWidget(WirelessAeStyle.sideTab(
                this.leftPos - 28,
                this.topPos + 8,
                Component.translatable("tooltip.gtlcore.wireless_target.back"),
                button -> WirelessAePackets.CHANNEL.sendToServer(
                        new WirelessAePackets.OpenNormalTargetMenuPacket(this.menu.getOriginPos()))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WirelessAeStyle.drawPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        WirelessAeStyle.drawInsetPanel(
                graphics,
                this.leftPos + 10,
                this.topPos + 26,
                this.imageWidth - 20,
                this.imageHeight - 38);
        drawNetworkRows(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, CONTENT_X, TITLE_Y, WirelessAeStyle.TEXT, false);
        WirelessAeTargetMenu.Entry connectedEntry = getConnectedEntry();
        WirelessAeStyle.drawStatusLight(graphics, CONTENT_X, STATUS_Y - 1, connectedEntry != null);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                connectedEntry == null ? Component.translatable("label.gtlcore.wireless_target.current_disconnected") : Component.translatable("label.gtlcore.wireless_target.current_connected",
                        connectedEntry.name()),
                CONTENT_X + 14,
                STATUS_Y,
                this.imageWidth - CONTENT_X * 2 - 14,
                connectedEntry == null ? WirelessAeStyle.MUTED_TEXT : WirelessAeStyle.ONLINE_TEXT);
        if (this.menu.getNetworks().isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("label.gtlcore.wireless_target.no_networks"),
                    CONTENT_X,
                    LIST_Y + 6,
                    this.imageWidth - CONTENT_X * 2,
                    WirelessAeStyle.WARNING_TEXT);
        }
    }

    private WirelessAeTargetMenu.Entry getConnectedEntry() {
        return this.connectedEntry;
    }

    private WirelessAeTargetMenu.Entry getDisconnectableEntry() {
        return this.disconnectableEntry;
    }

    private WirelessAeTargetMenu.Entry findConnectedEntry() {
        for (WirelessAeTargetMenu.Entry entry : this.menu.getNetworks()) {
            if (entry.connected()) {
                return entry;
            }
        }
        return null;
    }

    private WirelessAeTargetMenu.Entry findDisconnectableEntry() {
        for (WirelessAeTargetMenu.Entry entry : this.menu.getNetworks()) {
            if (entry.disconnectable()) {
                return entry;
            }
        }
        return null;
    }

    private boolean isLockedByCableConnection() {
        return this.lockedByCableConnection;
    }

    private void drawNetworkRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleRows = getVisibleRows();
        int totalRows = this.menu.getNetworks().size();
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(this.scrollOffset, totalRows, visibleRows);
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(totalRows, visibleRows);
        int listX = getListX();
        int listY = getListY();
        int listWidth = getListWidth(hasScrollbar);
        boolean lockedByCableConnection = isLockedByCableConnection();

        int rows = Math.min(visibleRows, Math.max(0, totalRows - this.scrollOffset));
        for (int i = 0; i < rows; i++) {
            WirelessAeTargetMenu.Entry entry = this.menu.getNetworks().get(this.scrollOffset + i);
            int rowY = listY + i * ROW_HEIGHT;
            boolean hovered = !lockedByCableConnection && !entry.connected() &&
                    isInsideRect(mouseX, mouseY, listX, rowY, listWidth, BUTTON_HEIGHT);
            WirelessAeStyle.drawButtonBackground(graphics, listX, rowY, listWidth, BUTTON_HEIGHT,
                    true, entry.connected(), false, hovered);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.literal(entry.name()),
                    listX + 10,
                    rowY + 5,
                    Math.max(8, listWidth - 20 - (entry.connected() ? 24 : 0)),
                    WirelessAeStyle.TEXT);
        }

        if (hasScrollbar) {
            WirelessAeStyle.drawScrollbar(
                    graphics,
                    getScrollbarX(),
                    listY,
                    getScrollbarHeight(),
                    totalRows,
                    visibleRows,
                    this.scrollOffset);
        }

        WirelessAeTargetMenu.Entry disconnectableEntry = getDisconnectableEntry();
        if (disconnectableEntry != null) {
            int disconnectY = getDisconnectY();
            WirelessAeStyle.drawSeparator(graphics, this.leftPos + 16, disconnectY - 5, this.imageWidth - 32);
            boolean hovered = isInsideRect(mouseX, mouseY, listX, disconnectY, getFullContentWidth(), BUTTON_HEIGHT);
            WirelessAeStyle.drawButtonBackground(graphics, listX, disconnectY, getFullContentWidth(), BUTTON_HEIGHT,
                    true, false, true, hovered);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("button.gtlcore.wireless_target.disconnect"),
                    listX + 8,
                    disconnectY + 5,
                    getFullContentWidth() - 16,
                    WirelessAeStyle.WARNING_TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleNetworkClick(mouseX, mouseY)) {
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
        int totalRows = this.menu.getNetworks().size();
        if (!WirelessAeStyle.needsScrollbar(totalRows, visibleRows) ||
                !isInsideRect(mouseX, mouseY, getScrollbarX(), getListY(), SCROLLBAR_WIDTH, getScrollbarHeight())) {
            return false;
        }
        this.draggingScrollbar = true;
        updateScrollOffsetFromMouse(mouseY);
        return true;
    }

    private boolean handleNetworkClick(double mouseX, double mouseY) {
        int listX = getListX();
        int listY = getListY();
        int visibleRows = getVisibleRows();
        boolean hasScrollbar = WirelessAeStyle.needsScrollbar(this.menu.getNetworks().size(), visibleRows);
        int listWidth = getListWidth(hasScrollbar);
        if (isInsideRect(mouseX, mouseY, listX, listY, listWidth, visibleRows * ROW_HEIGHT)) {
            double relativeY = mouseY - listY;
            if ((int) relativeY % ROW_HEIGHT >= BUTTON_HEIGHT) {
                return true;
            }
            int index = this.scrollOffset + (int) (relativeY / ROW_HEIGHT);
            if (index >= 0 && index < this.menu.getNetworks().size() && !isLockedByCableConnection()) {
                connectEntry(this.menu.getNetworks().get(index));
            }
            return true;
        }

        WirelessAeTargetMenu.Entry disconnectableEntry = getDisconnectableEntry();
        if (disconnectableEntry != null && isInsideRect(mouseX, mouseY, listX, getDisconnectY(), getFullContentWidth(), BUTTON_HEIGHT)) {
            disconnectEntry(disconnectableEntry);
            return true;
        }
        return false;
    }

    private void connectEntry(WirelessAeTargetMenu.Entry entry) {
        if (entry.connected()) {
            return;
        }
        WirelessAePackets.CHANNEL.sendToServer(
                new WirelessAePackets.ConnectTargetPacket(
                        this.menu.getTargetPos(),
                        this.menu.getTargetSide(),
                        null,
                        entry.frequency(),
                        false));
        this.onClose();
    }

    private void disconnectEntry(WirelessAeTargetMenu.Entry entry) {
        WirelessAePackets.CHANNEL.sendToServer(
                new WirelessAePackets.ConnectTargetPacket(
                        this.menu.getTargetPos(),
                        this.menu.getTargetSide(),
                        null,
                        entry.frequency(),
                        true));
        this.onClose();
    }

    private boolean scrollBy(double delta) {
        int visibleRows = getVisibleRows();
        int totalRows = this.menu.getNetworks().size();
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
        this.scrollOffset = WirelessAeStyle.scrollbarOffsetFromMouse(
                mouseY,
                getListY(),
                getScrollbarHeight(),
                this.menu.getNetworks().size(),
                getVisibleRows());
    }

    private void clampScrollOffset() {
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset,
                this.menu.getNetworks().size(),
                getVisibleRows());
    }

    private int getListX() {
        return this.leftPos + CONTENT_X;
    }

    private int getListY() {
        return this.topPos + LIST_Y;
    }

    private int getFullContentWidth() {
        return this.imageWidth - CONTENT_X * 2;
    }

    private int getListWidth(boolean hasScrollbar) {
        return getFullContentWidth() - (hasScrollbar ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
    }

    private int getScrollbarX() {
        return this.leftPos + this.imageWidth - CONTENT_X - SCROLLBAR_WIDTH;
    }

    private int getScrollbarHeight() {
        return getVisibleRows() * ROW_HEIGHT - 4;
    }

    private int getVisibleRows() {
        return Math.max(1, (getListBottomY() - getListY()) / ROW_HEIGHT);
    }

    private int getListBottomY() {
        return getDisconnectableEntry() == null ? this.topPos + this.imageHeight - 16 : getDisconnectY() - DISCONNECT_GAP;
    }

    private int getDisconnectY() {
        return this.topPos + this.imageHeight - 30;
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
