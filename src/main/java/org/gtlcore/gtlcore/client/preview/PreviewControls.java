package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.mixin.ldlib.client.PreviewSceneAccessor;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PreviewControls {

    private final WidgetGroup widget;
    private final SceneWidget scene;
    private final IntSupplier height;
    private final Supplier<PreviewHeight.Dimensions> dimensions;
    private final ButtonWidget expand;
    private final Map<Widget, Geometry> original = new IdentityHashMap<>();
    private Position parentPosition;
    private Geometry rootGeometry;
    private boolean fullscreen;
    private int width;
    private int screenHeight;

    public PreviewControls(WidgetGroup widget, SceneWidget scene, IntSupplier height,
                           Supplier<PreviewHeight.Dimensions> dimensions) {
        this.widget = widget;
        this.scene = scene;
        this.height = height;
        this.dimensions = dimensions;
        expand = new ButtonWidget(138, 10, 18, 18,
                PreviewButtonTexture.EXPAND, click -> {
                    var mc = Minecraft.getInstance();
                    if (mc.screen instanceof FullscreenPreviewScreen screen && screen.controls() == this) {
                        screen.onClose();
                    } else if (!fullscreen && mc.screen != null) {
                        mc.setScreen(new FullscreenPreviewScreen(mc.screen, this));
                    }
                });
        expand.setHoverTooltips(Component.translatable("gui.gtlcore.preview.fullscreen"));
        widget.addWidget(expand);
    }

    public WidgetGroup widget() {
        return widget;
    }

    public SceneWidget scene() {
        return scene;
    }

    public ButtonWidget expandButton() {
        return expand;
    }

    public int controllerHeight() {
        return height.getAsInt();
    }

    public PreviewHeight.Dimensions dimensions() {
        return dimensions.get();
    }

    public boolean fullscreen() {
        return fullscreen;
    }

    public int headerHeight() {
        var font = Minecraft.getInstance().font;
        int available = Math.max(20, width - 40);
        return 24 + (font.split(heightText(), available).size() + font.split(dimensionsText(), available).size()) * font.lineHeight;
    }

    public boolean canPan(double x, double y) {
        if (!fullscreen || !scene.isMouseOverElement(x, y) || ((PreviewSceneAccessor) scene).gtlcore$dragging()) return false;
        for (var child : widget.widgets) {
            if (child != scene && child.isVisible() && child.isActive() && child.isMouseOverElement(x, y)) return false;
        }
        return true;
    }

    public void enter(int width, int height) {
        if (!fullscreen) {
            rootGeometry = Geometry.of(widget);
            parentPosition = widget.getParentPosition();
            fullscreen = true;
            expand.setButtonTexture(PreviewButtonTexture.RESTORE);
            expand.setHoverTooltips(Component.translatable("gui.gtlcore.preview.restore"));
        }
        this.width = width;
        screenHeight = height;
        layout();
    }

    public void layout() {
        if (!fullscreen) return;
        // Save only live children. Page/module changes replace the material strip.
        original.keySet().removeIf(child -> !widget.widgets.contains(child));
        for (var child : widget.widgets) original.computeIfAbsent(child, Geometry::of);
        widget.setParentPosition(Position.ORIGIN);
        widget.setSelfPosition(0, 0);
        widget.setSize(width, screenHeight);
        int sceneTop = headerHeight() + 2;
        int candidateRows = Math.max(1, (screenHeight - sceneTop - 32) / 18);
        int candidate = 0;
        for (var child : widget.widgets) {
            if (child == scene) {
                child.setSelfPosition(4, sceneTop);
                child.setSize(Math.max(1, width - 8), Math.max(1, screenHeight - sceneTop - 30));
            } else if (child == expand) {
                child.setSelfPosition(width - 24, 6);
            } else if (child instanceof DraggableScrollableWidgetGroup) {
                child.setSelfPosition(4, screenHeight - 26);
                child.setSize(Math.max(18, width - 8), 22);
            } else if (child instanceof ButtonWidget) {
                child.setSelfPosition(width - 24, original.get(child).position.y - 30 + sceneTop + 2);
            } else if (child instanceof ImageWidget image && image.getImage() instanceof TextTexture text) {
                child.setSelfPosition(8, 6);
                child.setSize(Math.max(1, width - 40), 10);
                text.setWidth(Math.max(1, width - 40));
            } else if (child instanceof SlotWidget) {
                child.setSelfPosition(8 + candidate / candidateRows * 18, sceneTop + 2 + candidate % candidateRows * 18);
                candidate++;
            }
        }
    }

    public void exit() {
        if (!fullscreen) return;
        fullscreen = false;
        rootGeometry.restore(widget);
        widget.setParentPosition(parentPosition);
        for (var child : widget.widgets) {
            var saved = original.get(child);
            if (saved != null) saved.restore(child);
        }
        original.clear();
        expand.setButtonTexture(PreviewButtonTexture.EXPAND);
        expand.setHoverTooltips(Component.translatable("gui.gtlcore.preview.fullscreen"));
    }

    private Component heightText() {
        int distance = controllerHeight();
        return distance < 0 ? Component.translatable("gui.gtlcore.preview.height_unknown") : Component.translatable("gui.gtlcore.preview.height", distance);
    }

    private Component dimensionsText() {
        var size = dimensions();
        return size.known() ? Component.translatable("gui.gtlcore.preview.dimensions",
                size.width(), size.depth(), size.height()) : Component.translatable("gui.gtlcore.preview.dimensions_unknown");
    }

    public void drawInfo(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int available = fullscreen ? Math.max(20, width - 40) : widget.getSizeWidth();
        var text = heightText();
        var lines = new java.util.ArrayList<>(font.split(text, available));
        if (fullscreen) lines.addAll(font.split(dimensionsText(), available));
        int y = fullscreen ? 20 : widget.getPositionY() - 5 - lines.size() * font.lineHeight;
        int center = fullscreen ? (width - 24) / 2 : widget.getPositionX() + widget.getSizeWidth() / 2;
        if (!fullscreen && Minecraft.getInstance().screen instanceof PreviewHeaderBounds header) {
            float screenY = graphics.pose().last().pose().transformPosition(0, y, 0, new Vector3f()).y;
            if (screenY < header.gtlcore$headerBottom() + 5) {
                lines = new java.util.ArrayList<>(font.split(text, Math.max(20, available - 28)));
                y = widget.getPositionY() + 16;
                center -= 12;
            }
        }
        for (var line : lines) {
            graphics.drawString(font, line, center - font.width(line) / 2, y, 0xFFE4E4E4, true);
            y += font.lineHeight;
        }
    }

    private record Geometry(Position position, Size size, Integer textWidth) {

        static Geometry of(Widget widget) {
            Integer textWidth = widget instanceof ImageWidget image && image.getImage() instanceof TextTexture text ? text.width : null;
            return new Geometry(widget.getSelfPosition(), widget.getSize(), textWidth);
        }

        void restore(Widget widget) {
            widget.setSelfPosition(position);
            widget.setSize(size);
            if (textWidth != null && widget instanceof ImageWidget image && image.getImage() instanceof TextTexture text) {
                text.setWidth(textWidth);
            }
        }
    }
}
