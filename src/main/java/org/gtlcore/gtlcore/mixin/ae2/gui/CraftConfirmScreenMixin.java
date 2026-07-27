package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.common.IConfirmStartMenu;
import org.gtlcore.gtlcore.integration.jei.JeiMissingIngredientBookmarks;

import com.lowdragmc.lowdraglib.LDLib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftConfirmMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {

    @Unique
    private static final String GTLCORE$FAVORITE_MISSING_WIDGET_ID = "favoriteMissing";

    @Unique
    private Button gtlcore$favoriteMissing;

    protected CraftConfirmScreenMixin(CraftConfirmMenu menu, Inventory playerInventory,
                                      Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$addFavoriteMissingButton(CraftConfirmMenu menu, Inventory playerInventory,
                                                  Component title, ScreenStyle style, CallbackInfo ci) {
        if (LDLib.isJeiLoaded()) {
            this.gtlcore$favoriteMissing = this.widgets.addButton(
                    GTLCORE$FAVORITE_MISSING_WIDGET_ID,
                    Component.translatable("gui.gtlcore.ae.favorite_missing"),
                    this::gtlcore$favoriteMissing);
            this.gtlcore$favoriteMissing.active = false;
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$updateFavoriteMissingButton(CallbackInfo ci) {
        if (this.gtlcore$favoriteMissing == null) {
            return;
        }
        this.gtlcore$favoriteMissing.active = JeiMissingIngredientBookmarks.isAvailable() &&
                !gtlcore$collectMissingKeys().isEmpty();
    }

    @Unique
    private List<AEKey> gtlcore$collectMissingKeys() {
        // 菜单端带缓存，仅在计划或实时库存变化时重算
        return this.menu instanceof IConfirmStartMenu confirmMenu ? confirmMenu.gtlcore$getMissingNow() : List.of();
    }

    @Unique
    private void gtlcore$favoriteMissing() {
        var missingKeys = gtlcore$collectMissingKeys();
        var result = JeiMissingIngredientBookmarks.add(missingKeys);
        Component message = switch (result.status()) {
            case ADDED -> Component.translatable("message.gtlcore.ae.favorite_missing.added", result.added());
            case ALREADY_BOOKMARKED -> Component.translatable("message.gtlcore.ae.favorite_missing.already");
            case NOTHING_TO_ADD -> Component.translatable("message.gtlcore.ae.favorite_missing.nothing");
            case UNAVAILABLE -> Component.translatable("message.gtlcore.ae.favorite_missing.unavailable");
            case FAILED -> Component.translatable("message.gtlcore.ae.favorite_missing.failed");
        };
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
