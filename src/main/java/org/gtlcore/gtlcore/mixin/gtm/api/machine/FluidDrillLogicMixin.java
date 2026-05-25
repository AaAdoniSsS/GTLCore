package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FluidDrillLogic.class)
public abstract class FluidDrillLogicMixin extends RecipeLogic {

    @Shadow(remap = false)
    private @Nullable Fluid veinFluid;

    @Shadow(remap = false)
    protected abstract int getChunkX();

    @Shadow(remap = false)
    protected abstract int getChunkZ();

    @Shadow(remap = false)
    public abstract FluidDrillMachine getMachine();

    @Shadow(remap = false)
    protected abstract long getFluidToProduce(FluidVeinWorldEntry entry);

    @Shadow(remap = false)
    protected abstract void depleteVein();

    public FluidDrillLogicMixin(IRecipeLogicMachine machine) {
        super(machine);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void findAndHandleRecipe() {
        if (this.getMachine().getLevel() instanceof ServerLevel serverLevel) {
            this.lastRecipe = null;
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            if (this.veinFluid == null) {
                this.veinFluid = data.getFluidInChunk(this.getChunkX(), this.getChunkZ());
                if (this.veinFluid == null) {
                    if (this.subscription != null) {
                        this.subscription.unsubscribe();
                        this.subscription = null;
                    }
                    return;
                }
            }
            GTRecipe match = this.getFluidDrillRecipe();
            if (match != null) {
                this.setupRecipe(match);
            }
        }
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    private @Nullable GTRecipe getFluidDrillRecipe() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel && veinFluid != null) {
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            var recipe = GTRecipeBuilder.ofRaw()
                    .duration(20)
                    .EUt(GTValues.VA[getMachine().getEnergyTier()])
                    .outputFluids(FluidStack.create(this.veinFluid,
                            this.getFluidToProduce(data.getFluidVeinWorldEntry(this.getChunkX(), this.getChunkZ()))))
                    .buildRawRecipe();
            if (RecipeRunnerHelper.matchRecipe(getMachine(), recipe) && recipe.matchTickRecipe(this.getMachine()).isSuccess()) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public void onRecipeFinish() {
        this.machine.afterWorking();
        if (this.lastRecipe != null) {
            RecipeRunnerHelper.handleRecipeOutput(this.machine, this.lastRecipe);
        }
        this.depleteVein();

        if (this.machine instanceof ISuspendableMachine suspendableMachine && suspendableMachine.gtlcore$isSuspendAfterFinish()) {
            this.setStatus(RecipeLogic.Status.SUSPEND);
            suspendableMachine.gtlcore$setSuspendAfterFinish(false);
        } else {
            GTRecipe match = this.getFluidDrillRecipe();
            if (match != null) {
                this.setupRecipe(match);
                return;
            }
            this.setStatus(Status.IDLE);
        }
        this.progress = 0;
        this.duration = 0;
    }
}
