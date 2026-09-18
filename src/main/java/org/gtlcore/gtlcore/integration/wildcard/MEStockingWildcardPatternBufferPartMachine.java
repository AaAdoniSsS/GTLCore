package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureFluidSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureItemSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.StockingWildcardRecipeHandlerTrait;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.gtlcore.gtlcore.utils.NumberUtils.saturatedAdd;

/**
 * Pattern buffer variant backed by one wildcard pattern.
 * <p>
 * The wildcard item is persisted. Expanded pattern slots are rebuilt at runtime, and slot-bound state is restored by
 * matching saved pattern outputs because wildcard expansion order is not stable across reloads.
 */
public class MEStockingWildcardPatternBufferPartMachine extends MEPatternBufferPartMachineBase implements IModifiableSyncOffset {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingWildcardPatternBufferPartMachine.class, MEPatternBufferPartMachineBase.MANAGED_FIELD_HOLDER);

    // ========================================
    // Wildcard Pattern State
    // ========================================

    @Getter
    @Persisted
    private final ItemStackTransfer wildcardPatternSlot;

    private final List<IPatternDetails> expandedPatterns = new ObjectArrayList<>();

    private final Object2IntMap<IPatternDetails> patternToSlotMap = new Object2IntOpenHashMap<>();

    private final List<InternalSlot> internalSlots = new ObjectArrayList<>();

    private final IntSet activeSlotIndices = new IntOpenHashSet();

    private final MEWildcardPatternBufferPersistenceHelper persistenceHelper = new MEWildcardPatternBufferPersistenceHelper();

    // ========================================
    // Runtime Recipe Cache
    // ========================================

    protected final Int2ReferenceMap<GTRecipe> recipeCacheMap = new Int2ReferenceOpenHashMap<>();
    protected IntConsumer removeSlotFromMap = i -> {};

    // ========================================
    // Traits
    // ========================================

    protected final StockingWildcardRecipeHandlerTrait recipeHandler;

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

    @DescSynced
    @Persisted
    public boolean isHiddenTerminal = false;

    @Override
    public boolean isVisibleInTerminal() {
        return !isHiddenTerminal;
    }

    // AE2 terminal wrapper for the persisted wildcard pattern slot.
    private final InternalInventory internalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return wildcardPatternSlot.getStackInSlot(0);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            wildcardPatternSlot.setStackInSlot(0, stack);
            wildcardPatternSlot.onContentsChanged(0);
            onWildcardPatternChange();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return WildcardPatternCompatImpl.isWildcardPattern(stack);
        }
    };

    public MEStockingWildcardPatternBufferPartMachine(IMachineBlockEntity holder, IO io) {
        super(holder, io);

        this.wildcardPatternSlot = new ItemStackTransfer(1);
        this.wildcardPatternSlot.setFilter(WildcardPatternCompatImpl::isWildcardPattern);

        this.patternToSlotMap.defaultReturnValue(-1);

        this.recipeHandler = new StockingWildcardRecipeHandlerTrait(this, io);
        this.stockItemHandler = new ExportOnlyAEStockingItemList(this, CONFIG_SIZE);
        this.stockFluidHandler = new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE);

        getMainNode().addService(IGridTickable.class, new Ticker());
    }

    // ========================================
    // LIFECYCLE & NETWORK MANAGEMENT
    // ========================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                refreshPatterns(true);
                needPatternSync = true;
            });
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
        if (!buffer.isEmpty()) {
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        }
        int offset = getOffset();
        if (getOffsetTimer() % (offset == 0 ? ME_UPDATE_INTERVAL : offset) == 0) syncStockInput();
    }

    @Override
    public int getOffset() {
        return syncOffset;
    }

    @Override
    public void setOffset(int offset) {
        syncOffset = Math.max(0, offset);
    }

    protected void onStockInputConfigChanged(boolean removed) {
        if (!isRemote()) {
            syncStockInput();
            if (removed) clearRuntimeRecipeCache();
        }
    }

    private void syncStockInput() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            stockItemHandler.clearStocks();
            stockFluidHandler.clearStocks();
            return;
        }
        IStorageService service = grid.getStorageService();
        stockItemHandler.syncStock(service.getInventory());
        stockFluidHandler.syncStock(service.getInventory());
        recipeHandler.getMeItemHandler().notifyListeners();
        recipeHandler.getMeFluidHandler().notifyListeners();
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

    protected void onWildcardPatternChange() {
        if (isRemote()) return;

        for (InternalSlot slot : internalSlots) {
            refundSlot(slot.getItemInventory(), slot.getFluidInventory());
        }
        AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);

        clearPatternData();

        refreshPatterns(false);
        needPatternSync = true;
    }

    private void clearPatternData() {
        clearRuntimeRecipeCache();
        internalSlots.clear();
        expandedPatterns.clear();
        patternToSlotMap.clear();
        activeSlotIndices.clear();
        persistenceHelper.clearPatternIndex();
        persistenceHelper.clearPendingRestoreData();
    }

    private void clearRuntimeRecipeCache() {
        for (int slot : activeSlotIndices) {
            removeSlotFromMap.accept(slot);
            notifyProxySlotRemoved(slot);
        }
        recipeCacheMap.clear();
    }

    private void refreshPatterns(boolean restorePersistedData) {
        ItemStack wildcardStack = wildcardPatternSlot.getStackInSlot(0);
        if (!wildcardStack.isEmpty() && getLevel() != null) {
            List<IPatternDetails> patterns = WildcardPatternCompatImpl.expandPatterns(wildcardStack, getLevel());

            for (int i = 0; i < patterns.size(); i++) {
                IPatternDetails pattern = patterns.get(i);
                expandedPatterns.add(pattern);
                patternToSlotMap.put(pattern, i);
                activeSlotIndices.add(i);
                persistenceHelper.indexPattern(pattern, i);

                InternalSlot slot = new StockingWildcardInternalSlot(i);
                slot.setOnContentsChanged(() -> {
                    recipeHandler.getMeFluidHandler().notifyListeners();
                    recipeHandler.getMeItemHandler().notifyListeners();
                });
                internalSlots.add(slot);
            }

            if (restorePersistedData) {
                persistenceHelper.restore(
                        recipeCacheMap,
                        (slot, slotData) -> internalSlots.get(slot).deserializeNBT(slotData),
                        this::refundPersistedSlotData);
            }
        }
    }

    private void refundPersistedSlotData(List<CompoundTag> slotDataList) {
        Object2LongOpenHashMap<AEItemKey> itemInventory = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<AEFluidKey> fluidInventory = new Object2LongOpenHashMap<>();

        for (CompoundTag slotData : slotDataList) {
            AEUtils.appendPersistedInventory(slotData.getList("inventory", Tag.TAG_COMPOUND), AEItemKey::fromTag, itemInventory);
            AEUtils.appendPersistedInventory(slotData.getList("fluidInventory", Tag.TAG_COMPOUND), AEFluidKey::fromTag, fluidInventory);
        }

        refundSlot(itemInventory, fluidInventory);
    }

    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
        clearInventory(wildcardPatternSlot);
    }

    // ========================================
    // Slot Access and Pattern Routing
    // ========================================

    @Override
    @Nullable
    protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        int slot = patternToSlotMap.getInt(pattern);
        return slot >= 0 ? slot : null;
    }

    @Override
    protected int getInternalSlotCount() {
        return internalSlots.size();
    }

    @Override
    protected boolean hasRecipeCacheInSlot(int slotIndex) {
        return recipeCacheMap.containsKey(slotIndex);
    }

    @Override
    protected boolean hasPatternInSlot(int slotIndex) {
        return activeSlotIndices.contains(slotIndex);
    }

    @Override
    protected InternalSlot getInternalSlot(int index) {
        return internalSlots.get(index);
    }

    // ========================================
    // Persistence
    // ========================================

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        persistenceHelper.save(
                tag, internalSlots.size(),
                slot -> internalSlots.get(slot).isActive(),
                slot -> internalSlots.get(slot).serializeNBT(),
                recipeCacheMap::containsKey,
                recipeCacheMap);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        persistenceHelper.load(tag);
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ========================================
    // GUI SYSTEM
    // ========================================

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new AdvancedMEConfigurator(this::setOffset, this::getOffset));
        // Visibility in ME Pattern Access Terminal
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                org.gtlcore.gtlcore.api.gui.GuiTextures.BUTTON_VISIBLE.getSubTexture(0, 0, 1, 0.5),
                org.gtlcore.gtlcore.api.gui.GuiTextures.BUTTON_VISIBLE.getSubTexture(0, 0.5, 1, 0.5),
                () -> this.isHiddenTerminal, (clickData, pressed) -> this.isHiddenTerminal = pressed)
                .setTooltipsSupplier(pressed -> List.of(
                        Component.translatable(pressed ? "gui.gtlcore.hidden_in_terminal" : "gui.gtlcore.visible_in_terminal"))));
    }

    @Override
    public @NotNull Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 158, 231);

        group.addWidget(new LabelWidget(8, 4,
                () -> this.isOnline ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline"));

        group.addWidget(new AETextInputButtonWidget(90, 4, 60, 10)
                .setText(customName)
                .setOnConfirm(this::setCustomName)
                .setButtonTooltips(Component.translatable("gui.gtceu.rename.desc")));

        group.addWidget(new SlotWidget(wildcardPatternSlot, 0, 70, 25)
                .setChangeListener(this::onWildcardPatternChange)
                .setBackground(GuiTextures.SLOT, GuiTextures.PATTERN_OVERLAY));

        group.addWidget(new LabelWidget(8, 50,
                () -> Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.patterns",
                        expandedPatterns.size()).getString()));

        group.addWidget(new PatternCycleWidget(8, 62, 142, 36, () -> expandedPatterns));

        group.addWidget(new LabelWidget(8, 104,
                () -> Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.cached",
                        recipeCacheMap.size()).getString()));

        group.addWidget(new PatternCycleWidget(8, 116, 142, 36, this::getCachedPreviewPatterns));
        group.addWidget(new LabelWidget(8, 160, () -> Component.translatable("gui.gtlcore.stock_input_config").getString()));
        group.addWidget(new LabelWidget(128, 160, () -> FormattingUtil.formatNumbers(stockItemHandler.configList.size() + stockFluidHandler.configList.size()) + " / " + CONFIG_SIZE));
        group.addWidget(new AEDualConfigWidget(7, 174, stockItemHandler, stockFluidHandler, this::setPage, page, false, 1));
        return group;
    }

    private List<IPatternDetails> getCachedPreviewPatterns() {
        List<IPatternDetails> patterns = new ObjectArrayList<>();
        for (var entry : Int2ReferenceMaps.fastIterable(recipeCacheMap)) {
            int slot = entry.getIntKey();
            if (slot >= 0 && slot < expandedPatterns.size()) {
                patterns.add(expandedPatterns.get(slot));
            }
        }
        return patterns;
    }

    // ========================================
    // AE2 CRAFTING
    // ========================================

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(expandedPatterns);
    }

    // ========================================
    // PATTERN CONTAINER IMPLEMENTATION
    // ========================================

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return internalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (!customName.isEmpty()) {
            return new PatternContainerGroup(
                    AEItemKey.of(WildcardPatternCompatImpl.getStockingWildcardPatternBufferDefinition().asStack()),
                    Component.literal(customName),
                    Collections.emptyList());
        } else {
            return new PatternContainerGroup(
                    AEItemKey.of(WildcardPatternCompatImpl.getStockingWildcardPatternBufferDefinition().asStack()),
                    WildcardPatternCompatImpl.getStockingWildcardPatternBufferDefinition().getItem().getDescription(),
                    Collections.emptyList());
        }
    }

    // ========================================
    // IMEPatternPartMachine
    // ========================================

    @Override
    protected @NotNull WildcardMEPatternTrait createMETrait() {
        return new WildcardMEPatternTrait(this);
    }

    @Override
    public Pair<IMERecipeHandlerTrait<Ingredient, ItemStack>, IMERecipeHandlerTrait<FluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.getMeItemHandler(), recipeHandler.getMeFluidHandler());
    }

    protected class StockingWildcardInternalSlot extends InternalSlot {

        public StockingWildcardInternalSlot(int slotIndex) {
            super(slotIndex);
        }

        @Override
        public boolean isItemActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getItemInventory().isEmpty() || !sharedCatalystInventory.isEmpty() ||
                            !getCircuitForRecipe(getSlotIndex()).isEmpty() || hasItemCatalystInventory() ||
                            hasVirtualItemSupply() || stockItemHandler.hasConfig()) :
                    (!getItemInventory().isEmpty() || stockItemHandler.hasConfig()));
        }

        @Override
        public boolean isFluidActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getFluidInventory().isEmpty() || !sharedCatalystTank.isEmpty() ||
                            hasFluidCatalystInventory() || hasVirtualFluidSupply() ||
                            stockFluidHandler.hasConfig()) :
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
                    onStockInputConfigChanged(onConfigChanged());
                });
            }
        }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onStockInputConfigChanged(onConfigChanged());
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

        public boolean onConfigChanged() {
            var previousConfig = new ObjectOpenHashSet<>(configList);
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEItemKey key) {
                    configList.add(key);
                    configIndexList.add(i);
                    previousConfig.remove(key);
                }
            }
            return !previousConfig.isEmpty();
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
                    onStockInputConfigChanged(onConfigChanged());
                });
            }
        }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onStockInputConfigChanged(onConfigChanged());
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

        public boolean onConfigChanged() {
            var previousConfig = new ObjectOpenHashSet<>(configList);
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEFluidKey key) {
                    configList.add(key);
                    configIndexList.add(i);
                    previousConfig.remove(key);
                }
            }
            return !previousConfig.isEmpty();
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
    // ========================================
    // TICKER
    // ========================================

    protected class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 60, false, true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                return TickRateModulation.SLEEP;
            }

            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= 60) {
                    return TickRateModulation.SLEEP;
                } else return TickRateModulation.SLOWER;
            } else {
                return AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource) ?
                        TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }
        }
    }

    // ========================================
    // ME PATTERN TRAIT
    // ========================================

    protected class WildcardMEPatternTrait extends MEIOTrait implements IMEPatternTrait {

        public WildcardMEPatternTrait(MEStockingWildcardPatternBufferPartMachine machine) {
            super(machine);
        }

        @Override
        public MEStockingWildcardPatternBufferPartMachine getMachine() {
            return (MEStockingWildcardPatternBufferPartMachine) machine;
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            ObjectSet<GTRecipe> recipes = new ObjectOpenHashSet<>();
            for (var it = Int2ReferenceMaps.fastIterator(recipeCacheMap); it.hasNext();) {
                var entry = it.next();
                int slot = entry.getIntKey();
                if (slot < internalSlots.size() && internalSlots.get(slot).isActive()) {
                    recipes.add(entry.getValue());
                }
            }
            return recipes;
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            if (recipe != null && recipe.recipeType != GTRecipeTypes.DUMMY_RECIPES && index >= 0 && index < internalSlots.size()) {
                recipeCacheMap.put(index, recipe);
            }
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> slot2Recipes = new Int2ReferenceOpenHashMap<>();
            for (var entry : Int2ReferenceMaps.fastIterable(recipeCacheMap)) {
                ObjectSet<GTRecipe> recipes = new ObjectArraySet<>();
                recipes.add(entry.getValue());
                slot2Recipes.put(entry.getIntKey(), recipes);
            }
            return slot2Recipes;
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            return recipeCacheMap.containsKey(slot);
        }
    }
}
