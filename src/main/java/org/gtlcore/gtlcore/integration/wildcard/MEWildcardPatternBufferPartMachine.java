package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.WildcardRecipeHandlerTrait;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Pattern buffer variant backed by one wildcard pattern.
 * <p>
 * The wildcard item is persisted. Expanded pattern slots are rebuilt at runtime, and slot-bound state is restored by
 * matching saved pattern outputs because wildcard expansion order is not stable across reloads.
 */
public class MEWildcardPatternBufferPartMachine extends MEPatternBufferPartMachineBase {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEWildcardPatternBufferPartMachine.class, MEPatternBufferPartMachineBase.MANAGED_FIELD_HOLDER);

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

    protected final WildcardRecipeHandlerTrait recipeHandler;

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

    public MEWildcardPatternBufferPartMachine(IMachineBlockEntity holder, IO io) {
        super(holder, io);

        this.wildcardPatternSlot = new ItemStackTransfer(1);
        this.wildcardPatternSlot.setFilter(WildcardPatternCompatImpl::isWildcardPattern);

        this.patternToSlotMap.defaultReturnValue(-1);

        this.recipeHandler = new WildcardRecipeHandlerTrait(this, io);

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
        }
    }

    @Override
    protected void update() {
        super.update();
        if (!buffer.isEmpty()) {
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
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

                InternalSlot slot = new InternalSlot(i);
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
    public @NotNull Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 158, 156);

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
                    AEItemKey.of(WildcardPatternCompatImpl.getWildcardPatternBufferDefinition().asStack()),
                    Component.literal(customName),
                    Collections.emptyList());
        } else {
            return new PatternContainerGroup(
                    AEItemKey.of(WildcardPatternCompatImpl.getWildcardPatternBufferDefinition().asStack()),
                    WildcardPatternCompatImpl.getWildcardPatternBufferDefinition().getItem().getDescription(),
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

        public WildcardMEPatternTrait(MEWildcardPatternBufferPartMachine machine) {
            super(machine);
        }

        @Override
        public MEWildcardPatternBufferPartMachine getMachine() {
            return (MEWildcardPatternBufferPartMachine) machine;
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
