package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkBookmarkMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class WirelessNetworkBookmarkScreen extends AbstractContainerScreen<WirelessNetworkBookmarkMenu> {

    private static final int CONTENT_X = 14;
    private static final int TITLE_Y = 8;
    private static final int STATUS_Y = 30;
    private static final int LIST_Y = 54;
    private static final int ROW_HEIGHT = 24;

    public WirelessNetworkBookmarkScreen(WirelessNetworkBookmarkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        int rows = Math.max(1, menu.getNetworks().size());
        this.imageHeight = Math.max(128, LIST_Y + rows * ROW_HEIGHT + 16);
    }

    @Override
    protected void init() {
        super.init();
        int y = this.topPos + LIST_Y;
        UUID favoriteNetwork = this.menu.getFavoriteNetwork();
        for (WirelessNetworkBookmarkMenu.Entry entry : this.menu.getNetworks()) {
            boolean selected = entry.frequency().equals(favoriteNetwork);
            this.addRenderableWidget(WirelessAeStyle.networkButton(
                    this.leftPos + CONTENT_X,
                    y,
                    this.imageWidth - CONTENT_X * 2,
                    20,
                    Component.literal(entry.name()),
                    selected,
                    button -> {
                        WirelessAePackets.CHANNEL.sendToServer(
                                new WirelessAePackets.SetFavoriteNetworkPacket(this.menu.getPos(), entry.frequency()));
                        this.onClose();
                    }));
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WirelessAeStyle.drawPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        WirelessAeStyle.drawInsetPanel(
                graphics,
                this.leftPos + 10,
                this.topPos + 24,
                this.imageWidth - 20,
                this.imageHeight - 34);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, CONTENT_X, TITLE_Y, WirelessAeStyle.TEXT, false);
        UUID favoriteNetwork = this.menu.getFavoriteNetwork();
        String favoriteName = getFavoriteName(favoriteNetwork);
        WirelessAeStyle.drawStatusLight(graphics, CONTENT_X, STATUS_Y - 1, favoriteNetwork != null);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                favoriteName == null ? Component.translatable("label.gtlcore.wireless_bookmark.no_favorite") : Component.translatable("label.gtlcore.wireless_bookmark.favorite", favoriteName),
                CONTENT_X + 14,
                STATUS_Y,
                this.imageWidth - CONTENT_X * 2 - 14,
                favoriteName == null ? WirelessAeStyle.MUTED_TEXT : WirelessAeStyle.ONLINE_TEXT);

        if (this.menu.getNetworks().isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("label.gtlcore.wireless_bookmark.no_networks"),
                    CONTENT_X,
                    LIST_Y + 4,
                    this.imageWidth - CONTENT_X * 2,
                    WirelessAeStyle.WARNING_TEXT);
        }
    }

    private String getFavoriteName(UUID favoriteNetwork) {
        if (favoriteNetwork == null) {
            return null;
        }
        for (WirelessNetworkBookmarkMenu.Entry entry : this.menu.getNetworks()) {
            if (entry.frequency().equals(favoriteNetwork)) {
                return entry.name();
            }
        }
        return null;
    }
}
