package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.google.common.primitives.Ints;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(GTRecipe.class)
public abstract class GTRecipeMixin implements IGTRecipe {

    @Unique
    private int tier = -1;
    @Unique
    @Getter
    private long realParallels = 1;
    @Unique
    @Getter
    @Setter
    private int batchSize = 1;
    @Unique
    @Getter
    @Setter
    private boolean batchProcessed;
    @Unique
    @Setter
    private boolean hasTick;
    @Unique
    private IO io;

    @Shadow(remap = false)
    public int parallels;
    @Shadow(remap = false)
    public ResourceLocation id;
    @Shadow(remap = false)
    public @NotNull CompoundTag data;
    @Shadow(remap = false)
    @Final
    public GTRecipeType recipeType;

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/recipe/GTRecipeType;Lnet/minecraft/resources/ResourceLocation;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/List;Ljava/util/List;Lnet/minecraft/nbt/CompoundTag;IZ)V",
            at = @At("RETURN"),
            remap = false)
    public void GTRecipe(GTRecipeType recipeType, ResourceLocation id, Map inputs, Map outputs, Map tickInputs, Map tickOutputs, Map inputChanceLogics, Map outputChanceLogics, Map tickInputChanceLogics, Map tickOutputChanceLogics, List conditions, List ingredientActions, CompoundTag data, int duration, boolean isFuel, CallbackInfo ci) {
        this.tier = this.getEuTier();
        this.hasTick = !tickInputs.isEmpty() || !tickOutputs.isEmpty();
        this.io = tickInputs.isEmpty() ? (tickOutputs.isEmpty() ? IO.NONE : IO.OUT) : IO.IN;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public boolean hasTick() {
        return this.hasTick;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public GTRecipe.ActionResult matchTickRecipe(IRecipeCapabilityHolder holder) {
        if (!this.hasTick()) return GTRecipe.ActionResult.SUCCESS;
        var rlm = (IRecipeLogicMachine) holder;
        if (holder instanceof WorkableElectricMultiblockMachine machine && this.io == IO.IN) {
            var lastRecipe = machine.getRecipeLogic().getLastOriginRecipe();
            if (lastRecipe == null || !this.id.equals(lastRecipe.id)) {
                if (this.getEuTier() > GTUtil.getFloorTierByVoltage(machine.getMaxVoltage())) {
                    RecipeResult.of(rlm, RecipeResult.FAIL_VOLTAGE_TIER);
                    return GTRecipe.ActionResult.fail(() -> null);
                }
            }
        }
        var result = this.matchRecipe(holder, true);
        if (!result.isSuccess() && result.reason() != null) {
            String s = result.reason().get().toString();
            if (s.contains("_in")) {
                if (s.contains("cwu")) RecipeResult.of(rlm, RecipeResult.FAIL_NO_ENOUGH_CWU_IN);
                else if (s.contains("eu.name"))
                    RecipeResult.of(rlm, holder instanceof ISteamMachine ?
                            RecipeResult.fail(Component.translatable("gtceu.recipe.fail.steam.enough")) : RecipeResult.FAIL_NO_ENOUGH_EU_IN);
            } else if (s.contains("_out")) {
                if (s.contains("eu.name")) RecipeResult.of(rlm, RecipeResult.FAIL_NO_ENOUGH_EU_OUT);
            }
        }
        return result;
    }

    @Shadow(remap = false)
    protected abstract GTRecipe.ActionResult matchRecipe(IRecipeCapabilityHolder holder, boolean tick);

    @Override
    public int getEuTier() {
        return this.tier = tier == -1 ? this.data.getInt("euTier") : tier;
    }

    @Override
    public void setRealParallels(long realParallel) {
        this.realParallels = realParallel;
        this.parallels = Ints.saturatedCast(realParallel);
    }
}
