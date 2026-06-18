package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEOutputPart;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings("all")
@Mixin(MEOutputPartMachine.class)
public abstract class MEOutputPartMachineMixin extends DualHatchPartMachine implements IMEOutputPart, IGridConnectedMachine {

    @Unique
    private byte gTLCore$time;

    @Shadow(remap = false)
    private KeyStorage internalBuffer;
    @Shadow(remap = false)
    private KeyStorage internalTankBuffer;
    @Mutable
    @Final
    @Shadow(remap = false)
    protected final IActionSource actionSource;

    @Shadow(remap = false)
    public abstract IManagedGridNode getMainNode();

    public MEOutputPartMachineMixin(IMachineBlockEntity holder, int tier, IO io, IActionSource actionSource, Object... args) {
        super(holder, tier, io, args);
        this.actionSource = actionSource;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected void autoIO() {
        if (this.isReturn()) {
            this.gTLCore$time++;
            if (this.updateMEStatus()) {
                IGrid grid = this.getMainNode().getGrid();
                if (grid != null) {
                    if (!this.internalBuffer.isEmpty()) {
                        this.internalBuffer.insertInventory(grid.getStorageService().getInventory(), this.actionSource);
                    }
                    if (!this.internalTankBuffer.isEmpty()) {
                        this.internalTankBuffer.insertInventory(grid.getStorageService().getInventory(), this.actionSource);
                    }
                }
                this.updateInventorySubscription();
            }
        } else this.gTLCore$time++;
    }

    @Override
    public void attachConfigurators(@NotNull ConfiguratorPanel configuratorPanel) {
        IMEOutputPart.attachRecipeLockable(configuratorPanel, this);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.getCompound("ForgeData").getBoolean("isAllFacing")) {
            getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        }
    }

    @Override
    public void returnStorage() {
        this.gTLCore$time = 0;
    }

    @Override
    public byte getTime() {
        return gTLCore$time;
    }

    @Override
    public void setTime(byte time) {
        gTLCore$time = time;
    }

    @Mixin(targets = "com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine$InaccessibleInfiniteTank", remap = false)
    public static class InaccessibleInfiniteTankMixin extends NotifiableFluidTank {

        @Shadow(remap = false)
        FluidStorage storage;

        public InaccessibleInfiniteTankMixin(MetaMachine machine, int slots, long capacity, IO io, IO capabilityIO) {
            super(machine, slots, capacity, io, capabilityIO);
        }

        @Override
        public List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left, @Nullable String slotName, boolean simulate) {
            if (io != IO.OUT) return left;
            for (var fluid : left) {
                if (fluid.isEmpty()) continue;
                for (var f : fluid.getStacks()) {
                    if (f != null) this.storage.fill(f, simulate);
                }
            }
            return null;
        }
    }

    @Mixin(targets = "com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine$InaccessibleInfiniteHandler", remap = false)
    private static class InaccessibleInfiniteHandlerMixin extends NotifiableItemStackHandler {

        public InaccessibleInfiniteHandlerMixin(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO, Function<Integer, ItemStackTransfer> transferFactory) {
            super(machine, slots, handlerIO, capabilityIO, transferFactory);
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, @Nullable String slotName, boolean simulate) {
            if (io != IO.OUT) return left;
            for (var item : left) {
                if (item.isEmpty()) continue;
                for (var i : item.getItems()) if (i != null) storage.insertItem(0, i, simulate);
            }
            return null;
        }
    }
}
