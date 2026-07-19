package org.gtlcore.gtlcore.client.gui.widget;

import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.misc.IGhostFluidTarget;
import com.gregtechceu.gtceu.api.gui.misc.IGhostItemTarget;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.FluidTransferHelper;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Setter;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.lowdragmc.lowdraglib.gui.util.DrawerHelper.*;
import static com.lowdragmc.lowdraglib.gui.widget.PhantomFluidWidget.drainFrom;

/**
 * @author EasterFG on 2025/3/7
 */
public class AEDualConfigSlotWidget extends Widget implements IGhostItemTarget, IGhostFluidTarget {

    protected AEDualConfigWidget parentWidget;
    protected int index;
    protected final static int REMOVE_ID = 1000;
    protected final static int ITEM_UPDATE_ID = 1001;
    protected final static int FLUID_UPDATE_ID = 1002;

    @Setter
    protected boolean select = false;

    public AEDualConfigSlotWidget(int x, int y, AEDualConfigWidget widget, int index) {
        super(new Position(x, y), new Size(18, 18 * 2));
        this.parentWidget = widget;
        this.index = index;
    }

    protected int getIndex() {
        return (this.parentWidget.page - 1) * AEDualConfigWidget.CONFIG_SIZE + index;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        IConfigurableSlot slot = this.parentWidget.getDisplay(this.getIndex());
        if (slot.getConfig() == null) {
            if (mouseOverConfig(mouseX, mouseY)) {
                List<Component> hoverStringList = new ArrayList<>();
                hoverStringList.add(Component.translatable("gtceu.gui.config_slot"));
                if (this.parentWidget.isAutoPull()) {
                    hoverStringList.add(Component.translatable("gtceu.gui.config_slot.auto_pull_managed"));
                } else {
                    hoverStringList.add(Component.translatable("gtceu.gui.config_slot.set"));
                    hoverStringList.add(Component.translatable("gtceu.gui.config_slot.scroll"));
                }
                hoverStringList.add(Component.translatable("gtceu.gui.config_slot.remove"));
                graphics.renderTooltip(Minecraft.getInstance().font, hoverStringList, Optional.empty(), mouseX, mouseY);
            }
        } else {
            GenericStack item = null;
            if (mouseOverConfig(mouseX, mouseY)) {
                item = slot.getConfig();
            } else if (mouseOverStock(mouseX, mouseY)) {
                item = slot.getStock();
            }
            if (item != null) {
                graphics.renderTooltip(Minecraft.getInstance().font, GenericStack.wrapInItemStack(item), mouseX,
                        mouseY);
            }
        }
    }

    protected boolean mouseOverConfig(double mouseX, double mouseY) {
        Position position = getPosition();
        return isMouseOver(position.x, position.y, 18, 18, mouseX, mouseY);
    }

    protected boolean mouseOverStock(double mouseX, double mouseY) {
        Position position = getPosition();
        return isMouseOver(position.x, position.y + 18, 18, 18, mouseX, mouseY);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawSelectionOverlay(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.disableDepthTest();
        RenderSystem.colorMask(true, true, true, false);
        drawGradientRect(graphics, x, y, width, height, -2130706433, -2130706433);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
    }

    protected boolean isStackValidForSlot(GenericStack stack) {
        if (stack == null || stack.amount() < 0) return false;
        return parentWidget.hasStackInConfig(stack);
    }

    // custom draw

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        Position position = getPosition();
        IConfigurableSlot slot = this.parentWidget.getDisplay(this.getIndex());
        GenericStack config = slot.getConfig();
        GenericStack stock = slot.getStock();
        drawSlots(graphics, mouseX, mouseY, position.x, position.y);
        if (this.select) {
            GuiTextures.SELECT_BOX.draw(graphics, mouseX, mouseY, position.x, position.y, 18, 18);
        }
        int stackX = position.x + 1;
        int stackY = position.y + 1;
        if (config != null) {
            if (config.what() instanceof AEItemKey itemKey) {
                ItemStack stack = new ItemStack(itemKey.getItem(), (int) config.amount());
                stack.setCount(1);
                drawItemStack(graphics, stack, stackX, stackY, 0xFFFFFFFF, null);
            } else if (config.what() instanceof AEFluidKey) {
                var stack = AEUtil.toFluidStack(config);
                if (!stack.isEmpty()) {
                    DrawerHelper.drawFluidForGui(graphics, stack, config.amount(), stackX, stackY, 16, 16);
                }
            }
            if (!this.parentWidget.isAutoPull()) {
                String amount = TextFormattingUtil.formatLongToCompactString(config.amount(), 4);
                drawStringFixedCorner(graphics, amount, stackX + 17, stackY + 17, 16777215, true, 0.5f);
            }
        }

        if (stock != null) {
            if (stock.what() instanceof AEItemKey itemKey) {
                ItemStack stack = new ItemStack(itemKey.getItem(), (int) stock.amount());
                stack.setCount(1);
                drawItemStack(graphics, stack, stackX, stackY + 18, 0xFFFFFFFF, null);
            } else if (stock.what() instanceof AEFluidKey) {
                var stack = AEUtil.toFluidStack(stock);
                if (!stack.isEmpty()) {
                    DrawerHelper.drawFluidForGui(graphics, stack, stock.amount(), stackX, stackY + 18, 16, 16);
                }
            }
            if (stock.amount() > 0) {
                drawStringFixedCorner(graphics, stock.what().formatAmount(stock.amount(), AmountFormat.SLOT),
                        stackX + 17, stackY + 18 + 17, 16777215, true, 0.5f);
            }
            if (mouseOverConfig(mouseX, mouseY)) {
                drawSelectionOverlay(graphics, stackX, stackY, 16, 16);
            } else if (mouseOverStock(mouseX, mouseY)) {
                drawSelectionOverlay(graphics, stackX, stackY + 18, 16, 16);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawSlots(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        if (parentWidget.isAutoPull()) {
            GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, x, y, 18, 18);
            GuiTextures.CONFIG_ARROW_DARK.draw(graphics, mouseX, mouseY, x, y, 18, 18);
        } else {
            GuiTextures.FLUID_SLOT.draw(graphics, mouseX, mouseY, x, y, 18, 18);
            GuiTextures.CONFIG_ARROW.draw(graphics, mouseX, mouseY, x, y, 18, 18);
        }
        GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, x, y + 18, 18, 18);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseOverConfig(mouseX, mouseY)) {
            if (parentWidget.isAutoPull()) {
                return false;
            }

            if (button == 1) {
                writeClientAction(REMOVE_ID, buf -> {});
                this.parentWidget.disableAmount();
            } else if (button == 0) {
                ItemStack item = this.gui.getModularUIContainer().getCarried();
                FluidStack fluid = FluidTransferHelper.getFluidContained(item);
                if (fluid != null) {
                    writeClientAction(FLUID_UPDATE_ID, fluid::writeToBuf);
                } else if (!item.isEmpty()) {
                    writeClientAction(ITEM_UPDATE_ID, buf -> buf.writeItem(item));
                }
                this.parentWidget.enableAmount(this.getIndex());
                this.select = true;
            }
        }
        return false;
    }

    // handler network

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        super.handleClientAction(id, buffer);
        int index = this.getIndex();
        IConfigurableSlot slot = this.parentWidget.getConfig(index);
        if (id == REMOVE_ID) {
            slot.setConfig(null);
            writeUpdateInfo(REMOVE_ID, buf -> {});
        }

        if (id == ITEM_UPDATE_ID) {
            ItemStack item = buffer.readItem();
            var stack = GenericStack.fromItemStack(item);
            if (isStackValidForSlot(stack)) return;
            // item set
            this.parentWidget.setItemConfig(index, stack);
            if (!item.isEmpty()) {
                writeUpdateInfo(ITEM_UPDATE_ID, buf -> buf.writeItem(item));
            }
        }

        if (id == FLUID_UPDATE_ID) {
            FluidStack fluid = FluidStack.readFromBuf(buffer);
            var stack = AEUtil.fromFluidStack(fluid);
            if (isStackValidForSlot(stack)) return;
            this.parentWidget.setFluidConfig(index, stack);
            if (fluid != FluidStack.empty()) {
                writeUpdateInfo(FLUID_UPDATE_ID, fluid::writeToBuf);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        super.readUpdateInfo(id, buffer);
        int index = this.getIndex();
        IConfigurableSlot slot = this.parentWidget.getDisplay(index);
        if (id == REMOVE_ID) {
            slot.setConfig(null);
        }
        if (id == ITEM_UPDATE_ID) {
            ItemStack item = buffer.readItem();
            this.parentWidget.setItemConfig(index, new GenericStack(AEItemKey.of(item.getItem(), item.getTag()), item.getCount()));
        }
        if (id == FLUID_UPDATE_ID) {
            FluidStack fluid = FluidStack.create(Registries.getFluid(buffer.readResourceLocation()),
                    buffer.readVarLong());
            this.parentWidget.setFluidConfig(index, new GenericStack(AEFluidKey.of(fluid.getFluid()), fluid.getAmount()));
        }
    }

    // Interface IGhostItemTarget and IGhostFluidTarget

    @OnlyIn(Dist.CLIENT)
    @Override
    public Rect2i getRectangleBox() {
        Rect2i rectangle = toRectangleBox();
        rectangle.setHeight(rectangle.getHeight() / 2);
        return rectangle;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public List<Target> getPhantomTargets(Object ingredient) {
        Object result = convertIngredient(ingredient);
        final Rect2i rectangle = getRectangleBox();
        if (result instanceof ItemStack) {
            return Lists.newArrayList(new Target() {

                @NotNull
                @Override
                public Rect2i getArea() {
                    return rectangle;
                }

                @Override
                public void accept(Object ingredient) {
                    ingredient = convertIngredientItem(ingredient);

                    if (ingredient instanceof ItemStack stack) {
                        acceptItem(stack);
                    }
                }
            });
        } else if (result instanceof FluidStack && drainFrom(ingredient) != null) {
            return Lists.newArrayList(new Target[] { new Target() {

                @NotNull
                public Rect2i getArea() {
                    return rectangle;
                }

                public void accept(@NotNull Object ingredient) {
                    ingredient = convertIngredientFluid(ingredient);

                    FluidStack ingredientStack;
                    if (ingredient instanceof FluidStack fluidStack) {
                        ingredientStack = fluidStack;
                    } else {
                        ingredientStack = drainFrom(ingredient);
                    }

                    if (ingredientStack != null) {
                        acceptFluid(ingredientStack);
                    }
                }
            } });
        }
        return Collections.emptyList();
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Object convertIngredient(Object ingredient) {
        Object result = IGhostItemTarget.super.convertIngredient(ingredient);
        if (result instanceof ItemStack stack && !stack.isEmpty()) return result;
        result = this.convertIngredientFluid(ingredient);
        return result;
    }

    @OnlyIn(Dist.CLIENT)
    protected Object convertIngredientItem(Object ingredient) {
        return IGhostItemTarget.super.convertIngredient(ingredient);
    }

    @OnlyIn(Dist.CLIENT)
    protected Object convertIngredientFluid(Object ingredient) {
        if (LDLib.isJeiLoaded() && ingredient instanceof ITypedIngredient<?> type) {
            if (type.getIngredient() instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
                return FluidStack.create(fluidStack.getFluid(), fluidStack.getAmount(), fluidStack.getTag());
            }
        }
        return IGhostFluidTarget.super.convertIngredient(ingredient);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void acceptItem(ItemStack itemStack) {
        writeClientAction(ITEM_UPDATE_ID, buf -> buf.writeItem(itemStack));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void acceptFluid(FluidStack fluidStack) {
        if (fluidStack.getRawFluid() != Fluids.EMPTY && fluidStack.getAmount() <= 0L) {
            fluidStack.setAmount(1000L);
        }

        if (!fluidStack.isEmpty()) {
            writeClientAction(FLUID_UPDATE_ID, fluidStack::writeToBuf);
        }
    }
}
