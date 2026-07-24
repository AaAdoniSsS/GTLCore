package org.gtlcore.gtlcore.integration.ae2.throughput;

import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellItem;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThroughputMonitorTerminalMenu extends AEBaseMenu {

    public static final int MAX_SYNC_ENTRIES = ThroughputMonitorCollector.MAX_TRACKED_KEYS;
    public static final int MAX_SYNC_SOURCE_ENTRIES = ThroughputMonitorCollector.MAX_TRACKED_SOURCES_PER_KEY;
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;
    private static final double BLOCK_CENTER_OFFSET = 0.5D;
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing((Entry entry) -> entry.key().getType().getId().toString())
            .thenComparing(entry -> entry.key().toTagGeneric().toString());
    private static final Comparator<SourceEntry> SOURCE_ORDER = Comparator
            .comparing((SourceEntry entry) -> entry.dimension().toString())
            .thenComparingInt(entry -> entry.pos().getX())
            .thenComparingInt(entry -> entry.pos().getY())
            .thenComparingInt(entry -> entry.pos().getZ())
            .thenComparingInt(entry -> entry.side() == null ? -1 : entry.side().ordinal());

    private final ThroughputMonitorTerminalPart terminal;
    private final ThroughputMonitorCollector wirelessCollector;
    private final boolean universalTerminal;
    private final Player menuPlayer;
    private final List<Slot> viewCellSlots = new ArrayList<>();
    private final Set<AEKey> trackedSourceKeys = new HashSet<>();
    private final Map<AEKey, List<SourceEntry>> clientSources = new HashMap<>();
    private List<Entry> entries;
    private ThroughputMonitorUpdateInterval updateInterval = ThroughputMonitorUpdateInterval.SECOND;
    private int ticksUntilSync;
    private boolean initialSyncPending;

    public static ThroughputMonitorTerminalMenu createWiredClientMenu(int containerId, Inventory inventory,
                                                                      FriendlyByteBuf data) {
        return new ThroughputMonitorTerminalMenu(
                GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                null,
                null,
                false);
    }

    public static ThroughputMonitorTerminalMenu createWirelessClientMenu(int containerId, Inventory inventory,
                                                                         FriendlyByteBuf data) {
        MenuLocator locator = MenuLocators.readFromPacket(data);
        boolean returningFromSubmenu = data.readBoolean();
        WTMenuHost host = locator.locate(inventory.player, WTMenuHost.class);
        return new ThroughputMonitorTerminalMenu(
                GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                host,
                locator,
                returningFromSubmenu);
    }

    private ThroughputMonitorTerminalMenu(MenuType<?> menuType, int containerId, Inventory inventory,
                                          ThroughputMonitorTerminalPart terminal, @Nullable WTMenuHost wirelessHost,
                                          @Nullable MenuLocator locator, boolean returningFromSubmenu) {
        super(menuType, containerId, inventory, terminal == null ? wirelessHost : terminal);
        this.terminal = terminal;
        this.wirelessCollector = createWirelessCollector(inventory.player, wirelessHost);
        this.universalTerminal = wirelessHost != null && wirelessHost.getItemStack().getItem() instanceof ItemWUT;
        this.menuPlayer = inventory.player;
        this.entries = List.of();
        this.initialSyncPending = terminal != null || wirelessCollector != null;
        if (locator != null) {
            setLocator(locator);
            setReturnedFromSubScreen(returningFromSubmenu);
        }
        addPlayerInventorySlots(inventory);
        addViewCellSlots(terminal == null ? wirelessHost : terminal);
    }

    public static void open(ServerPlayer player, ThroughputMonitorTerminalPart terminal) {
        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.throughput_monitor_terminal");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new ThroughputMonitorTerminalMenu(
                                GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
                                containerId,
                                inventory,
                                terminal,
                                null,
                                null,
                                false);
                    }
                },
                buffer -> {});
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
                        return Component.translatable("screen.gtlcore.wireless_throughput_monitor_terminal");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new ThroughputMonitorTerminalMenu(
                                GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
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

    public boolean isUniversalTerminal() {
        return universalTerminal;
    }

    public List<ItemStack> getViewCells() {
        return viewCellSlots.stream().map(Slot::getItem).toList();
    }

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Set<AEKey> currentKeys = new HashSet<>();
        for (Entry entry : entries) {
            currentKeys.add(entry.key());
        }
        clientSources.keySet().retainAll(currentKeys);
    }

    public List<SourceEntry> getSourceEntries(AEKey key) {
        return clientSources.getOrDefault(key, List.of());
    }

    public void setSourceEntries(AEKey key, List<SourceEntry> sources) {
        clientSources.put(key, List.copyOf(sources));
    }

    public void trackSources(ServerPlayer player, AEKey key, boolean track) {
        if (player.containerMenu != this || entries.stream().noneMatch(entry -> entry.key().equals(key))) {
            return;
        }
        if (track) {
            trackedSourceKeys.add(key);
            sendSources(player, key, getSnapshots());
        } else {
            trackedSourceKeys.remove(key);
        }
    }

    public void setUpdateInterval(ServerPlayer player, ThroughputMonitorUpdateInterval updateInterval) {
        if (player.containerMenu != this) {
            return;
        }
        this.updateInterval = updateInterval;
        this.ticksUntilSync = 0;
        this.initialSyncPending = true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isValidMenu() || menuPlayer.level().isClientSide() || !hasDataSource() || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = updateInterval.ticks();

        List<ThroughputMonitorCollector.Snapshot> snapshots = getSnapshots();
        List<Entry> currentEntries = collectEntries(snapshots);
        if (menuPlayer instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
            if (initialSyncPending || !currentEntries.equals(entries)) {
                initialSyncPending = false;
                entries = currentEntries;
                WirelessAePackets.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new WirelessAePackets.SyncThroughputMonitorTerminalPacket(containerId, entries));
            }
            for (AEKey key : trackedSourceKeys) {
                sendSources(serverPlayer, key, snapshots);
            }
        }
    }

    private void sendSources(ServerPlayer player, AEKey key,
                             List<ThroughputMonitorCollector.Snapshot> snapshots) {
        List<SourceEntry> sources = snapshots.stream()
                .filter(snapshot -> snapshot.key().equals(key))
                .findFirst()
                .map(ThroughputMonitorTerminalMenu::collectSourceEntries)
                .orElseGet(List::of);
        WirelessAePackets.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WirelessAePackets.SyncThroughputMonitorSourcesPacket(containerId, key, sources));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return super.quickMoveStack(player, index);
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

    @Override
    public void removed(@NotNull Player player) {
        if (wirelessCollector != null) {
            wirelessCollector.close();
        }
        super.removed(player);
    }

    public static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            AEKey.writeKey(buffer, entry.key());
            buffer.writeDouble(entry.insertedPerSecond());
            buffer.writeDouble(entry.extractedPerSecond());
            buffer.writeVarInt(entry.sourceCount());
        }
    }

    public static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) {
            throw new IllegalArgumentException("Invalid throughput monitor entry count: " + size);
        }

        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    AEKey.readKey(buffer),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readVarInt()));
        }
        return entries;
    }

    public static void writeSourceEntries(FriendlyByteBuf buffer, List<SourceEntry> sources) {
        buffer.writeVarInt(sources.size());
        for (SourceEntry source : sources) {
            buffer.writeResourceLocation(source.dimension());
            buffer.writeBlockPos(source.pos());
            buffer.writeBoolean(source.side() != null);
            if (source.side() != null) {
                buffer.writeEnum(source.side());
            }
            buffer.writeDouble(source.insertedPerSecond());
            buffer.writeDouble(source.extractedPerSecond());
        }
    }

    public static List<SourceEntry> readSourceEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SYNC_SOURCE_ENTRIES) {
            throw new IllegalArgumentException("Invalid throughput monitor source entry count: " + size);
        }
        List<SourceEntry> sources = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation dimension = buffer.readResourceLocation();
            BlockPos pos = buffer.readBlockPos();
            Direction side = buffer.readBoolean() ? buffer.readEnum(Direction.class) : null;
            sources.add(new SourceEntry(
                    dimension,
                    pos,
                    side,
                    buffer.readDouble(),
                    buffer.readDouble()));
        }
        return sources;
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < ThroughputMonitorTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS; column++) {
                int inventoryIndex = column + row * ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS +
                        ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS;
                addSlot(
                        new Slot(
                                inventory,
                                inventoryIndex,
                                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_X +
                                        column * ThroughputMonitorTerminalLayout.SLOT_SIZE,
                                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_Y +
                                        row * ThroughputMonitorTerminalLayout.SLOT_SIZE),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }
        for (int column = 0; column < ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS; column++) {
            addSlot(
                    new Slot(
                            inventory,
                            column,
                            ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_X +
                                    column * ThroughputMonitorTerminalLayout.SLOT_SIZE,
                            ThroughputMonitorTerminalLayout.PLAYER_HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private void addViewCellSlots(@Nullable Object host) {
        InternalInventory viewCellInventory = host instanceof IViewCellStorage viewCellStorage ?
                viewCellStorage.getViewCellStorage() :
                new AppEngInternalInventory(ThroughputMonitorTerminalPart.VIEW_CELL_SLOT_COUNT);
        Container container = viewCellInventory.toContainer();
        for (int slotIndex = 0; slotIndex < viewCellInventory.size(); slotIndex++) {
            Slot slot = new ViewCellSlot(
                    container,
                    slotIndex,
                    ThroughputMonitorTerminalLayout.VIEW_CELL_X,
                    ThroughputMonitorTerminalLayout.VIEW_CELL_Y +
                            slotIndex * ThroughputMonitorTerminalLayout.SLOT_SIZE);
            addSlot(slot, SlotSemantics.VIEW_CELL);
            viewCellSlots.add(slot);
        }
    }

    private static List<Entry> collectEntries(List<ThroughputMonitorCollector.Snapshot> snapshots) {
        List<Entry> entries = new ArrayList<>();
        for (ThroughputMonitorCollector.Snapshot snapshot : snapshots) {
            entries.add(new Entry(
                    snapshot.key(),
                    snapshot.insertedPerSecond(),
                    snapshot.extractedPerSecond(),
                    snapshot.sources().size()));
        }
        entries.sort(ENTRY_ORDER);
        return List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_SYNC_ENTRIES)));
    }

    private static List<SourceEntry> collectSourceEntries(ThroughputMonitorCollector.Snapshot snapshot) {
        List<SourceEntry> sources = new ArrayList<>();
        for (ThroughputMonitorCollector.SourceSnapshot sourceSnapshot : snapshot.sources()) {
            ThroughputMonitorStorageTracker.SourceLocation source = sourceSnapshot.source();
            sources.add(new SourceEntry(
                    source.dimension(),
                    source.pos(),
                    source.side(),
                    sourceSnapshot.insertedPerSecond(),
                    sourceSnapshot.extractedPerSecond()));
        }
        sources.sort(SOURCE_ORDER);
        return List.copyOf(sources.subList(0, Math.min(sources.size(), MAX_SYNC_SOURCE_ENTRIES)));
    }

    private boolean hasDataSource() {
        return terminal != null || wirelessCollector != null;
    }

    private List<ThroughputMonitorCollector.Snapshot> getSnapshots() {
        if (terminal != null) {
            return terminal.getSnapshots();
        }
        return wirelessCollector == null ? List.of() : wirelessCollector.getSnapshots();
    }

    private static @Nullable ThroughputMonitorCollector createWirelessCollector(Player player,
                                                                                @Nullable WTMenuHost host) {
        if (player.level().isClientSide() || host == null) {
            return null;
        }
        ThroughputMonitorCollector collector = new ThroughputMonitorCollector();
        collector.attach(host.getInventory());
        return collector;
    }

    public record Entry(AEKey key, double insertedPerSecond, double extractedPerSecond, int sourceCount) {}

    public record SourceEntry(ResourceLocation dimension, BlockPos pos, @Nullable Direction side,
                              double insertedPerSecond, double extractedPerSecond) {}

    private static final class ViewCellSlot extends Slot {

        private ViewCellSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return AEItems.VIEW_CELL.isSameAs(stack) || TagViewCellItem.isTagViewCell(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
