package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;
import org.gtlcore.gtlcore.integration.ae2.throughput.METhroughputMonitorPart;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalLayout;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorUpdateInterval;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.SettingToggleButton;
import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ThroughputMonitorTerminalScreen extends AbstractContainerScreen<ThroughputMonitorTerminalMenu>
                                             implements IUniversalTerminalCapable {

    private static final int SEARCH_MAX_LENGTH = 80;
    private static final int GROUP_ICON_X = ThroughputMonitorTerminalLayout.LIST_X + 19;
    private static final int GROUP_NAME_X = ThroughputMonitorTerminalLayout.LIST_X + 38;
    private static final int SOURCE_POSITION_X = ThroughputMonitorTerminalLayout.LIST_X + 22;
    private static final int ROW_RATE_RIGHT = ThroughputMonitorTerminalLayout.LIST_X +
            ThroughputMonitorTerminalLayout.LIST_CONTENT_WIDTH - 1;
    private static final int ROW_TEXT_GAP = 4;
    private static final int ROW_ICON_Y_OFFSET = 3;
    private static final int ROW_TEXT_Y_OFFSET = 7;
    private static final int ROW_SEPARATOR_X = ThroughputMonitorTerminalLayout.LIST_X + 18;
    private static final int ROW_SEPARATOR_WIDTH = ROW_RATE_RIGHT - ROW_SEPARATOR_X;
    private static final int SLOT_BACKGROUND_OFFSET = 1;
    private static final int HIGHLIGHT_DURATION_MILLIS = 15_000;
    private static final Comparator<ThroughputMonitorTerminalMenu.Entry> ENTRY_NAME_ORDER = Comparator
            .comparing(
                    (ThroughputMonitorTerminalMenu.Entry entry) -> entry.key().getDisplayName().getString(),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.key().getId().toString());
    private static final Comparator<ThroughputMonitorTerminalMenu.Entry> ENTRY_MOD_ORDER = Comparator
            .comparing(
                    (ThroughputMonitorTerminalMenu.Entry entry) -> entry.key().getId().getNamespace(),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ENTRY_NAME_ORDER);
    private static final Comparator<ThroughputMonitorTerminalMenu.Entry> ENTRY_AMOUNT_ORDER = Comparator
            .comparingDouble(ThroughputMonitorTerminalScreen::normalizedActivity)
            .thenComparing(ENTRY_NAME_ORDER);

    private static SortOrder rememberedSortOrder = SortOrder.NAME;
    private static SortDir rememberedSortDirection = SortDir.ASCENDING;
    private static ThroughputMonitorUpdateInterval rememberedUpdateInterval = ThroughputMonitorUpdateInterval.SECOND;

    private final Set<AEKey> expandedKeys = new HashSet<>();
    private EditBox searchField;
    private SettingToggleButton<SortOrder> sortByButton;
    private SettingToggleButton<SortDir> sortDirectionButton;
    private Button updateIntervalButton;
    private SortOrder sortOrder = rememberedSortOrder;
    private SortDir sortDirection = rememberedSortDirection;
    private ThroughputMonitorUpdateInterval updateInterval = rememberedUpdateInterval;
    private List<ThroughputMonitorTerminalMenu.Entry> sortedEntriesSource = List.of();
    private List<ThroughputMonitorTerminalMenu.Entry> sortedEntries = List.of();
    private SortOrder sortedEntriesOrder;
    private SortDir sortedEntriesDirection;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private boolean leftShiftDown;
    private boolean rightShiftDown;
    private List<ThroughputMonitorTerminalMenu.Entry> frozenEntries;
    private Map<AEKey, List<ThroughputMonitorTerminalMenu.SourceEntry>> frozenSources;

    public ThroughputMonitorTerminalScreen(ThroughputMonitorTerminalMenu menu, Inventory inventory,
                                           Component title) {
        super(menu, inventory, title);
        this.imageWidth = ThroughputMonitorTerminalLayout.IMAGE_WIDTH;
        this.imageHeight = ThroughputMonitorTerminalLayout.IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.searchField = new EditBox(
                this.font,
                this.leftPos + ThroughputMonitorTerminalLayout.SEARCH_X,
                this.topPos + ThroughputMonitorTerminalLayout.SEARCH_Y,
                ThroughputMonitorTerminalLayout.SEARCH_WIDTH,
                ThroughputMonitorTerminalLayout.SEARCH_HEIGHT,
                Component.translatable("field.gtlcore.throughput_monitor_terminal.search"));
        this.searchField.setMaxLength(SEARCH_MAX_LENGTH);
        this.searchField.setBordered(false);
        this.searchField.setTextColor(0xFFFFFFFF);
        this.searchField.setTextColorUneditable(0xFFAAAAAA);
        this.searchField.setHint(Component.translatable("field.gtlcore.throughput_monitor_terminal.search_hint"));
        this.searchField.setResponder(ignored -> this.scrollOffset = 0);
        this.addRenderableWidget(this.searchField);
        this.sortByButton = new SettingToggleButton<>(
                Settings.SORT_BY,
                this.sortOrder,
                (button, backwards) -> setSortOrder(button.getNextValue(backwards)));
        positionLeftToolbarButton(this.sortByButton, ThroughputMonitorTerminalLayout.SORT_BY_BUTTON_Y);
        this.addRenderableWidget(this.sortByButton);
        this.sortDirectionButton = new SettingToggleButton<>(
                Settings.SORT_DIRECTION,
                this.sortDirection,
                (button, backwards) -> setSortDirection(button.getNextValue(backwards)));
        positionLeftToolbarButton(
                this.sortDirectionButton,
                ThroughputMonitorTerminalLayout.SORT_DIRECTION_BUTTON_Y);
        this.addRenderableWidget(this.sortDirectionButton);
        this.updateIntervalButton = WirelessAeStyle.button(
                this.leftPos + ThroughputMonitorTerminalLayout.UPDATE_INTERVAL_BUTTON_X,
                this.topPos + ThroughputMonitorTerminalLayout.UPDATE_INTERVAL_BUTTON_Y,
                ThroughputMonitorTerminalLayout.UPDATE_INTERVAL_BUTTON_WIDTH,
                ThroughputMonitorTerminalLayout.UPDATE_INTERVAL_BUTTON_HEIGHT,
                Component.literal(this.updateInterval.label()),
                ignored -> setUpdateInterval(this.updateInterval.next(false)));
        updateIntervalButtonTooltip();
        this.addRenderableWidget(this.updateIntervalButton);
        sendUpdateInterval();
        if (this.menu.isUniversalTerminal()) {
            CycleTerminalButton cycleTerminalButton = new CycleTerminalButton(ignored -> cycleTerminal());
            cycleTerminalButton.setPosition(
                    this.leftPos - cycleTerminalButton.getWidth() -
                            ThroughputMonitorTerminalLayout.UNIVERSAL_TERMINAL_BUTTON_GAP,
                    this.topPos + ThroughputMonitorTerminalLayout.UNIVERSAL_TERMINAL_BUTTON_Y);
            cycleTerminalButton.setTooltip(Tooltip.create(cycleTerminalButton.getTooltipMessage().get(0)));
            this.addRenderableWidget(cycleTerminalButton);
        }
        clampScrollOffset();
    }

    @Override
    public boolean isHandlingRightClick() {
        return false;
    }

    @Override
    public void storeState() {
        rememberedSortOrder = this.sortOrder;
        rememberedSortDirection = this.sortDirection;
        rememberedUpdateInterval = this.updateInterval;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.searchField.tick();
        clampScrollOffset();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isShiftKey(keyCode)) {
            boolean wasUnlocked = !isSnapshotLocked();
            setShiftState(keyCode, true);
            if (wasUnlocked) {
                freezeSnapshot();
            }
            return true;
        }
        if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.searchField.isFocused() &&
                keyCode == this.minecraft.options.keyInventory.getKey().getValue()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isShiftKey(keyCode)) {
            setShiftState(keyCode, false);
            if (!isSnapshotLocked()) {
                this.frozenEntries = null;
                this.frozenSources = null;
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        renderMonitorTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        WirelessAeStyle.drawPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        WirelessAeStyle.drawTextField(
                graphics,
                this.leftPos + ThroughputMonitorTerminalLayout.SEARCH_PANEL_X,
                this.topPos + ThroughputMonitorTerminalLayout.SEARCH_PANEL_Y,
                ThroughputMonitorTerminalLayout.SEARCH_PANEL_WIDTH);
        WirelessAeStyle.drawInsetPanel(
                graphics,
                this.leftPos + ThroughputMonitorTerminalLayout.LIST_PANEL_X,
                this.topPos + ThroughputMonitorTerminalLayout.LIST_PANEL_Y,
                ThroughputMonitorTerminalLayout.LIST_PANEL_WIDTH,
                ThroughputMonitorTerminalLayout.LIST_PANEL_HEIGHT);
        drawPlayerInventorySlotBackgrounds(graphics);

        List<DisplayRow> rows = getDisplayRows();
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset,
                rows.size(),
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS);
        int drawnRows = Math.min(
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS,
                Math.max(0, rows.size() - this.scrollOffset));
        for (int rowIndex = 0; rowIndex < drawnRows - 1; rowIndex++) {
            int separatorY = this.topPos + ThroughputMonitorTerminalLayout.LIST_Y +
                    (rowIndex + 1) * ThroughputMonitorTerminalLayout.LIST_ROW_HEIGHT - 2;
            WirelessAeStyle.drawSeparator(
                    graphics,
                    this.leftPos + ROW_SEPARATOR_X,
                    separatorY,
                    ROW_SEPARATOR_WIDTH);
        }

        WirelessAeStyle.drawAe2Scrollbar(
                graphics,
                this.leftPos + ThroughputMonitorTerminalLayout.SCROLLBAR_X,
                this.topPos + ThroughputMonitorTerminalLayout.SCROLLBAR_Y,
                ThroughputMonitorTerminalLayout.SCROLLBAR_HEIGHT,
                rows.size(),
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS,
                this.scrollOffset);
    }

    private void drawPlayerInventorySlotBackgrounds(GuiGraphics graphics) {
        for (int row = 0; row < ThroughputMonitorTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS; column++) {
                drawSlotBackground(
                        graphics,
                        ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_X +
                                column * ThroughputMonitorTerminalLayout.SLOT_SIZE,
                        ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_Y +
                                row * ThroughputMonitorTerminalLayout.SLOT_SIZE);
            }
        }
        drawSlotGroupOutline(
                graphics,
                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_Y,
                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_HEIGHT);
        for (int column = 0; column < ThroughputMonitorTerminalLayout.INVENTORY_COLUMNS; column++) {
            drawSlotBackground(
                    graphics,
                    ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_X +
                            column * ThroughputMonitorTerminalLayout.SLOT_SIZE,
                    ThroughputMonitorTerminalLayout.PLAYER_HOTBAR_Y);
        }
        drawSlotGroupOutline(
                graphics,
                ThroughputMonitorTerminalLayout.PLAYER_HOTBAR_Y,
                ThroughputMonitorTerminalLayout.PLAYER_HOTBAR_HEIGHT);
    }

    private void drawSlotGroupOutline(GuiGraphics graphics, int slotY, int height) {
        WirelessAeStyle.drawSlotGroupOutline(
                graphics,
                this.leftPos + ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_X - SLOT_BACKGROUND_OFFSET,
                this.topPos + slotY - SLOT_BACKGROUND_OFFSET,
                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_WIDTH,
                height);
    }

    private void drawSlotBackground(GuiGraphics graphics, int slotX, int slotY) {
        Icon.SLOT_BACKGROUND.getBlitter().copy()
                .dest(
                        this.leftPos + slotX - SLOT_BACKGROUND_OFFSET,
                        this.topPos + slotY - SLOT_BACKGROUND_OFFSET)
                .blit(graphics);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(
                this.font,
                this.title,
                ThroughputMonitorTerminalLayout.TITLE_X,
                ThroughputMonitorTerminalLayout.TITLE_Y,
                WirelessAeStyle.TEXT,
                false);
        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_LABEL_X,
                ThroughputMonitorTerminalLayout.PLAYER_INVENTORY_LABEL_Y,
                WirelessAeStyle.TEXT,
                false);

        List<DisplayRow> rows = getDisplayRows();
        int drawnRows = Math.min(
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS,
                Math.max(0, rows.size() - this.scrollOffset));
        for (int rowIndex = 0; rowIndex < drawnRows; rowIndex++) {
            DisplayRow row = rows.get(this.scrollOffset + rowIndex);
            int rowY = ThroughputMonitorTerminalLayout.LIST_Y +
                    rowIndex * ThroughputMonitorTerminalLayout.LIST_ROW_HEIGHT;
            if (row.isSourceRow()) {
                drawSourceRow(graphics, row, rowY);
            } else {
                drawGroupRow(graphics, row, rowY, mouseX, mouseY);
            }
        }

        if (rows.isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("label.gtlcore.throughput_monitor_terminal.no_activity"),
                    ThroughputMonitorTerminalLayout.LIST_X + 2,
                    ThroughputMonitorTerminalLayout.LIST_Y + ROW_TEXT_Y_OFFSET,
                    ThroughputMonitorTerminalLayout.LIST_CONTENT_WIDTH - 4,
                    WirelessAeStyle.MUTED_TEXT);
        }
    }

    private void drawGroupRow(GuiGraphics graphics, DisplayRow row, int rowY, int mouseX, int mouseY) {
        ThroughputMonitorTerminalMenu.Entry entry = row.group().entry();
        boolean canExpand = entry.sourceCount() > 0;
        boolean expanded = this.expandedKeys.contains(entry.key());
        drawExpandButton(graphics, rowY + ROW_ICON_Y_OFFSET, canExpand, expanded, mouseX, mouseY);

        graphics.renderItem(entry.key().wrapForDisplayOrFilter(), GROUP_ICON_X, rowY + ROW_ICON_Y_OFFSET);
        Component rate = rateText(entry.key(), entry.insertedPerSecond(), entry.extractedPerSecond());
        int rateWidth = Math.min(this.font.width(rate), ROW_RATE_RIGHT - GROUP_NAME_X);
        int rateX = ROW_RATE_RIGHT - rateWidth;
        int nameWidth = Math.max(1, rateX - ROW_TEXT_GAP - GROUP_NAME_X);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                entry.key().getDisplayName(),
                GROUP_NAME_X,
                rowY + ROW_TEXT_Y_OFFSET,
                nameWidth,
                WirelessAeStyle.TEXT);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                rate,
                rateX,
                rowY + ROW_TEXT_Y_OFFSET,
                rateWidth,
                WirelessAeStyle.TEXT);
    }

    private void drawExpandButton(GuiGraphics graphics, int y, boolean active, boolean expanded,
                                  int mouseX, int mouseY) {
        if (!active) {
            return;
        }
        int screenX = this.leftPos + ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X;
        int screenY = this.topPos + y;
        boolean hovered = isInsideRect(
                mouseX,
                mouseY,
                screenX,
                screenY,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE);
        WirelessAeStyle.drawButtonBackground(
                graphics,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X,
                y,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE,
                true,
                false,
                false,
                hovered);
        String symbol = expanded ? "-" : "+";
        graphics.drawString(
                this.font,
                symbol,
                ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X +
                        (ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE - this.font.width(symbol)) / 2,
                y + (ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE - this.font.lineHeight) / 2,
                WirelessAeStyle.TEXT,
                false);
    }

    private void drawSourceRow(GuiGraphics graphics, DisplayRow row, int rowY) {
        ThroughputMonitorTerminalMenu.SourceEntry source = row.source();
        Component position = Component.translatable(
                "label.gtlcore.throughput_monitor_terminal.source",
                source.pos().getX(),
                source.pos().getY(),
                source.pos().getZ());
        Component rate = rateText(
                row.group().entry().key(),
                source.insertedPerSecond(),
                source.extractedPerSecond());
        int rateWidth = Math.min(this.font.width(rate), ROW_RATE_RIGHT - SOURCE_POSITION_X);
        int rateX = ROW_RATE_RIGHT - rateWidth;
        int positionWidth = Math.max(1, rateX - ROW_TEXT_GAP - SOURCE_POSITION_X);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                position,
                SOURCE_POSITION_X,
                rowY + ROW_TEXT_Y_OFFSET,
                positionWidth,
                WirelessAeStyle.MUTED_TEXT);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                rate,
                rateX,
                rowY + ROW_TEXT_Y_OFFSET,
                rateWidth,
                WirelessAeStyle.TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.sortByButton.isMouseOver(mouseX, mouseY)) {
            setSortOrder(this.sortByButton.getNextValue(true));
            return true;
        }
        if (button == 1 && this.sortDirectionButton.isMouseOver(mouseX, mouseY)) {
            setSortDirection(this.sortDirectionButton.getNextValue(true));
            return true;
        }
        if (button == 1 && this.updateIntervalButton.isMouseOver(mouseX, mouseY)) {
            setUpdateInterval(this.updateInterval.next(true));
            return true;
        }
        if (button == 0 && handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleRowClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            updateScrollOffsetFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInsideList(mouseX, mouseY) && scrollBy(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean handleRowClick(double mouseX, double mouseY) {
        DisplayRow row = rowAt(mouseX, mouseY);
        if (row == null) {
            return false;
        }
        if (!row.isSourceRow()) {
            if (isSnapshotLocked()) {
                return true;
            }
            int buttonX = this.leftPos + ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X;
            if (!isInsideRect(
                    mouseX,
                    mouseY,
                    buttonX,
                    rowScreenY(mouseY),
                    ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE,
                    ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE)) {
                return false;
            }
            AEKey key = row.group().entry().key();
            if (row.group().entry().sourceCount() <= 0) {
                return true;
            }
            boolean expanding = !this.expandedKeys.remove(key);
            if (expanding) {
                this.expandedKeys.add(key);
            }
            WirelessAePackets.CHANNEL.sendToServer(
                    new WirelessAePackets.SetThroughputMonitorSourceTrackingPacket(
                            this.menu.containerId,
                            key,
                            expanding));
            clampScrollOffset();
            return true;
        }

        ThroughputMonitorTerminalMenu.SourceEntry source = row.source();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, source.dimension());
        BlockHighlightHandler.highlight(
                source.pos(),
                source.side(),
                dimension,
                System.currentTimeMillis() + HIGHLIGHT_DURATION_MILLIS,
                new AABB(source.pos()));
        return true;
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY) {
        List<DisplayRow> rows = getDisplayRows();
        if (!WirelessAeStyle.needsScrollbar(rows.size(), ThroughputMonitorTerminalLayout.VISIBLE_ROWS) ||
                !isInsideRect(
                        mouseX,
                        mouseY,
                        this.leftPos + ThroughputMonitorTerminalLayout.SCROLLBAR_X,
                        this.topPos + ThroughputMonitorTerminalLayout.SCROLLBAR_Y,
                        WirelessAeStyle.AE2_SCROLLBAR_WIDTH,
                        ThroughputMonitorTerminalLayout.SCROLLBAR_HEIGHT)) {
            return false;
        }
        this.draggingScrollbar = true;
        updateScrollOffsetFromMouse(mouseY);
        return true;
    }

    private void renderMonitorTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (renderSettingTooltip(graphics, this.sortByButton, mouseX, mouseY) ||
                renderSettingTooltip(graphics, this.sortDirectionButton, mouseX, mouseY)) {
            return;
        }
        DisplayRow row = rowAt(mouseX, mouseY);
        if (row == null) {
            return;
        }
        if (!row.isSourceRow()) {
            if (mouseX >= this.leftPos + ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X &&
                    mouseX < this.leftPos + ThroughputMonitorTerminalLayout.EXPAND_BUTTON_X +
                            ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE) {
                String tooltipKey = row.group().entry().sourceCount() <= 0 ?
                        "tooltip.gtlcore.throughput_monitor_terminal.no_sources" :
                        (this.expandedKeys.contains(row.group().entry().key()) ?
                                "tooltip.gtlcore.throughput_monitor_terminal.collapse" :
                                "tooltip.gtlcore.throughput_monitor_terminal.expand");
                graphics.renderTooltip(this.font, Component.translatable(tooltipKey), mouseX, mouseY);
            } else if (mouseX >= this.leftPos + GROUP_ICON_X &&
                    mouseX < this.leftPos + GROUP_ICON_X + ThroughputMonitorTerminalLayout.EXPAND_BUTTON_SIZE) {
                        graphics.renderTooltip(
                                this.font,
                                row.group().entry().key().wrapForDisplayOrFilter(),
                                mouseX,
                                mouseY);
                    }
            return;
        }

        ThroughputMonitorTerminalMenu.SourceEntry source = row.source();
        List<Component> tooltip = List.of(
                row.group().entry().key().getDisplayName(),
                Component.translatable(
                        "tooltip.gtlcore.throughput_monitor_terminal.dimension",
                        source.dimension().toString()),
                Component.translatable(
                        "tooltip.gtlcore.throughput_monitor_terminal.position",
                        source.pos().getX(),
                        source.pos().getY(),
                        source.pos().getZ()),
                Component.translatable(
                        "tooltip.gtlcore.throughput_monitor_terminal.side",
                        directionText(source.side())),
                rateText(row.group().entry().key(), source.insertedPerSecond(), source.extractedPerSecond()),
                Component.translatable("tooltip.gtlcore.throughput_monitor_terminal.highlight"));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private List<DisplayRow> getDisplayRows() {
        String query = normalizedQuery(this.searchField);
        List<DisplayRow> rows = new ArrayList<>();
        for (ThroughputMonitorTerminalMenu.Entry entry : getSortedVisibleEntries()) {
            boolean resourceMatches = resourceMatches(entry.key(), query);
            List<ThroughputMonitorTerminalMenu.SourceEntry> sources = getVisibleSources(entry.key());
            if (!query.isEmpty() && !resourceMatches) {
                sources = sources.stream()
                        .filter(source -> sourceSearchText(source).contains(query))
                        .toList();
            }
            if (!resourceMatches && sources.isEmpty()) {
                continue;
            }

            GroupView group = new GroupView(entry);
            rows.add(new DisplayRow(group, null));
            if (!this.expandedKeys.contains(entry.key())) {
                continue;
            }
            for (ThroughputMonitorTerminalMenu.SourceEntry source : sources) {
                rows.add(new DisplayRow(group, source));
            }
        }
        return rows;
    }

    private @Nullable DisplayRow rowAt(double mouseX, double mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return null;
        }
        List<DisplayRow> rows = getDisplayRows();
        int visibleIndex = (int) ((mouseY - this.topPos - ThroughputMonitorTerminalLayout.LIST_Y) /
                ThroughputMonitorTerminalLayout.LIST_ROW_HEIGHT);
        int index = this.scrollOffset + visibleIndex;
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private boolean scrollBy(double delta) {
        List<DisplayRow> rows = getDisplayRows();
        int nextOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset - (int) Math.signum(delta),
                rows.size(),
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS);
        if (nextOffset == this.scrollOffset) {
            return false;
        }
        this.scrollOffset = nextOffset;
        return true;
    }

    private void updateScrollOffsetFromMouse(double mouseY) {
        this.scrollOffset = WirelessAeStyle.ae2ScrollbarOffsetFromMouse(
                mouseY,
                this.topPos + ThroughputMonitorTerminalLayout.SCROLLBAR_Y,
                ThroughputMonitorTerminalLayout.SCROLLBAR_HEIGHT,
                getDisplayRows().size(),
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS);
    }

    private void clampScrollOffset() {
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset,
                getDisplayRows().size(),
                ThroughputMonitorTerminalLayout.VISIBLE_ROWS);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return isInsideRect(
                mouseX,
                mouseY,
                this.leftPos + ThroughputMonitorTerminalLayout.LIST_X,
                this.topPos + ThroughputMonitorTerminalLayout.LIST_Y,
                ThroughputMonitorTerminalLayout.LIST_CONTENT_WIDTH,
                ThroughputMonitorTerminalLayout.LIST_HEIGHT);
    }

    private int rowScreenY(double mouseY) {
        int visibleIndex = (int) ((mouseY - this.topPos - ThroughputMonitorTerminalLayout.LIST_Y) /
                ThroughputMonitorTerminalLayout.LIST_ROW_HEIGHT);
        return this.topPos + ThroughputMonitorTerminalLayout.LIST_Y +
                visibleIndex * ThroughputMonitorTerminalLayout.LIST_ROW_HEIGHT + ROW_ICON_Y_OFFSET;
    }

    private static Component rateText(AEKey key, double insertedPerSecond, double extractedPerSecond) {
        return Component.translatable(
                "label.gtlcore.throughput_monitor_terminal.rate",
                "+" + METhroughputMonitorPart.formatThroughputAmount(key, Math.abs(insertedPerSecond)),
                "-" + METhroughputMonitorPart.formatThroughputAmount(key, Math.abs(extractedPerSecond)));
    }

    private static boolean resourceMatches(AEKey key, String query) {
        return query.isEmpty() || UniversalSearch.contains(key.getDisplayName().getString(), query) ||
                UniversalSearch.contains(key.getId().toString(), query);
    }

    private static String sourceSearchText(ThroughputMonitorTerminalMenu.SourceEntry source) {
        String side = source.side() == null ? "" : source.side().getName();
        return (source.dimension() + " " + source.pos().getX() + " " + source.pos().getY() + " " +
                source.pos().getZ() + " " + side).toLowerCase(Locale.ROOT);
    }

    private static Component directionText(@Nullable Direction side) {
        return side == null ?
                Component.translatable("tooltip.gtlcore.throughput_monitor_terminal.unknown_side") :
                Component.translatable("tooltip.gtlcore.throughput_monitor_terminal.direction." + side.getName());
    }

    private static String normalizedQuery(@Nullable EditBox field) {
        return field == null ? "" : field.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private void freezeSnapshot() {
        this.frozenEntries = List.copyOf(this.menu.getEntries());
        Map<AEKey, List<ThroughputMonitorTerminalMenu.SourceEntry>> sources = new HashMap<>();
        for (ThroughputMonitorTerminalMenu.Entry entry : this.frozenEntries) {
            sources.put(entry.key(), List.copyOf(this.menu.getSourceEntries(entry.key())));
        }
        this.frozenSources = Map.copyOf(sources);
    }

    private List<ThroughputMonitorTerminalMenu.Entry> getVisibleEntries() {
        return this.frozenEntries == null ? this.menu.getEntries() : this.frozenEntries;
    }

    private List<ThroughputMonitorTerminalMenu.Entry> getSortedVisibleEntries() {
        List<ThroughputMonitorTerminalMenu.Entry> source = getVisibleEntries();
        if (source == this.sortedEntriesSource && this.sortOrder == this.sortedEntriesOrder &&
                this.sortDirection == this.sortedEntriesDirection) {
            return this.sortedEntries;
        }

        List<ThroughputMonitorTerminalMenu.Entry> entries = new ArrayList<>(source);
        Comparator<ThroughputMonitorTerminalMenu.Entry> comparator = switch (this.sortOrder) {
            case NAME -> ENTRY_NAME_ORDER;
            case AMOUNT -> ENTRY_AMOUNT_ORDER;
            case MOD -> ENTRY_MOD_ORDER;
        };
        if (this.sortDirection == SortDir.DESCENDING) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);
        this.sortedEntriesSource = source;
        this.sortedEntries = List.copyOf(entries);
        this.sortedEntriesOrder = this.sortOrder;
        this.sortedEntriesDirection = this.sortDirection;
        return this.sortedEntries;
    }

    private void positionLeftToolbarButton(AbstractWidget button, int y) {
        button.setPosition(
                this.leftPos - button.getWidth() - ThroughputMonitorTerminalLayout.LEFT_TOOLBAR_GAP,
                this.topPos + y);
    }

    private void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        rememberedSortOrder = sortOrder;
        this.sortByButton.set(sortOrder);
        this.scrollOffset = 0;
    }

    private void setSortDirection(SortDir sortDirection) {
        this.sortDirection = sortDirection;
        rememberedSortDirection = sortDirection;
        this.sortDirectionButton.set(sortDirection);
        this.scrollOffset = 0;
    }

    private void setUpdateInterval(ThroughputMonitorUpdateInterval updateInterval) {
        this.updateInterval = updateInterval;
        rememberedUpdateInterval = updateInterval;
        this.updateIntervalButton.setMessage(Component.literal(updateInterval.label()));
        updateIntervalButtonTooltip();
        sendUpdateInterval();
    }

    private void updateIntervalButtonTooltip() {
        this.updateIntervalButton.setTooltip(Tooltip.create(Component.translatable(
                "tooltip.gtlcore.throughput_monitor_terminal.update_interval",
                this.updateInterval.label())));
    }

    private void sendUpdateInterval() {
        WirelessAePackets.CHANNEL.sendToServer(
                new WirelessAePackets.SetThroughputMonitorUpdateIntervalPacket(
                        this.menu.containerId,
                        this.updateInterval));
    }

    private boolean renderSettingTooltip(GuiGraphics graphics, SettingToggleButton<?> button,
                                         int mouseX, int mouseY) {
        if (!button.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        graphics.renderComponentTooltip(this.font, button.getTooltipMessage(), mouseX, mouseY);
        return true;
    }

    private static double normalizedActivity(ThroughputMonitorTerminalMenu.Entry entry) {
        double totalActivity = Math.abs(entry.insertedPerSecond()) + Math.abs(entry.extractedPerSecond());
        return totalActivity / entry.key().getAmountPerUnit();
    }

    private List<ThroughputMonitorTerminalMenu.SourceEntry> getVisibleSources(AEKey key) {
        return this.frozenSources == null ?
                this.menu.getSourceEntries(key) :
                this.frozenSources.getOrDefault(key, List.of());
    }

    private boolean isSnapshotLocked() {
        return this.leftShiftDown || this.rightShiftDown;
    }

    private void setShiftState(int keyCode, boolean pressed) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            this.leftShiftDown = pressed;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.rightShiftDown = pressed;
        }
    }

    private static boolean isShiftKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private record GroupView(ThroughputMonitorTerminalMenu.Entry entry) {}

    private record DisplayRow(GroupView group, @Nullable ThroughputMonitorTerminalMenu.SourceEntry source) {

        private boolean isSourceRow() {
            return source != null;
        }
    }
}
