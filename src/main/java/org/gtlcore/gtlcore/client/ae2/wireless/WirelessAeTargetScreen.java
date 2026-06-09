package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeTargetMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WirelessAeTargetScreen extends AbstractContainerScreen<WirelessAeTargetMenu> {

    private static final int CONTENT_X = 14;
    private static final int TITLE_Y = 8;
    private static final int STATUS_Y = 31;
    private static final int LIST_Y = 54;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int DISCONNECT_GAP = 8;

    public WirelessAeTargetScreen(WirelessAeTargetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 248;
        int rows = Math.max(1, menu.getNetworks().size());
        this.imageHeight = Math.max(128,
                LIST_Y + 8 + rows * ROW_HEIGHT + (getConnectedEntry() == null ? 0 : DISCONNECT_GAP + BUTTON_HEIGHT) + 14);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(WirelessAeStyle.sideTab(
                this.leftPos - 28,
                this.topPos + 8,
                Component.translatable("tooltip.gtlcore.wireless_target.back"),
                button -> WirelessAePackets.CHANNEL.sendToServer(
                        new WirelessAePackets.OpenNormalTargetMenuPacket(this.menu.getOriginPos()))));

        int y = this.topPos + LIST_Y;
        for (WirelessAeTargetMenu.Entry entry : this.menu.getNetworks()) {
            Button.OnPress onPress = button -> {
                WirelessAePackets.CHANNEL.sendToServer(
                        new WirelessAePackets.ConnectTargetPacket(
                                this.menu.getTargetPos(),
                                this.menu.getTargetSide(),
                                null,
                                entry.frequency(),
                                false));
                this.onClose();
            };
            this.addRenderableWidget(WirelessAeStyle.networkButton(
                    this.leftPos + CONTENT_X,
                    y,
                    this.imageWidth - CONTENT_X * 2,
                    BUTTON_HEIGHT,
                    Component.literal(entry.name()),
                    entry.connected(),
                    onPress));
            y += ROW_HEIGHT;
        }

        WirelessAeTargetMenu.Entry connectedEntry = getConnectedEntry();
        if (connectedEntry != null) {
            y += DISCONNECT_GAP;
            this.addRenderableWidget(WirelessAeStyle.warningButton(
                    this.leftPos + CONTENT_X,
                    y,
                    this.imageWidth - CONTENT_X * 2,
                    BUTTON_HEIGHT,
                    Component.translatable("button.gtlcore.wireless_target.disconnect"),
                    button -> {
                        WirelessAePackets.CHANNEL.sendToServer(
                                new WirelessAePackets.ConnectTargetPacket(
                                        this.menu.getTargetPos(),
                                        this.menu.getTargetSide(),
                                        null,
                                        connectedEntry.frequency(),
                                        true));
                        this.onClose();
                    }));
        }
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
        if (getConnectedEntry() != null) {
            int rows = Math.max(1, this.menu.getNetworks().size());
            WirelessAeStyle.drawSeparator(
                    graphics,
                    this.leftPos + 16,
                    this.topPos + LIST_Y + rows * ROW_HEIGHT + 3,
                    this.imageWidth - 32);
        }
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
        for (WirelessAeTargetMenu.Entry entry : this.menu.getNetworks()) {
            if (entry.connected()) {
                return entry;
            }
        }
        return null;
    }
}
