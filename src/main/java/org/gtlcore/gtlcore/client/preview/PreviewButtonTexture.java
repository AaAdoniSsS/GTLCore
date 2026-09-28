package org.gtlcore.gtlcore.client.preview;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.client.gui.GuiGraphics;

final class PreviewButtonTexture implements IGuiTexture {

    static final IGuiTexture EXPAND = new PreviewButtonTexture("expand");
    static final IGuiTexture RESTORE = new PreviewButtonTexture("restore");
    private static final IGuiTexture NORMAL = new ResourceBorderTexture(
            "jei:textures/jei/atlas/gui/button_enabled.png", 20, 20, 2, 2);
    private static final IGuiTexture HOVER = new ResourceBorderTexture(
            "jei:textures/jei/atlas/gui/button_highlight.png", 20, 20, 2, 2);
    private static final int ICON_SIZE = 16;
    private final ResourceTexture icon;

    private PreviewButtonTexture(String name) {
        icon = new ResourceTexture("gtlcore:textures/gui/preview/" + name + ".png");
    }

    @Override
    public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        var background = LDLib.isJeiLoaded() ? (hovered ? HOVER : NORMAL) : ResourceBorderTexture.BUTTON_COMMON;
        background.draw(graphics, mouseX, mouseY, x, y, width, height);
        icon.draw(graphics, mouseX, mouseY, x + (width - ICON_SIZE) / 2f, y + (height - ICON_SIZE) / 2f, ICON_SIZE, ICON_SIZE);
    }
}
