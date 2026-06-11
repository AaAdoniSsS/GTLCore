package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class DysonSphereMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            DysonSphereMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    private int DysonSphereData;
    @Persisted
    private int DysonSpheredamageData;
    @Persisted
    private boolean upgradeTriggeredThisRecipe = false;
    @Persisted
    private boolean damageTriggeredThisRecipe = false;

    protected ConditionalSubscriptionHandler nightSubs;

    @Nullable
    private List<@NotNull BlockPos> cachedCheckPositions;

    public DysonSphereMachine(IMachineBlockEntity holder) {
        super(holder);
        this.nightSubs = new ConditionalSubscriptionHandler(this, this::nightUpdate, () -> DysonSphereData > 0);
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    protected void nightUpdate() {
        if (getOffsetTimer() % 10 == 0 && getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.setDayTime(18000L);
        }
    }

    @Override
    public long getMaxVoltage() {
        long totalPower = 0;
        // 获取所有输出仓（动力仓）
        List<IEnergyContainer> outputContainers = getOutputContainers();
        for (IEnergyContainer container : outputContainers) {
            totalPower += container.getOutputVoltage() * container.getOutputAmperage();
        }
        if (totalPower <= 0) {
            // 没有输出仓时，回退到父类逻辑（避免 NPE）
            return super.getMaxVoltage();
        }
        int equivalentTier = getEquivalentTier(totalPower);
        return GTValues.V[equivalentTier];
    }

    private int getEquivalentTier(long totalPower) {
        int maxTier = GTValues.V.length - 1;
        for (int tier = maxTier; tier >= 0; tier--) {
            if (GTValues.V[tier] <= totalPower) {
                return tier;
            }
        }
        return 0;
    }

    private List<IEnergyContainer> getOutputContainers() {
        List<IEnergyContainer> outputContainers = new ArrayList<>();
        List<IRecipeHandler<?>> capabilities = this.capabilitiesProxy.get(IO.OUT, EURecipeCapability.CAP);
        if (capabilities != null) {
            for (IRecipeHandler<?> handler : capabilities) {
                if (handler instanceof IEnergyContainer container) {
                    outputContainers.add(container);
                }
            }
        }
        return outputContainers;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        nightSubs.initialize(getLevel());
        cachedCheckPositions = findCheckPositions();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        cachedCheckPositions = null;
        if (nightSubs != null) nightSubs.unsubscribe();
    }

    @Nullable
    protected List<@NotNull BlockPos> findCheckPositions() {
        Level level = getLevel();
        if (level == null) return null;

        BlockPos pos = getPos();
        BlockPos[] coordinates = new BlockPos[] {
                pos.offset(4, 14, 0),
                pos.offset(-4, 14, 0),
                pos.offset(0, 14, 4),
                pos.offset(0, 14, -4)
        };

        for (BlockPos blockPos : coordinates) {
            if (Objects.equals(Registries.getBlockId(level.getBlockState(blockPos).getBlock()), "kubejs:dyson_receiver_casing")) {
                var checkPositions = new ObjectArrayList<BlockPos>(169);
                for (int i = -6; i < 7; i++) {
                    for (int j = -6; j < 7; j++) {
                        if (i != 0 || j != 0) {
                            checkPositions.add(blockPos.offset(i, 1, j));
                        }
                    }
                }
                return checkPositions;
            }
        }
        return null;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        GTRecipe currentRecipe = getRecipeLogic().getLastRecipe();
        if (currentRecipe == null) return value;

        int progress = getRecipeLogic().getProgress();
        int duration = getRecipeLogic().getDuration();
        if (duration <= 0) return value;

        if (!upgradeTriggeredThisRecipe && progress + 1 >= duration && getDysonSphereData() < 10000 && isLaunch(currentRecipe)) {
            upgradeTriggeredThisRecipe = true;
            if (getDysonSpheredamageData() > 60) {
                this.DysonSpheredamageData = 0;
            } else {
                this.DysonSphereData++;
            }
        }

        int targetProgress = (int) (duration * 0.95);
        if (!damageTriggeredThisRecipe && progress >= targetProgress && getDysonSphereData() > 0 && !isLaunch(currentRecipe)) {
            damageTriggeredThisRecipe = true;
            if (Math.random() < 0.01 * (1 + (double) getDysonSphereData() / 256)) {
                if (getDysonSpheredamageData() > 99) {
                    this.DysonSphereData--;
                    this.DysonSpheredamageData = 0;
                } else {
                    this.DysonSpheredamageData++;
                }
            }
        }

        return value;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        upgradeTriggeredThisRecipe = false;
        damageTriggeredThisRecipe = false;
        if (cachedCheckPositions != null) {
            Level level = getLevel();
            if (level != null) {
                for (BlockPos checkPos : cachedCheckPositions) {
                    if (!level.canSeeSky(checkPos)) {
                        getRecipeLogic().resetRecipeLogic();
                        return false;
                    }
                }
            }
        } else {
            getRecipeLogic().resetRecipeLogic();
            return false;
        }

        if (recipe != null && isLaunch(recipe)) {
            return getDysonSphereData() <= 10000;
        } else {
            return getDysonSphereData() > 0;
        }
    }

    private boolean isLaunch(GTRecipe recipe) {
        return RecipeHelper.getOutputEUt(recipe) < GTValues.V[GTValues.MAX];
    }

    private double getEfficiency() {
        return (double) (50 - Math.max(0, getDysonSpheredamageData() - 60)) / 50;
    }

    @Override
    public int getOutputSignal(@Nullable Direction side) {
        return (int) ((1 - getEfficiency()) * 15);
    }

    @Override
    public long getOverclockVoltage() {
        return (long) (2L * GTValues.V[GTValues.MAX] * getDysonSphereData() * getEfficiency());
    }

    @Nullable
    public static GTRecipe recipeModifier(MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof DysonSphereMachine engineMachine) {
            if (!engineMachine.isLaunch(recipe)) {
                GTRecipe recipe1 = recipe.copy();
                recipe1.tickOutputs.put(EURecipeCapability.CAP, List.of(new Content(
                        engineMachine.getOverclockVoltage(),
                        ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0, null, null)));
                recipe1.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(
                        engineMachine.getDysonSphereData(),
                        ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0, null, null)));
                return recipe1;
            }
            return recipe;
        }
        return null;
    }

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        if (!this.isFormed) return;
        textList.add(Component.translatable("gtceu.machine.dyson_sphere.number", getDysonSphereData()));
        textList.add(Component.translatable("gtceu.machine.dyson_sphere.voltage",
                (getDysonSphereData() > 0 ? getOverclockVoltage() : 0)));
        textList.add(Component.translatable("gtceu.machine.fission_reactor.damaged",
                getDysonSpheredamageData()).append("%"));
    }
}
