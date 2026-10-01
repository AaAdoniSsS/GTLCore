package org.gtlcore.gtlcore.api.machine.computation;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.NetworkSwitchMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.core.Direction;

import com.hepdd.gtmthings.common.block.machine.multiblock.part.computation.WirelessOpticalComputationHatchMachine;

import java.util.ArrayList;
import java.util.List;

public final class ComputationConnections {

    private ComputationConnections() {}

    public static boolean loaded(MetaMachine machine) {
        var level = machine.getLevel();
        return level != null && !level.isClientSide && level.hasChunkAt(machine.getPos()) &&
                MetaMachine.getMachine(level, machine.getPos()) == machine;
    }

    public static boolean participates(MetaMachine machine) {
        if (machine instanceof ComputationNode || machine instanceof ComputationSource) return true;
        for (var trait : machine.getTraits()) if (trait instanceof ComputationNode) return true;
        return false;
    }

    public static List<IOpticalComputationProvider> controllers(MetaMachine machine) {
        if (machine instanceof IOpticalComputationProvider provider) return List.of(provider);
        List<IOpticalComputationProvider> result = new ArrayList<>();
        if (machine instanceof IMultiPart part) {
            for (var controller : part.getControllers()) {
                if (!controller.isFormed()) continue;
                if (controller instanceof IOpticalComputationProvider provider) result.add(provider);
                else for (var trait : controller.self().getTraits()) {
                    if (trait instanceof IOpticalComputationProvider provider) result.add(provider);
                }
            }
        }
        return result;
    }

    public static List<IOpticalComputationProvider> wired(MetaMachine machine) {
        var level = machine.getLevel();
        if (level == null) return List.of();
        List<IOpticalComputationProvider> result = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            var pos = machine.getPos().relative(direction);
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof OpticalPipeBlockEntity pipe)) continue;
            var provider = pipe.getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER, direction.getOpposite()).orElse(null);
            if (provider != null) result.add(provider);
        }
        return result;
    }

    public static List<IOpticalComputationProvider> wireless(MetaMachine machine) {
        if (!(machine instanceof WirelessOpticalComputationHatchMachine receiver)) return List.of();
        var level = machine.getLevel();
        var target = receiver.getTransmitterPos();
        if (level == null || target == null || !level.hasChunkAt(target)) return List.of();
        if (!(MetaMachine.getMachine(level, target) instanceof WirelessOpticalComputationHatchMachine transmitter) ||
                !transmitter.isTransmitter()) return List.of();
        return List.of(transmitter.getComputationContainer());
    }

    public static List<IOpticalComputationProvider> switchInputs(NetworkSwitchMachine machine) {
        List<IOpticalComputationProvider> result = new ArrayList<>();
        for (var part : machine.getParts()) {
            if (!PartAbility.COMPUTATION_DATA_RECEPTION.isApplicable(part.self().getBlockState().getBlock())) continue;
            if (part instanceof IOpticalComputationProvider provider) result.add(provider);
            else for (var handler : part.getRecipeHandlers()) {
                if (handler instanceof IOpticalComputationProvider provider) result.add(provider);
            }
        }
        return result;
    }

    /** Compatibility for callers invoking an individual handler outside GTRecipe. No controller is mutated here. */
    public static List<?> legacyInput(IOpticalComputationProvider provider, IO io, GTRecipe recipe,
                                     List<?> left, boolean simulate) {
        if (io != IO.IN) return left;
        long required = 0;
        try {
            for (Object amount : left) required = Math.addExact(required, ComputationAmounts.read(amount));
        } catch (IllegalArgumentException | ArithmeticException e) {
            return left;
        }
        required = ComputationRecipes.legacyRequired(recipe, required);
        if (required == 0) return null;
        long drawn = ComputationRecipes.legacyDraw(provider, recipe, required, simulate);
        if (drawn >= required) return null;
        return List.of(ComputationAmounts.box(required - drawn));
    }
}
