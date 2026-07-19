package org.gtlcore.gtlcore.common.machine;

import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Publishes an unlimited supply of virtual items to the ME network, one for every real stack parked inside it.
 * <p>
 * This is what makes virtual ingredients native to AE: the network genuinely holds the wrapper, so AE stocks, plans and
 * dispatches it exactly like any other pattern input, with no interception anywhere along the crafting path. What the
 * network never sees is the payload -- the single real copy of it stays locked in this machine's inventory, and that is
 * the whole ownership gate. Wrappers are free and infinite; the material behind them is neither.
 * <p>
 * Adapted from GTOCore's {@code VirtualItemProviderMachine} (com.gtocore.common.machine.noenergy), rewritten against
 * GTLCore's own virtual item and LDLib persistence rather than GTOLib's cell storage helpers and GTMThings' item.
 */
public class VirtualIngredientSupplyMachine extends MetaMachine
                                            implements IUIMachine, IDropSaveMachine, MEStorage, IGridConnectedMachine,
                                            IStorageProvider {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            VirtualIngredientSupplyMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    public static final int SLOT_COUNT = 288;
    private static final int COLS = 9;
    private static final int SLOT_SIZE = 18;
    /** Matches AE's own creative cell, which reports Integer.MAX_VALUE per configured key. */
    private static final long PUBLISHED_AMOUNT = Integer.MAX_VALUE;

    @Persisted
    private final NotifiableItemStackHandler inventory;

    @Persisted
    private final GridNodeHolder nodeHolder;

    @DescSynced
    private boolean isOnline;

    /** Rebuilt whenever the inventory changes; never persisted, because it is derived. */
    private final Object2LongMap<AEKey> published = new Object2LongOpenHashMap<>();

    private KeyCounter cachedStacks;
    private boolean dirty = true;

    public VirtualIngredientSupplyMachine(IMachineBlockEntity holder) {
        super(holder);
        // IO.NONE as the handler role keeps recipe logic from ever seeing this as machine input or output.
        this.inventory = new NotifiableItemStackHandler(this, SLOT_COUNT, IO.NONE, IO.BOTH);
        this.nodeHolder = new GridNodeHolder(this);
        // A standalone block, unlike the ME hatches this trait was written for: cabling it up should work from any
        // face rather than only the one it happens to be facing.
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        getMainNode().addService(IStorageProvider.class, this);
        this.inventory.addChangedListener(this::rebuildPublishedKeys);
    }

    @Override
    public void onRotated(@NotNull Direction oldFacing, @NotNull Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        // Keep every face connectable; the default would narrow this back down to the new front.
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        inventory.notifyListeners();
        rebuildPublishedKeys();
    }

    /**
     * Wraps every parked stack into a sealed virtual item. A stack that is already a wrapper is published as-is, so a
     * hand-configured one can be seeded here too.
     */
    private void rebuildPublishedKeys() {
        published.clear();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack wrapper;
            if (GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) {
                // Seal the parked wrapper itself. Publishing it hands out unlimited copies, so letting the player
                // then pull the payload back out would turn one locked copy into an unlimited source of the real
                // material.
                VirtualIngredientBehavior.mark(stack);
                wrapper = stack.copyWithCount(1);
            } else {
                wrapper = VirtualIngredientBehavior.wrap(stack);
            }
            published.put(AEItemKey.of(wrapper), PUBLISHED_AMOUNT);
        }
        dirty = true;
        if (cachedStacks != null) cachedStacks.clear();
    }

    // ==================== AE storage ====================

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        // Lowest priority: this must never win over real storage for anything.
        storageMounts.mount(this, Integer.MAX_VALUE - 1);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof AEItemKey itemKey && GTLItems.VIRTUAL_INGREDIENT.isIn(itemKey.getReadOnlyStack());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEItemKey itemKey)) return 0;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (!GTLItems.VIRTUAL_INGREDIENT.isIn(stack)) return 0;

        // Sealed wrappers are what we handed out, so swallow them rather than let returned ones pile up in the network.
        if (VirtualIngredientBehavior.isMarked(stack)) return amount;

        // An unsealed wrapper is real cargo: accept it into a slot so it starts being published.
        return store(stack, mode.isSimulate()) ? amount : 0;
    }

    private boolean store(ItemStack stack, boolean simulate) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.storage.insertItem(slot, stack.copyWithCount(1), simulate).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Extraction never decrements: the wrapper is a token, and tokens are free. The payload is not stored here in
        // any form AE can reach, so nothing real leaves the machine.
        return amount > 0 && published.containsKey(what) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(published)) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public KeyCounter getAvailableStacks() {
        if (cachedStacks == null) {
            cachedStacks = new KeyCounter();
            dirty = true;
        }
        if (dirty) {
            cachedStacks.clear();
            getAvailableStacks(cachedStacks);
            cachedStacks.removeEmptySubmaps();
            dirty = false;
        }
        return cachedStacks;
    }

    @Override
    public Component getDescription() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    // ==================== Grid ====================

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public boolean isOnline() {
        return isOnline;
    }

    @Override
    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    // ==================== Persistence ====================

    @Override
    public void loadFromItem(CompoundTag tag) {
        inventory.storage.deserializeNBT(tag.getCompound("inventory"));
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        tag.put("inventory", inventory.storage.serializeNBT());
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ==================== GUI ====================

    @Override
    public ModularUI createUI(Player player) {
        int width = 181;
        ModularUI ui = new ModularUI(width, 244, this, player)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(5, 5, () -> Component.translatable(
                        "block.gtceu.virtual_ingredient_supply_machine").getString()))
                .widget(UITemplate.bindPlayerInventory(player.getInventory(), GuiTextures.SLOT, 7, 162, true));

        var scroll = new DraggableScrollableWidgetGroup(4, 4, width - 13, 130)
                .setYBarStyle(GuiTextures.BACKGROUND_INVERSE, GuiTextures.BUTTON)
                .setYScrollBarWidth(4);

        int x = 0;
        int y = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            scroll.addWidget(new SlotWidget(inventory.storage, slot, x * SLOT_SIZE, y * SLOT_SIZE) {

                @Override
                public boolean isEnabled() {
                    // Slots scrolled out of view are disabled by default, which silently makes everything past the
                    // first visible page reject insertion.
                    return true;
                }
            }.setBackgroundTexture(GuiTextures.SLOT));
            if (++x == COLS) {
                x = 0;
                y++;
            }
        }

        return ui.widget(new WidgetGroup(3, 17, width - 6, 140).addWidget(scroll));
    }
}
