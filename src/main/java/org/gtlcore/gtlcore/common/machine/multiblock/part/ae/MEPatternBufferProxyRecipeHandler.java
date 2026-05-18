package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import lombok.Setter;

import java.util.List;
import java.util.function.Predicate;

public class MEPatternBufferProxyRecipeHandler<T extends Predicate<S>, S> extends NotifiableMERecipeHandlerTrait<T, S> {

    @Setter
    private IMERecipeHandlerTrait<T, S> handler;
    private final RecipeCapability<T> capability;

    public MEPatternBufferProxyRecipeHandler(MetaMachine machine, RecipeCapability<T> capability) {
        super(machine);
        this.capability = capability;
    }

    @Override
    public RecipeCapability<T> getCapability() {
        return capability;
    }

    @Override
    public IO getIo() {
        if (handler != null) {
            return handler.getIo();
        }
        return IO.NONE;
    }

    @Override
    public int[] getActiveSlots() {
        if (handler != null) {
            return handler.getActiveSlots();
        }
        return new int[0];
    }

    @Override
    public Int2ObjectMap<List<S>> getActiveAndUnCachedSlotsLimitContentsMap() {
        if (handler != null) {
            return handler.getActiveAndUnCachedSlotsLimitContentsMap();
        }
        return Int2ObjectMaps.emptyMap();
    }

    @Override
    public Object2LongMap<S> getStackMapFromFirstAvailableSlot(IntCollection slots) {
        if (handler != null) {
            return handler.getStackMapFromFirstAvailableSlot(slots);
        }
        return Object2LongMaps.emptyMap();
    }

    @Override
    public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<T> left, boolean simulate, int trySlot) {
        if (handler != null) {
            return handler.meHandleRecipeInner(recipe, left, simulate, trySlot);
        }
        return false;
    }

    @Override
    public List<T> meHandleRecipeOutputInner(List<T> contents, boolean simulate) {
        if (handler != null) {
            return handler.meHandleRecipeOutputInner(contents, simulate);
        }
        return contents;
    }

    @Override
    public void prepareMEHandleContents(GTRecipe recipe, List<T> left, boolean simulate) {
        if (handler != null) {
            handler.prepareMEHandleContents(recipe, left, simulate);
        }
    }

    @Override
    public Object2LongMap<T> getPreparedMEHandleContents() {
        if (handler != null) {
            return handler.getPreparedMEHandleContents();
        }
        return Object2LongMaps.emptyMap();
    }
}
