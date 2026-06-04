package org.gtlcore.gtlcore.mixin.gtmt.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.CacheHashStrategies;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

@Mixin(CatalystItemStackHandler.class)
public abstract class CatalystItemStackHandlerMixin extends NotifiableItemStackHandler {

    @Unique
    private final Object2IntOpenCustomHashMap<ItemStack> gTLCore$itemCatalystInventory = new Object2IntOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());

    public CatalystItemStackHandlerMixin(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO, Function<Integer, ItemStackTransfer> transferFactory) {
        super(machine, slots, handlerIO, capabilityIO, transferFactory);
    }

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;ILcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;)V", at = @At("RETURN"), remap = false)
    private void gTLCore$disableExternalCapability(MetaMachine machine, int slots, IO handlerIO, IO capabilityIO, CallbackInfo ci) {
        setCapabilityValidator(side -> false);
    }

    /**
     * @author Dragons
     * @reason 性能优化
     */
    @Overwrite(remap = false)
    public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, @Nullable String slotName, boolean simulate) {
        if (!simulate) return left;

        ObjectSet<Ingredient> catalysts = new ObjectOpenCustomHashSet<>(CacheHashStrategies.IngredientHashStrategy.INSTANCE);
        for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            Ingredient ingredient = (Ingredient) content.getContent();
            if (content.chance <= 0) {
                for (ItemStack item : ingredient.getItems()) {
                    if (gTLCore$itemCatalystInventory.getInt(item) >= item.getCount()) catalysts.add(ingredient);
                }
            } else {
                for (ItemStack item : ingredient.getItems()) {
                    if (gTLCore$itemCatalystInventory.containsKey(item)) return left;
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
        gTLCore$itemCatalystInventory.clear();
        for (int i = 0; i < this.storage.getSlots(); i++) {
            final ItemStack itemStack = this.storage.getStackInSlot(i);
            if (!itemStack.isEmpty()) gTLCore$itemCatalystInventory.addTo(itemStack, itemStack.getCount());
        }
    }
}
