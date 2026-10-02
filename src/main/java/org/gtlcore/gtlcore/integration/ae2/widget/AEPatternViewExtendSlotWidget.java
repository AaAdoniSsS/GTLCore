package org.gtlcore.gtlcore.integration.ae2.widget;

import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEPatternViewSlotWidget;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.Slot;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;

public class AEPatternViewExtendSlotWidget extends AEPatternViewSlotWidget {

    public AEPatternViewExtendSlotWidget(IItemTransfer inventory, int slotIndex, int xPosition, int yPosition) {
        super(inventory, slotIndex, xPosition, yPosition);
    }

    @Nullable
    private Runnable onMiddleClick;
    @Nullable
    private Runnable onPatternSlotChanged;

    public AEPatternViewExtendSlotWidget setOnMiddleClick(@Nullable Runnable runnable) {
        this.onMiddleClick = runnable;
        return this;
    }

    public AEPatternViewExtendSlotWidget setOnPatternSlotChanged(@Nullable Runnable runnable) {
        this.onPatternSlotChanged = runnable;
        return this;
    }

    @Override
    protected Slot createSlot(IItemTransfer itemHandler, int index) {
        return new ExtendWidgetSlotItemTransfer(itemHandler, index, 0, 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (slotReference != null && isMouseOverElement(mouseX, mouseY) && gui != null) {
            if (button == 2 && onMiddleClick != null) {
                writeClientAction(10, writer -> writer.writeBoolean(true));
                onMiddleClick.run();
                return true;
            }

            var stack = slotReference.getItem();
            if (canPutItems && stack.isEmpty() || canTakeItems && !stack.isEmpty()) {
                ModularUIGuiContainer modularUIGui = gui.getModularUIGui();
                boolean last = modularUIGui.getQuickCrafting();
                InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
                HOVER_SLOT = slotReference;
                gui.getModularUIGui().superMouseClicked(mouseX, mouseY, button);
                HOVER_SLOT = null;
                if (last != modularUIGui.getQuickCrafting()) {
                    modularUIGui.dragSplittingButton = button;
                    if (button == 0) {
                        modularUIGui.dragSplittingLimit = 0;
                    } else if (button == 1) {
                        modularUIGui.dragSplittingLimit = 1;
                    } else if (Minecraft.getInstance().options.keyPickItem.matchesMouse(mouseKey.getValue())) {
                        modularUIGui.dragSplittingLimit = 2;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        super.handleClientAction(id, buffer);
        if (id == 10) {
            if (onMiddleClick != null) {
                onMiddleClick.run();
            }
        }
    }

    protected class ExtendWidgetSlotItemTransfer extends WidgetSlotItemTransfer {

        public ExtendWidgetSlotItemTransfer(IItemTransfer itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // Taking a stack or editing its contents can notify the slot without
            // calling set(). Refresh the advertised pattern for those edits too.
            if (onPatternSlotChanged != null) {
                onPatternSlotChanged.run();
            }
        }
    }
}
