package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.NotNull;

public class WirelessNetworkCoreScreen extends AbstractContainerScreen<WirelessNetworkCoreMenu> {

    private static final int CONTENT_X = 14;
    private static final int TITLE_Y = 8;
    private static final int STATUS_Y = 30;
    private static final int FIELD_LABEL_Y = 49;
    private static final int FIELD_Y = 60;
    private static final int BUTTON_Y = 91;

    private EditBox nameField;

    public WirelessNetworkCoreScreen(WirelessNetworkCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 128;
    }

    @Override
    protected void init() {
        super.init();
        this.nameField = new EditBox(
                this.font,
                this.leftPos + CONTENT_X + 8,
                this.topPos + FIELD_Y + 5,
                this.imageWidth - CONTENT_X * 2 - 16,
                10,
                Component.translatable("field.gtlcore.wireless_core.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setBordered(false);
        this.nameField.setTextColor(0xFFFFFFFF);
        this.nameField.setTextColorUneditable(0xFFAAAAAA);
        this.nameField.setValue(this.menu.getNetworkName());
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(WirelessAeStyle.button(
                this.leftPos + 42,
                this.topPos + BUTTON_Y,
                this.imageWidth - 84,
                Component.translatable("button.gtlcore.wireless_core.save"),
                button -> {
                    WirelessAePackets.CHANNEL.sendToServer(
                            new WirelessAePackets.RenameNetworkPacket(this.menu.getPos(), this.nameField.getValue()));
                    this.onClose();
                }));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.nameField.tick();
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
                58);
        WirelessAeStyle.drawTextField(
                graphics,
                this.leftPos + CONTENT_X,
                this.topPos + FIELD_Y,
                this.imageWidth - CONTENT_X * 2);
        WirelessAeStyle.drawSeparator(graphics, this.leftPos + 12, this.topPos + 84, this.imageWidth - 24);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, CONTENT_X, TITLE_Y, WirelessAeStyle.TEXT, false);
        WirelessAeStyle.drawStatusLight(graphics, CONTENT_X, STATUS_Y - 1, this.menu.isLinkedToAeNetwork());
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                Component.translatable(this.menu.isLinkedToAeNetwork() ? "label.gtlcore.wireless_core.ae_connected" : "label.gtlcore.wireless_core.ae_disconnected"),
                CONTENT_X + 14,
                STATUS_Y,
                this.imageWidth - CONTENT_X * 2 - 14,
                this.menu.isLinkedToAeNetwork() ? WirelessAeStyle.ONLINE_TEXT : WirelessAeStyle.WARNING_TEXT);
        graphics.drawString(
                this.font,
                Component.translatable("field.gtlcore.wireless_core.name"),
                CONTENT_X,
                FIELD_LABEL_Y,
                WirelessAeStyle.MUTED_TEXT,
                false);
    }
}
