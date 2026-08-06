package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import com.lowdragmc.lowdraglib.gui.widget.PhantomFluidWidget;
import com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.lowdragmc.lowdraglib.LDLib.isRemote;
import static org.gtlcore.gtlcore.integration.ae2.AEUtils.createListTag;
import static org.gtlcore.gtlcore.integration.ae2.AEUtils.loadInventory;

public class MEOutputFilterHandler implements ITagSerializable<CompoundTag> {

    private final ObjectOpenHashSet<AEItemKey> itemFilterHashSet = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<AEFluidKey> fluidFilterHashSet = new ObjectOpenHashSet<>();
    private final Runnable markDirty;
    private final Runnable updatePriority;
    private final Supplier<Integer> prioritySupplier;
    private final Consumer<Integer> onPriorityChanged;

    @Getter
    @Setter
    protected boolean isItemBlackList;
    @Getter
    @Setter
    protected boolean ignoreItemNbt = true;
    @Getter
    @Setter
    protected boolean isFluidBlackList;
    @Getter
    @Setter
    protected boolean ignoreFluidNbt = true;

    private boolean hasItemFilterChange;
    private boolean hasFluidFilterChange;
    @Getter
    private boolean hasItemFilter;
    @Getter
    private boolean hasFluidFilter;
    private final int row;
    private final int col;

    private final FluidStorage[] filterTanks;
    private final ItemStackTransfer filterSlots;

    public MEOutputFilterHandler(int row, int col, Runnable markDirty, Runnable updatePriority, Supplier<Integer> prioritySupplier, Consumer<Integer> onPriorityChanged) {
        this.row = row;
        this.col = col;
        this.markDirty = markDirty;
        this.updatePriority = updatePriority;
        this.prioritySupplier = prioritySupplier;
        this.onPriorityChanged = onPriorityChanged;
        filterTanks = new FluidStorage[row * col];
        filterSlots = new ItemStackTransfer(row * col);
        Arrays.setAll(filterTanks, i -> new FluidStorage(FluidHelper.getBucket()));
    }

    public WidgetGroup createMainWidgetGroup() {
        final var itemFilter = openItemFilterConfigurator(20, 5, this.row, this.col);
        int height = itemFilter.getSizeHeight() + 5;
        final var fluidFilter = openFluidFilterConfigurator(20, height + 5, this.row, this.col);
        height += fluidFilter.getSizeHeight() + 10;
        final var priorityWidget = new IntInputWidget(new Position(20, height), prioritySupplier, onPriorityChanged)
                .setMin(10)
                .setMax(100000);
        height += priorityWidget.getSizeHeight();
        WidgetGroup widgetGroup = new WidgetGroup(0, 0, itemFilter.getSizeWidth() + 6, height);
        widgetGroup.addWidget(itemFilter);
        widgetGroup.addWidget(fluidFilter);
        widgetGroup.addWidget(priorityWidget);
        return widgetGroup;
    }

    public int getFilterSlotCount() {
        return row * col;
    }

    public @Nullable AEItemKey getItemFilter(int slot) {
        if (slot < 0 || slot >= filterSlots.getSlots()) return null;
        ItemStack stack = filterSlots.getStackInSlot(slot);
        return stack.isEmpty() ? null : AEItemKey.of(stack);
    }

    public @Nullable AEFluidKey getFluidFilter(int slot) {
        if (slot < 0 || slot >= filterTanks.length) return null;
        FluidStack stack = filterTanks[slot].getFluid();
        return stack.isEmpty() ? null : AEFluidKey.of(stack.getFluid(), stack.getTag());
    }

    public void setItemFilter(int slot, @Nullable AEItemKey key) {
        if (slot < 0 || slot >= filterSlots.getSlots()) return;
        filterSlots.setStackInSlot(slot, key == null ? ItemStack.EMPTY : key.toStack(1));
        hasItemFilterChange = true;
        onUIClosed();
    }

    public void setFluidFilter(int slot, @Nullable AEFluidKey key) {
        if (slot < 0 || slot >= filterTanks.length) return;
        filterTanks[slot].setFluid(key == null ? FluidStack.empty() :
                FluidStack.create(key.getFluid(), FluidHelper.getBucket(), key.getTag()));
        hasFluidFilterChange = true;
        onUIClosed();
    }

    public void updateItemFilterSettings(boolean blackList, boolean ignoreNbt) {
        isItemBlackList = blackList;
        ignoreItemNbt = ignoreNbt;
        hasItemFilterChange = true;
        onUIClosed();
    }

    public void updateFluidFilterSettings(boolean blackList, boolean ignoreNbt) {
        isFluidBlackList = blackList;
        ignoreFluidNbt = ignoreNbt;
        hasFluidFilterChange = true;
        onUIClosed();
    }

    protected WidgetGroup openItemFilterConfigurator(int x, int y, int row, int col) {
        WidgetGroup group = new WidgetGroup(x, y, 18 * col + 25, 18 * row);
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                final int index = i * col + j;

                var slot = new PhantomSlotWidget(filterSlots, index, j * 18, i * 18) {

                    @Override
                    public void updateScreen() {
                        super.updateScreen();
                        setMaxStackSize(1);
                    }

                    @Override
                    public void detectAndSendChanges() {
                        super.detectAndSendChanges();
                        setMaxStackSize(1);
                    }
                }
                        .setChangeListener(() -> hasItemFilterChange = true)
                        .setBackground(GuiTextures.SLOT);

                group.addWidget(slot);
            }
        }
        group.addWidget(new ToggleButtonWidget(18 * col + 17, 9, 18, 18,
                GuiTextures.BUTTON_BLACKLIST, this::isItemBlackList, this::setItemBlackList));
        group.addWidget(new ToggleButtonWidget(18 * col + 17, (18) + 9, 18, 18,
                GuiTextures.BUTTON_FILTER_NBT, this::isIgnoreItemNbt, this::setIgnoreItemNbt));
        return group;
    }

    protected WidgetGroup openFluidFilterConfigurator(int x, int y, int row, int col) {
        WidgetGroup group = new WidgetGroup(x, y, 18 * col + 25, 18 * row);
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                final int index = i * col + j;

                var slot = new PhantomFluidWidget(filterTanks[index], 0, j * 18, i * 18, 18, 18,
                        () -> filterTanks[index].getFluid(),
                        fluidStack -> filterTanks[index].setFluid(fluidStack)) {

                    @Override
                    public void updateScreen() {
                        super.updateScreen();
                        setShowAmount(false);
                    }

                    @Override
                    public void detectAndSendChanges() {
                        super.detectAndSendChanges();
                        setShowAmount(false);
                    }
                }
                        .setChangeListener(() -> hasFluidFilterChange = true)
                        .setBackground(GuiTextures.SLOT);

                group.addWidget(slot);
            }
        }
        group.addWidget(new ToggleButtonWidget(18 * col + 17, 9, 18, 18,
                GuiTextures.BUTTON_BLACKLIST, this::isFluidBlackList, this::setFluidBlackList));
        group.addWidget(new ToggleButtonWidget(18 * col + 17, (18) + 9, 18, 18,
                GuiTextures.BUTTON_FILTER_NBT, this::isIgnoreFluidNbt, this::setIgnoreFluidNbt));
        return group;
    }

    protected void onUIClosed() {
        if (isRemote()) return;
        if (hasItemFilterChange || hasFluidFilterChange) {
            if (hasItemFilterChange) {
                itemFilterHashSet.clear();
                for (int i = 0; i < filterSlots.getSlots(); i++) {
                    var itemStack = filterSlots.getStackInSlot(i);
                    if (!itemStack.isEmpty()) {
                        itemFilterHashSet.add(ignoreItemNbt ? AEItemKey.of(itemStack.getItem()) : AEItemKey.of(itemStack));
                    }
                }
                hasItemFilter = !itemFilterHashSet.isEmpty();
                hasItemFilterChange = false;
            }
            if (hasFluidFilterChange) {
                fluidFilterHashSet.clear();
                for (FluidStorage filterTank : filterTanks) {
                    var fluidStack = filterTank.getFluid();
                    if (!fluidStack.isEmpty()) {
                        fluidFilterHashSet.add(ignoreFluidNbt ? AEFluidKey.of(fluidStack.getFluid()) : AEFluidKey.of(fluidStack.getFluid(), fluidStack.getTag()));
                    }
                }
                hasFluidFilter = !fluidFilterHashSet.isEmpty();
                hasFluidFilterChange = false;
            }
            this.markDirty.run();
        }
        this.updatePriority.run();
    }

    public List<Ingredient> testIngredient(List<Ingredient> left) {
        if (!hasItemFilter) return List.of();
        left.removeIf(ingredient -> {
            var items = ingredient.getItems();
            if (items.length == 0) return false;
            return test(items[0]);
        });
        return left;
    }

    public List<FluidIngredient> testFluidIngredient(List<FluidIngredient> left) {
        if (!hasFluidFilter) return List.of();
        left.removeIf(ingredient -> {
            var fluids = ingredient.getStacks();
            if (fluids.length == 0) return false;
            return test(fluids[0]);
        });
        return left;
    }

    protected boolean test(ItemStack itemStack) {
        if (!hasItemFilter) return true;
        final var key = ignoreItemNbt ? AEItemKey.of(itemStack.getItem()) : AEItemKey.of(itemStack);
        return isItemBlackList != itemFilterHashSet.contains(key);
    }

    protected boolean test(FluidStack fluidStack) {
        if (!hasFluidFilter) return true;
        final var key = ignoreFluidNbt ? AEFluidKey.of(fluidStack.getFluid()) : AEFluidKey.of(fluidStack.getFluid(), fluidStack.getTag());
        return isFluidBlackList != fluidFilterHashSet.contains(key);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag itemsTag = createListTag(AEItemKey::toTag, itemFilterHashSet);
        if (!itemsTag.isEmpty()) tag.put("items", itemsTag);
        ListTag fluidsTag = createListTag(AEFluidKey::toTag, fluidFilterHashSet);
        if (!fluidsTag.isEmpty()) tag.put("fluids", fluidsTag);

        tag.putBoolean("isItemBlackList", isItemBlackList);
        tag.putBoolean("ignoreItemNbt", ignoreItemNbt);
        tag.putBoolean("isFluidBlackList", isFluidBlackList);
        tag.putBoolean("ignoreFluidNbt", ignoreFluidNbt);
        tag.put("filterSlots", filterSlots.serializeNBT());

        var list = new ListTag();
        Arrays.stream(filterTanks).map(FluidStorage::serializeNBT).forEach(list::add);
        tag.put("filterTanks", list);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        loadInventory(tag.getList("items", Tag.TAG_COMPOUND), AEItemKey::fromTag, itemFilterHashSet);
        loadInventory(tag.getList("fluids", Tag.TAG_COMPOUND), AEFluidKey::fromTag, fluidFilterHashSet);

        isItemBlackList = tag.getBoolean("isItemBlackList");
        isFluidBlackList = tag.getBoolean("isFluidBlackList");
        ignoreItemNbt = tag.getBoolean("ignoreItemNbt");
        ignoreFluidNbt = tag.getBoolean("ignoreFluidNbt");
        filterSlots.deserializeNBT(tag.getCompound("filterSlots"));

        var list = tag.getList("filterTanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) filterTanks[i].deserializeNBT(list.getCompound(i));

        hasItemFilter = !itemFilterHashSet.isEmpty();
        hasFluidFilter = !fluidFilterHashSet.isEmpty();
    }
}
