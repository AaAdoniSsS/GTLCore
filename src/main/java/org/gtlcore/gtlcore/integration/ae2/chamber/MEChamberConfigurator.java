package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.api.gui.TagFilterConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

/** Builds the same native configurator widget tree on the terminal client and authoritative server. */
public final class MEChamberConfigurator {

    public static final int MAX_ACTION_BYTES = 4096;
    public static final int MAX_TAG_LENGTH = 256;
    public static final int ROOT_ACTION_ID = 1;

    private MEChamberConfigurator() {}

    public static boolean supports(MetaMachine machine, Kind kind) {
        return switch (kind) {
            case CIRCUIT -> machine instanceof ItemBusPartMachine || machine instanceof FluidHatchPartMachine;
            case SYNC_OFFSET -> machine instanceof IModifiableSyncOffset;
            case TAG_FILTER -> machine instanceof TagFilterMEStockBusPartMachine ||
                    machine instanceof TagFilterMEStockHatchPartMachine;
        };
    }

    public static View createClientView(Kind kind, MEChamberManagerTerminalMenu.ChamberDetails details) {
        IFancyConfigurator configurator = switch (kind) {
            case CIRCUIT -> createClientCircuitConfigurator(details);
            case SYNC_OFFSET -> createClientOffsetConfigurator(details.syncOffset());
            case TAG_FILTER -> new TagFilterConfigurator(
                    details::tagWhite, value -> {}, details::tagBlack, value -> {});
        };
        return createView(configurator);
    }

    private static IFancyConfigurator createClientOffsetConfigurator(int initialOffset) {
        AtomicInteger offset = new AtomicInteger(initialOffset);
        return new AdvancedMEConfigurator(offset::set, offset::get);
    }

    public static void handleClientAction(MetaMachine machine, Kind kind, int actionId, FriendlyByteBuf payload) {
        createView(createMachineConfigurator(machine, kind)).root().handleClientAction(actionId, payload);
    }

    private static IFancyConfigurator createClientCircuitConfigurator(
                                                                      MEChamberManagerTerminalMenu.ChamberDetails details) {
        ItemStackTransfer transfer = new ItemStackTransfer(1);
        if (details.circuitSet()) {
            transfer.setStackInSlot(0, IntCircuitBehaviour.stack(details.circuitConfiguration()));
        }
        return new CircuitFancyConfigurator(transfer);
    }

    private static IFancyConfigurator createMachineConfigurator(MetaMachine machine, Kind kind) {
        return switch (kind) {
            case CIRCUIT -> createMachineCircuitConfigurator(machine);
            case SYNC_OFFSET -> new AdvancedMEConfigurator(
                    value -> setMachineOffset(machine, value),
                    () -> machine instanceof IModifiableSyncOffset configurable ? configurable.getOffset() : 0);
            case TAG_FILTER -> new TagFilterConfigurator(
                    () -> readWhitelist(machine), value -> setWhitelist(machine, value),
                    () -> readBlacklist(machine), value -> setBlacklist(machine, value));
        };
    }

    private static IFancyConfigurator createMachineCircuitConfigurator(MetaMachine machine) {
        ItemStackTransfer transfer = new ItemStackTransfer(1);
        ItemStack circuit = getMachineCircuitStack(machine);
        if (!circuit.isEmpty()) {
            transfer.setStackInSlot(0, circuit.copy());
        }
        transfer.setOnContentsChanged(() -> setMachineCircuit(machine, transfer.getStackInSlot(0)));
        return new CircuitFancyConfigurator(transfer);
    }

    private static View createView(IFancyConfigurator configurator) {
        Widget content = configurator.createConfigurator();
        WidgetGroup root = new WidgetGroup(0, 0, content.getSize().width, content.getSize().height);
        root.addWidget(content);
        return new View(configurator, root);
    }

    private static void setMachineCircuit(MetaMachine machine, ItemStack stack) {
        if (machine instanceof ItemBusPartMachine bus) {
            bus.getCircuitInventory().setStackInSlot(0, stack.copy());
        } else if (machine instanceof FluidHatchPartMachine hatch) {
            hatch.getCircuitInventory().setStackInSlot(0, stack.copy());
        } else {
            return;
        }
        markMachineChanged(machine);
    }

    private static ItemStack getMachineCircuitStack(MetaMachine machine) {
        if (machine instanceof ItemBusPartMachine bus) {
            return bus.getCircuitInventory().getStackInSlot(0);
        }
        if (machine instanceof FluidHatchPartMachine hatch) {
            return hatch.getCircuitInventory().getStackInSlot(0);
        }
        return ItemStack.EMPTY;
    }

    private static void setMachineOffset(MetaMachine machine, int value) {
        if (machine instanceof IModifiableSyncOffset configurable) {
            configurable.setOffset(Math.max(0, value));
            markMachineChanged(machine);
        }
    }

    private static String readWhitelist(MetaMachine machine) {
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            return bus.getTagWhite();
        }
        return machine instanceof TagFilterMEStockHatchPartMachine hatch ? hatch.getTagWhite() : "";
    }

    private static void setWhitelist(MetaMachine machine, String value) {
        String normalized = normalizeTag(value);
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            bus.setTagWhite(normalized);
        } else if (machine instanceof TagFilterMEStockHatchPartMachine hatch) {
            hatch.setTagWhite(normalized);
        } else {
            return;
        }
        markMachineChanged(machine);
    }

    private static String readBlacklist(MetaMachine machine) {
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            return bus.getTagBlack();
        }
        return machine instanceof TagFilterMEStockHatchPartMachine hatch ? hatch.getTagBlack() : "";
    }

    private static void setBlacklist(MetaMachine machine, String value) {
        String normalized = normalizeTag(value);
        if (machine instanceof TagFilterMEStockBusPartMachine bus) {
            bus.setTagBlack(normalized);
        } else if (machine instanceof TagFilterMEStockHatchPartMachine hatch) {
            hatch.setTagBlack(normalized);
        } else {
            return;
        }
        markMachineChanged(machine);
    }

    private static String normalizeTag(String value) {
        return value.length() <= MAX_TAG_LENGTH ? value : value.substring(0, MAX_TAG_LENGTH);
    }

    private static void markMachineChanged(MetaMachine machine) {
        machine.markDirty();
        machine.notifyBlockUpdate();
    }

    public enum Kind {
        CIRCUIT,
        SYNC_OFFSET,
        TAG_FILTER
    }

    public record View(IFancyConfigurator configurator, WidgetGroup root) {}
}
