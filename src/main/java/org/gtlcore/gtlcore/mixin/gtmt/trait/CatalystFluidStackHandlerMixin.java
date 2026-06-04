package org.gtlcore.gtlcore.mixin.gtmt.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.utils.FluidStackHashStrategy;

import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CatalystFluidStackHandler.class)
public abstract class CatalystFluidStackHandlerMixin extends NotifiableFluidTank {

    @Unique
    private final Object2LongOpenCustomHashMap<FluidStack> gTLCore$fluidCatalystInventory = new Object2LongOpenCustomHashMap<>(FluidStackHashStrategy.comparingAllButAmount());

    public CatalystFluidStackHandlerMixin(MetaMachine machine, int slots, long capacity, IO io, IO capabilityIO) {
        super(machine, slots, capacity, io, capabilityIO);
    }

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;IJLcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)V", at = @At("RETURN"), remap = false)
    private void gTLCore$disableExternalCapability(MetaMachine machine, int slots, long capacity, IO io, IO capabilityIO, CallbackInfo ci) {
        setCapabilityValidator(side -> false);
    }

    /**
     * @author Dragons
     * @reason 性能优化
     */
    @Overwrite(remap = false)
    public List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left, @Nullable String slotName, boolean simulate) {
        if (!simulate) return left;

        ObjectSet<FluidIngredient> catalysts = new ObjectOpenCustomHashSet<>(CacheHashStrategies.FluidIngredientHashStrategy.INSTANCE);
        for (Content content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
            FluidIngredient fluidIngredient = (FluidIngredient) content.getContent();
            if (content.chance <= 0) {
                for (FluidStack fluidStack : fluidIngredient.getStacks()) {
                    if (gTLCore$fluidCatalystInventory.getLong(fluidStack) >= fluidStack.getAmount()) catalysts.add(fluidIngredient);
                }
            } else {
                for (FluidStack fluidStack : fluidIngredient.getStacks()) {
                    if (gTLCore$fluidCatalystInventory.containsKey(fluidStack)) return left;
                }
            }
        }

        left.removeIf(catalysts::contains);
        return left.isEmpty() ? null : left;
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        if (machine.isRemote()) return;
        gTLCore$rebuildMap();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (machine.isRemote()) return;
        gTLCore$rebuildMap();
        notifyListeners();
    }

    @Unique
    private void gTLCore$rebuildMap() {
        gTLCore$fluidCatalystInventory.clear();
        for (FluidStorage storage : this.getStorages()) {
            final FluidStack fluidStack = storage.getFluid();
            if (!fluidStack.isEmpty()) gTLCore$fluidCatalystInventory.addTo(fluidStack, fluidStack.getAmount());
        }
    }
}
