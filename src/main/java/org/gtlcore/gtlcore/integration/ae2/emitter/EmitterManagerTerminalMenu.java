package org.gtlcore.gtlcore.integration.ae2.emitter;

import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.mixin.mc.SlotAccessor;

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
import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.Icon;
import appeng.helpers.IConfigInvHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.util.Platform;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class EmitterManagerTerminalMenu extends AEBaseMenu {

    public static final int MAX_SYNC_ENTRIES = 1_024;
    public static final int MAX_SYNC_SETTINGS = 32;
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int MAX_SETTING_NAME_LENGTH = 64;
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;
    private static final double BLOCK_CENTER_OFFSET = 0.5D;
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing((Entry entry) -> entry.address().dimension().toString())
            .thenComparingInt(entry -> entry.address().pos().getX())
            .thenComparingInt(entry -> entry.address().pos().getY())
            .thenComparingInt(entry -> entry.address().pos().getZ())
            .thenComparingInt(entry -> entry.address().side().ordinal());

    private final @Nullable EmitterManagerTerminalPart terminal;
    private final @Nullable WTMenuHost wirelessHost;
    private final Player menuPlayer;
    private final SelectedEmitterConfig selectedConfig;
    private final boolean universalTerminal;
    private List<Entry> entries = List.of();
    private int ticksUntilSync;
    /** Emitter the player has selected in the list; upgrade slots forward to its cards. */
    private @Nullable Address selectedAddress;
    private @Nullable AbstractLevelEmitterPart cachedEmitter;
    private @Nullable Address cachedEmitterAddress;

    public static EmitterManagerTerminalMenu createWiredClientMenu(int containerId, Inventory inventory,
                                                                   FriendlyByteBuf data) {
        return new EmitterManagerTerminalMenu(
                GTLWirelessAeContent.EMITTER_MANAGER_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                null,
                null,
                false);
    }

    public static EmitterManagerTerminalMenu createWirelessClientMenu(int containerId, Inventory inventory,
                                                                      FriendlyByteBuf data) {
        MenuLocator locator = MenuLocators.readFromPacket(data);
        boolean returningFromSubmenu = data.readBoolean();
        WTMenuHost host = locator.locate(inventory.player, WTMenuHost.class);
        return new EmitterManagerTerminalMenu(
                GTLWirelessAeContent.WIRELESS_EMITTER_MANAGER_TERMINAL_MENU.get(),
                containerId,
                inventory,
                null,
                host,
                locator,
                returningFromSubmenu);
    }

    private EmitterManagerTerminalMenu(MenuType<?> menuType, int containerId, Inventory inventory,
                                       @Nullable EmitterManagerTerminalPart terminal,
                                       @Nullable WTMenuHost wirelessHost, @Nullable MenuLocator locator,
                                       boolean returningFromSubmenu) {
        super(menuType, containerId, inventory, terminal == null ? wirelessHost : terminal);
        this.terminal = terminal;
        this.wirelessHost = wirelessHost;
        this.menuPlayer = inventory.player;
        this.universalTerminal = wirelessHost != null && wirelessHost.getItemStack().getItem() instanceof ItemWUT;
        this.selectedConfig = new SelectedEmitterConfig(
                this::resolveSelectedConfig,
                inventory.player.level().isClientSide());
        if (locator != null) {
            setLocator(locator);
            setReturnedFromSubScreen(returningFromSubmenu);
        }
        addUpgradeSlots(inventory.player.level().isClientSide());
        addConfigSlot();
        addPlayerInventorySlots(inventory);
    }

    private void addUpgradeSlots(boolean client) {
        SelectedEmitterUpgrades upgrades = new SelectedEmitterUpgrades(
                this::resolveSelectedUpgrades,
                EmitterManagerTerminalLayout.MAX_UPGRADE_SLOTS,
                client);
        for (int index = 0; index < EmitterManagerTerminalLayout.MAX_UPGRADE_SLOTS; index++) {
            Slot slot = new UpgradeCardSlot(upgrades, index);
            ((SlotAccessor) slot).gtlcore$setX(
                    EmitterManagerTerminalLayout.CARD_SLOT_X + index * EmitterManagerTerminalLayout.SLOT_SIZE);
            ((SlotAccessor) slot).gtlcore$setY(EmitterManagerTerminalLayout.CARD_SLOT_Y);
            addSlot(slot, SlotSemantics.UPGRADE);
        }
    }

    private void addConfigSlot() {
        Slot slot = new EmitterConfigSlot(selectedConfig);
        ((SlotAccessor) slot).gtlcore$setX(EmitterManagerTerminalLayout.CONFIG_SLOT_X);
        ((SlotAccessor) slot).gtlcore$setY(EmitterManagerTerminalLayout.CONFIG_SLOT_Y);
        addSlot(slot, SlotSemantics.CONFIG);
    }

    /** Number of upgrade slots the selected emitter exposes, or 0 when nothing usable is selected. */
    public int getSelectedUpgradeSlots() {
        Entry entry = findEntry(selectedAddress);
        return entry == null ? 0 : entry.upgradeSlots();
    }

    /** Whether the selected emitter watches a configurable key, i.e. whether the filter slot applies. */
    public boolean hasSelectedConfig() {
        Entry entry = findEntry(selectedAddress);
        return entry != null && entry.hasConfig();
    }

    public @Nullable Address getSelectedAddress() {
        return selectedAddress;
    }

    public boolean isUniversalTerminal() {
        return universalTerminal;
    }

    /**
     * Records the emitter the player selected. The client calls this directly so its upgrade slots line up
     * with what it renders; the server receives it over the wire and re-resolves the backing part.
     */
    public void setSelectedAddress(@Nullable Address address) {
        if (!Objects.equals(this.selectedAddress, address)) {
            this.selectedAddress = address;
            this.cachedEmitter = null;
            this.cachedEmitterAddress = null;
            refreshSelectedConfigClientView();
        }
    }

    private @Nullable IUpgradeInventory resolveSelectedUpgrades() {
        AbstractLevelEmitterPart emitter = resolveSelectedEmitter();
        return emitter == null ? null : emitter.getUpgrades();
    }

    private @Nullable GenericStackInv resolveSelectedConfig() {
        AbstractLevelEmitterPart emitter = resolveSelectedEmitter();
        if (!(emitter instanceof IConfigInvHost configHost)) {
            return null;
        }
        GenericStackInv config = configHost.getConfig();
        return config.size() > 0 ? config : null;
    }

    private @Nullable AbstractLevelEmitterPart resolveSelectedEmitter() {
        if (!hasDataSource() || selectedAddress == null || menuPlayer.level().isClientSide() ||
                !(menuPlayer instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        if (cachedEmitter != null && selectedAddress.equals(cachedEmitterAddress) && isStillPresent(cachedEmitter)) {
            return cachedEmitter;
        }
        cachedEmitter = findEditableEmitter(serverPlayer, selectedAddress);
        cachedEmitterAddress = cachedEmitter == null ? null : selectedAddress;
        return cachedEmitter;
    }

    private static boolean isStillPresent(AbstractLevelEmitterPart emitter) {
        IPartHost host = emitter.getHost();
        return host.getPart(emitter.getSide()) == emitter;
    }

    private @Nullable Entry findEntry(@Nullable Address address) {
        if (address == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.address().equals(address)) {
                return entry;
            }
        }
        return null;
    }

    /** Upgrade slot that only exists while the selected emitter actually has that many card slots. */
    private final class UpgradeCardSlot extends AppEngSlot {

        private final int index;

        private UpgradeCardSlot(SelectedEmitterUpgrades inventory, int index) {
            super(inventory, index);
            this.index = index;
            setIcon(Icon.BACKGROUND_UPGRADE);
            setEmptyTooltip(this::buildEmptyTooltip);
        }

        @Override
        public boolean isSlotEnabled() {
            return index < getSelectedUpgradeSlots();
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // Installing or pulling a card changes which toggles apply, so refresh instead of
            // waiting out the periodic sync.
            ticksUntilSync = 0;
        }

        private List<Component> buildEmptyTooltip() {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.gtlcore.emitter_manager_terminal.upgrade_slot"));
            Entry entry = findEntry(selectedAddress);
            if (entry != null) {
                lines.addAll(Upgrades.getTooltipLinesForMachine(entry.icon().getItem()));
            }
            return lines;
        }
    }

    /**
     * Filter slot for the item or fluid the selected emitter watches. A {@link FakeSlot} holds a filter
     * rather than real contents, so clicking it stamps the carried stack in without consuming anything.
     */
    private final class EmitterConfigSlot extends FakeSlot {

        private EmitterConfigSlot(SelectedEmitterConfig config) {
            super(config, 0);
            setEmptyTooltip(
                    () -> List.of(Component.translatable("gui.gtlcore.emitter_manager_terminal.config_slot")));
        }

        @Override
        public boolean isSlotEnabled() {
            return hasSelectedConfig();
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // The entry list shows the watched key, so resync instead of waiting out the periodic update.
            ticksUntilSync = 0;
        }
    }

    public static void open(ServerPlayer player, EmitterManagerTerminalPart terminal) {
        NetworkHooks.openScreen(
                player,
                new MenuProvider() {

                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("screen.gtlcore.emitter_manager_terminal");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new EmitterManagerTerminalMenu(
                                GTLWirelessAeContent.EMITTER_MANAGER_TERMINAL_MENU.get(),
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
                        return Component.translatable("screen.gtlcore.wireless_emitter_manager_terminal");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                            @NotNull Player menuPlayer) {
                        return new EmitterManagerTerminalMenu(
                                GTLWirelessAeContent.WIRELESS_EMITTER_MANAGER_TERMINAL_MENU.get(),
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

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        refreshSelectedConfigClientView();
    }

    private void refreshSelectedConfigClientView() {
        if (!menuPlayer.level().isClientSide()) {
            return;
        }
        Entry entry = findEntry(selectedAddress);
        selectedConfig.setClientKey(entry == null ? null : entry.configuredKey());
    }

    public void setEmitterSetting(ServerPlayer player, Address address, String settingName, String valueName) {
        AbstractLevelEmitterPart emitter = findEditableEmitter(player, address);
        if (emitter == null || !EmitterManagerSupport.setSetting(emitter, settingName, valueName)) {
            return;
        }
        emitter.getHost().markForSave();
        emitter.getHost().markForUpdate();
        syncNow(player);
    }

    public void setEmitterValue(ServerPlayer player, Address address, ValueKind kind, long value) {
        AbstractLevelEmitterPart emitter = findEditableEmitter(player, address);
        if (emitter == null || !EmitterManagerSupport.setValue(emitter, kind, value)) {
            return;
        }
        emitter.getHost().markForSave();
        emitter.getHost().markForUpdate();
        syncNow(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!hasDataSource() || menuPlayer.level().isClientSide() || !isValidMenu() || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL_TICKS;
        if (menuPlayer instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
            syncNow(serverPlayer);
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
                player.distanceToSqr(
                        pos.getX() + BLOCK_CENTER_OFFSET,
                        pos.getY() + BLOCK_CENTER_OFFSET,
                        pos.getZ() + BLOCK_CENTER_OFFSET) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    private void syncNow(ServerPlayer player) {
        List<Entry> currentEntries = collectEntries();
        if (entriesEqual(currentEntries, entries)) {
            return;
        }
        entries = currentEntries;
        WirelessAePackets.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WirelessAePackets.SyncEmitterManagerTerminalPacket(containerId, entries));
    }

    private static boolean entriesEqual(List<Entry> first, List<Entry> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            Entry left = first.get(index);
            Entry right = second.get(index);
            if (!left.address().equals(right.address()) ||
                    !ItemStack.isSameItemSameTags(left.icon(), right.icon()) ||
                    !left.name().equals(right.name()) || left.function() != right.function() ||
                    !Objects.equals(left.configuredKey(), right.configuredKey()) ||
                    left.monitoredValue() != right.monitoredValue() ||
                    left.reportingValue() != right.reportingValue() || left.lowerValue() != right.lowerValue() ||
                    left.upperValue() != right.upperValue() || left.outputOn() != right.outputOn() ||
                    left.online() != right.online() || left.craftingCard() != right.craftingCard() ||
                    left.fuzzyCard() != right.fuzzyCard() || left.upgradeSlots() != right.upgradeSlots() ||
                    left.hasConfig() != right.hasConfig() ||
                    !left.settings().equals(right.settings())) {
                return false;
            }
        }
        return true;
    }

    private List<Entry> collectEntries() {
        IGrid grid = getGrid();
        if (grid == null) {
            return List.of();
        }
        List<Entry> result = new ArrayList<>();
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof AbstractLevelEmitterPart emitter) {
                result.add(EmitterManagerSupport.snapshot(emitter));
                if (result.size() >= MAX_SYNC_ENTRIES) {
                    break;
                }
            }
        }
        result.sort(ENTRY_ORDER);
        return List.copyOf(result);
    }

    private @Nullable AbstractLevelEmitterPart findEditableEmitter(ServerPlayer player, Address address) {
        if (player.containerMenu != this || !hasDataSource() || !stillValid(player)) {
            return null;
        }
        IGrid grid = getGrid();
        if (grid == null) {
            return null;
        }
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof AbstractLevelEmitterPart emitter &&
                    address.equals(Address.of(emitter)) &&
                    Platform.hasPermissions(emitter.getHost().getLocation(), player)) {
                return emitter;
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

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < EmitterManagerTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < EmitterManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
                int inventoryIndex = column + row * EmitterManagerTerminalLayout.INVENTORY_COLUMNS +
                        EmitterManagerTerminalLayout.INVENTORY_COLUMNS;
                addSlot(
                        new Slot(
                                inventory,
                                inventoryIndex,
                                EmitterManagerTerminalLayout.PLAYER_INVENTORY_X +
                                        column * EmitterManagerTerminalLayout.SLOT_SIZE,
                                EmitterManagerTerminalLayout.PLAYER_INVENTORY_Y +
                                        row * EmitterManagerTerminalLayout.SLOT_SIZE),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }
        for (int column = 0; column < EmitterManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
            addSlot(
                    new Slot(
                            inventory,
                            column,
                            EmitterManagerTerminalLayout.PLAYER_INVENTORY_X +
                                    column * EmitterManagerTerminalLayout.SLOT_SIZE,
                            EmitterManagerTerminalLayout.PLAYER_HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    public static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.address().write(buffer);
            buffer.writeItem(entry.icon());
            buffer.writeComponent(entry.name());
            buffer.writeEnum(entry.function());
            AEKey.writeOptionalKey(buffer, entry.configuredKey());
            buffer.writeVarLong(entry.monitoredValue());
            buffer.writeVarLong(entry.reportingValue());
            buffer.writeVarLong(entry.lowerValue());
            buffer.writeVarLong(entry.upperValue());
            buffer.writeBoolean(entry.outputOn());
            buffer.writeBoolean(entry.online());
            buffer.writeBoolean(entry.craftingCard());
            buffer.writeBoolean(entry.fuzzyCard());
            buffer.writeVarInt(entry.upgradeSlots());
            buffer.writeBoolean(entry.hasConfig());
            buffer.writeVarInt(entry.settings().size());
            for (SettingValue setting : entry.settings()) {
                buffer.writeUtf(setting.name(), MAX_SETTING_NAME_LENGTH);
                buffer.writeUtf(setting.value(), MAX_SETTING_NAME_LENGTH);
            }
        }
    }

    public static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SYNC_ENTRIES) {
            throw new IllegalArgumentException("Invalid emitter manager entry count: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Address address = Address.read(buffer);
            ItemStack icon = buffer.readItem();
            Component name = buffer.readComponent();
            Function function = buffer.readEnum(Function.class);
            AEKey configuredKey = AEKey.readOptionalKey(buffer);
            long monitoredValue = buffer.readVarLong();
            long reportingValue = buffer.readVarLong();
            long lowerValue = buffer.readVarLong();
            long upperValue = buffer.readVarLong();
            boolean outputOn = buffer.readBoolean();
            boolean online = buffer.readBoolean();
            boolean craftingCard = buffer.readBoolean();
            boolean fuzzyCard = buffer.readBoolean();
            int upgradeSlots = Math.min(
                    buffer.readVarInt(),
                    EmitterManagerTerminalLayout.MAX_UPGRADE_SLOTS);
            boolean hasConfig = buffer.readBoolean();
            int settingCount = buffer.readVarInt();
            if (settingCount < 0 || settingCount > MAX_SYNC_SETTINGS) {
                throw new IllegalArgumentException("Invalid emitter setting count: " + settingCount);
            }
            List<SettingValue> settings = new ArrayList<>(settingCount);
            for (int settingIndex = 0; settingIndex < settingCount; settingIndex++) {
                settings.add(new SettingValue(
                        buffer.readUtf(MAX_SETTING_NAME_LENGTH),
                        buffer.readUtf(MAX_SETTING_NAME_LENGTH)));
            }
            entries.add(new Entry(
                    address,
                    icon,
                    name,
                    function,
                    configuredKey,
                    monitoredValue,
                    reportingValue,
                    lowerValue,
                    upperValue,
                    outputOn,
                    online,
                    craftingCard,
                    fuzzyCard,
                    upgradeSlots,
                    hasConfig,
                    List.copyOf(settings)));
        }
        return List.copyOf(entries);
    }

    public enum Function {
        STORAGE,
        ENERGY,
        THRESHOLD,
        GENERIC
    }

    public enum ValueKind {
        REPORTING,
        LOWER_THRESHOLD,
        UPPER_THRESHOLD
    }

    public record Address(ResourceLocation dimension, BlockPos pos, Direction side) {

        public static Address of(AbstractLevelEmitterPart emitter) {
            return new Address(
                    emitter.getLevel().dimension().location(),
                    emitter.getHost().getLocation().getPos(),
                    emitter.getSide());
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeBlockPos(pos);
            buffer.writeEnum(side);
        }

        public static Address read(FriendlyByteBuf buffer) {
            return new Address(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readEnum(Direction.class));
        }
    }

    public record SettingValue(String name, String value) {}

    public record Entry(Address address, ItemStack icon, Component name, Function function,
                        @Nullable AEKey configuredKey, long monitoredValue, long reportingValue,
                        long lowerValue, long upperValue, boolean outputOn, boolean online,
                        boolean craftingCard, boolean fuzzyCard, int upgradeSlots, boolean hasConfig,
                        List<SettingValue> settings) {

        public @Nullable String settingValue(String settingName) {
            for (SettingValue setting : settings) {
                if (setting.name().equals(settingName)) {
                    return setting.value();
                }
            }
            return null;
        }
    }
}
