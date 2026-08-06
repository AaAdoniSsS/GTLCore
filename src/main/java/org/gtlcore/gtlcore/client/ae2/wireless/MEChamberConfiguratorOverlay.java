package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Hosts GTCEu/LDLib configurators as a modal layer without replacing the terminal menu. */
final class MEChamberConfiguratorOverlay {

    private static final int CONTENT_PADDING = 6;
    private static final int HEADER_HEIGHT = 22;
    private static final int CLOSE_SIZE = 16;
    private static final int CLOSE_INSET = 3;
    private static final int DIM_COLOR = 0x88000000;
    private static final float OVERLAY_Z = 400.0F;
    private static final Component CLOSE_LABEL = Component.literal("x");

    private final MEChamberManagerTerminalMenu menu;
    private @Nullable MEChamberManagerTerminalMenu.Address address;
    private @Nullable MEChamberConfigurator.Kind kind;
    private @Nullable MEChamberConfigurator.View view;
    private @Nullable ModularUIGuiContainer widgetHost;

    MEChamberConfiguratorOverlay(MEChamberManagerTerminalMenu menu) {
        this.menu = menu;
    }

    void open(MEChamberManagerTerminalMenu.Address address, MEChamberConfigurator.Kind kind,
              MEChamberManagerTerminalMenu.ChamberDetails details, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        close();
        this.address = address;
        this.kind = kind;
        this.view = MEChamberConfigurator.createClientView(kind, details);

        ModularUI modularUI = new ModularUI(view.root(), IUIHolder.EMPTY, minecraft.player);
        this.widgetHost = new ModularUIGuiContainer(modularUI, menu.containerId);
        view.root().setUiAccess(new TerminalWidgetUIAccess());
        modularUI.initWidgets();
        initializeTextFields(view.root());
        widgetHost.init(minecraft, screenWidth, screenHeight);
    }

    void close() {
        address = null;
        kind = null;
        view = null;
        widgetHost = null;
    }

    boolean isOpen() {
        return view != null && widgetHost != null;
    }

    void tick() {
        if (widgetHost != null) {
            widgetHost.containerTick();
        }
    }

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY,
                float partialTick) {
        if (view == null || widgetHost == null) {
            return;
        }

        WidgetGroup root = view.root();
        int panelLeft = root.getPositionX() - CONTENT_PADDING;
        int panelTop = root.getPositionY() - HEADER_HEIGHT;
        int panelWidth = root.getSize().width + CONTENT_PADDING * 2;
        int panelHeight = root.getSize().height + HEADER_HEIGHT + CONTENT_PADDING;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        try {
            graphics.fill(0, 0, screenWidth, screenHeight, DIM_COLOR);
            WirelessAeStyle.drawInsetPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
            graphics.drawString(
                    Minecraft.getInstance().font,
                    view.configurator().getTitle(),
                    panelLeft + CONTENT_PADDING,
                    panelTop + CONTENT_PADDING,
                    WirelessAeStyle.TEXT,
                    false);
            drawCloseButton(graphics, panelLeft, panelTop, panelWidth, mouseX, mouseY);

            widgetHost.tooltipTexts = null;
            root.drawInBackground(graphics, mouseX, mouseY, partialTick);
            root.drawInForeground(graphics, mouseX, mouseY, partialTick);
            root.drawOverlay(graphics, mouseX, mouseY, partialTick);
            if (widgetHost.tooltipTexts != null && !widgetHost.tooltipTexts.isEmpty()) {
                graphics.renderComponentTooltip(
                        Minecraft.getInstance().font, widgetHost.tooltipTexts, mouseX, mouseY);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view == null || widgetHost == null) {
            return false;
        }
        if (button == 0 && isInsideCloseButton(mouseX, mouseY, view.root())) {
            close();
            return true;
        }
        widgetHost.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (widgetHost == null) {
            return false;
        }
        widgetHost.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (widgetHost == null) {
            return false;
        }
        widgetHost.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (widgetHost == null) {
            return false;
        }
        widgetHost.mouseScrolled(mouseX, mouseY, delta);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (view == null || widgetHost == null) {
            return false;
        }
        widgetHost.focused = false;
        view.root().keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (view == null || widgetHost == null) {
            return false;
        }
        widgetHost.focused = false;
        view.root().keyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (view == null || widgetHost == null) {
            return false;
        }
        widgetHost.focused = false;
        view.root().charTyped(codePoint, modifiers);
        return true;
    }

    private void drawCloseButton(GuiGraphics graphics, int panelLeft, int panelTop, int panelWidth,
                                 int mouseX, int mouseY) {
        int closeX = panelLeft + panelWidth - CLOSE_INSET - CLOSE_SIZE;
        int closeY = panelTop + CLOSE_INSET;
        boolean hovered = isInside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        WirelessAeStyle.drawButtonBackground(
                graphics, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, true, false, false, hovered);
        var font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                CLOSE_LABEL,
                closeX + (CLOSE_SIZE - font.width(CLOSE_LABEL)) / 2,
                closeY + (CLOSE_SIZE - font.lineHeight) / 2,
                WirelessAeStyle.TEXT,
                false);
    }

    private static boolean isInsideCloseButton(double mouseX, double mouseY, WidgetGroup root) {
        int panelLeft = root.getPositionX() - CONTENT_PADDING;
        int panelTop = root.getPositionY() - HEADER_HEIGHT;
        int panelWidth = root.getSize().width + CONTENT_PADDING * 2;
        int closeX = panelLeft + panelWidth - CLOSE_INSET - CLOSE_SIZE;
        int closeY = panelTop + CLOSE_INSET;
        return isInside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void initializeTextFields(WidgetGroup root) {
        for (Widget widget : root.getContainedWidgets(false)) {
            if (widget instanceof TextFieldWidget) {
                widget.detectAndSendChanges();
            }
        }
    }

    private final class TerminalWidgetUIAccess implements WidgetUIAccess {

        @Override
        public boolean attemptMergeStack(ItemStack itemStack, boolean fromContainer, boolean simulate) {
            return false;
        }

        @Override
        public void writeClientAction(Widget widget, int actionId, Consumer<FriendlyByteBuf> payloadWriter) {
            if (address == null || kind == null) {
                return;
            }
            FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
            try {
                payloadWriter.accept(payload);
                if (payload.readableBytes() > MEChamberConfigurator.MAX_ACTION_BYTES) {
                    return;
                }
                byte[] actionData = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), actionData);
                WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.MEChamberConfiguratorActionPacket(
                        menu.containerId, address, kind, actionId, actionData));
                FriendlyByteBuf localAction = new FriendlyByteBuf(Unpooled.wrappedBuffer(actionData));
                try {
                    view.root().handleClientAction(actionId, localAction);
                    initializeTextFields(view.root());
                } finally {
                    localAction.release();
                }
            } finally {
                payload.release();
            }
        }

        @Override
        public void writeUpdateInfo(Widget widget, int id, Consumer<FriendlyByteBuf> payloadWriter) {}
    }
}
