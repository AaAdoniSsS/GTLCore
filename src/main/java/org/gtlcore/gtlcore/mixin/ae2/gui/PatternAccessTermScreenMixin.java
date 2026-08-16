package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.JeiTerminalSearchTarget;
import org.gtlcore.gtlcore.client.ae2.PatternSearchHighlight;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.me.patternaccess.PatternSlot;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.implementations.PatternAccessTermMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternAccessTermScreen.class)
public abstract class PatternAccessTermScreenMixin<C extends PatternAccessTermMenu>
                                                  extends AEBaseScreen<C> implements JeiTerminalSearchTarget {

    @Shadow(remap = false)
    @Final
    private AETextField searchField;

    private PatternAccessTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void gtlcore$setJeiSearchText(String searchText) {
        this.searchField.setValue(searchText);
    }

    @Invoker(value = "itemStackMatchesSearchTerm", remap = false)
    protected abstract boolean gtlcore$itemStackMatchesSearchTerm(ItemStack stack, String searchTerm);

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void gtlcore$drawMatchedPatternBorders(GuiGraphics graphics, int offsetX, int offsetY,
                                                   int mouseX, int mouseY, CallbackInfo ci) {
        String searchTerm = this.searchField.getValue().toLowerCase();
        if (searchTerm.isEmpty()) {
            return;
        }
        for (var slot : this.getMenu().slots) {
            if (slot instanceof PatternSlot patternSlot &&
                    this.gtlcore$itemStackMatchesSearchTerm(patternSlot.getItem(), searchTerm)) {
                PatternSearchHighlight.drawBorder(graphics, patternSlot.x, patternSlot.y);
            }
        }
    }
}
