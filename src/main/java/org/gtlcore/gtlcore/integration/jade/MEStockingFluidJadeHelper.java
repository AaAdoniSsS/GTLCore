package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.mixin.gtlcore.machine.MEDualHatchStockPartMachineAccessor;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MEStockingFluidJadeHelper {

    private MEStockingFluidJadeHelper() {}

    public static @Nullable List<ViewGroup<CompoundTag>> createGroups(MetaMachine machine) {
        ExportOnlyAEFluidList fluidHandler = getStockingFluidHandler(machine);
        if (fluidHandler == null) {
            return null;
        }

        List<CompoundTag> views = createFluidViews(fluidHandler);
        if (views.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(new ViewGroup<>(views));
    }

    public static List<ClientViewGroup<FluidView>> createClientGroups(List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, FluidView::readDefault, null);
    }

    private static @Nullable ExportOnlyAEFluidList getStockingFluidHandler(MetaMachine machine) {
        if (machine instanceof MEDualHatchStockPartMachine) {
            ExportOnlyAEFluidList fluidHandler = ((MEDualHatchStockPartMachineAccessor) machine).gtlcore$getAeFluidHandler();
            if (fluidHandler instanceof IOptimizedMEList optimized && optimized.isStocking()) {
                return fluidHandler;
            }
        }
        return null;
    }

    private static List<CompoundTag> createFluidViews(ExportOnlyAEFluidList fluidHandler) {
        Object2LongLinkedOpenHashMap<AEFluidKey> amounts = new Object2LongLinkedOpenHashMap<>();
        for (ExportOnlyAEFluidSlot slot : fluidHandler.getInventory()) {
            GenericStack stock = slot.getStock();
            if (stock == null || stock.amount() <= 0L || !(stock.what() instanceof AEFluidKey fluidKey)) {
                continue;
            }
            long amount = NumberUtils.saturatedAdd(amounts.getLong(fluidKey), stock.amount());
            amounts.put(fluidKey, amount);
        }

        List<CompoundTag> views = new ArrayList<>(amounts.size());
        for (Object2LongMap.Entry<AEFluidKey> entry : amounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            AEFluidKey fluidKey = entry.getKey();
            JadeFluidObject fluid = JadeFluidObject.of(fluidKey.getFluid(), amount, fluidKey.copyTag());
            views.add(FluidView.writeDefault(fluid, amount));
        }
        return views;
    }
}
