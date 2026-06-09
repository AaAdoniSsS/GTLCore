package org.gtlcore.gtlcore.client.ae2.wireless;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

final class WirelessAeStyle {

    static final int TEXT = 0xFF303030;
    static final int MUTED_TEXT = 0xFF5A5A5A;
    static final int WARNING_TEXT = 0xFFA33A2A;
    static final int ONLINE_TEXT = 0xFF245F68;

    private static final ResourceLocation AE2_BACKGROUND = new ResourceLocation("ae2", "textures/guis/background.png");
    private static final ResourceLocation INSET_PANEL = wirelessTexture("inset_panel.png");
    private static final ResourceLocation TEXT_FIELD = wirelessTexture("text_field.png");
    private static final ResourceLocation SEPARATOR = wirelessTexture("separator.png");
    private static final ResourceLocation STATUS_ONLINE = wirelessTexture("status_online.png");
    private static final ResourceLocation STATUS_OFFLINE = wirelessTexture("status_offline.png");
    private static final ResourceLocation BUTTON_NORMAL = wirelessTexture("button_normal.png");
    private static final ResourceLocation BUTTON_HOVER = wirelessTexture("button_hover.png");
    private static final ResourceLocation BUTTON_SELECTED = wirelessTexture("button_selected.png");
    private static final ResourceLocation BUTTON_WARNING = wirelessTexture("button_warning.png");
    private static final ResourceLocation BUTTON_DISABLED = wirelessTexture("button_disabled.png");
    private static final ResourceLocation TAB_WIRELESS_NORMAL = wirelessTexture("tab_wireless_normal.png");
    private static final ResourceLocation TAB_WIRELESS_HOVER = wirelessTexture("tab_wireless_hover.png");
    private static final ResourceLocation TAB_WIRELESS_SELECTED = wirelessTexture("tab_wireless_selected.png");
    private static final int PANEL_TEXTURE_SIZE = 256;
    private static final int BUTTON_TEXTURE_WIDTH = 80;
    private static final int BUTTON_TEXTURE_HEIGHT = 20;
    private static final int BUTTON_BORDER = 4;
    private static final int RIGHT_ICON_FRAME_RIGHT_BORDER = 3;
    private static final int SELECTED_CHECK_X = 64;
    private static final int RIGHT_ICON_FRAME_CENTER_WIDTH = SELECTED_CHECK_X - BUTTON_BORDER;
    private static final int SELECTED_CHECK_Y = 5;
    private static final int SELECTED_CHECK_WIDTH = 10;
    private static final int SELECTED_CHECK_HEIGHT = 9;
    private static final int DISABLED_CROSS_X = 69;
    private static final int DISABLED_CROSS_Y = 6;
    private static final int DISABLED_CROSS_WIDTH = 8;
    private static final int DISABLED_CROSS_HEIGHT = 8;
    private static final int WARNING_MARK_X = 4;
    private static final int WARNING_MARK_Y = 4;
    private static final int WARNING_MARK_WIDTH = 4;
    private static final int WARNING_MARK_HEIGHT = 12;

    private WirelessAeStyle() {}

    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(AE2_BACKGROUND, x, y, 0.0F, 0.0F, width, height, PANEL_TEXTURE_SIZE, PANEL_TEXTURE_SIZE);
        graphics.fill(x, y, x + width, y + 1, 0xFFEEEEEE);
        graphics.fill(x, y, x + 1, y + height, 0xFFEEEEEE);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF8A8A8A);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF8A8A8A);
    }

    static void drawInsetPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawNineSlice(graphics, INSET_PANEL, x, y, width, height, 120, 52, 4);
    }

    static void drawTextField(GuiGraphics graphics, int x, int y, int width) {
        drawNineSlice(graphics, TEXT_FIELD, x, y, width, 20, 120, 20, 3);
    }

    static void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        drawTiledRegion(graphics, SEPARATOR, x, y, width, 2, 0, 0, 120, 2, 120, 2);
    }

    static void drawStatusLight(GuiGraphics graphics, int x, int y, boolean online) {
        graphics.blit(online ? STATUS_ONLINE : STATUS_OFFLINE, x, y, 0.0F, 0.0F, 9, 9, 9, 9);
    }

    static void drawTrimmedString(GuiGraphics graphics, Font font, Component component, int x, int y, int width,
                                  int color) {
        String text = trimToWidth(font, component.getString(), width);
        graphics.drawString(font, text, x, y, color, false);
    }

    static Button button(int x, int y, int width, Component message, Button.OnPress onPress) {
        return new Ae2Button(x, y, width, 20, message, onPress, Accent.NONE);
    }

    static Button selectedButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        return new Ae2Button(x, y, width, height, message, onPress, Accent.SELECTED);
    }

    static Button warningButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        return new Ae2Button(x, y, width, height, message, onPress, Accent.WARNING);
    }

    static Button networkButton(int x, int y, int width, int height, Component name, boolean connected,
                                Button.OnPress onPress) {
        return new Ae2NetworkButton(x, y, width, height, name, connected, onPress);
    }

    static Button sideTab(int x, int y, Component tooltip, Button.OnPress onPress) {
        Button button = new Ae2SideTab(x, y, tooltip, TabIcon.BACK, false, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    static void drawButtonBackground(GuiGraphics graphics, int x, int y, int width, int height,
                                     boolean active, boolean selected, boolean warning, boolean hovered) {
        ResourceLocation texture;
        if (!active) {
            drawRightIconButtonBackground(graphics, BUTTON_DISABLED, x, y, width, height,
                    DISABLED_CROSS_X, DISABLED_CROSS_Y, DISABLED_CROSS_WIDTH, DISABLED_CROSS_HEIGHT);
            return;
        } else if (warning) {
            drawLeftIconButtonBackground(graphics, x, y, width, height);
            return;
        } else if (selected) {
            drawRightIconButtonBackground(graphics, BUTTON_SELECTED, x, y, width, height,
                    SELECTED_CHECK_X, SELECTED_CHECK_Y, SELECTED_CHECK_WIDTH, SELECTED_CHECK_HEIGHT);
            return;
        } else if (hovered) {
            texture = BUTTON_HOVER;
        } else {
            texture = BUTTON_NORMAL;
        }
        drawNineSlice(graphics, texture, x, y, width, height, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT,
                BUTTON_BORDER);
    }

    private static ResourceLocation wirelessTexture(String name) {
        return new ResourceLocation("gtlcore", "textures/gui/wireless/" + name);
    }

    private static String trimToWidth(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        if (width <= ellipsisWidth) {
            return font.plainSubstrByWidth(text, Math.max(0, width));
        }
        return font.plainSubstrByWidth(text, width - ellipsisWidth) + "...";
    }

    private static void drawNineSlice(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                      int width, int height, int textureWidth, int textureHeight, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int left = Math.min(border, width / 2);
        int right = Math.min(border, width - left);
        int top = Math.min(border, height / 2);
        int bottom = Math.min(border, height - top);
        int centerSourceWidth = Math.max(1, textureWidth - border * 2);
        int centerSourceHeight = Math.max(1, textureHeight - border * 2);

        blitRegion(graphics, texture, x, y, 0, 0, left, top, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + width - right, y, textureWidth - right, 0, right, top,
                textureWidth, textureHeight);
        blitRegion(graphics, texture, x, y + height - bottom, 0, textureHeight - bottom, left, bottom,
                textureWidth, textureHeight);
        blitRegion(graphics, texture, x + width - right, y + height - bottom,
                textureWidth - right, textureHeight - bottom, right, bottom, textureWidth, textureHeight);

        drawTiledRegion(graphics, texture, x + left, y, width - left - right, top,
                border, 0, centerSourceWidth, top, textureWidth, textureHeight);
        drawTiledRegion(graphics, texture, x + left, y + height - bottom, width - left - right, bottom,
                border, textureHeight - bottom, centerSourceWidth, bottom, textureWidth, textureHeight);
        drawTiledRegion(graphics, texture, x, y + top, left, height - top - bottom,
                0, border, left, centerSourceHeight, textureWidth, textureHeight);
        drawTiledRegion(graphics, texture, x + width - right, y + top, right, height - top - bottom,
                textureWidth - right, border, right, centerSourceHeight, textureWidth, textureHeight);
        drawTiledRegion(graphics, texture, x + left, y + top, width - left - right, height - top - bottom,
                border, border, centerSourceWidth, centerSourceHeight, textureWidth, textureHeight);
    }

    private static void drawTiledRegion(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                        int width, int height, int sourceX, int sourceY, int sourceWidth,
                                        int sourceHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }

        int drawnY = 0;
        while (drawnY < height) {
            int tileHeight = Math.min(sourceHeight, height - drawnY);
            int drawnX = 0;
            while (drawnX < width) {
                int tileWidth = Math.min(sourceWidth, width - drawnX);
                blitRegion(graphics, texture, x + drawnX, y + drawnY, sourceX, sourceY,
                        tileWidth, tileHeight, textureWidth, textureHeight);
                drawnX += tileWidth;
            }
            drawnY += tileHeight;
        }
    }

    private static void blitRegion(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                   int sourceX, int sourceY, int width, int height,
                                   int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture, x, y, (float) sourceX, (float) sourceY, width, height, textureWidth, textureHeight);
    }

    private static void drawRightIconButtonBackground(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                                      int width, int height, int iconX, int iconY, int iconWidth,
                                                      int iconHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }

        drawButtonFrame(
                graphics,
                texture,
                x,
                y,
                width,
                height,
                RIGHT_ICON_FRAME_RIGHT_BORDER,
                BUTTON_BORDER,
                RIGHT_ICON_FRAME_CENTER_WIDTH);

        int drawX = x + width - (BUTTON_TEXTURE_WIDTH - iconX);
        int drawY = y + Math.max(0, (height - BUTTON_TEXTURE_HEIGHT) / 2) + iconY;
        blitRegion(graphics, texture, drawX, drawY, iconX, iconY, iconWidth, iconHeight,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
    }

    private static void drawLeftIconButtonBackground(GuiGraphics graphics, int x, int y,
                                                     int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int frameCenterSourceX = WirelessAeStyle.WARNING_MARK_X + WirelessAeStyle.WARNING_MARK_WIDTH;
        int frameCenterSourceWidth = BUTTON_TEXTURE_WIDTH - BUTTON_BORDER - frameCenterSourceX;
        drawButtonFrame(
                graphics,
                WirelessAeStyle.BUTTON_WARNING,
                x,
                y,
                width,
                height,
                BUTTON_BORDER,
                frameCenterSourceX,
                frameCenterSourceWidth);

        int drawY = y + Math.max(0, (height - BUTTON_TEXTURE_HEIGHT) / 2) + WirelessAeStyle.WARNING_MARK_Y;
        blitRegion(graphics, WirelessAeStyle.BUTTON_WARNING, x + WirelessAeStyle.WARNING_MARK_X, drawY, WirelessAeStyle.WARNING_MARK_X, WirelessAeStyle.WARNING_MARK_Y, WirelessAeStyle.WARNING_MARK_WIDTH, WirelessAeStyle.WARNING_MARK_HEIGHT,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
    }

    private static void drawButtonFrame(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width,
                                        int height, int preferredRight, int centerSourceX,
                                        int centerSourceWidth) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int left = Math.min(WirelessAeStyle.BUTTON_BORDER, width / 2);
        int right = Math.min(preferredRight, width - left);
        int top = Math.min(BUTTON_BORDER, height / 2);
        int bottom = Math.min(BUTTON_BORDER, height - top);
        int centerSourceHeight = BUTTON_TEXTURE_HEIGHT - BUTTON_BORDER * 2;

        blitRegion(graphics, texture, x, y, 0, 0, left, top, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        blitRegion(graphics, texture, x + width - right, y, BUTTON_TEXTURE_WIDTH - right, 0, right, top,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        blitRegion(graphics, texture, x, y + height - bottom, 0, BUTTON_TEXTURE_HEIGHT - bottom, left, bottom,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        blitRegion(graphics, texture, x + width - right, y + height - bottom,
                BUTTON_TEXTURE_WIDTH - right, BUTTON_TEXTURE_HEIGHT - bottom, right, bottom, BUTTON_TEXTURE_WIDTH,
                BUTTON_TEXTURE_HEIGHT);

        drawTiledRegion(graphics, texture, x + left, y, width - left - right, top,
                centerSourceX, 0, centerSourceWidth, top, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        drawTiledRegion(graphics, texture, x + left, y + height - bottom,
                width - left - right, bottom, centerSourceX, BUTTON_TEXTURE_HEIGHT - bottom, centerSourceWidth,
                bottom, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        drawTiledRegion(graphics, texture, x, y + top, left, height - top - bottom,
                0, BUTTON_BORDER, left, centerSourceHeight, BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
        drawTiledRegion(graphics, texture, x + width - right, y + top, right, height - top - bottom,
                BUTTON_TEXTURE_WIDTH - right, BUTTON_BORDER, right, centerSourceHeight, BUTTON_TEXTURE_WIDTH,
                BUTTON_TEXTURE_HEIGHT);
        drawTiledRegion(graphics, texture, x + left, y + top, width - left - right,
                height - top - bottom, centerSourceX, BUTTON_BORDER, centerSourceWidth, centerSourceHeight,
                BUTTON_TEXTURE_WIDTH, BUTTON_TEXTURE_HEIGHT);
    }

    private enum Accent {
        NONE,
        SELECTED,
        WARNING
    }

    enum TabIcon {
        WIRELESS,
        BACK
    }

    private static final class Ae2Button extends Button {

        private final Accent accent;

        private Ae2Button(int x, int y, int width, int height, Component message, OnPress onPress, Accent accent) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.accent = accent;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            boolean hovered = this.isHoveredOrFocused();

            drawButtonBackground(
                    graphics,
                    x,
                    y,
                    width,
                    height,
                    this.active,
                    this.accent == Accent.SELECTED,
                    this.accent == Accent.WARNING,
                    hovered);

            Font font = Minecraft.getInstance().font;
            int textColor = this.active ? 0xFF202020 : 0xFF6F6F6F;
            String text = trimToWidth(font, this.getMessage().getString(), width - 12);
            graphics.drawString(
                    font,
                    text,
                    x + (width - font.width(text)) / 2,
                    y + (height - 8) / 2,
                    textColor,
                    false);
        }
    }

    private static final class Ae2NetworkButton extends Button {

        private final Component name;
        private final boolean connected;

        private Ae2NetworkButton(int x, int y, int width, int height, Component name, boolean connected,
                                 OnPress onPress) {
            super(x, y, width, height, name, onPress, DEFAULT_NARRATION);
            this.name = name;
            this.connected = connected;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            drawButtonBackground(graphics, x, y, width, height, this.active, this.connected, false,
                    this.isHoveredOrFocused());

            Font font = Minecraft.getInstance().font;
            int checkSpace = this.connected ? 24 : 0;
            int nameX = x + 10;
            int nameWidth = Math.max(8, width - 20 - checkSpace);
            String nameText = trimToWidth(font, this.name.getString(), nameWidth);
            int textY = y + (height - 8) / 2;

            graphics.drawString(font, nameText, nameX, textY, this.active ? TEXT : 0xFF6F6F6F, false);
        }
    }

    private static final class Ae2SideTab extends Button {

        private static final int SIZE = 24;
        private final TabIcon icon;
        private final boolean selected;

        private Ae2SideTab(int x, int y, Component tooltip, TabIcon icon, boolean selected, OnPress onPress) {
            super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.selected = selected;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            draw(graphics, this.getX(), this.getY(), this.selected, this.isHoveredOrFocused());
        }

        private static void draw(GuiGraphics graphics, int x, int y, boolean selected, boolean hovered) {
            ResourceLocation texture;
            texture = selected ? TAB_WIRELESS_SELECTED : (hovered ? TAB_WIRELESS_HOVER : TAB_WIRELESS_NORMAL);
            graphics.blit(texture, x, y, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        }
    }
}
