package org.gtlcore.gtlcore.integration.jade.provider;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.integration.jade.provider.CapabilityBlockProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class BatchProcessingProvider extends CapabilityBlockProvider<IRecipeLogicMachine> {

    private static final String BATCH_SIZE_TAG = "batch_size";

    public BatchProcessingProvider() {
        super(GTLCore.id("batch_processing_provider"));
    }

    @Nullable
    @Override
    protected IRecipeLogicMachine getCapability(Level level, BlockPos pos, @Nullable Direction side) {
        return MetaMachine.getMachine(level, pos) instanceof IRecipeLogicMachine machine ? machine : null;
    }

    @Override
    protected void write(CompoundTag data, IRecipeLogicMachine capability) {
        var recipeLogic = capability.getRecipeLogic();
        if (!recipeLogic.isActive()) return;
        var recipe = recipeLogic.getLastRecipe();
        if (recipe != null && IGTRecipe.of(recipe).getBatchSize() > 1) {
            data.putInt(BATCH_SIZE_TAG, IGTRecipe.of(recipe).getBatchSize());
        }
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        int batchSize = capData.getInt(BATCH_SIZE_TAG);
        if (batchSize > 1) {
            tooltip.add(Component.translatable("gui.gtlcore.batch_processing.active", batchSize));
        }
    }
}
