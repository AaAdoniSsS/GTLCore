package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureFluidSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureItemSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.pattern.EncodedPatternItem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.gtlcore.gtlcore.utils.NumberUtils.saturatedAdd;

public class MEStockingPatternBufferPartMachine extends MEPatternBufferPartMachine implements IModifiableSyncOffset {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingPatternBufferPartMachine.class, MEPatternBufferPartMachine.MANAGED_FIELD_HOLDER);

    protected static final int CONFIG_SIZE = 32;

    @Persisted
    protected final ExportOnlyAEStockingItemList stockItemHandler;

    @Persisted
    protected final ExportOnlyAEStockingFluidList stockFluidHandler;

    @Persisted
    private int syncOffset;

    @DescSynced
    @Setter
    protected int page = 1;

    public MEStockingPatternBufferPartMachine(IMachineBlockEntity holder, int maxPatternCount, IO io) {
        super(holder, maxPatternCount, io);
        this.stockItemHandler = new ExportOnlyAEStockingItemList(this, CONFIG_SIZE);
        this.stockFluidHandler = new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE);
    }

    @Override
    protected MEPatternBufferRecipeHandlerTrait createRecipeHandler(IO io) {
        return super.createRecipeHandler(io);
    }

    @Override
    protected InternalSlot createInternalSlot(int slotIndex) {
        return new StockingPatternBufferInternalSlot(slotIndex);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(1, () -> {
                stockItemHandler.onConfigChanged();
                stockFluidHandler.onConfigChanged();
                syncStockInput();
            }));
        }
    }

    @Override
    protected void update() {
        super.update();
        int offset = getOffset();
        if (getOffsetTimer() % (offset == 0 ? ME_UPDATE_INTERVAL : offset) == 0) {
            syncStockInput();
        }
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new AdvancedMEConfigurator(this::setOffset, this::getOffset));
    }

    @Override
    public @NotNull Widget createUIWidget() {
        int rowSize = 9;
        int colSize = maxPatternCount / rowSize;
        int width = 18 * rowSize + 16;
        int patternBottom = 18 * colSize + 16;
        int stockLabelY = patternBottom + 6;
        int stockWidgetY = stockLabelY + 12;
        var group = new WidgetGroup(0, 0, width, stockWidgetY + 95);

        group.addWidget(new LabelWidget(8, 2,
                () -> this.isOnline ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline"));

        group.addWidget(new AETextInputButtonWidget(18 * rowSize + 8 - 70, 2, 70, 10)
                .setText(customName)
                .setOnConfirm(this::setCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));

        final var catalystUIManager = new MEPatternCatalystUIManager(group.getSizeWidth() + 4, catalystItems, catalystFluids, cacheRecipeCount, this::removeSlotFromGTRecipeCache);
        group.waitToAdded(catalystUIManager);

        int index = 0;
        for (int y = 0; y < colSize; ++y) {
            for (int x = 0; x < rowSize; ++x) {
                int finalI = index;

                var slot = new AEPatternViewExtendSlotWidget(getPatternInventory(), index++, x * 18 + 8, y * 18 + 14)
                        .setOnMiddleClick(() -> catalystUIManager.toggleFor(finalI))
                        .setOnPatternSlotChanged(() -> this.onPatternChange(finalI))
                        .setOccupiedTexture(GuiTextures.SLOT)
                        .setItemHook(stack -> {
                            if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem iep) {
                                final ItemStack out = iep.getOutput(stack);
                                if (!out.isEmpty()) return out;
                            }
                            return stack;
                        })
                        .setOnAddedTooltips((s, l) -> {
                            if (cacheRecipe[finalI]) {
                                l.add(Component.translatable("gtceu.machine.pattern.recipe.cache"));
                            }
                        })
                        .setBackground(GuiTextures.SLOT, GuiTextures.PATTERN_OVERLAY);
                group.addWidget(slot);
            }
        }

        group.addWidget(new LabelWidget(8, stockLabelY, () -> Component.translatable("gui.gtlcore.stock_input_config").getString()));
        group.addWidget(new LabelWidget(width - 30, stockLabelY,
                () -> FormattingUtil.formatNumbers(stockItemHandler.configList.size() + stockFluidHandler.configList.size()) + " / " + CONFIG_SIZE));
        group.addWidget(new AEDualConfigWidget((width - 144) / 2, stockWidgetY, stockItemHandler, stockFluidHandler, this::setPage, page));

        return group;
    }

    @Override
    public int getOffset() {
        return syncOffset;
    }

    @Override
    public void setOffset(int offset) {
        this.syncOffset = Math.max(0, offset);
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    protected void onStockInputConfigChanged() {
        if (!isRemote()) {
            syncStockInput();
            invalidateRecipeCaches();
        }
    }

    protected void invalidateRecipeCaches() {
        for (int slot = 0; slot < maxPatternCount; slot++) {
            removeSlotFromGTRecipeCache(slot);
        }
    }

    protected void syncStockInput() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            stockItemHandler.clearStocks();
            stockFluidHandler.clearStocks();
            return;
        }

        IStorageService storageService = grid.getStorageService();
        MEStorage networkStorage = storageService.getInventory();
        stockItemHandler.syncStock(networkStorage);
        stockFluidHandler.syncStock(networkStorage);
    }

    protected @Nullable AEItemKey findStockItemKey(Ingredient ingredient, Object2LongMap<AEItemKey> internal, Object2LongMap<AEItemKey> catalyst, long needAmount, boolean includeCatalyst) {
        for (ItemStack item : ingredient.getItems()) {
            if (item.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(item);
            long amount = saturatedAdd(internal.getLong(key), stockItemHandler.getAvailableAmount(key));
            if (includeCatalyst) amount = saturatedAdd(amount, catalyst.getLong(key));
            if (amount >= needAmount) return key;
        }
        for (AEItemKey key : stockItemHandler.configList) {
            if (!key.matches(ingredient)) continue;
            long amount = saturatedAdd(internal.getLong(key), stockItemHandler.getAvailableAmount(key));
            if (includeCatalyst) amount = saturatedAdd(amount, catalyst.getLong(key));
            if (amount >= needAmount) return key;
        }
        return null;
    }

    protected @Nullable AEFluidKey findStockFluidKey(FluidIngredient ingredient, Object2LongMap<AEFluidKey> internal, Object2LongMap<AEFluidKey> catalyst, long needAmount, boolean includeCatalyst) {
        for (FluidStack stack : ingredient.getStacks()) {
            if (stack.isEmpty()) continue;
            AEFluidKey key = AEFluidKey.of(stack.getFluid());
            long amount = saturatedAdd(internal.getLong(key), stockFluidHandler.getAvailableAmount(key));
            if (includeCatalyst) amount = saturatedAdd(amount, catalyst.getLong(key));
            if (amount >= needAmount) return key;
        }
        for (AEFluidKey key : stockFluidHandler.configList) {
            if (!AEUtils.testFluidIngredient(ingredient, key)) continue;
            long amount = saturatedAdd(internal.getLong(key), stockFluidHandler.getAvailableAmount(key));
            if (includeCatalyst) amount = saturatedAdd(amount, catalyst.getLong(key));
            if (amount >= needAmount) return key;
        }
        return null;
    }

    protected boolean consumeStockItem(Object2LongMap<AEItemKey> internal, AEItemKey key, long needAmount) {
        long internalAmount = internal.getLong(key);
        long consumedInternal = Math.min(internalAmount, needAmount);
        if (consumedInternal > 0) {
            long leftInternal = internalAmount - consumedInternal;
            if (leftInternal <= 0) internal.removeLong(key);
            else internal.put(key, leftInternal);
            needAmount -= consumedInternal;
        }
        return needAmount <= 0 || stockItemHandler.extractStock(key, needAmount) >= needAmount;
    }

    protected boolean consumeStockFluid(Object2LongMap<AEFluidKey> internal, AEFluidKey key, long needAmount) {
        long internalAmount = internal.getLong(key);
        long consumedInternal = Math.min(internalAmount, needAmount);
        if (consumedInternal > 0) {
            long leftInternal = internalAmount - consumedInternal;
            if (leftInternal <= 0) internal.removeLong(key);
            else internal.put(key, leftInternal);
            needAmount -= consumedInternal;
        }
        return needAmount <= 0 || stockFluidHandler.extractStock(key, needAmount) >= needAmount;
    }

    protected void appendStockItemContents(List<ItemStack> inputs) {
        for (var entry : Object2LongMaps.fastIterable(stockItemHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                inputs.add(entry.getKey().toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
        }
    }

    protected void appendStockFluidContents(List<FluidStack> inputs) {
        for (var entry : Object2LongMaps.fastIterable(stockFluidHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                inputs.add(FluidStack.create(entry.getKey().getFluid(), amount));
            }
        }
    }

    protected void addStockItemMap(Object2LongOpenHashMap<ItemStack> map) {
        for (var entry : Object2LongMaps.fastIterable(stockItemHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                map.addTo(entry.getKey().toStack(), amount);
            }
        }
    }

    protected void addStockFluidMap(Object2LongOpenHashMap<FluidStack> map) {
        for (var entry : Object2LongMaps.fastIterable(stockFluidHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                map.addTo(FluidStack.create(entry.getKey().getFluid(), 1), amount);
            }
        }
    }

    protected class StockingPatternBufferInternalSlot extends PatternBufferInternalSlot {

        public StockingPatternBufferInternalSlot(int slotIndex) {
            super(slotIndex);
        }

        @Override
        public boolean isItemActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getItemInventory().isEmpty() || !sharedCatalystInventory.isEmpty() ||
                            !getCircuitForRecipe(getSlotIndex()).isEmpty() || hasItemCatalystInventory() ||
                            stockItemHandler.hasConfig()) :
                    (!getItemInventory().isEmpty() || stockItemHandler.hasConfig()));
        }

        @Override
        public boolean isFluidActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getFluidInventory().isEmpty() || !sharedCatalystTank.isEmpty() ||
                            hasFluidCatalystInventory() || stockFluidHandler.hasConfig()) :
                    (!getFluidInventory().isEmpty() || stockFluidHandler.hasConfig()));
        }

        @Override
        public ObjectList<ItemStack> getLimitItemStackInput() {
            var inputs = super.getLimitItemStackInput();
            appendStockItemContents(inputs);
            return inputs;
        }

        @Override
        public ObjectList<FluidStack> getLimitFluidStackInput() {
            var inputs = super.getLimitFluidStackInput();
            appendStockFluidContents(inputs);
            return inputs;
        }

        @Override
        public Object2LongMap<ItemStack> getItemStackInputMap() {
            var map = new Object2LongOpenHashMap<ItemStack>();
            for (var entry : Object2LongMaps.fastIterable(super.getItemStackInputMap())) {
                map.addTo(entry.getKey(), entry.getLongValue());
            }
            addStockItemMap(map);
            return map;
        }

        @Override
        public Object2LongMap<FluidStack> getFluidStackInputMap() {
            var map = new Object2LongOpenHashMap<FluidStack>();
            for (var entry : Object2LongMaps.fastIterable(super.getFluidStackInputMap())) {
                map.addTo(entry.getKey(), entry.getLongValue());
            }
            addStockFluidMap(map);
            return map;
        }

        @Override
        public boolean handleItemInternal(Object2LongMap<Ingredient> left, int leftCircuit, boolean simulate) {
            if (!stockItemHandler.hasConfig()) {
                return super.handleItemInternal(left, leftCircuit, simulate);
            }
            if (left.isEmpty() && leftCircuit < 0) return true;

            if (simulate && leftCircuit > 0 && leftCircuit != getCacheManager().getCircuitCache()) {
                return false;
            }

            var itemInventory = getItemInventory();
            var catalystInventory = getItemCatalystInventory();
            for (var entry : Object2LongMaps.fastIterable(left)) {
                Ingredient ingredient = entry.getKey();
                long needAmount = entry.getLongValue();
                if (needAmount <= 0) continue;
                AEItemKey key = findStockItemKey(ingredient, itemInventory, catalystInventory, needAmount, simulate);
                if (key == null) return false;
            }

            if (!simulate) {
                for (var it = Object2LongMaps.fastIterator(left); it.hasNext();) {
                    var entry = it.next();
                    Ingredient ingredient = entry.getKey();
                    long needAmount = entry.getLongValue();
                    if (needAmount <= 0) {
                        it.remove();
                        continue;
                    }

                    AEItemKey key = findStockItemKey(ingredient, itemInventory, Object2LongMaps.emptyMap(), needAmount, false);
                    if (key == null || !consumeStockItem(itemInventory, key, needAmount)) return false;
                    it.remove();
                }
            }

            return true;
        }

        @Override
        public boolean handleFluidInternal(Object2LongMap<FluidIngredient> left, boolean simulate) {
            if (!stockFluidHandler.hasConfig()) {
                return super.handleFluidInternal(left, simulate);
            }
            if (left.isEmpty()) return true;

            var fluidInventory = getFluidInventory();
            var catalystInventory = getFluidCatalystInventory();
            for (var entry : Object2LongMaps.fastIterable(left)) {
                FluidIngredient ingredient = entry.getKey();
                long needAmount = entry.getLongValue();
                if (needAmount <= 0) continue;
                AEFluidKey key = findStockFluidKey(ingredient, fluidInventory, catalystInventory, needAmount, simulate);
                if (key == null) return false;
            }

            if (!simulate) {
                for (var it = Object2LongMaps.fastIterator(left); it.hasNext();) {
                    var entry = it.next();
                    FluidIngredient ingredient = entry.getKey();
                    long needAmount = entry.getLongValue();
                    if (needAmount <= 0) {
                        it.remove();
                        continue;
                    }

                    AEFluidKey key = findStockFluidKey(ingredient, fluidInventory, Object2LongMaps.emptyMap(), needAmount, false);
                    if (key == null || !consumeStockFluid(fluidInventory, key, needAmount)) return false;
                    it.remove();
                }
            }

            return true;
        }
    }

    protected class ExportOnlyAEStockingItemList extends ExportOnlyAEItemList {

        protected final ObjectArrayList<AEItemKey> configList = new ObjectArrayList<>();
        protected final IntArrayList configIndexList = new IntArrayList();
        protected final Object2LongOpenHashMap<AEItemKey> stockMap = new Object2LongOpenHashMap<>();

        public ExportOnlyAEStockingItemList(MetaMachine holder, int slots) {
            super(holder, slots, ExportOnlyAEStockingItemSlot::new);
            stockMap.defaultReturnValue(0);
            for (ExportOnlyAEItemSlot slot : inventory) {
                ((IMESlot) slot).setOnConfigChanged(() -> {
                    onConfigChanged();
                    onStockInputConfigChanged();
                });
            }
        }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onConfigChanged();
        }

        public void clearStocks() {
            stockMap.clear();
            for (ExportOnlyAEItemSlot slot : inventory) {
                slot.setStock(null);
            }
        }

        public void syncStock(MEStorage networkStorage) {
            stockMap.clear();
            for (ExportOnlyAEItemSlot slot : inventory) {
                GenericStack config = slot.getConfig();
                if (config != null && config.what() instanceof AEItemKey key) {
                    long amount = networkStorage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    if (amount > 0) {
                        slot.setStock(new GenericStack(key, amount));
                        stockMap.addTo(key, amount);
                        continue;
                    }
                }
                slot.setStock(null);
            }
        }

        public void onConfigChanged() {
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEItemKey key) {
                    configList.add(key);
                    configIndexList.add(i);
                }
            }
        }

        public boolean hasConfig() {
            return !configList.isEmpty();
        }

        public long getAvailableAmount(AEItemKey key) {
            IGrid grid = getMainNode().getGrid();
            return grid == null ? 0 : configList.contains(key) ? grid.getStorageService().getInventory()
                    .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource) : 0;
        }

        public long extractStock(AEItemKey key, long amount) {
            if (amount <= 0 || getMainNode().getGrid() == null) return 0;
            long extracted = getMainNode().getGrid().getStorageService().getInventory()
                    .extract(key, amount, Actionable.MODULATE, actionSource);
            if (extracted <= 0) return 0;

            long left = getAvailableAmount(key);
            if (left <= 0) stockMap.removeLong(key);
            else stockMap.put(key, left);
            for (int index : configIndexList) {
                ExportOnlyAEItemSlot slot = inventory[index];
                GenericStack config = slot.getConfig();
                if (config != null && key.equals(config.what())) {
                    slot.setStock(left > 0 ? new GenericStack(key, left) : null);
                    break;
                }
            }
            return extracted;
        }
    }

    protected static class ExportOnlyAEStockingItemSlot extends ExportOnlyAEConfigureItemSlot {

        public ExportOnlyAEStockingItemSlot() {
            super();
        }

        public ExportOnlyAEStockingItemSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
        }

        @Override
        public @NotNull ExportOnlyAEStockingItemSlot copy() {
            return new ExportOnlyAEStockingItemSlot(this.config == null ? null : copy(this.config), this.stock == null ? null : copy(this.stock));
        }
    }

    protected class ExportOnlyAEStockingFluidList extends ExportOnlyAEFluidList {

        protected final ObjectArrayList<AEFluidKey> configList = new ObjectArrayList<>();
        protected final IntArrayList configIndexList = new IntArrayList();
        protected final Object2LongOpenHashMap<AEFluidKey> stockMap = new Object2LongOpenHashMap<>();

        public ExportOnlyAEStockingFluidList(MetaMachine holder, int slots) {
            super(holder, slots, ExportOnlyAEStockingFluidSlot::new);
            stockMap.defaultReturnValue(0);
            for (ExportOnlyAEFluidSlot slot : inventory) {
                ((IMESlot) slot).setOnConfigChanged(() -> {
                    onConfigChanged();
                    onStockInputConfigChanged();
                });
            }
        }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onConfigChanged();
        }

        public void clearStocks() {
            stockMap.clear();
            for (ExportOnlyAEFluidSlot slot : inventory) {
                slot.setStock(null);
            }
        }

        public void syncStock(MEStorage networkStorage) {
            stockMap.clear();
            for (ExportOnlyAEFluidSlot slot : inventory) {
                GenericStack config = slot.getConfig();
                if (config != null && config.what() instanceof AEFluidKey key) {
                    long amount = networkStorage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    if (amount > 0) {
                        slot.setStock(new GenericStack(key, amount));
                        stockMap.addTo(key, amount);
                        continue;
                    }
                }
                slot.setStock(null);
            }
        }

        public void onConfigChanged() {
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEFluidKey key) {
                    configList.add(key);
                    configIndexList.add(i);
                }
            }
        }

        public boolean hasConfig() {
            return !configList.isEmpty();
        }

        public long getAvailableAmount(AEFluidKey key) {
            IGrid grid = getMainNode().getGrid();
            return grid == null ? 0 : configList.contains(key) ? grid.getStorageService().getInventory()
                    .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource) : 0;
        }

        public long extractStock(AEFluidKey key, long amount) {
            if (amount <= 0 || getMainNode().getGrid() == null) return 0;
            long extracted = getMainNode().getGrid().getStorageService().getInventory()
                    .extract(key, amount, Actionable.MODULATE, actionSource);
            if (extracted <= 0) return 0;

            long left = getAvailableAmount(key);
            if (left <= 0) stockMap.removeLong(key);
            else stockMap.put(key, left);
            for (int index : configIndexList) {
                ExportOnlyAEFluidSlot slot = inventory[index];
                GenericStack config = slot.getConfig();
                if (config != null && key.equals(config.what())) {
                    slot.setStock(left > 0 ? new GenericStack(key, left) : null);
                    break;
                }
            }
            return extracted;
        }
    }

    protected static class ExportOnlyAEStockingFluidSlot extends ExportOnlyAEConfigureFluidSlot {

        public ExportOnlyAEStockingFluidSlot() {
            super();
        }

        public ExportOnlyAEStockingFluidSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
        }

        @Override
        public @NotNull ExportOnlyAEStockingFluidSlot copy() {
            return new ExportOnlyAEStockingFluidSlot(this.config == null ? null : copy(this.config), this.stock == null ? null : copy(this.stock));
        }
    }
}
