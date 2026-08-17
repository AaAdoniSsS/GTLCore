package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualInputHatchPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEOutputFilterHandler;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.mixin.gtm.ae.machine.MEOutputBusPartMachineAccessor;
import org.gtlcore.gtlcore.mixin.gtm.ae.machine.MEOutputHatchPartMachineAccessor;
import org.gtlcore.gtlcore.mixin.gtmt.MEOutputPartMachineAccessor;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.DimensionalBlockPos;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.util.Platform;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.MEOutputPartMachine;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-authoritative index of GT ME chambers reachable from this terminal's AE grid. */
public final class MEChamberManagerTerminalMenu extends AEBaseMenu {

    public static final int MAX_SYNC_ENTRIES = 1_024;
    public static final int MAX_SYNC_SLOT_COUNT = 65_536;
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;
    private static final double BLOCK_CENTER_OFFSET = 0.5D;
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing((Entry entry) -> entry.address().dimension().toString())
            .thenComparingInt(entry -> entry.address().pos().getX())
            .thenComparingInt(entry -> entry.address().pos().getY())
            .thenComparingInt(entry -> entry.address().pos().getZ());

    private final @Nullable MEChamberManagerTerminalPart terminal;
    private final @Nullable WTMenuHost wirelessHost;
    private final Player menuPlayer;
    private final boolean universalTerminal;
    private List<Entry> entries = List.of();
    private @Nullable Address selectedAddress;
    private List<SlotContent> selectedContents = List.of();
    private ChamberDetails selectedDetails = ChamberDetails.EMPTY;
    private int ticksUntilSync;

    public static MEChamberManagerTerminalMenu createWiredClientMenu(int containerId, Inventory inventory,
                                                                     FriendlyByteBuf data) {
        return new MEChamberManagerTerminalMenu(
                GTLWirelessAeContent.ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                null,
                null,
                false);
    }

    public static MEChamberManagerTerminalMenu createWirelessClientMenu(int containerId, Inventory inventory,
                                                                        FriendlyByteBuf data) {
        MenuLocator locator = MenuLocators.readFromPacket(data);
        boolean returningFromSubmenu = data.readBoolean();
        WTMenuHost host = locator.locate(inventory.player, WTMenuHost.class);
        return new MEChamberManagerTerminalMenu(
                GTLWirelessAeContent.WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                host,
                locator,
                returningFromSubmenu);
    }

    private MEChamberManagerTerminalMenu(MenuType<?> menuType, int containerId, Inventory inventory,
                                         @Nullable MEChamberManagerTerminalPart terminal,
                                         @Nullable WTMenuHost wirelessHost, @Nullable MenuLocator locator,
                                         boolean returningFromSubmenu) {
        super(menuType, containerId, inventory, terminal == null ? wirelessHost : terminal);
        this.terminal = terminal;
        this.wirelessHost = wirelessHost;
        this.menuPlayer = inventory.player;
        this.universalTerminal = wirelessHost != null && wirelessHost.getItemStack().getItem() instanceof ItemWUT;
        if (locator != null) {
            setLocator(locator);
            setReturnedFromSubScreen(returningFromSubmenu);
        }
        addPlayerInventorySlots(inventory);
    }

    public static void open(ServerPlayer player, MEChamberManagerTerminalPart terminal) {
        NetworkHooks.openScreen(player, new MenuProvider() {

            @Override
            public @NotNull Component getDisplayName() {
                return Component.translatable("screen.gtlcore.me_chamber_manager_terminal");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                    @NotNull Player menuPlayer) {
                return new MEChamberManagerTerminalMenu(
                        GTLWirelessAeContent.ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                        containerId,
                        inventory,
                        terminal,
                        null,
                        null,
                        false);
            }
        }, buffer -> {});
    }

    public static boolean openWireless(Player player, MenuLocator locator, boolean returningFromSubmenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        WTMenuHost host = locator.locate(player, WTMenuHost.class);
        if (host == null || !host.rangeCheck()) {
            return false;
        }

        NetworkHooks.openScreen(
                serverPlayer,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.wireless_me_chamber_manager_terminal");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new MEChamberManagerTerminalMenu(
                                GTLWirelessAeContent.WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                                containerId,
                                inventory,
                                null,
                                host,
                                locator,
                                returningFromSubmenu);
                    }
                },
                buffer -> {
                    MenuLocators.writeToPacket(buffer, locator);
                    buffer.writeBoolean(returningFromSubmenu);
                });
        return true;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public @Nullable Address getSelectedAddress() {
        return selectedAddress;
    }

    public List<SlotContent> getSelectedContents() {
        return selectedContents;
    }

    public ChamberDetails getSelectedDetails() {
        return selectedDetails;
    }

    public boolean isUniversalTerminal() {
        return universalTerminal;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        if (selectedAddress != null && entries.stream().noneMatch(entry -> entry.address().equals(selectedAddress))) {
            selectedAddress = null;
            selectedContents = List.of();
            selectedDetails = ChamberDetails.EMPTY;
        }
    }

    public void setSelectedContents(@Nullable Address address, List<SlotContent> contents, ChamberDetails details) {
        this.selectedAddress = address;
        this.selectedContents = List.copyOf(contents);
        this.selectedDetails = details;
    }

    public void selectChamber(ServerPlayer player, Address address) {
        if (player.containerMenu != this || !stillValid(player)) {
            return;
        }
        MetaMachine chamber = findAccessibleChamber(player, address);
        if (chamber == null) {
            return;
        }
        selectedAddress = address;
        selectedContents = collectContents(chamber);
        selectedDetails = snapshotDetails(chamber);
        sendSelectedContents(player);
    }

    public void setSlotConfig(ServerPlayer player, Address address, StorageKind storage, int slot,
                              @Nullable AEKey key) {
        if (player.containerMenu != this || !stillValid(player) || !isKeyValidForStorage(key, storage)) {
            return;
        }
        MetaMachine chamber = findAccessibleChamber(player, address);
        if (chamber instanceof MEExtendedOutputPartMachine extendedOutput) {
            MEOutputFilterHandler filter = extendedOutput.getFilterHandler();
            if (storage == StorageKind.ITEM) {
                filter.setItemFilter(slot, (AEItemKey) key);
            } else {
                filter.setFluidFilter(slot, (AEFluidKey) key);
            }
            updateSelectedChamber(player, address, chamber);
            return;
        }
        IConfigurableSlot configurableSlot = chamber == null ? null : findConfigurableSlot(chamber, storage, slot);
        if (configurableSlot == null) {
            return;
        }
        GenericStack previous = configurableSlot.getConfig();
        long amount = previous == null || previous.amount() <= 0L ? defaultAmount(storage) : previous.amount();
        configurableSlot.setConfig(key == null ? null : new GenericStack(key, amount));
        updateSelectedChamber(player, address, chamber);
    }

    public void setSlotAmount(ServerPlayer player, Address address, StorageKind storage, int slot, long amount) {
        if (player.containerMenu != this || !stillValid(player) || amount <= 0L) {
            return;
        }
        MetaMachine chamber = findAccessibleChamber(player, address);
        IConfigurableSlot configurableSlot = chamber == null ? null : findEditableSlot(chamber, storage, slot);
        GenericStack config = configurableSlot == null ? null : configurableSlot.getConfig();
        if (config == null) {
            return;
        }
        configurableSlot.setConfig(new GenericStack(config.what(), amount));
        updateSelectedChamber(player, address, chamber);
    }

    public void setControl(ServerPlayer player, Address address, ControlKind control, int value) {
        if (player.containerMenu != this || !stillValid(player)) {
            return;
        }
        MetaMachine chamber = findAccessibleChamber(player, address);
        if (chamber == null) {
            return;
        }
        switch (control) {
            case WORKING -> {
                if (chamber instanceof TieredIOPartMachine ioPart && !(chamber instanceof MEOutputPartMachine)) {
                    ioPart.setWorkingEnabled(value != 0);
                } else {
                    return;
                }
            }
            case DISTINCT -> {
                if (chamber instanceof IDistinctPart distinctPart && isInputChamber(chamber)) {
                    distinctPart.setDistinct(value != 0);
                } else {
                    return;
                }
            }
            case AUTO_PULL -> {
                if (chamber instanceof MEStockingBusPartMachine stockingBus) {
                    stockingBus.setAutoPull(value != 0);
                } else if (chamber instanceof MEStockingHatchPartMachine stockingHatch) {
                    stockingHatch.setAutoPull(value != 0);
                } else if (chamber instanceof MEDualHatchStockPartMachine dualStock) {
                    dualStock.setTerminalAutoPullMode(value);
                } else {
                    return;
                }
            }
            case COUNT_SORT -> {
                if (chamber instanceof TagFilterMEStockBusPartMachine tagBus) {
                    tagBus.setCountSort(value != 0);
                } else if (chamber instanceof TagFilterMEStockHatchPartMachine tagHatch) {
                    tagHatch.setCountSort(value != 0);
                } else {
                    return;
                }
            }
            case PRIORITY -> {
                if (chamber instanceof MEExtendedOutputPartMachineBase extendedOutput) {
                    extendedOutput.setTerminalPriority(value);
                } else {
                    return;
                }
            }
            case ITEM_BLACKLIST, ITEM_NBT, FLUID_BLACKLIST, FLUID_NBT -> {
                if (!(chamber instanceof MEExtendedOutputPartMachine extendedOutput)) {
                    return;
                }
                MEOutputFilterHandler filter = extendedOutput.getFilterHandler();
                switch (control) {
                    case ITEM_BLACKLIST -> filter.updateItemFilterSettings(value != 0, filter.isIgnoreItemNbt());
                    case ITEM_NBT -> filter.updateItemFilterSettings(filter.isItemBlackList(), value != 0);
                    case FLUID_BLACKLIST -> filter.updateFluidFilterSettings(value != 0, filter.isIgnoreFluidNbt());
                    case FLUID_NBT -> filter.updateFluidFilterSettings(filter.isFluidBlackList(), value != 0);
                    default -> throw new IllegalStateException("Unexpected filter control: " + control);
                }
            }
        }
        updateSelectedChamber(player, address, chamber);
    }

    public void handleConfiguratorAction(ServerPlayer player, Address address, MEChamberConfigurator.Kind kind,
                                         int actionId, byte[] actionData) {
        if (player.containerMenu != this || !stillValid(player)) {
            return;
        }
        MetaMachine chamber = findAccessibleChamber(player, address);
        if (chamber == null || !MEChamberConfigurator.supports(chamber, kind) ||
                kind != MEChamberConfigurator.Kind.TAG_FILTER && !isInputChamber(chamber) ||
                actionId != MEChamberConfigurator.ROOT_ACTION_ID ||
                actionData.length > MEChamberConfigurator.MAX_ACTION_BYTES) {
            return;
        }
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(actionData));
        try {
            MEChamberConfigurator.handleClientAction(chamber, kind, actionId, payload);
            updateSelectedChamber(player, address, chamber);
        } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
            // Reject malformed widget actions without taking down the server network thread.
        } finally {
            payload.release();
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!hasDataSource() || menuPlayer.level().isClientSide() || !isValidMenu() || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL_TICKS;
        if (menuPlayer instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
            entries = collectEntries();
            WirelessAePackets.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new WirelessAePackets.SyncMEChamberManagerEntriesPacket(containerId, entries));
            if (selectedAddress != null) {
                MetaMachine chamber = findAccessibleChamber(serverPlayer, selectedAddress);
                if (chamber == null) {
                    selectedAddress = null;
                    selectedContents = List.of();
                    selectedDetails = ChamberDetails.EMPTY;
                } else {
                    selectedContents = collectContents(chamber);
                    selectedDetails = snapshotDetails(chamber);
                }
                sendSelectedContents(serverPlayer);
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (terminal == null) {
            return super.stillValid(player);
        }
        IPartHost host = terminal.getHost();
        BlockPos pos = host.getLocation().getPos();
        return player.level() == terminal.getLevel() && host.getPart(terminal.getSide()) == terminal &&
                terminal.getMainNode().isActive() &&
                player.distanceToSqr(
                        pos.getX() + BLOCK_CENTER_OFFSET,
                        pos.getY() + BLOCK_CENTER_OFFSET,
                        pos.getZ() + BLOCK_CENTER_OFFSET) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    private void sendSelectedContents(ServerPlayer player) {
        WirelessAePackets.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WirelessAePackets.SyncMEChamberManagerContentsPacket(
                        containerId, selectedAddress, selectedContents, selectedDetails));
    }

    private void updateSelectedChamber(ServerPlayer player, Address address, MetaMachine chamber) {
        chamber.markDirty();
        chamber.notifyBlockUpdate();
        selectedAddress = address;
        selectedContents = collectContents(chamber);
        selectedDetails = snapshotDetails(chamber);
        sendSelectedContents(player);
    }

    private List<Entry> collectEntries() {
        IGrid grid = getGrid();
        if (grid == null) {
            return List.of();
        }
        List<Entry> result = new ArrayList<>();
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof MetaMachine machine && isMEChamber(machine)) {
                result.add(snapshot(machine));
                if (result.size() >= MAX_SYNC_ENTRIES) {
                    break;
                }
            }
        }
        result.sort(ENTRY_ORDER);
        return List.copyOf(result);
    }

    private @Nullable MetaMachine findAccessibleChamber(ServerPlayer player, Address address) {
        IGrid grid = getGrid();
        if (grid == null) {
            return null;
        }
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof MetaMachine machine && isMEChamber(machine) &&
                    address.equals(Address.of(machine)) && hasEditPermission(machine, player)) {
                return machine;
            }
        }
        return null;
    }

    private @Nullable IGrid getGrid() {
        if (terminal != null) {
            return terminal.getMainNode().getGrid();
        }
        IGridNode node = wirelessHost == null ? null : wirelessHost.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    private boolean hasDataSource() {
        return terminal != null || wirelessHost != null;
    }

    private boolean hasEditPermission(MetaMachine machine, Player player) {
        DimensionalBlockPos location = terminal == null ?
                new DimensionalBlockPos(machine.getLevel(), machine.getPos()) :
                terminal.getHost().getLocation();
        return Platform.hasPermissions(location, player);
    }

    private static boolean isMEChamber(MetaMachine machine) {
        return machine instanceof MEBusPartMachine || machine instanceof MEHatchPartMachine ||
                machine instanceof MEOutputPartMachine || machine instanceof MEExtendedOutputPartMachineBase;
    }

    private static Entry snapshot(MetaMachine machine) {
        ControllerInfo controller = controllerInfo(machine);
        return new Entry(
                Address.of(machine),
                machine.getDefinition().asStack(),
                machine.getDefinition().asStack().getHoverName(),
                controller.name(),
                controller.pos());
    }

    private static ControllerInfo controllerInfo(MetaMachine machine) {
        if (!(machine instanceof MultiblockPartMachine part)) {
            return ControllerInfo.NONE;
        }
        return part.getControllers().stream()
                .filter(IMultiController::isFormed)
                .findFirst()
                .map(controller -> new ControllerInfo(
                        Component.translatable(controller.self().getDefinition().getDescriptionId()),
                        controller.self().getPos()))
                .orElse(ControllerInfo.NONE);
    }

    private static List<SlotContent> collectContents(MetaMachine machine) {
        List<SlotContent> contents = new ArrayList<>();
        if (machine instanceof MEExtendedOutputPartMachineBase extendedOutput) {
            addExtendedOutputBufferContents(contents, extendedOutput);
            if (extendedOutput instanceof MEExtendedOutputPartMachine filteredOutput) {
                addFilterContents(contents, filteredOutput.getFilterHandler());
            }
            return List.copyOf(contents);
        }
        if (machine instanceof MEOutputPartMachine output) {
            addKeyStorageContents(
                    contents,
                    ((MEOutputPartMachineAccessor) output).gtlcore$getInternalBuffer(),
                    StorageKind.ITEM);
            addKeyStorageContents(
                    contents,
                    ((MEOutputPartMachineAccessor) output).gtlcore$getInternalTankBuffer(),
                    StorageKind.FLUID);
            return List.copyOf(contents);
        }
        if (machine instanceof MEOutputBusPartMachine outputBus) {
            addKeyStorageContents(
                    contents,
                    ((MEOutputBusPartMachineAccessor) outputBus).gtlcore$getInternalBuffer(),
                    StorageKind.ITEM);
            return List.copyOf(contents);
        }
        if (machine instanceof MEOutputHatchPartMachine outputHatch) {
            addKeyStorageContents(
                    contents,
                    ((MEOutputHatchPartMachineAccessor) outputHatch).gtlcore$getInternalBuffer(),
                    StorageKind.FLUID);
            return List.copyOf(contents);
        }
        if (machine instanceof ItemBusPartMachine bus) {
            if (bus.getInventory() instanceof ExportOnlyAEItemList itemList) {
                addConfigurableContents(contents, itemList, StorageKind.ITEM, itemList.isStocking());
            } else {
                var itemHandler = bus.getInventory();
                for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                    ItemStack stack = itemHandler.getStackInSlot(slot);
                    contents.add(new SlotContent(
                            slot,
                            StorageKind.ITEM,
                            SlotMode.BUFFER,
                            stack.isEmpty() ? null : AEItemKey.of(stack),
                            stack.getCount()));
                }
            }
        }
        ExportOnlyAEFluidList fluidList = getConfigurableFluidList(machine);
        if (fluidList != null) {
            addConfigurableContents(contents, fluidList, StorageKind.FLUID, fluidList.isStocking());
        } else {
            if (machine instanceof MEHatchPartMachine hatch) {
                addFluidBufferContents(contents, hatch.tank);
            }
            if (machine instanceof DualHatchPartMachine dualHatch) {
                addFluidBufferContents(contents, dualHatch.tank);
            }
        }
        return List.copyOf(contents);
    }

    private static ChamberDetails snapshotDetails(MetaMachine machine) {
        ChamberView view;
        if (machine instanceof MEExtendedOutputPartMachineBase) {
            view = ChamberView.EXTENDED_OUTPUT;
        } else if (machine instanceof MEOutputPartMachine) {
            view = ChamberView.OUTPUT_ASSEMBLY;
        } else if (machine instanceof MEOutputBusPartMachine || machine instanceof MEOutputHatchPartMachine) {
            view = ChamberView.OUTPUT;
        } else if (isStockingChamber(machine)) {
            view = ChamberView.STOCKING;
        } else {
            view = ChamberView.INPUT;
        }

        boolean online = machine instanceof MEExtendedOutputPartMachineBase extendedOutput ?
                extendedOutput.isOnline() : machine instanceof MEBusPartMachine bus ? bus.isOnline() :
                        machine instanceof MEHatchPartMachine hatch ? hatch.isOnline() :
                                machine instanceof MEOutputPartMachine output && output.isOnline();
        boolean workingSupported = machine instanceof TieredIOPartMachine &&
                !(machine instanceof MEOutputPartMachine);
        boolean workingEnabled = workingSupported && ((TieredIOPartMachine) machine).isWorkingEnabled();
        boolean distinctSupported = machine instanceof IDistinctPart && isInputChamber(machine);
        boolean distinct = distinctSupported && ((IDistinctPart) machine).isDistinct();
        boolean autoPullSupported = machine instanceof MEStockingBusPartMachine ||
                machine instanceof MEStockingHatchPartMachine || machine instanceof MEDualHatchStockPartMachine;
        int autoPullMode = machine instanceof MEStockingBusPartMachine stockingBus ?
                stockingBus.isAutoPull() ? 1 : 0 :
                machine instanceof MEStockingHatchPartMachine stockingHatch ? stockingHatch.isAutoPull() ? 1 : 0 :
                        machine instanceof MEDualHatchStockPartMachine dualStock ? dualStock.getAutoPullMode() : 0;
        int autoPullModeCount = machine instanceof MEDualHatchStockPartMachine ? 4 : autoPullSupported ? 2 : 0;
        boolean circuitSupported = isInputChamber(machine) &&
                (machine instanceof ItemBusPartMachine || machine instanceof FluidHatchPartMachine);
        ItemStack circuitStack = machine instanceof ItemBusPartMachine bus ?
                bus.getCircuitInventory().getStackInSlot(0) :
                machine instanceof FluidHatchPartMachine hatch ?
                        hatch.getCircuitInventory().getStackInSlot(0) :
                        ItemStack.EMPTY;
        int circuitConfiguration = circuitSupported ? IntCircuitBehaviour.getCircuitConfiguration(circuitStack) : 0;
        boolean tagFilterSupported = machine instanceof TagFilterMEStockBusPartMachine ||
                machine instanceof TagFilterMEStockHatchPartMachine;
        boolean countSort = machine instanceof TagFilterMEStockBusPartMachine tagBus ? tagBus.isCountSort() :
                machine instanceof TagFilterMEStockHatchPartMachine tagHatch && tagHatch.isCountSort();
        String tagWhite = machine instanceof TagFilterMEStockBusPartMachine tagBus ? tagBus.getTagWhite() :
                machine instanceof TagFilterMEStockHatchPartMachine tagHatch ? tagHatch.getTagWhite() : "";
        String tagBlack = machine instanceof TagFilterMEStockBusPartMachine tagBus ? tagBus.getTagBlack() :
                machine instanceof TagFilterMEStockHatchPartMachine tagHatch ? tagHatch.getTagBlack() : "";
        boolean syncOffsetSupported = machine instanceof IModifiableSyncOffset && isInputChamber(machine);
        int syncOffset = syncOffsetSupported ? ((IModifiableSyncOffset) machine).getOffset() : 0;
        MEOutputFilterHandler outputFilter = machine instanceof MEExtendedOutputPartMachine extendedOutput ?
                extendedOutput.getFilterHandler() : null;
        boolean extendedOutput = machine instanceof MEExtendedOutputPartMachineBase;
        return new ChamberDetails(
                view,
                online,
                workingSupported,
                workingEnabled,
                distinctSupported,
                distinct,
                autoPullSupported,
                autoPullMode,
                autoPullModeCount,
                circuitSupported,
                circuitConfiguration,
                circuitSupported && !circuitStack.isEmpty(),
                tagFilterSupported,
                countSort,
                tagWhite,
                tagBlack,
                syncOffsetSupported,
                syncOffset,
                machine instanceof ItemBusPartMachine || machine instanceof MEOutputPartMachine || extendedOutput,
                machine instanceof MEHatchPartMachine || machine instanceof MEOutputPartMachine ||
                        machine instanceof MEDualInputHatchPartMachine ||
                        machine instanceof MEDualHatchStockPartMachine || extendedOutput,
                outputFilter != null,
                outputFilter != null && outputFilter.isItemBlackList(),
                outputFilter != null && outputFilter.isIgnoreItemNbt(),
                outputFilter != null && outputFilter.isFluidBlackList(),
                outputFilter != null && outputFilter.isIgnoreFluidNbt(),
                extendedOutput,
                extendedOutput ? ((MEExtendedOutputPartMachineBase) machine).getPriority() : 0);
    }

    private static boolean isInputChamber(MetaMachine machine) {
        return !(machine instanceof MEOutputBusPartMachine) && !(machine instanceof MEOutputHatchPartMachine) &&
                !(machine instanceof MEOutputPartMachine) && !(machine instanceof MEExtendedOutputPartMachineBase);
    }

    private static boolean isStockingChamber(MetaMachine machine) {
        if (machine instanceof ItemBusPartMachine bus && bus.getInventory() instanceof ExportOnlyAEItemList list &&
                list.isStocking()) {
            return true;
        }
        ExportOnlyAEFluidList fluidList = getConfigurableFluidList(machine);
        return fluidList != null && fluidList.isStocking();
    }

    private static boolean isKeyValidForStorage(@Nullable AEKey key, StorageKind storage) {
        return key == null || storage == StorageKind.ITEM && key instanceof AEItemKey ||
                storage == StorageKind.FLUID && key instanceof AEFluidKey;
    }

    private static long defaultAmount(StorageKind storage) {
        return storage == StorageKind.ITEM ? 1L : 1_000L;
    }

    private static void addKeyStorageContents(List<SlotContent> contents, KeyStorage storage,
                                              StorageKind storageKind) {
        int slot = 0;
        for (var entry : storage) {
            if (entry.getLongValue() > 0L) {
                contents.add(new SlotContent(
                        slot++,
                        storageKind,
                        SlotMode.BUFFER,
                        entry.getKey(),
                        entry.getLongValue()));
            }
        }
    }

    private static void addExtendedOutputBufferContents(List<SlotContent> contents,
                                                        MEExtendedOutputPartMachineBase output) {
        int itemSlot = 0;
        int fluidSlot = 0;
        for (var entry : output.getBuffer().object2LongEntrySet()) {
            AEKey key = entry.getKey();
            StorageKind storage = key instanceof AEFluidKey ? StorageKind.FLUID : StorageKind.ITEM;
            int slot = storage == StorageKind.FLUID ? fluidSlot++ : itemSlot++;
            contents.add(new SlotContent(slot, storage, SlotMode.BUFFER, key, entry.getLongValue()));
        }
    }

    private static void addFilterContents(List<SlotContent> contents, MEOutputFilterHandler filter) {
        for (int slot = 0; slot < filter.getFilterSlotCount(); slot++) {
            contents.add(new SlotContent(slot, StorageKind.ITEM, SlotMode.FILTER, filter.getItemFilter(slot), 0));
            contents.add(new SlotContent(slot, StorageKind.FLUID, SlotMode.FILTER, filter.getFluidFilter(slot), 0));
        }
    }

    private static void addConfigurableContents(List<SlotContent> contents, ExportOnlyAEItemList list,
                                                StorageKind storage, boolean stocking) {
        for (int slot = 0; slot < list.getConfigurableSlots(); slot++) {
            addConfigurableContent(contents, list.getConfigurableSlot(slot), slot, storage, stocking);
        }
    }

    private static void addConfigurableContents(List<SlotContent> contents, ExportOnlyAEFluidList list,
                                                StorageKind storage, boolean stocking) {
        for (int slot = 0; slot < list.getConfigurableSlots(); slot++) {
            addConfigurableContent(contents, list.getConfigurableSlot(slot), slot, storage, stocking);
        }
    }

    private static void addConfigurableContent(List<SlotContent> contents, IConfigurableSlot slot, int slotIndex,
                                               StorageKind storage, boolean stocking) {
        GenericStack config = slot.getConfig();
        contents.add(new SlotContent(
                slotIndex,
                storage,
                stocking ? SlotMode.STOCKING : SlotMode.CONFIGURABLE,
                config == null ? null : config.what(),
                config == null ? 0L : config.amount()));
    }

    private static void addFluidBufferContents(
                                               List<SlotContent> contents,
                                               com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank tank) {
        for (int slot = 0; slot < tank.getTanks(); slot++) {
            com.lowdragmc.lowdraglib.side.fluid.FluidStack fluid = tank.getFluidInTank(slot);
            contents.add(new SlotContent(
                    slot,
                    StorageKind.FLUID,
                    SlotMode.BUFFER,
                    fluid.isEmpty() ? null : AEFluidKey.of(fluid.getFluid()),
                    fluid.getAmount()));
        }
    }

    private static @Nullable ExportOnlyAEFluidList getConfigurableFluidList(MetaMachine machine) {
        if (machine instanceof MEHatchPartMachine hatch && hatch.tank instanceof ExportOnlyAEFluidList list) {
            return list;
        }
        if (machine instanceof MEDualInputHatchPartMachine dualInput) {
            return dualInput.aeFluidHandler;
        }
        if (machine instanceof MEDualHatchStockPartMachine dualStock) {
            return dualStock.aeFluidHandler;
        }
        return null;
    }

    private static @Nullable IConfigurableSlot findEditableSlot(MetaMachine machine, StorageKind storage, int slot) {
        IConfigurableSlot configurableSlot = findConfigurableSlot(machine, storage, slot);
        if (configurableSlot == null) {
            return null;
        }
        if (storage == StorageKind.ITEM && machine instanceof ItemBusPartMachine bus &&
                bus.getInventory() instanceof ExportOnlyAEItemList list && list.isStocking()) {
            return null;
        }
        ExportOnlyAEFluidList fluidList = getConfigurableFluidList(machine);
        return storage == StorageKind.FLUID && fluidList != null && fluidList.isStocking() ? null : configurableSlot;
    }

    private static @Nullable IConfigurableSlot findConfigurableSlot(MetaMachine machine, StorageKind storage,
                                                                    int slot) {
        if (slot < 0) {
            return null;
        }
        if (storage == StorageKind.ITEM && machine instanceof ItemBusPartMachine bus &&
                bus.getInventory() instanceof ExportOnlyAEItemList list &&
                slot < list.getConfigurableSlots()) {
            return list.getConfigurableSlot(slot);
        }
        ExportOnlyAEFluidList list = getConfigurableFluidList(machine);
        return storage == StorageKind.FLUID && list != null &&
                slot < list.getConfigurableSlots() ?
                        list.getConfigurableSlot(slot) :
                        null;
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < MEChamberManagerTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < MEChamberManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
                int inventoryIndex = column + row * MEChamberManagerTerminalLayout.INVENTORY_COLUMNS +
                        MEChamberManagerTerminalLayout.INVENTORY_COLUMNS;
                addSlot(new Slot(
                        inventory,
                        inventoryIndex,
                        MEChamberManagerTerminalLayout.PLAYER_INVENTORY_X +
                                column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                        MEChamberManagerTerminalLayout.PLAYER_INVENTORY_Y +
                                row * MEChamberManagerTerminalLayout.SLOT_SIZE),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }
        for (int column = 0; column < MEChamberManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    MEChamberManagerTerminalLayout.PLAYER_INVENTORY_X +
                            column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                    MEChamberManagerTerminalLayout.PLAYER_HOTBAR_Y), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    public static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.address().write(buffer);
            buffer.writeItem(entry.icon());
            buffer.writeComponent(entry.name());
            buffer.writeBoolean(entry.controllerName() != null);
            if (entry.controllerName() != null) {
                buffer.writeComponent(entry.controllerName());
                buffer.writeBlockPos(entry.controllerPos());
            }
        }
    }

    public static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) {
            throw new IllegalArgumentException("Invalid ME chamber entry count: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            Address address = Address.read(buffer);
            ItemStack icon = buffer.readItem();
            Component name = buffer.readComponent();
            Component controllerName = buffer.readBoolean() ? buffer.readComponent() : null;
            BlockPos controllerPos = controllerName == null ? null : buffer.readBlockPos();
            entries.add(new Entry(address, icon, name, controllerName, controllerPos));
        }
        return List.copyOf(entries);
    }

    public static void writeContents(FriendlyByteBuf buffer, List<SlotContent> contents) {
        buffer.writeVarInt(contents.size());
        for (SlotContent content : contents) {
            buffer.writeVarInt(content.slot());
            buffer.writeEnum(content.storage());
            buffer.writeEnum(content.mode());
            AEKey.writeOptionalKey(buffer, content.key());
            buffer.writeVarLong(content.amount());
        }
    }

    public static List<SlotContent> readContents(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SYNC_SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid ME chamber slot count: " + size);
        }
        List<SlotContent> contents = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            contents.add(new SlotContent(
                    buffer.readVarInt(),
                    buffer.readEnum(StorageKind.class),
                    buffer.readEnum(SlotMode.class),
                    AEKey.readOptionalKey(buffer),
                    buffer.readVarLong()));
        }
        return List.copyOf(contents);
    }

    public static void writeDetails(FriendlyByteBuf buffer, ChamberDetails details) {
        buffer.writeEnum(details.view());
        buffer.writeBoolean(details.online());
        buffer.writeBoolean(details.workingSupported());
        buffer.writeBoolean(details.workingEnabled());
        buffer.writeBoolean(details.distinctSupported());
        buffer.writeBoolean(details.distinct());
        buffer.writeBoolean(details.autoPullSupported());
        buffer.writeVarInt(details.autoPullMode());
        buffer.writeVarInt(details.autoPullModeCount());
        buffer.writeBoolean(details.circuitSupported());
        buffer.writeVarInt(details.circuitConfiguration());
        buffer.writeBoolean(details.circuitSet());
        buffer.writeBoolean(details.tagFilterSupported());
        buffer.writeBoolean(details.countSort());
        buffer.writeUtf(details.tagWhite(), 256);
        buffer.writeUtf(details.tagBlack(), 256);
        buffer.writeBoolean(details.syncOffsetSupported());
        buffer.writeVarInt(details.syncOffset());
        buffer.writeBoolean(details.itemStorage());
        buffer.writeBoolean(details.fluidStorage());
        buffer.writeBoolean(details.outputFilterSupported());
        buffer.writeBoolean(details.itemBlackList());
        buffer.writeBoolean(details.ignoreItemNbt());
        buffer.writeBoolean(details.fluidBlackList());
        buffer.writeBoolean(details.ignoreFluidNbt());
        buffer.writeBoolean(details.prioritySupported());
        buffer.writeVarInt(details.priority());
    }

    public static ChamberDetails readDetails(FriendlyByteBuf buffer) {
        return new ChamberDetails(
                buffer.readEnum(ChamberView.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt());
    }

    public record Address(ResourceLocation dimension, BlockPos pos, Direction side) {

        public static Address of(MetaMachine machine) {
            return new Address(machine.getLevel().dimension().location(), machine.getPos(), machine.getFrontFacing());
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeBlockPos(pos);
            buffer.writeEnum(side);
        }

        public static Address read(FriendlyByteBuf buffer) {
            return new Address(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readEnum(Direction.class));
        }
    }

    public record Entry(Address address, ItemStack icon, Component name, @Nullable Component controllerName,
                        @Nullable BlockPos controllerPos) {}

    public enum StorageKind {
        ITEM,
        FLUID
    }

    public enum SlotMode {
        CONFIGURABLE,
        STOCKING,
        BUFFER,
        FILTER
    }

    public record SlotContent(int slot, StorageKind storage, SlotMode mode, @Nullable AEKey key, long amount) {}

    public enum ChamberView {
        INPUT,
        STOCKING,
        OUTPUT,
        OUTPUT_ASSEMBLY,
        EXTENDED_OUTPUT
    }

    public enum ControlKind {
        WORKING,
        DISTINCT,
        AUTO_PULL,
        COUNT_SORT,
        PRIORITY,
        ITEM_BLACKLIST,
        ITEM_NBT,
        FLUID_BLACKLIST,
        FLUID_NBT
    }

    public record ChamberDetails(ChamberView view, boolean online, boolean workingSupported,
                                 boolean workingEnabled, boolean distinctSupported, boolean distinct,
                                 boolean autoPullSupported, int autoPullMode, int autoPullModeCount,
                                 boolean circuitSupported,
                                 int circuitConfiguration, boolean circuitSet,
                                 boolean tagFilterSupported, boolean countSort,
                                 String tagWhite, String tagBlack,
                                 boolean syncOffsetSupported, int syncOffset,
                                 boolean itemStorage, boolean fluidStorage,
                                 boolean outputFilterSupported, boolean itemBlackList, boolean ignoreItemNbt,
                                 boolean fluidBlackList, boolean ignoreFluidNbt,
                                 boolean prioritySupported, int priority) {

        public static final ChamberDetails EMPTY = new ChamberDetails(
                ChamberView.INPUT, false, false, false, false, false, false, 0, 0, false, 0, false, false, false,
                "", "", false, 0, false, false, false, false, false, false, false, false, 0);
    }

    private record ControllerInfo(@Nullable Component name, @Nullable BlockPos pos) {

        private static final ControllerInfo NONE = new ControllerInfo(null, null);
    }
}
