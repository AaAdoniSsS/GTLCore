package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.mixin.gtlcore.machine.MEDualHatchStockPartMachineAccessor;
import org.gtlcore.gtlcore.mixin.gtm.ae.machine.MEInputHatchPartMachineAccessor;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

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
import java.util.Map;
import java.util.WeakHashMap;

public final class MEStockingFluidJadeHelper {

    private static final String FLUID_ID_TAG = "fluid";
    private static final String FLUID_DATA_TAG = "tag";
    private static final int DISPLAY_FLUID_AMOUNT = 1;
    private static final Map<MetaMachine, List<CompoundTag>> LAST_VALID_VIEWS = Collections.synchronizedMap(new WeakHashMap<>());

    private MEStockingFluidJadeHelper() {}

    public static @Nullable List<ViewGroup<CompoundTag>> createGroups(MetaMachine machine) {
        ExportOnlyAEFluidList aeFluidHandler = getAeFluidHandler(machine);
        List<CompoundTag> views;
        if (aeFluidHandler != null) {
            views = createFluidViews(aeFluidHandler);
        } else {
            IFluidTransfer fluidTransfer = machine.getFluidTransferCap(null, false);
            if (fluidTransfer == null || fluidTransfer.getTanks() == 0) {
                return null;
            }
            views = createFluidViews(fluidTransfer);
        }

        if (views.isEmpty()) {
            views = LAST_VALID_VIEWS.getOrDefault(machine, Collections.emptyList());
        } else {
            views = List.copyOf(views);
            LAST_VALID_VIEWS.put(machine, views);
        }
        if (views.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(new ViewGroup<>(views));
    }

    public static List<ClientViewGroup<FluidView>> createClientGroups(List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, MEStockingFluidJadeHelper::readFluidView, null);
    }

    private static @Nullable FluidView readFluidView(CompoundTag viewData) {
        FluidView view = FluidView.readDefault(viewData);
        if (view != null) {
            var fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(viewData.getString(FLUID_ID_TAG)));
            CompoundTag fluidData = viewData.contains(FLUID_DATA_TAG) ? viewData.getCompound(FLUID_DATA_TAG) : null;
            view.fluidName = new net.minecraftforge.fluids.FluidStack(fluid, DISPLAY_FLUID_AMOUNT, fluidData).getDisplayName();
        }
        return view;
    }

    private static @Nullable ExportOnlyAEFluidList getAeFluidHandler(MetaMachine machine) {
        if (machine instanceof MEInputHatchPartMachine inputHatch) {
            return ((MEInputHatchPartMachineAccessor) inputHatch).gtlcore$getAeFluidHandler();
        }
        if (machine instanceof MEDualHatchStockPartMachine dualHatch) {
            return ((MEDualHatchStockPartMachineAccessor) dualHatch).gtlcore$getAeFluidHandler();
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
            amounts.put(fluidKey, NumberUtils.saturatedAdd(amounts.getLong(fluidKey), stock.amount()));
        }

        List<CompoundTag> views = new ArrayList<>(amounts.size());
        for (Object2LongMap.Entry<AEFluidKey> entry : amounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            JadeFluidObject fluid = JadeFluidObject.of(entry.getKey().getFluid(), amount, entry.getKey().copyTag());
            views.add(FluidView.writeDefault(fluid, amount));
        }
        return views;
    }

    private static List<CompoundTag> createFluidViews(IFluidTransfer fluidTransfer) {
        List<CompoundTag> views = new ArrayList<>(fluidTransfer.getTanks());
        for (int tank = 0; tank < fluidTransfer.getTanks(); tank++) {
            FluidStack fluidStack = fluidTransfer.getFluidInTank(tank);
            if (fluidStack.isEmpty()) {
                continue;
            }
            long amount = fluidStack.getAmount();
            if (amount <= 0L) {
                continue;
            }
            long capacity = Math.max(amount, fluidTransfer.getTankCapacity(tank));
            JadeFluidObject fluid = JadeFluidObject.of(fluidStack.getFluid(), amount, fluidStack.getTag());
            views.add(FluidView.writeDefault(fluid, capacity));
        }
        return views;
    }
}
