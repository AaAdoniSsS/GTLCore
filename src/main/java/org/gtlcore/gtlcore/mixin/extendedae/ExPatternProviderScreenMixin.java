package org.gtlcore.gtlcore.mixin.extendedae;

import org.gtlcore.gtlcore.mixin.mc.SlotAccessor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class ExPatternProviderScreenMixin<T extends AEBaseMenu> extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<T> {

    @Unique
    private static final int GTLCORE$PATTERNS_PER_ROW = 9;
    @Unique
    private static final int GTLCORE$ROWS_PER_PAGE = 4;
    @Unique
    private static final int GTLCORE$PATTERNS_PER_PAGE = GTLCORE$PATTERNS_PER_ROW * GTLCORE$ROWS_PER_PAGE;
    @Unique
    private static final int GTLCORE$PATTERN_SLOT_LEFT = 8;
    @Unique
    private static final int GTLCORE$PATTERN_SLOT_TOP = 42;
    @Unique
    private static final int GTLCORE$PATTERN_SLOT_SIZE = 18;
    @Unique
    private static final int GTLCORE$HIDDEN_SLOT_POSITION = -9999;
    @Unique
    private static final int GTLCORE$BUTTON_SIZE = 16;
    @Unique
    private static final int GTLCORE$PAGE_BUTTON_Y = 24;
    @Unique
    private static final int GTLCORE$PREVIOUS_BUTTON_X = 136;
    @Unique
    private static final int GTLCORE$NEXT_BUTTON_X = 154;
    @Unique
    private static final int GTLCORE$PAGE_LABEL_Y = 29;
    @Unique
    private static final int GTLCORE$PAGE_LABEL_COLOR = 0x404040;

    @Unique
    private int gtlcore$exPatternProviderPage;
    @Unique
    private IconButton gtlcore$previousPageButton;
    @Unique
    private IconButton gtlcore$nextPageButton;

    private ExPatternProviderScreenMixin(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gtlcore$initExPatternProviderPagination(CallbackInfo ci) {
        if (!gtlcore$isExPatternProviderMenu()) {
            return;
        }
        gtlcore$clampExPatternProviderPage();
        gtlcore$addExPatternProviderPageButtons();
        gtlcore$applyExPatternProviderPage();
    }

    @Inject(method = "repositionSlots", at = @At("TAIL"), remap = false)
    private void gtlcore$applyExPatternProviderPageAfterReposition(SlotSemantic semantic, CallbackInfo ci) {
        if (semantic == SlotSemantics.ENCODED_PATTERN && gtlcore$isExPatternProviderMenu()) {
            gtlcore$clampExPatternProviderPage();
            gtlcore$applyExPatternProviderPage();
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void gtlcore$drawExPatternProviderPageLabel(GuiGraphics graphics, int offsetX, int offsetY,
                                                        int mouseX, int mouseY, CallbackInfo ci) {
        int maxPage = gtlcore$getExPatternProviderMaxPage();
        if (!gtlcore$isExPatternProviderMenu() || maxPage <= 1) {
            return;
        }
        Component pageLabel = Component.literal((this.gtlcore$exPatternProviderPage + 1) + " / " + maxPage);
        graphics.drawString(
                this.font,
                pageLabel,
                GTLCORE$PREVIOUS_BUTTON_X - this.font.width(pageLabel) - 4,
                GTLCORE$PAGE_LABEL_Y,
                GTLCORE$PAGE_LABEL_COLOR,
                false);
    }

    @Unique
    private boolean gtlcore$isExPatternProviderMenu() {
        return this.menu instanceof ContainerExPatternProvider;
    }

    @Unique
    private void gtlcore$addExPatternProviderPageButtons() {
        if (gtlcore$getExPatternProviderMaxPage() <= 1) {
            return;
        }
        this.gtlcore$previousPageButton = new PageButton(Icon.ARROW_LEFT, button -> {
            if (this.gtlcore$exPatternProviderPage > 0) {
                this.gtlcore$exPatternProviderPage--;
                gtlcore$applyExPatternProviderPage();
            }
        });
        this.gtlcore$nextPageButton = new PageButton(Icon.ARROW_RIGHT, button -> {
            if (this.gtlcore$exPatternProviderPage < gtlcore$getExPatternProviderMaxPage() - 1) {
                this.gtlcore$exPatternProviderPage++;
                gtlcore$applyExPatternProviderPage();
            }
        });
        this.gtlcore$previousPageButton.setX(this.leftPos + GTLCORE$PREVIOUS_BUTTON_X);
        this.gtlcore$previousPageButton.setY(this.topPos + GTLCORE$PAGE_BUTTON_Y);
        this.gtlcore$nextPageButton.setX(this.leftPos + GTLCORE$NEXT_BUTTON_X);
        this.gtlcore$nextPageButton.setY(this.topPos + GTLCORE$PAGE_BUTTON_Y);
        this.addRenderableWidget(this.gtlcore$previousPageButton);
        this.addRenderableWidget(this.gtlcore$nextPageButton);
        gtlcore$updateExPatternProviderPageButtons();
    }

    @Unique
    private void gtlcore$applyExPatternProviderPage() {
        var patternSlots = this.menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int startSlot = this.gtlcore$exPatternProviderPage * GTLCORE$PATTERNS_PER_PAGE;
        int endSlot = Math.min(startSlot + GTLCORE$PATTERNS_PER_PAGE, patternSlots.size());
        for (int slotIndex = 0; slotIndex < patternSlots.size(); slotIndex++) {
            Slot slot = patternSlots.get(slotIndex);
            SlotAccessor slotAccessor = (SlotAccessor) slot;
            if (slotIndex < startSlot || slotIndex >= endSlot) {
                slotAccessor.gtlcore$setX(GTLCORE$HIDDEN_SLOT_POSITION);
                slotAccessor.gtlcore$setY(GTLCORE$HIDDEN_SLOT_POSITION);
                continue;
            }
            int pageSlot = slotIndex - startSlot;
            slotAccessor.gtlcore$setX(GTLCORE$PATTERN_SLOT_LEFT + pageSlot % GTLCORE$PATTERNS_PER_ROW * GTLCORE$PATTERN_SLOT_SIZE);
            slotAccessor.gtlcore$setY(GTLCORE$PATTERN_SLOT_TOP + pageSlot / GTLCORE$PATTERNS_PER_ROW * GTLCORE$PATTERN_SLOT_SIZE);
        }
        gtlcore$updateExPatternProviderPageButtons();
    }

    @Unique
    private int gtlcore$getExPatternProviderMaxPage() {
        return Math.max(1, (this.menu.getSlots(SlotSemantics.ENCODED_PATTERN).size() + GTLCORE$PATTERNS_PER_PAGE - 1) / GTLCORE$PATTERNS_PER_PAGE);
    }

    @Unique
    private void gtlcore$clampExPatternProviderPage() {
        int maxPage = gtlcore$getExPatternProviderMaxPage();
        if (this.gtlcore$exPatternProviderPage >= maxPage) {
            this.gtlcore$exPatternProviderPage = maxPage - 1;
        }
        if (this.gtlcore$exPatternProviderPage < 0) {
            this.gtlcore$exPatternProviderPage = 0;
        }
    }

    @Unique
    private void gtlcore$updateExPatternProviderPageButtons() {
        int maxPage = gtlcore$getExPatternProviderMaxPage();
        if (this.gtlcore$previousPageButton != null) {
            this.gtlcore$previousPageButton.active = this.gtlcore$exPatternProviderPage > 0;
            this.gtlcore$previousPageButton.visible = maxPage > 1;
        }
        if (this.gtlcore$nextPageButton != null) {
            this.gtlcore$nextPageButton.active = this.gtlcore$exPatternProviderPage < maxPage - 1;
            this.gtlcore$nextPageButton.visible = maxPage > 1;
        }
    }

    @Unique
    private static final class PageButton extends IconButton {

        private final Icon icon;

        private PageButton(Icon icon, OnPress onPress) {
            super(onPress);
            this.icon = icon;
            this.width = GTLCORE$BUTTON_SIZE;
            this.height = GTLCORE$BUTTON_SIZE;
        }

        @Override
        protected Icon getIcon() {
            return this.icon;
        }
    }
}
