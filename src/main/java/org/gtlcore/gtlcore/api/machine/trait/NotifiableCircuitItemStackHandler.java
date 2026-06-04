package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NotifiableCircuitItemStackHandler extends NotifiableItemStackHandler {

    public NotifiableCircuitItemStackHandler(MetaMachine machine) {
        super(machine, 1, IO.IN, IO.IN);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate, boolean notifyChange) {
        if (GTItems.INTEGRATED_CIRCUIT.isIn(stack)) {
            if (!simulate) {
                storage.setStackInSlot(slot, stack.copyWithCount(1));
            }
            return ItemStack.EMPTY;
        }
        return stack;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, @Nullable String slotName, boolean simulate) {
        if (!simulate) return left;

        for (var it = left.iterator(); it.hasNext();) {
            var items = it.next().getItems();

            if (items.length == 0 || items[0].isEmpty()) {
                continue;
            }

            if (GTItems.INTEGRATED_CIRCUIT.is(items[0].getItem()) && IntCircuitBehaviour.getCircuitConfiguration(items[0]) == IntCircuitBehaviour.getCircuitConfiguration(storage.getStackInSlot(0))) {
                it.remove();
                break;
            }
        }

        return left.isEmpty() ? null : left;
    }
}
