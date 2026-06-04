package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class MEPatternCatalystUIManager extends WidgetGroup {

    static final int SLOT_SIZE = 18;
    static final int PAD_OUT = 8;
    static final int PAD_IN = 4;
    static final int MAX_COLS = 9;
    private static final int[] OPTIMAL_COLUMNS = new int[MAX_COLS + 1];
    // dragging
    protected double lastDeltaX, lastDeltaY;
    protected int dragOffsetX, dragOffsetY;
    protected boolean isDragging;

    static {
        for (int slots = 1; slots <= MAX_COLS; slots++) {
            OPTIMAL_COLUMNS[slots] = calculateOptimalColumnsInternal(slots);
        }
    }

    private int lastIndex = -1;

    private final IItemTransfer[] itemTransfers;
    private final FluidTransferList[] fluidTankTransfers;
    private final byte[] cacheRecipeCount;
    private final IntConsumer onCacheCountChange;

    public MEPatternCatalystUIManager(int dockX, IItemTransfer[] itemTransfers, FluidTransferList[] fluidTankTransfers, byte[] cacheRecipeCount, IntConsumer onCacheCountChange) {
        // 初始高度给个最小值，后面会根据内容 resize
        super(dockX, 16, 16, 16);
        this.setBackground(GuiTextures.BACKGROUND);
        this.setVisible(false).setActive(false);
        this.itemTransfers = itemTransfers;
        this.fluidTankTransfers = fluidTankTransfers;
        this.cacheRecipeCount = cacheRecipeCount;
        this.onCacheCountChange = onCacheCountChange;
    }

    /**
     * 为指定 pattern 槽位切换显示/隐藏：
     * - 如果点击同一slot执行开关
     * - 否则即时创建新的催化剂 UI 并显示它。
     */
    public void toggleFor(int index) {
        if (lastIndex == index) {
            this.setVisible(!this.isVisible()).setActive(!this.isActive());
            return;
        }
        show(index, itemTransfers[index], fluidTankTransfers[index]);
    }

    private void show(int index, IItemTransfer itemInventory, FluidTransferList tanks) {
        this.clearAllWidgets();

        final int itemSlots = (itemInventory != null) ? itemInventory.getSlots() : 0;
        final int fluidTanks = (tanks != null) ? tanks.transfers.length : 0;

        int currentY = 3;
        int maxWidth = 0;

        Widget cacheCountWidget = createCacheCountInputWidget(() -> (int) cacheRecipeCount[index], value -> {
            if (cacheRecipeCount[index] != value.byteValue()) {
                cacheRecipeCount[index] = value.byteValue();
                onCacheCountChange.accept(index);
            }
        });
        cacheCountWidget.setSelfPosition(0, currentY);
        this.addWidget(cacheCountWidget);
        currentY += cacheCountWidget.getSize().height + 4;
        maxWidth = Math.max(maxWidth, cacheCountWidget.getSize().width + PAD_OUT);

        if (itemSlots > 0) {
            Widget itemContainer = createInventoryContainer(itemInventory, itemSlots);
            itemContainer.setSelfPosition(0, currentY);
            this.addWidget(itemContainer);
            currentY += itemContainer.getSize().height;
            maxWidth = Math.max(maxWidth, itemContainer.getSize().width);
        }

        if (fluidTanks > 0) {
            Widget fluidContainer = createFluidContainer(tanks, fluidTanks);
            fluidContainer.setSelfPosition(0, currentY);
            this.addWidget(fluidContainer);
            currentY += fluidContainer.getSize().height;
            maxWidth = Math.max(maxWidth, fluidContainer.getSize().width);
        }

        if (maxWidth == 0) maxWidth = 16;
        if (currentY <= 0) currentY = 16;
        this.setSize(maxWidth, currentY);
        this.setVisible(true).setActive(true);
        lastIndex = index;
    }

    private static @NotNull Widget createCacheCountInputWidget(Supplier<Integer> supplier, Consumer<Integer> consumer) {
        WidgetGroup group = new WidgetGroup(new Position(0, 0));
        group.addWidget(new ExtendLabelWidget(PAD_OUT, 2, Component.translatable("gui.gtlcore.pattern_buffer_gui_1")));
        group.addWidget(new IntInputWidget(new Position(PAD_OUT, PAD_OUT + 4), supplier, consumer)
                .setMin(1)
                .setMax((int) Byte.MAX_VALUE));
        return group;
    }

    private static @NotNull Widget createInventoryContainer(IItemTransfer inventory, int slots) {
        final int cols = calculateOptimalColumns(slots);
        final int rows = (slots + cols - 1) / cols;
        final int containerW = PAD_IN * 2 + cols * SLOT_SIZE;
        final int containerH = PAD_IN * 2 + rows * SLOT_SIZE;
        final int groupW = PAD_OUT * 2 + containerW;
        final int groupH = PAD_OUT * 2 + containerH;

        WidgetGroup group = new WidgetGroup(0, 0, groupW, groupH);
        group.addWidget(new ExtendLabelWidget(PAD_OUT, 2, Component.translatable("gui.gtlcore.pattern_buffer_gui_2")));
        WidgetGroup container = new WidgetGroup(PAD_OUT, PAD_OUT + 4, containerW, containerH);

        int index = 0;
        for (int y = 0; y < rows && index < slots; ++y) {
            for (int x = 0; x < cols && index < slots; ++x) {
                int sx = PAD_IN + x * SLOT_SIZE;
                int sy = PAD_IN + y * SLOT_SIZE;
                container.addWidget(createSlotWidget(inventory, index++, sx, sy));
            }
        }

        container.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(container);
        return group;
    }

    private static @NotNull Widget createFluidContainer(FluidTransferList tanks, int slots) {
        final int cols = calculateOptimalColumns(slots);
        final int rows = (slots + cols - 1) / cols;
        final int containerW = PAD_IN * 2 + cols * SLOT_SIZE;
        final int containerH = PAD_IN * 2 + rows * SLOT_SIZE;
        final int groupW = PAD_OUT * 2 + containerW;
        final int groupH = PAD_OUT * 2 + containerH;

        WidgetGroup group = new WidgetGroup(0, 0, groupW, groupH);
        group.addWidget(new ExtendLabelWidget(PAD_OUT, 2, Component.translatable("gui.gtlcore.pattern_buffer_gui_3")));
        WidgetGroup container = new WidgetGroup(PAD_OUT, PAD_OUT + 4, containerW, containerH);

        int index = 0;
        for (int y = 0; y < rows && index < slots; ++y) {
            for (int x = 0; x < cols && index < slots; ++x) {
                int sx = PAD_IN + x * SLOT_SIZE;
                int sy = PAD_IN + y * SLOT_SIZE;
                container.addWidget(createTankWidget(tanks.transfers[index], sx, sy));
                index++;
            }
        }

        container.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(container);
        return group;
    }

    private static int calculateOptimalColumns(int slots) {
        if (slots <= 0) return 1;
        if (slots <= MAX_COLS) return OPTIMAL_COLUMNS[slots];
        return MAX_COLS;
    }

    private static int calculateOptimalColumnsInternal(int slots) {
        if (slots <= 0) return 1;
        int bestCols = 1;
        double bestRatio = Double.MAX_VALUE;
        for (int cols = 1; cols <= Math.min(slots, MAX_COLS); cols++) {
            int rows = (slots + cols - 1) / cols;
            double ratio = Math.abs((double) cols / rows - 1.0); // 越接近 1 越“方”
            if (ratio < bestRatio) {
                bestRatio = ratio;
                bestCols = cols;
            }
        }
        return bestCols;
    }

    private static @NotNull Widget createSlotWidget(IItemTransfer inventory, int slotIndex, int sx, int sy) {
        return new SlotWidget(inventory, slotIndex, sx, sy, true, true)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setIngredientIO(IngredientIO.INPUT);
    }

    private static @NotNull Widget createTankWidget(IFluidTransfer storage, int sx, int sy) {
        return new TankWidget(storage, 0, sx, sy, true, true)
                .setBackground(GuiTextures.FLUID_SLOT);
    }

    private boolean isMouseover(int x, int y, int width, int height, double mouseX, double mouseY) {
        boolean b = mouseX >= x && mouseY >= y && x + width > mouseX && y + height > mouseY;
        if (b) {
            l:
            for (var ui : this.widgets) {
                if (ui instanceof WidgetGroup wg) for (var u : wg.widgets)
                    if (u instanceof WidgetGroup w) {
                        int pX = w.getPositionX(), pY = w.getPositionY(), pW = w.getSizeWidth(), pH = w.getSizeHeight();
                        b = !(mouseX >= pX && mouseY >= pY && pX + pW > mouseX && pY + pH > mouseY);
                        if (!b) break l;
                    }
            }
            return b;
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.lastDeltaX = 0;
        this.lastDeltaY = 0;
        this.isDragging = false;
        if (isMouseover(getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), mouseX, mouseY)) {
            isDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button) || isMouseOverElement(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double dx = dragX + lastDeltaX;
        double dy = dragY + lastDeltaY;
        dragX = (int) dx;
        dragY = (int) dy;
        lastDeltaX = dx - dragX;
        lastDeltaY = dy - dragY;
        if (isDragging) {
            this.dragOffsetX += (int) dragX;
            this.dragOffsetY += (int) dragY;
            this.addSelfPosition((int) dragX, (int) dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY) || isMouseOverElement(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.lastDeltaX = 0;
        this.lastDeltaY = 0;
        this.isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button) || isMouseOverElement(mouseX, mouseY);
    }
}
