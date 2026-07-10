package org.gtlcore.gtlcore.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author EasterFG on 2024/9/13
 */
public class ModifyIconButton extends Button implements ITooltip {

    private static final int BUTTON_SIZE = 8;

    private final ModifyIcon icon;

    private final Component tooltipMessage;

    public ModifyIconButton(Button.OnPress onPress, ModifyIcon icon, Component displayName, Component displayValue) {
        this(onPress, icon, Component.empty().append(displayName).append("\n").append(displayValue));
    }

    public ModifyIconButton(Button.OnPress onPress, ModifyIcon icon, Component tooltipMessage) {
        super(0, 0, BUTTON_SIZE, BUTTON_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.tooltipMessage = tooltipMessage;
    }

    public void setVisibility(boolean vis) {
        this.visible = vis;
        this.active = vis;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (this.visible) {
            ModifyIcon icon = this.getIcon();
            Blitter blitter = icon.getBlitter();
            if (!this.active) {
                blitter.opacity(0.5F);
            }

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            if (this.isFocused()) {
                guiGraphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY(), -1);
                guiGraphics.fill(this.getX() - 1, this.getY(), this.getX(), this.getY() + this.height, -1);
                guiGraphics.fill(this.getX() + this.width, this.getY(), this.getX() + this.width + 1, this.getY() + this.height, -1);
                guiGraphics.fill(this.getX() - 1, this.getY() + this.height, this.getX() + this.width + 1, this.getY() + this.height + 1, -1);
            }

            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate((float) this.getX(), (float) this.getY(), 0.0F);
            pose.scale(0.5F, 0.5F, 1.0F);
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(0, 0).blit(guiGraphics);
            blitter.dest(0, 0).blit(guiGraphics);
            pose.popPose();
            RenderSystem.enableDepthTest();
        }
    }

    private ModifyIcon getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int value) {
        this.height = value;
    }

    @Override
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), BUTTON_SIZE, BUTTON_SIZE);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return Collections.singletonList(this.tooltipMessage);
    }
}
