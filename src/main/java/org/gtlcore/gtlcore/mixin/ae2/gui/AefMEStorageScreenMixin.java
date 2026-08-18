package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.compat.aef.AefFavoriteKeyCompat;
import org.gtlcore.gtlcore.mixin.mc.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MEStorageScreen.class, priority = 900)
public abstract class AefMEStorageScreenMixin {

    @Shadow(remap = false)
    @Final
    protected Repo repo;

    @Shadow(remap = false)
    protected abstract void handleGridInventoryEntryMouseClick(
                                                               GridInventoryEntry entry, int mouseButton, ClickType clickType);

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handleAefFavoriteClick(
                                                double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!AefFavoriteKeyCompat.hasKeyMapping()) {
            return;
        }

        boolean configuredKey = AefFavoriteKeyCompat.matchesMouse(button);
        boolean legacyKey = AefFavoriteKeyCompat.matchesLegacyMouse(button);
        if (!configuredKey && !legacyKey) {
            return;
        }

        Slot slot = ((AbstractContainerScreenAccessor) this).gtlcore$findSlot(mouseX, mouseY);
        if (!(slot instanceof RepoSlot repoSlot)) {
            return;
        }
        GridInventoryEntry entry = repoSlot.getEntry();
        if (configuredKey && entry != null && entry.getWhat() != null && gtlcore$toggleFavorite(entry)) {
            cir.setReturnValue(true);
            return;
        }

        if (legacyKey) {
            if (repoSlot.isCraftable()) {
                handleGridInventoryEntryMouseClick(entry, button, ClickType.CLONE);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handleAefFavoriteKey(
                                              int keyCode, int scanCode, int modifiers,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!AefFavoriteKeyCompat.matchesKey(keyCode, scanCode)) {
            return;
        }
        Slot slot = ((AbstractContainerScreen<?>) (Object) this).getSlotUnderMouse();
        if (!(slot instanceof RepoSlot repoSlot)) {
            return;
        }
        GridInventoryEntry entry = repoSlot.getEntry();
        if (entry != null && entry.getWhat() != null && gtlcore$toggleFavorite(entry)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean gtlcore$toggleFavorite(GridInventoryEntry entry) {
        if (!AefFavoriteKeyCompat.toggleFavorite(entry.getWhat())) {
            return false;
        }
        repo.updateView();
        return true;
    }
}
