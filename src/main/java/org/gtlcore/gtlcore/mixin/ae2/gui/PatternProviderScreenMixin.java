package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.api.crafting.IAutoExpandMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.implementations.PatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {

    @Unique
    private ToggleButton gtlcore$autoExpandButton;

    private PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$addAutoExpandButton(C menu, Inventory playerInventory, Component title, ScreenStyle style,
                                             CallbackInfo ci) {
        this.gtlcore$autoExpandButton = new ToggleButton(
                Icon.BLOCKING_MODE_YES,
                Icon.BLOCKING_MODE_NO,
                this::gtlcore$onAutoExpandChanged);
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
            this.gtlcore$autoExpandButton.setState(((IAutoExpandMenu) this.menu).gtlcore$isAutoExpand());
        }
    }

    @Unique
    private void gtlcore$onAutoExpandChanged(boolean newState) {
        ((IAutoExpandMenu) this.menu).gtlcore$toggleAutoExpand();
    }
}
