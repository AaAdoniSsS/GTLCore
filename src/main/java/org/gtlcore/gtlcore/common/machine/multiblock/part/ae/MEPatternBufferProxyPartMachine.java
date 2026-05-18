package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MEPatternBufferProxyPartMachine extends MultiblockPartMachine implements IMachineLife, IMEPatternPartMachine, IInteractedMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEPatternBufferProxyPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    protected final ISubscription[] handlerSubscriptions = new ISubscription[2];

    protected final IMEPatternTrait meTrait;
    protected final MEPatternBufferProxyRecipeHandler<Ingredient, ItemStack> itemProxyHandler;
    protected final MEPatternBufferProxyRecipeHandler<FluidIngredient, FluidStack> fluidProxyHandler;

    protected IntConsumer removeSlotFromMap = i -> {};

    @Persisted
    @Getter
    @DescSynced
    private BlockPos bufferPos;
    private @Nullable MEPatternBufferPartMachineBase buffer = null;
    private @Nullable IMEPatternTrait bufferTrait = null;
    private boolean bufferResolved = false;

    public MEPatternBufferProxyPartMachine(IMachineBlockEntity holder) {
        super(holder);
        this.itemProxyHandler = new MEPatternBufferProxyRecipeHandler<>(this, ItemRecipeCapability.CAP);
        this.fluidProxyHandler = new MEPatternBufferProxyRecipeHandler<>(this, FluidRecipeCapability.CAP);
        this.meTrait = new MEPatternProxyTrait(this);
    }

    @Override
    public MetaMachine self() {
        var buffer = getBuffer();
        return buffer != null ? buffer.self() : super.self();
    }

    public void setBuffer(@Nullable BlockPos pos) {
        bufferResolved = true;
        var level = getLevel();
        releaseBuffer();
        if (level != null && pos != null) {
            if (MetaMachine.getMachine(level, pos) instanceof MEPatternBufferPartMachineBase machine) {
                bufferPos = pos;
                buffer = machine;
                machine.addProxy(this);
                if (!isRemote()) {
                    var pair = machine.getMERecipeHandlerTraits();
                    this.itemProxyHandler.setHandler(pair.left());
                    this.fluidProxyHandler.setHandler(pair.right());
                    this.bufferTrait = machine.getMETrait();
                    handlerSubscriptions[0] = pair.left().addChangedListener(itemProxyHandler::notifyListeners);
                    handlerSubscriptions[1] = pair.right().addChangedListener(fluidProxyHandler::notifyListeners);
                }
            }
        }
        if (!isRemote()) updateIO();
    }

    @Nullable
    public MEPatternBufferPartMachineBase getBuffer() {
        if (!bufferResolved) setBuffer(bufferPos);
        return buffer;
    }

    protected void releaseBuffer() {
        var oldBuffer = buffer;
        if (oldBuffer != null) {
            oldBuffer.removeProxy(this);
        }
        buffer = null;
        bufferPos = null;
        if (!isRemote()) {
            this.itemProxyHandler.setHandler(null);
            this.fluidProxyHandler.setHandler(null);
            this.bufferTrait = null;
            for (int i = 0; i < handlerSubscriptions.length; i++) {
                if (handlerSubscriptions[i] != null) {
                    handlerSubscriptions[i].unsubscribe();
                    handlerSubscriptions[i] = null;
                }
            }
        }
    }

    protected void updateIO() {
        for (var controller : this.getControllers()) {
            if (controller instanceof IRecipeCapabilityMachine machine) {
                machine.upDate();
            }
        }
        itemProxyHandler.notifyListeners();
        fluidProxyHandler.notifyListeners();
    }

    // ========================================
    // GUI
    // ========================================

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResult.PASS;

        if (stack.is(GTItems.TOOL_DATA_STICK.asItem())) {
            if (!world.isClientSide) {
                // Check if it's research data - if so, pass to avoid conflicts
                var researchData = ResearchManager.readResearchId(stack);
                if (researchData != null) {
                    return InteractionResult.PASS;
                }

                // Read pattern buffer position from the data stick
                var tag = stack.getTag();
                if (tag != null && tag.contains("pos")) {
                    int[] posArray = tag.getIntArray("pos");
                    if (posArray.length == 3) {
                        BlockPos bufferPos = new BlockPos(posArray[0], posArray[1], posArray[2]);
                        player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
                        setBuffer(bufferPos);
                    }
                }
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return getBuffer() != null;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        assert getBuffer() != null; // UI should never be able to be opened when buffer is null
        return getBuffer().createUI(entityPlayer);
    }

    // ========================================
    // LIFECYCLE
    // ========================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel level) {
            level.getServer().tell(new TickTask(0, () -> this.setBuffer(bufferPos)));
        }
    }

    @Override
    public void onMachineRemoved() {
        releaseBuffer();
    }

    @Override
    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ========================================
    // IMEPatternPartMachine
    // ========================================

    @Override
    public Pair<IMERecipeHandlerTrait<Ingredient, ItemStack>, IMERecipeHandlerTrait<FluidIngredient, FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(itemProxyHandler, fluidProxyHandler);
    }

    @Override
    public @NotNull IMEPatternTrait getMETrait() {
        return this.meTrait;
    }

    protected class MEPatternProxyTrait extends MachineTrait implements IMEPatternTrait {

        protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
                MEPatternBufferProxyPartMachine.class);

        public MEPatternProxyTrait(MEPatternBufferProxyPartMachine machine) {
            super(machine);
        }

        @Override
        public MEPatternBufferProxyPartMachine getMachine() {
            return (MEPatternBufferProxyPartMachine) machine;
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            if (bufferTrait == null) return ObjectSets.emptySet();
            return bufferTrait.getCachedGTRecipe();
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            if (bufferTrait != null) {
                bufferTrait.setSlotCacheRecipe(index, recipe);
            }
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            return bufferTrait == null ? Int2ReferenceMaps.emptyMap() : bufferTrait.getSlot2RecipesCache();
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            if (bufferTrait == null) return false;
            return bufferTrait.hasCacheInSlot(slot);
        }

        @Override
        public void notifySelfIO() {
            if (bufferTrait != null) {
                bufferTrait.notifySelfIO();
            }
        }

        @Override
        public IO getIO() {
            if (bufferTrait != null) {
                return bufferTrait.getIO();
            }
            return IO.NONE;
        }

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }
    }
}
