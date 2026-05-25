package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedInfiniteDrillMachine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import com.lowdragmc.lowdraglib.side.fluid.FluidHelper;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.*;

/**
 * @author EasterFG on 2024/10/27
 */
public class AdvancedInfiniteDrillLogic extends RecipeLogic {

    public static final int MAX_PROGRESS = 20;

    @NotNull
    private final Map<Fluid, Long> veinFluids;

    @Getter
    @Setter
    private int range;

    public AdvancedInfiniteDrillLogic(IRecipeLogicMachine machine, int range) {
        super(machine);
        this.veinFluids = new HashMap<>();
        this.range = range;
    }

    @Override
    public AdvancedInfiniteDrillMachine getMachine() {
        return (AdvancedInfiniteDrillMachine) super.getMachine();
    }

    @Override
    public void findAndHandleRecipe() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            lastRecipe = null;
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            if (veinFluids.isEmpty()) {
                this.getGridFluid(data);
                if (veinFluids.isEmpty()) {
                    if (subscription != null) {
                        subscription.unsubscribe();
                        subscription = null;
                    }
                }
            }
            if (getMachine().isEmpty() || !getMachine().canRunnable()) return;
            var match = getFluidDrillRecipe();
            if (match != null) {
                var copied = match.copy(new ContentModifier(match.duration, 0));
                if (matchRecipe(this.machine, match) && copied.matchTickRecipe(this.machine).isSuccess()) {
                    setupRecipe(match);
                }
            }
        }
    }

    @Nullable
    private GTRecipe getFluidDrillRecipe() {
        if (!veinFluids.isEmpty()) {
            long total = veinFluids.values().stream().mapToLong(Long::longValue).sum();
            var recipe = GTRecipeBuilder.ofRaw()
                    .duration(MAX_PROGRESS)
                    .EUt(GTValues.V[9] + total)
                    .outputFluids(veinFluids.entrySet().stream()
                            .map(entry -> FluidStack.create(entry.getKey(), entry.getValue()))
                            .toArray(FluidStack[]::new))
                    .buildRawRecipe();
            recipe = recipe.copy(ContentModifier.multiplier(getMachine().getRate()), false);
            if (matchRecipe(getMachine(), recipe) && recipe.matchTickRecipe(getMachine()).isSuccess()) {
                return recipe;
            }
        }
        return null;
    }

    private long getFluidToProduce(FluidVeinWorldEntry entry) {
        var definition = entry.getDefinition();
        if (definition != null) {
            int depletedYield = definition.getDepletedYield();
            int regularYield = entry.getFluidYield();
            int remainingOperations = entry.getOperationsRemaining();

            int produced = Math.max(depletedYield,
                    regularYield * remainingOperations / BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
            return produced * FluidHelper.getBucket() / 1000;
        }
        return 0;
    }

    protected void getGridFluid(BedrockFluidVeinSavedData data) {
        int x = getChunkX();
        int z = getChunkZ();
        int mid = range / 2;
        for (int i = -mid; i <= mid; i++) {
            for (int j = -mid; j <= mid; j++) {
                var fluid = data.getFluidInChunk(x + i, z + j);
                if (fluid != null) {
                    long produced = getFluidToProduce(data.getFluidVeinWorldEntry(x + i, z + j));
                    if (produced > 0) {
                        var value = veinFluids.getOrDefault(fluid, 0L) + (produced * 10);
                        veinFluids.put(fluid, value);
                    }
                }
            }
        }
    }

    @NotNull
    public Map<Fluid, Long> getVeinFluids() {
        return veinFluids;
    }

    @Override
    public void onRecipeFinish() {
        if (lastRecipe != null) handleRecipeOutput(this.machine, lastRecipe);

        if (this.machine instanceof ISuspendableMachine suspendableMachine && suspendableMachine.gtlcore$isSuspendAfterFinish()) {
            this.setStatus(RecipeLogic.Status.SUSPEND);
            suspendableMachine.gtlcore$setSuspendAfterFinish(false);
        } else {
            // try it again
            var match = getFluidDrillRecipe();
            if (match != null) {
                var copied = match.copy(new ContentModifier(match.duration, 0));
                if (matchRecipe(this.machine, match) && copied.matchTickRecipe(this.machine).isSuccess()) {
                    setupRecipe(match);
                    return;
                }
            }
            setStatus(Status.IDLE);
        }
        progress = 0;
        duration = 0;
    }

    private int getChunkX() {
        return SectionPos.blockToSectionCoord(getMachine().getPos().getX());
    }

    private int getChunkZ() {
        return SectionPos.blockToSectionCoord(getMachine().getPos().getZ());
    }
}
