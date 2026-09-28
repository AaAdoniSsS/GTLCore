package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.mixin.ldlib.client.PreviewSceneAccessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/** Borrows the existing client-only widget without replacing its UI, world, slots or renderer. */
public final class FullscreenPreviewScreen extends Screen {

    private final Screen parent;
    private final PreviewControls controls;
    private boolean panning;

    public FullscreenPreviewScreen(Screen parent, PreviewControls controls) {
        super(Component.translatable("gui.gtlcore.preview.fullscreen"));
        this.parent = parent;
        this.controls = controls;
    }

    public PreviewControls controls() {
        return controls;
    }

    @Override
    protected void init() {
        panning = false;
        controls.enter(width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF303336);
        graphics.fill(0, 0, width, controls.headerHeight(), 0xFF45484A);
        graphics.fill(0, height - 28, width, height, 0xFF45484A);
        var widget = controls.widget();
        var gui = widget.getGui().getModularUIGui();
        gui.tooltipTexts = null;
        gui.tooltipComponent = null;
        gui.tooltipFont = null;
        gui.tooltipStack = ItemStack.EMPTY;
        try {
            widget.drawInBackground(graphics, mouseX, mouseY, partialTick);
            widget.drawInForeground(graphics, mouseX, mouseY, partialTick);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            if (gui.tooltipTexts != null && !gui.tooltipTexts.isEmpty()) {
                graphics.renderTooltip(gui.tooltipFont == null ? font : gui.tooltipFont, gui.tooltipTexts,
                        Optional.ofNullable(gui.tooltipComponent), mouseX, mouseY);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    @Override
    public void tick() {
        controls.widget().updateScreen();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            panning = controls.canPan(x, y);
            return panning;
        }
        if (panning) return true;
        return controls.widget().mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            boolean handled = panning;
            panning = false;
            return handled;
        }
        if (panning) return true;
        return controls.widget().mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!panning) return false;
            var scene = controls.scene();
            var renderer = scene.getRenderer();
            var access = (PreviewSceneAccessor) scene;
            double scale = access.gtlcore$ortho() ? 2.0 * access.gtlcore$range() * scene.getZoom() / Math.max(1, scene.getSizeWidth()) : 2.0 * renderer.getEyePos().distance(renderer.getLookAt()) * Math.tan(Math.toRadians(((PreviewRendererAccess) renderer).gtlcore$fov()) / 2) /
                    Math.max(1, scene.getSizeHeight());
            var offset = PreviewPan.offset(renderer.getEyePos(), renderer.getLookAt(), renderer.getWorldUp(),
                    dx, dy, scale);
            scene.setCenter(new Vector3f(scene.getCenter()).add(offset));
            return true;
        }
        if (panning) return true;
        return controls.widget().mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return controls.widget().mouseWheelMove(x, y, delta);
    }

    @Override
    public void mouseMoved(double x, double y) {
        controls.widget().mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_E || minecraft.options.keyInventory.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers) || controls.widget().keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        return controls.widget().keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return controls.widget().charTyped(character, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        panning = false;
        controls.exit();
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        panning = false;
        controls.exit();
    }
}
