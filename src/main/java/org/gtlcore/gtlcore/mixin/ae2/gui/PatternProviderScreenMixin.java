package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.crafting.IAutoExpandMenu;
import org.gtlcore.gtlcore.client.gui.ModifyIconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.PatternProviderMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private AutoExpandButton gtlcore$autoExpandButton;

    private PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$addAutoExpandButton(C menu, Inventory playerInventory, Component title, ScreenStyle style,
                                             CallbackInfo ci) {
        this.gtlcore$autoExpandButton = new AutoExpandButton(
                btn -> ((IAutoExpandMenu) this.menu).gtlcore$toggleAutoExpand());
        this.gtlcore$autoExpandButton.setTooltipOn(List.of(
                Component.translatable("gui.gtlcore.pattern_provider.auto_expand"),
                Component.translatable("gui.gtlcore.pattern_provider.auto_expand.on")));
        this.gtlcore$autoExpandButton.setTooltipOff(List.of(
                Component.translatable("gui.gtlcore.pattern_provider.auto_expand"),
                Component.translatable("gui.gtlcore.pattern_provider.auto_expand.off")));
        addToLeftToolbar(this.gtlcore$autoExpandButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$syncAutoExpandButton(CallbackInfo ci) {
        if (this.gtlcore$autoExpandButton != null) {
            this.gtlcore$autoExpandButton.setState(
                    ((IAutoExpandMenu) this.menu).gtlcore$isAutoExpand());
        }
    }

    @Setter
    @Unique
    private static class AutoExpandButton extends ModifyIconButton {

        private static final ResourceLocation TEXTURE_ON = GTLCore.id("textures/guis/advanced_blocking_mode_on.png");
        private static final ResourceLocation TEXTURE_OFF = GTLCore.id("textures/guis/advanced_blocking_mode_off.png");
        private static final int TEX_SIZE = 16;

        private boolean state;
        private List<Component> tooltipOn = Collections.emptyList();
        private List<Component> tooltipOff = Collections.emptyList();

        public AutoExpandButton(OnPress onPress) {
            super(onPress, null, Component.empty());
            this.state = false;
            this.setWidth(16);
            this.setHeight(16);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
            if (!this.visible) return;

            ResourceLocation tex = state ? TEXTURE_ON : TEXTURE_OFF;
            Blitter blitter = Blitter.texture(tex, TEX_SIZE, TEX_SIZE).src(0, 0, TEX_SIZE, TEX_SIZE);

            if (!this.active) {
                blitter.opacity(1.0F);
            }

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            if (this.isFocused()) {
                guiGraphics.fill(this.getX() - 1, this.getY() - 1,
                        this.getX() + this.width + 1, this.getY(), -1);
                guiGraphics.fill(this.getX() - 1, this.getY(),
                        this.getX(), this.getY() + this.height, -1);
                guiGraphics.fill(this.getX() + this.width, this.getY(),
                        this.getX() + this.width + 1, this.getY() + this.height, -1);
                guiGraphics.fill(this.getX() - 1, this.getY() + this.height,
                        this.getX() + this.width + 1, this.getY() + this.height + 1, -1);
            }
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(this.getX(), this.getY()).blit(guiGraphics);
            blitter.dest(this.getX(), this.getY()).blit(guiGraphics);
            RenderSystem.enableDepthTest();
        }

        @Override
        public List<Component> getTooltipMessage() {
            return state ? tooltipOn : tooltipOff;
        }

        @Override
        public Rect2i getTooltipArea() {
            return new Rect2i(this.getX(), this.getY(), this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return super.isTooltipAreaVisible();
        }
    }
}
