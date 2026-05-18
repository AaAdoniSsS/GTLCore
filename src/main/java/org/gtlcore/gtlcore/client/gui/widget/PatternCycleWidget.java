package org.gtlcore.gtlcore.client.gui.widget;

import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PatternCycleWidget extends WidgetGroup {

    private static final int CYCLE_TICKS = 20;

    private final Supplier<List<IPatternDetails>> patternSupplier;
    private final WidgetGroup inputGroup;
    private final WidgetGroup outputGroup;
    private int tick;
    private int displayedIndex = -1;
    private int displayedSize = -1;

    public PatternCycleWidget(int xPosition, int yPosition, int width, int height,
                              Supplier<List<IPatternDetails>> patternSupplier) {
        super(xPosition, yPosition, width, height);
        this.patternSupplier = patternSupplier;

        int startX = Math.max(0, (width - 131) / 2);
        this.inputGroup = new WidgetGroup(startX, 0, 54, 36).setClientSideWidget();
        this.outputGroup = new WidgetGroup(startX + 77, 0, 54, 36).setClientSideWidget();
        Widget arrow = new Widget(startX + 58, 13, 15, 10)
                .setBackground(GuiTextures.PROGRESS_BAR_ARROW.getSubTexture(0, 0, 1, 0.5));

        addWidget(inputGroup);
        addWidget(arrow);
        addWidget(outputGroup);
        displayPreview(Preview.EMPTY);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        writePreview(buffer, getPreview(getInitialPattern()));
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        displayPreview(readPreview(buffer));
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        tick++;

        List<IPatternDetails> patterns = patternSupplier.get();
        if (patterns.isEmpty()) {
            if (displayedIndex != -1 || displayedSize != 0) {
                displayPreview(Preview.EMPTY);
                displayedIndex = -1;
                displayedSize = 0;
                writeUpdateInfo(0, buffer -> writePreview(buffer, Preview.EMPTY));
            }
            return;
        }

        int index = (tick / CYCLE_TICKS) % patterns.size();
        if (index != displayedIndex || patterns.size() != displayedSize) {
            displayedIndex = index;
            displayedSize = patterns.size();
            writeUpdateInfo(0, buffer -> writePreview(buffer, getPreview(patterns.get(index))));
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 0) {
            displayPreview(readPreview(buffer));
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    private @Nullable IPatternDetails getInitialPattern() {
        List<IPatternDetails> patterns = patternSupplier.get();
        return patterns.isEmpty() ? null : patterns.get(0);
    }

    private Preview getPreview(@Nullable IPatternDetails pattern) {
        if (pattern == null) {
            return Preview.EMPTY;
        }

        return new Preview(
                Arrays.stream(pattern.getInputs())
                        .map(PatternCycleWidget::getInputPreviewStack)
                        .filter(Objects::nonNull)
                        .toList(),
                Arrays.stream(pattern.getOutputs()).toList());
    }

    private void displayPreview(Preview preview) {
        displayPatternSlots(inputGroup, preview.inputs().stream());
        displayPatternSlots(outputGroup, preview.outputs().stream());
    }

    private static @Nullable GenericStack getInputPreviewStack(IPatternDetails.IInput input) {
        GenericStack[] possibleInputs = input.getPossibleInputs();
        if (possibleInputs.length == 0) return null;
        return new GenericStack(possibleInputs[0].what(), input.getMultiplier());
    }

    private void displayPatternSlots(WidgetGroup group, Stream<GenericStack> stacks) {
        group.clearAllWidgets();
        Iterator<GenericStack> iterator = stacks.iterator();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int x = col * 18;
                int y = row * 18;
                if (!iterator.hasNext()) {
                    group.addWidget(createItemSlot(ItemStack.EMPTY, x, y));
                    continue;
                }

                GenericStack stack = iterator.next();
                AEKey key = stack.what();
                if (key instanceof AEItemKey itemKey) {
                    group.addWidget(createItemSlot(itemKey.toStack(Ints.saturatedCast(stack.amount())), x, y));
                } else if (key instanceof AEFluidKey fluidKey) {
                    group.addWidget(createTankSlot(fluidKey, stack.amount(), x, y));
                } else {
                    group.addWidget(createItemSlot(ItemStack.EMPTY, x, y));
                }
            }
        }
    }

    private Widget createItemSlot(ItemStack stack, int x, int y) {
        ItemStackTransfer transfer = new ItemStackTransfer(1);
        transfer.setStackInSlot(0, stack);
        SlotWidget widget = new SlotWidget(transfer, 0, x, y, false, false)
                .setBackgroundTexture(GuiTextures.SLOT);
        var handler = widget.getHandler();
        if (handler != null) {
            handler.set(stack);
        }
        return widget;
    }

    private Widget createTankSlot(AEFluidKey fluidKey, long amount, int x, int y) {
        FluidStorage storage = new FluidStorage(Integer.MAX_VALUE);
        storage.setFluid(FluidStack.create(fluidKey.getFluid(), amount));
        return new TankWidget(storage, x, y, false, false)
                .setBackground(GuiTextures.SLOT)
                .setClientSideWidget();
    }

    private static void writePreview(FriendlyByteBuf buffer, Preview preview) {
        writeStacks(buffer, preview.inputs());
        writeStacks(buffer, preview.outputs());
    }

    private static Preview readPreview(FriendlyByteBuf buffer) {
        return new Preview(readStacks(buffer), readStacks(buffer));
    }

    private static void writeStacks(FriendlyByteBuf buffer, List<GenericStack> stacks) {
        buffer.writeVarInt(stacks.size());
        for (GenericStack stack : stacks) {
            GenericStack.writeBuffer(stack, buffer);
        }
    }

    private static List<GenericStack> readStacks(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(GenericStack.readBuffer(buffer));
        }
        return stacks;
    }

    private record Preview(List<GenericStack> inputs, List<GenericStack> outputs) {

        private static final Preview EMPTY = new Preview(List.of(), List.of());
    }
}
