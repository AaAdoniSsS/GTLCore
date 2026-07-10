package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.wireless.PatternQuickUploadSelectionOverlay;
import org.gtlcore.gtlcore.client.gui.ModifyIcon;
import org.gtlcore.gtlcore.client.gui.ModifyIconButton;
import org.gtlcore.gtlcore.client.gui.PatterEncodingTermMenuModify;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseScreen.class)
public abstract class PatternEncodingTermScreenMixin<T extends AEBaseMenu> extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<T> {

    @Unique
    private static final String GTLCORE$ENCODE_PATTERN_WIDGET = "encodePattern";
    @Unique
    private static final int GTLCORE$QUICK_UPLOAD_BUTTON_SIZE = 8;
    @Unique
    private static final int GTLCORE$QUICK_UPLOAD_BUTTON_GAP = 2;

    @Shadow(remap = false)
    @Final
    protected WidgetContainer widgets;

    @Unique
    private IconButton gtlcore$quickUploadButton;
    @Unique
    private ModifyIconButton gtlcore$quickUploadUndoButton;
    @Unique
    private int gtlcore$quickUploadHitX;
    @Unique
    private int gtlcore$quickUploadHitY;
    @Unique
    private int gtlcore$quickUploadHitWidth;
    @Unique
    private int gtlcore$quickUploadHitHeight;
    @Unique
    private int gtlcore$quickUploadUndoHitX;
    @Unique
    private int gtlcore$quickUploadUndoHitY;
    @Unique
    private int gtlcore$quickUploadUndoHitWidth;
    @Unique
    private int gtlcore$quickUploadUndoHitHeight;

    private PatternEncodingTermScreenMixin(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gtlcore$initPatternQuickUploadButton(CallbackInfo ci) {
        if (!gtlcore$isPatternEncodingTermScreen()) {
            return;
        }
        if (this.gtlcore$quickUploadButton == null) {
            this.gtlcore$quickUploadButton = new QuickUploadButton(button -> {
                if (this.menu instanceof PatterEncodingTermMenuModify menuModify) {
                    menuModify.gTLCore$quickUploadPattern();
                }
            });
        }
        if (this.gtlcore$quickUploadUndoButton == null) {
            this.gtlcore$quickUploadUndoButton = new QuickUploadUndoButton(button -> {
                if (this.menu instanceof PatterEncodingTermMenuModify menuModify) {
                    menuModify.gTLCore$undoQuickUploadPattern();
                }
            });
        }
        gtlcore$updateQuickUploadButton();
        this.addRenderableWidget(this.gtlcore$quickUploadButton);
        this.addRenderableWidget(this.gtlcore$quickUploadUndoButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void gtlcore$updatePatternQuickUploadButton(CallbackInfo ci) {
        if (this.gtlcore$quickUploadButton != null) {
            gtlcore$updateQuickUploadButton();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handlePatternQuickUploadClick(double mouseX, double mouseY, int button,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$isPatternEncodingTermScreen() &&
                PatternQuickUploadSelectionOverlay.mouseClicked(mouseX, mouseY, button, this.width, this.height)) {
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && gtlcore$isQuickUploadUndoHit(mouseX, mouseY)) {
            gtlcore$triggerQuickUploadUndo();
            cir.setReturnValue(true);
            return;
        }
        if (button == 1 && gtlcore$isQuickUploadHit(mouseX, mouseY)) {
            gtlcore$triggerQuickUploadUndo();
            cir.setReturnValue(true);
            return;
        }
        if (button == 0 && gtlcore$isQuickUploadHit(mouseX, mouseY)) {
            gtlcore$triggerQuickUpload();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handlePatternQuickUploadSelectionDrag(double mouseX, double mouseY, int button,
                                                               double dragX, double dragY,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$isPatternEncodingTermScreen() &&
                PatternQuickUploadSelectionOverlay.mouseDragged(mouseY, this.width, this.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handlePatternQuickUploadSelectionRelease(double mouseX, double mouseY, int button,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$isPatternEncodingTermScreen() && PatternQuickUploadSelectionOverlay.mouseReleased()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void gtlcore$handlePatternQuickUploadSelectionScroll(double mouseX, double mouseY, double delta,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        if (gtlcore$isPatternEncodingTermScreen() &&
                PatternQuickUploadSelectionOverlay.mouseScrolled(mouseX, mouseY, delta, this.width, this.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void gtlcore$renderPatternQuickUploadSelection(GuiGraphics graphics, int mouseX, int mouseY,
                                                           float partialTick, CallbackInfo ci) {
        if (gtlcore$isPatternEncodingTermScreen()) {
            PatternQuickUploadSelectionOverlay.render(graphics, this.width, this.height, mouseX, mouseY);
        }
    }

    @Inject(method = "renderTooltips", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlcore$suppressPatternQuickUploadBaseTooltips(GuiGraphics graphics, int mouseX, int mouseY,
                                                                CallbackInfo ci) {
        if (gtlcore$isPatternEncodingTermScreen() && PatternQuickUploadSelectionOverlay.isOpen()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean gtlcore$isPatternEncodingTermScreen() {
        return (Object) this instanceof PatternEncodingTermScreen<?>;
    }

    @Unique
    private void gtlcore$updateQuickUploadButton() {
        var widgets = ((WidgetContainerAccessor) this.widgets).gtlcore$getWidgets();
        AbstractWidget encodeButton = widgets.get(GTLCORE$ENCODE_PATTERN_WIDGET);
        AbstractWidget modify1 = widgets.get("modify1");
        AbstractWidget modify2 = widgets.get("modify2");
        AbstractWidget modify3 = widgets.get("modify3");
        if (encodeButton != null && modify1 != null && modify2 != null && modify3 != null) {
            int encodeRight = encodeButton.getX() + encodeButton.getWidth();
            int rightBoundary = widgets.values().stream()
                    .filter(widget -> widget != encodeButton)
                    .mapToInt(AbstractWidget::getX)
                    .filter(x -> x > encodeRight)
                    .min()
                    .orElse(encodeRight + GTLCORE$QUICK_UPLOAD_BUTTON_SIZE);
            int hitX = encodeRight;
            int hitY = encodeButton.getY();
            int hitWidth = Math.max(GTLCORE$QUICK_UPLOAD_BUTTON_SIZE, rightBoundary - encodeRight);
            int hitHeight = Math.max(GTLCORE$QUICK_UPLOAD_BUTTON_SIZE, encodeButton.getHeight());
            int uploadButtonY = (modify1.getY() + modify2.getY()) / 2;
            int uploadButtonX = encodeRight + GTLCORE$QUICK_UPLOAD_BUTTON_GAP;
            int undoButtonY = (modify2.getY() + modify3.getY()) / 2;
            boolean showUndoButton = true;

            if (showUndoButton) {
                this.gtlcore$quickUploadButton.setX(uploadButtonX);
                this.gtlcore$quickUploadButton.setY(uploadButtonY);
                this.gtlcore$quickUploadHitX = uploadButtonX;
                this.gtlcore$quickUploadHitY = uploadButtonY;
                this.gtlcore$quickUploadHitWidth = GTLCORE$QUICK_UPLOAD_BUTTON_SIZE;
                this.gtlcore$quickUploadHitHeight = GTLCORE$QUICK_UPLOAD_BUTTON_SIZE;
                this.gtlcore$quickUploadUndoButton.setX(uploadButtonX);
                this.gtlcore$quickUploadUndoButton.setY(undoButtonY);
                this.gtlcore$quickUploadUndoHitX = uploadButtonX;
                this.gtlcore$quickUploadUndoHitY = undoButtonY;
                this.gtlcore$quickUploadUndoHitWidth = GTLCORE$QUICK_UPLOAD_BUTTON_SIZE;
                this.gtlcore$quickUploadUndoHitHeight = GTLCORE$QUICK_UPLOAD_BUTTON_SIZE;
            } else {
                this.gtlcore$quickUploadButton.setX(uploadButtonX);
                this.gtlcore$quickUploadButton.setY(uploadButtonY);
                this.gtlcore$quickUploadHitX = hitX;
                this.gtlcore$quickUploadHitY = hitY;
                this.gtlcore$quickUploadHitWidth = hitWidth;
                this.gtlcore$quickUploadHitHeight = hitHeight;
                this.gtlcore$quickUploadUndoHitX = 0;
                this.gtlcore$quickUploadUndoHitY = 0;
                this.gtlcore$quickUploadUndoHitWidth = 0;
                this.gtlcore$quickUploadUndoHitHeight = 0;
            }
            this.gtlcore$quickUploadUndoButton.setVisibility(showUndoButton);
        }

        boolean visible = !PatternQuickUploadSelectionOverlay.isOpen() &&
                this.menu instanceof PatternEncodingTermMenu patternMenu &&
                gtlcore$isQuickUploadMode(patternMenu.getMode());
        this.gtlcore$quickUploadButton.setVisibility(visible);
        this.gtlcore$quickUploadUndoButton.setVisibility(visible && this.gtlcore$quickUploadUndoHitWidth > 0);
    }

    @Unique
    private static boolean gtlcore$isQuickUploadMode(EncodingMode mode) {
        return switch (mode) {
            case CRAFTING, PROCESSING, SMITHING_TABLE, STONECUTTING -> true;
        };
    }

    @Unique
    private boolean gtlcore$isQuickUploadHit(double mouseX, double mouseY) {
        return this.gtlcore$quickUploadButton != null &&
                this.gtlcore$quickUploadButton.visible &&
                this.gtlcore$quickUploadButton.active &&
                mouseX >= this.gtlcore$quickUploadHitX &&
                mouseY >= this.gtlcore$quickUploadHitY &&
                mouseX < this.gtlcore$quickUploadHitX + this.gtlcore$quickUploadHitWidth &&
                mouseY < this.gtlcore$quickUploadHitY + this.gtlcore$quickUploadHitHeight;
    }

    @Unique
    private boolean gtlcore$isQuickUploadUndoHit(double mouseX, double mouseY) {
        return this.gtlcore$quickUploadUndoButton != null &&
                this.gtlcore$quickUploadUndoButton.visible &&
                this.gtlcore$quickUploadUndoButton.active &&
                mouseX >= this.gtlcore$quickUploadUndoHitX &&
                mouseY >= this.gtlcore$quickUploadUndoHitY &&
                mouseX < this.gtlcore$quickUploadUndoHitX + this.gtlcore$quickUploadUndoHitWidth &&
                mouseY < this.gtlcore$quickUploadUndoHitY + this.gtlcore$quickUploadUndoHitHeight;
    }

    @Unique
    private void gtlcore$triggerQuickUpload() {
        if (this.menu instanceof PatterEncodingTermMenuModify menuModify) {
            menuModify.gTLCore$quickUploadPattern();
        }
    }

    @Unique
    private void gtlcore$triggerQuickUploadUndo() {
        if (this.menu instanceof PatterEncodingTermMenuModify menuModify) {
            menuModify.gTLCore$undoQuickUploadPattern();
        }
    }

    @Unique
    private static final class QuickUploadButton extends IconButton {

        private QuickUploadButton(OnPress onPress) {
            super(onPress);
            this.setHalfSize(true);
            this.setWidth(GTLCORE$QUICK_UPLOAD_BUTTON_SIZE);
            this.setHeight(GTLCORE$QUICK_UPLOAD_BUTTON_SIZE);
            this.setTooltip(Tooltip.create(Component.translatable("tooltip.gtlcore.pattern_quick_upload")));
        }

        @Override
        protected Icon getIcon() {
            return Icon.ARROW_UP;
        }
    }

    @Unique
    private static final class QuickUploadUndoButton extends ModifyIconButton {

        private QuickUploadUndoButton(OnPress onPress) {
            super(onPress, ModifyIcon.UNDO,
                    Component.translatable("tooltip.gtlcore.pattern_quick_upload_undo"));
        }
    }
}
