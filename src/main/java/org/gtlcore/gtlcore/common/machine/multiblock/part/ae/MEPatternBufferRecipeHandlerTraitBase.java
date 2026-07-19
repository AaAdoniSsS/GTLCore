package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Shared ME item/fluid recipe handling for pattern buffer variants.
 */
public abstract class MEPatternBufferRecipeHandlerTraitBase extends MachineTrait {

    @Getter
    protected final MEItemInputHandlerBase meItemHandler;

    @Getter
    protected final MEFluidHandlerBase meFluidHandler;

    public MEPatternBufferRecipeHandlerTraitBase(MEPatternBufferPartMachineBase ioBuffer, IO io) {
        super(ioBuffer);
        meItemHandler = createMEItemHandler(io);
        meFluidHandler = createMEFluidHandler(io);
    }

    protected abstract MEItemInputHandlerBase createMEItemHandler(IO io);

    protected abstract MEFluidHandlerBase createMEFluidHandler(IO io);

    @Override
    public MEPatternBufferPartMachineBase getMachine() {
        return (MEPatternBufferPartMachineBase) super.getMachine();
    }

    @Override
    public void onChanged() {}

    protected abstract static class MEItemInputHandlerBase extends NotifiableMERecipeHandlerTrait<Ingredient, ItemStack> {

        @Getter
        private final IO io;

        @Getter
        @Setter
        private Object2LongMap<Ingredient> preparedMEHandleContents = new Object2LongOpenHashMap<>();

        @Setter
        private int preparedCircuitConfig = -1;

        public MEItemInputHandlerBase(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine);
            this.io = io;
        }

        public MEPatternBufferPartMachineBase getMachine() {
            return (MEPatternBufferPartMachineBase) this.machine;
        }

        @Override
        public RecipeCapability<Ingredient> getCapability() {
            return ItemRecipeCapability.CAP;
        }

        @Override
        public int[] getActiveSlots() {
            return getMachine().getActiveSlots();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Int2ObjectMap<List<ItemStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            var map = new Int2ObjectArrayMap<List<ItemStack>>();
            var machine = getMachine();
            var shared = (List<ItemStack>) (Object) machine.getSharedCatalystInventory().getContents();
            for (int slot : machine.getActiveAndUnCachedSlots()) {
                var inputs = machine.getInternalSlot(slot).getLimitItemStackInput();

                ItemStack circuitForRecipe = machine.getCircuitForRecipe(slot);
                if (!circuitForRecipe.isEmpty()) {
                    inputs.add(circuitForRecipe);
                }

                inputs.addAll(shared);
                map.put(slot, inputs);
            }
            return map;
        }

        @Override
        public Object2LongMap<ItemStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            final var inventory = getMachine().getFirstActiveInternalSlot(slots);

            if (inventory != null) {
                Object2LongOpenHashMap<ItemStack> map = new Object2LongOpenHashMap<>();
                for (var entry : Object2LongMaps.fastIterable(inventory.getItemStackInputMap())) {
                    map.addTo(entry.getKey(), entry.getLongValue());
                }
                return map;
            }

            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<Ingredient> left, boolean simulate, int trySlot) {
            var internalSlot = getMachine().getInternalSlotOrNull(trySlot);
            if (internalSlot != null && internalSlot.isItemActive(simulate)) {
                if (simulate) {
                    if (!internalSlot.testCatalystItemInternal(recipe)) return false;
                }
                return internalSlot.handleItemInternal(left, preparedCircuitConfig, simulate);
            } else return left.isEmpty() && preparedCircuitConfig < 0;
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<Ingredient> left, boolean simulate) {
            preparedCircuitConfig = -1;
            if (simulate) {
                getMachine().getSharedCircuitInventory().handleRecipeInner(IO.IN, recipe, left, null, true);

                getMachine().getSharedCatalystInventory().handleRecipeInner(IO.IN, recipe, left, null, true);

                // Circuit ingredients are captured separately from the prepared item map.
                setPreparedMEHandleContents(AEUtils.ingredientsMapWithOutCircuit(left, this::setPreparedCircuitConfig));
            } else {
                setPreparedMEHandleContents(AEUtils.ingredientsMap(left));
            }
        }

        @Override
        public List<Ingredient> meHandleRecipeOutputInner(List<Ingredient> left, boolean simulate) {
            if (simulate) return List.of();
            final var buffer = getMachine().buffer;
            for (Ingredient ingredient : left) {
                if (ingredient instanceof IntProviderIngredient intProvider) {
                    intProvider.setItemStacks(null);
                    intProvider.setSampledCount(null);
                }

                ItemStack[] items = ingredient.getItems();
                if (items.length != 0) {
                    ItemStack output = items[0];
                    if (!output.isEmpty()) {
                        buffer.addTo(AEItemKey.of(output), ingredient instanceof LongIngredient longIngredient ? longIngredient.getActualAmount() : output.getCount());
                    }
                }
            }
            return List.of();
        }
    }

    protected abstract static class MEFluidHandlerBase extends NotifiableMERecipeHandlerTrait<FluidIngredient, FluidStack> {

        @Getter
        private final IO io;

        @Getter
        @Setter
        private Object2LongMap<FluidIngredient> preparedMEHandleContents = new Object2LongOpenHashMap<>();

        public MEFluidHandlerBase(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine);
            this.io = io;
        }

        public MEPatternBufferPartMachineBase getMachine() {
            return (MEPatternBufferPartMachineBase) this.machine;
        }

        @Override
        public RecipeCapability<FluidIngredient> getCapability() {
            return FluidRecipeCapability.CAP;
        }

        @Override
        public int[] getActiveSlots() {
            return getMachine().getActiveSlots();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Int2ObjectMap<List<FluidStack>> getActiveAndUnCachedSlotsLimitContentsMap() {
            var map = new Int2ObjectArrayMap<List<FluidStack>>();
            var machine = getMachine();
            var shared = (List<FluidStack>) (Object) machine.getSharedCatalystTank().getContents();
            for (int slot : machine.getActiveAndUnCachedSlots()) {
                var inputs = machine.getInternalSlot(slot).getLimitFluidStackInput();
                inputs.addAll(shared);
                map.put(slot, inputs);
            }
            return map;
        }

        @Override
        public Object2LongMap<FluidStack> getStackMapFromFirstAvailableSlot(IntCollection slots) {
            final var inventory = getMachine().getFirstActiveInternalSlot(slots);

            if (inventory != null) {
                Object2LongOpenHashMap<FluidStack> map = new Object2LongOpenHashMap<>();
                for (var entry : Object2LongMaps.fastIterable(inventory.getFluidStackInputMap())) {
                    map.addTo(entry.getKey(), entry.getLongValue());
                }
                return map;
            }

            return Object2LongMaps.emptyMap();
        }

        @Override
        public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<FluidIngredient> left, boolean simulate, int trySlot) {
            var internalSlot = getMachine().getInternalSlotOrNull(trySlot);
            if (internalSlot != null && internalSlot.isFluidActive(simulate)) {
                if (simulate) {
                    if (!internalSlot.testCatalystFluidInternal(recipe)) return false;
                }
                return internalSlot.handleFluidInternal(left, simulate);
            } else return left.isEmpty();
        }

        @Override
        public void prepareMEHandleContents(GTRecipe recipe, List<FluidIngredient> left, boolean simulate) {
            if (simulate) {
                getMachine().getSharedCatalystTank().handleRecipeInner(IO.IN, recipe, left, null, true);
            }
            setPreparedMEHandleContents(AEUtils.fluidIngredientsMap(left));
        }

        @Override
        public List<FluidIngredient> meHandleRecipeOutputInner(List<FluidIngredient> left, boolean simulate) {
            if (simulate) return List.of();
            final var buffer = getMachine().buffer;
            for (FluidIngredient fluidIngredient : left) {
                if (!fluidIngredient.isEmpty()) {
                    FluidStack[] fluids = fluidIngredient.getStacks();
                    if (fluids.length != 0) {
                        FluidStack output = fluids[0];
                        buffer.addTo(AEFluidKey.of(output.getFluid()), output.getAmount());
                    }
                }
            }
            return List.of();
        }
    }
}
