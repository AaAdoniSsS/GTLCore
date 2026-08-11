package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalLayout;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;

public final class EmitterManagerTerminalScreen extends AEBaseScreen<EmitterManagerTerminalMenu>
                                                implements IUniversalTerminalCapable {

    private static final int SEARCH_MAX_LENGTH = 80;
    private static final int VALUE_MAX_LENGTH = Long.toString(Long.MAX_VALUE).length() + 1;
    private static final int ROW_ICON_X = EmitterManagerTerminalLayout.LIST_X + 2;
    private static final int ROW_TEXT_X = EmitterManagerTerminalLayout.LIST_X + 21;
    private static final int ROW_TEXT_WIDTH = EmitterManagerTerminalLayout.LIST_CONTENT_WIDTH - 23;
    private static final int ROW_SEPARATOR_COLOR = 0x66555555;
    private static final int HIGHLIGHT_DURATION_MILLIS = 15_000;
    private static String rememberedSearch = "";

    private EditBox searchField;
    private EditBox primaryValue;
    private EditBox secondaryValue;
    private SettingToggleButton<RedstoneMode> redstoneMode;
    private SettingToggleButton<YesNo> craftingMode;
    private SettingToggleButton<FuzzyMode> fuzzyMode;
    private HighlightEmitterButton locateButton;
    private CycleTerminalButton cycleTerminalButton;
    private @Nullable EmitterManagerTerminalMenu.Address selectedAddress;
    private @Nullable ControlState controlState;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private boolean cyclingToPreviousTerminal;

    public EmitterManagerTerminalScreen(EmitterManagerTerminalMenu menu, Inventory inventory, Component title,
                                        ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        super.init();
        this.searchField = createTextField(
                EmitterManagerTerminalLayout.SEARCH_X,
                EmitterManagerTerminalLayout.SEARCH_Y,
                EmitterManagerTerminalLayout.SEARCH_PANEL_WIDTH,
                Component.translatable("field.gtlcore.emitter_manager_terminal.search_hint"));
        this.searchField.setMaxLength(SEARCH_MAX_LENGTH);
        this.searchField.setResponder(ignored -> this.scrollOffset = 0);
        this.searchField.setValue(rememberedSearch);

        this.primaryValue = createTextField(
                EmitterManagerTerminalLayout.PRIMARY_INPUT_X + EmitterManagerTerminalLayout.VALUE_INPUT_X_OFFSET,
                EmitterManagerTerminalLayout.PRIMARY_INPUT_Y + EmitterManagerTerminalLayout.VALUE_INPUT_Y_OFFSET,
                EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH,
                Component.empty());
        this.secondaryValue = createTextField(
                EmitterManagerTerminalLayout.SECONDARY_INPUT_X + EmitterManagerTerminalLayout.VALUE_INPUT_X_OFFSET,
                EmitterManagerTerminalLayout.SECONDARY_INPUT_Y + EmitterManagerTerminalLayout.VALUE_INPUT_Y_OFFSET,
                EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH,
                Component.empty());
        this.primaryValue.setMaxLength(VALUE_MAX_LENGTH);
        this.secondaryValue.setMaxLength(VALUE_MAX_LENGTH);
        Tooltip valueEditTooltip = Tooltip.create(Component.translatable(
                "tooltip.gtlcore.emitter_manager_terminal.confirm_value"));
        this.primaryValue.setTooltip(valueEditTooltip);
        this.secondaryValue.setTooltip(valueEditTooltip);

        this.redstoneMode = new SettingToggleButton<>(
                Settings.REDSTONE_EMITTER,
                RedstoneMode.HIGH_SIGNAL,
                (button, backwards) -> setSetting(button, button.getNextValue(backwards)));
        this.craftingMode = new SettingToggleButton<>(
                Settings.CRAFT_VIA_REDSTONE,
                YesNo.NO,
                (button, backwards) -> setSetting(button, button.getNextValue(backwards)));
        this.fuzzyMode = new SettingToggleButton<>(
                Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL,
                (button, backwards) -> setSetting(button, button.getNextValue(backwards)));
        this.addRenderableWidget(this.redstoneMode);
        this.addRenderableWidget(this.craftingMode);
        this.addRenderableWidget(this.fuzzyMode);

        this.locateButton = new HighlightEmitterButton(
                Component.translatable("tooltip.gtlcore.emitter_manager_terminal.locate"),
                ignored -> locateSelected());
        this.addRenderableWidget(this.locateButton);
        if (this.menu.isUniversalTerminal()) {
            this.cycleTerminalButton = new CycleTerminalButton(ignored -> cycleTerminal());
            this.cycleTerminalButton.setPosition(
                    this.leftPos - this.cycleTerminalButton.getWidth() -
                            EmitterManagerTerminalLayout.UNIVERSAL_TERMINAL_BUTTON_GAP,
                    this.topPos + EmitterManagerTerminalLayout.UNIVERSAL_TERMINAL_BUTTON_Y);
            this.addRenderableWidget(this.cycleTerminalButton);
        }
        updateControls();
    }

    @Override
    public boolean isHandlingRightClick() {
        return cyclingToPreviousTerminal;
    }

    @Override
    public void storeState() {
        rememberedSearch = this.searchField.getValue();
    }

    private EditBox createTextField(int x, int y, int frameWidth, Component hint) {
        EditBox field = new EditBox(
                this.font,
                this.leftPos + x,
                this.topPos + y,
                WirelessAeStyle.ae2TextFieldTextWidth(this.font, frameWidth),
                EmitterManagerTerminalLayout.VALUE_INPUT_HEIGHT,
                hint);
        field.setBordered(false);
        field.setTextColor(0xFFFFFFFF);
        field.setTextColorUneditable(0xFFAAAAAA);
        field.setHint(hint);
        this.addRenderableWidget(field);
        return field;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.searchField.tick();
        this.primaryValue.tick();
        this.secondaryValue.tick();
        updateControls();
        this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                this.scrollOffset,
                getVisibleEntries().size(),
                EmitterManagerTerminalLayout.VISIBLE_ROWS);
    }

    private void updateControls() {
        if (this.searchField == null) {
            return;
        }
        List<EmitterManagerTerminalMenu.Entry> entries = getVisibleEntries();
        EmitterManagerTerminalMenu.Entry selected = findSelectedEntry();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
            this.selectedAddress = selected.address();
        } else if (selected != null && !containsAddress(entries, selected.address())) {
            selected = entries.isEmpty() ? null : entries.get(0);
            this.selectedAddress = selected == null ? null : selected.address();
        }
        syncSelectionToServer();
        ControlState nextState = ControlState.of(selected);
        if (!Objects.equals(this.controlState, nextState)) {
            syncControlValues(selected);
            this.controlState = nextState;
        }
        setControlsEnabled(selected);
    }

    /**
     * Keeps the server's notion of the selected emitter in step with the list, so the card slots read and
     * write the right emitter's upgrade inventory.
     */
    private void syncSelectionToServer() {
        if (Objects.equals(this.menu.getSelectedAddress(), this.selectedAddress)) {
            return;
        }
        this.menu.setSelectedAddress(this.selectedAddress);
        WirelessAePackets.CHANNEL.sendToServer(
                new WirelessAePackets.SelectEmitterPacket(this.menu.containerId, this.selectedAddress));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderEmitterTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void drawBG(@NotNull GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTick) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTick);
        WirelessAeStyle.drawAe2TextField(
                graphics,
                this.leftPos + EmitterManagerTerminalLayout.SEARCH_PANEL_X,
                this.topPos + EmitterManagerTerminalLayout.SEARCH_PANEL_Y,
                EmitterManagerTerminalLayout.SEARCH_PANEL_WIDTH,
                true,
                this.searchField.isFocused());
        WirelessAeStyle.drawInsetPanel(
                graphics,
                this.leftPos + EmitterManagerTerminalLayout.LIST_PANEL_X,
                this.topPos + EmitterManagerTerminalLayout.LIST_PANEL_Y,
                EmitterManagerTerminalLayout.LIST_PANEL_WIDTH,
                EmitterManagerTerminalLayout.LIST_PANEL_HEIGHT);
        WirelessAeStyle.drawInsetPanel(
                graphics,
                this.leftPos + EmitterManagerTerminalLayout.DETAIL_PANEL_X,
                this.topPos + EmitterManagerTerminalLayout.DETAIL_PANEL_Y,
                EmitterManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                EmitterManagerTerminalLayout.DETAIL_PANEL_HEIGHT);
        if (this.primaryValue.visible) {
            WirelessAeStyle.drawAe2TextField(
                    graphics,
                    this.leftPos + EmitterManagerTerminalLayout.PRIMARY_INPUT_X,
                    this.topPos + EmitterManagerTerminalLayout.PRIMARY_INPUT_Y,
                    EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH,
                    this.primaryValue.active,
                    this.primaryValue.isFocused());
        }
        if (this.secondaryValue.visible) {
            WirelessAeStyle.drawAe2TextField(
                    graphics,
                    this.leftPos + EmitterManagerTerminalLayout.SECONDARY_INPUT_X,
                    this.topPos + EmitterManagerTerminalLayout.SECONDARY_INPUT_Y,
                    EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH,
                    this.secondaryValue.active,
                    this.secondaryValue.isFocused());
        }
        drawPlayerInventorySlotBackgrounds(graphics);
        drawUpgradeSlotBackgrounds(graphics);
        List<EmitterManagerTerminalMenu.Entry> entries = getVisibleEntries();
        WirelessAeStyle.drawAe2Scrollbar(
                graphics,
                this.leftPos + EmitterManagerTerminalLayout.SCROLLBAR_X,
                this.topPos + EmitterManagerTerminalLayout.SCROLLBAR_Y,
                EmitterManagerTerminalLayout.SCROLLBAR_HEIGHT,
                entries.size(),
                EmitterManagerTerminalLayout.VISIBLE_ROWS,
                this.scrollOffset);
    }

    private void drawPlayerInventorySlotBackgrounds(GuiGraphics graphics) {
        for (int row = 0; row < EmitterManagerTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < EmitterManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
                drawSlotBackground(
                        graphics,
                        EmitterManagerTerminalLayout.PLAYER_INVENTORY_X +
                                column * EmitterManagerTerminalLayout.SLOT_SIZE,
                        EmitterManagerTerminalLayout.PLAYER_INVENTORY_Y +
                                row * EmitterManagerTerminalLayout.SLOT_SIZE);
            }
        }
        for (int column = 0; column < EmitterManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
            drawSlotBackground(
                    graphics,
                    EmitterManagerTerminalLayout.PLAYER_INVENTORY_X +
                            column * EmitterManagerTerminalLayout.SLOT_SIZE,
                    EmitterManagerTerminalLayout.PLAYER_HOTBAR_Y);
        }
    }

    /** Only as many card slots as the selected emitter reports get a background. */
    private void drawUpgradeSlotBackgrounds(GuiGraphics graphics) {
        int slots = this.menu.getSelectedUpgradeSlots();
        for (int index = 0; index < slots; index++) {
            drawSlotBackground(
                    graphics,
                    EmitterManagerTerminalLayout.CARD_SLOT_X + index * EmitterManagerTerminalLayout.SLOT_SIZE,
                    EmitterManagerTerminalLayout.CARD_SLOT_Y);
        }
        if (this.menu.hasSelectedConfig()) {
            drawSlotBackground(
                    graphics,
                    EmitterManagerTerminalLayout.CONFIG_SLOT_X,
                    EmitterManagerTerminalLayout.CONFIG_SLOT_Y);
        }
    }

    private void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        Icon.SLOT_BACKGROUND.getBlitter().copy().dest(this.leftPos + x - 1, this.topPos + y - 1).blit(graphics);
    }

    @Override
    public void drawFG(@NotNull GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(
                this.font,
                this.title,
                EmitterManagerTerminalLayout.TITLE_X,
                EmitterManagerTerminalLayout.TITLE_Y,
                WirelessAeStyle.TEXT,
                false);
        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                EmitterManagerTerminalLayout.PLAYER_INVENTORY_LABEL_X,
                EmitterManagerTerminalLayout.PLAYER_INVENTORY_LABEL_Y,
                WirelessAeStyle.TEXT,
                false);
        drawRows(graphics);
        drawSelectedDetails(graphics);
    }

    private static final int ROW_SELECTION_COLOR = 0x44AABBCC;

    private void drawRows(GuiGraphics graphics) {
        List<EmitterManagerTerminalMenu.Entry> entries = getVisibleEntries();
        int rows = Math.min(
                EmitterManagerTerminalLayout.VISIBLE_ROWS,
                Math.max(0, entries.size() - this.scrollOffset));
        for (int row = 0; row < rows; row++) {
            EmitterManagerTerminalMenu.Entry entry = entries.get(this.scrollOffset + row);
            int rowY = EmitterManagerTerminalLayout.LIST_Y + row * EmitterManagerTerminalLayout.LIST_ROW_HEIGHT;
            if (entry.address().equals(this.selectedAddress)) {
                graphics.fill(
                        EmitterManagerTerminalLayout.LIST_X,
                        rowY,
                        EmitterManagerTerminalLayout.LIST_X + EmitterManagerTerminalLayout.LIST_CONTENT_WIDTH,
                        rowY + EmitterManagerTerminalLayout.LIST_ROW_HEIGHT - 1,
                        ROW_SELECTION_COLOR);
            }
            if (row > 0) {
                graphics.fill(
                        EmitterManagerTerminalLayout.LIST_X,
                        rowY - 1,
                        EmitterManagerTerminalLayout.LIST_X + EmitterManagerTerminalLayout.LIST_CONTENT_WIDTH,
                        rowY,
                        ROW_SEPARATOR_COLOR);
            }
            graphics.renderItem(entry.icon(), ROW_ICON_X, rowY + 2);
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    entry.name(),
                    ROW_TEXT_X,
                    rowY + 2,
                    ROW_TEXT_WIDTH,
                    WirelessAeStyle.TEXT);
            Component detail = entry.configuredKey() == null ? functionText(entry) : entry.configuredKey().getDisplayName();
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    detail,
                    ROW_TEXT_X,
                    rowY + 11,
                    ROW_TEXT_WIDTH,
                    WirelessAeStyle.MUTED_TEXT);
        }
        if (entries.isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    this.font,
                    Component.translatable("label.gtlcore.emitter_manager_terminal.empty"),
                    EmitterManagerTerminalLayout.LIST_X + 3,
                    EmitterManagerTerminalLayout.LIST_Y + 6,
                    EmitterManagerTerminalLayout.LIST_CONTENT_WIDTH - 6,
                    WirelessAeStyle.MUTED_TEXT);
        }
    }

    private void drawSelectedDetails(GuiGraphics graphics) {
        EmitterManagerTerminalMenu.Entry selected = findSelectedEntry();
        if (selected == null) {
            return;
        }
        int y = EmitterManagerTerminalLayout.DETAIL_Y;
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                selected.name(),
                EmitterManagerTerminalLayout.DETAIL_X,
                y,
                EmitterManagerTerminalLayout.DETAIL_WIDTH,
                WirelessAeStyle.TEXT);
        y += EmitterManagerTerminalLayout.DETAIL_LINE_HEIGHT;
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                functionText(selected),
                EmitterManagerTerminalLayout.DETAIL_X,
                y,
                EmitterManagerTerminalLayout.DETAIL_WIDTH,
                WirelessAeStyle.MUTED_TEXT);
        y += EmitterManagerTerminalLayout.DETAIL_LINE_HEIGHT;
        WirelessAeStyle.drawTrimmedString(
                graphics,
                this.font,
                Component.translatable(
                        "label.gtlcore.emitter_manager_terminal.current." +
                                selected.function().name().toLowerCase(Locale.ROOT),
                        formatAmount(selected, selected.monitoredValue())),
                EmitterManagerTerminalLayout.DETAIL_X,
                y,
                EmitterManagerTerminalLayout.DETAIL_WIDTH,
                WirelessAeStyle.MUTED_TEXT);
        if (this.primaryValue.visible) {
            graphics.drawString(
                    this.font,
                    Component.translatable(isThresholdSelected() ?
                            "label.gtlcore.emitter_manager_terminal.upper_threshold" :
                            "label.gtlcore.emitter_manager_terminal.threshold"),
                    EmitterManagerTerminalLayout.DETAIL_X,
                    EmitterManagerTerminalLayout.PRIMARY_LABEL_Y,
                    WirelessAeStyle.TEXT,
                    false);
        }
        if (this.secondaryValue.visible) {
            graphics.drawString(
                    this.font,
                    Component.translatable("label.gtlcore.emitter_manager_terminal.lower_threshold"),
                    EmitterManagerTerminalLayout.DETAIL_X,
                    EmitterManagerTerminalLayout.SECONDARY_INPUT_Y -
                            EmitterManagerTerminalLayout.LABEL_GAP,
                    WirelessAeStyle.TEXT,
                    false);
        }
        drawValueUnits(graphics, selected);
    }

    private void drawValueUnits(GuiGraphics graphics, EmitterManagerTerminalMenu.Entry entry) {
        String unit = getValueUnit(entry);
        if (unit == null) {
            return;
        }
        int unitX = EmitterManagerTerminalLayout.VALUE_PANEL_X + EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH -
                EmitterManagerTerminalLayout.TEXT_FIELD_PADDING - this.font.width(unit);
        if (this.primaryValue.visible) {
            graphics.drawString(
                    this.font,
                    unit,
                    unitX,
                    EmitterManagerTerminalLayout.PRIMARY_INPUT_Y + EmitterManagerTerminalLayout.TEXT_FIELD_PADDING,
                    WirelessAeStyle.TEXT,
                    false);
        }
        if (this.secondaryValue.visible) {
            graphics.drawString(
                    this.font,
                    unit,
                    unitX,
                    EmitterManagerTerminalLayout.SECONDARY_INPUT_Y + EmitterManagerTerminalLayout.TEXT_FIELD_PADDING,
                    WirelessAeStyle.TEXT,
                    false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // GLFW_KEY_ENTER=257, GLFW_KEY_KP_ENTER=335
        if ((keyCode == 257 || keyCode == 335) &&
                (this.primaryValue.isFocused() || this.secondaryValue.isFocused())) {
            applyValues();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.cycleTerminalButton != null &&
                this.cycleTerminalButton.isMouseOver(mouseX, mouseY)) {
            this.cyclingToPreviousTerminal = true;
            cycleTerminal();
            this.cyclingToPreviousTerminal = false;
            return true;
        }
        if (button == 1 && handleBackwardsSettingClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0) {
            EmitterManagerTerminalMenu.Entry entry = entryAt(mouseX, mouseY);
            if (entry != null) {
                this.selectedAddress = entry.address();
                this.controlState = null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleBackwardsSettingClick(double mouseX, double mouseY) {
        if (this.redstoneMode.isMouseOver(mouseX, mouseY) && this.redstoneMode.visible) {
            setSetting(this.redstoneMode, this.redstoneMode.getNextValue(true));
            return true;
        }
        if (this.craftingMode.isMouseOver(mouseX, mouseY) && this.craftingMode.visible) {
            setSetting(this.craftingMode, this.craftingMode.getNextValue(true));
            return true;
        }
        if (this.fuzzyMode.isMouseOver(mouseX, mouseY) && this.fuzzyMode.visible) {
            setSetting(this.fuzzyMode, this.fuzzyMode.getNextValue(true));
            return true;
        }
        return false;
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY) {
        List<EmitterManagerTerminalMenu.Entry> entries = getVisibleEntries();
        int x = this.leftPos + EmitterManagerTerminalLayout.SCROLLBAR_X;
        int y = this.topPos + EmitterManagerTerminalLayout.SCROLLBAR_Y;
        if (!WirelessAeStyle.needsScrollbar(entries.size(), EmitterManagerTerminalLayout.VISIBLE_ROWS) ||
                mouseX < x || mouseX >= x + WirelessAeStyle.AE2_SCROLLBAR_WIDTH ||
                mouseY < y || mouseY >= y + EmitterManagerTerminalLayout.SCROLLBAR_HEIGHT) {
            return false;
        }
        this.draggingScrollbar = true;
        updateScrollFromMouse(mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            updateScrollFromMouse(mouseY);
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
        if (isInsideList(mouseX, mouseY)) {
            int direction = delta > 0 ? -1 : 1;
            this.scrollOffset = WirelessAeStyle.clampScrollOffset(
                    this.scrollOffset + direction,
                    getVisibleEntries().size(),
                    EmitterManagerTerminalLayout.VISIBLE_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateScrollFromMouse(double mouseY) {
        this.scrollOffset = WirelessAeStyle.ae2ScrollbarOffsetFromMouse(
                mouseY,
                this.topPos + EmitterManagerTerminalLayout.SCROLLBAR_Y,
                EmitterManagerTerminalLayout.SCROLLBAR_HEIGHT,
                getVisibleEntries().size(),
                EmitterManagerTerminalLayout.VISIBLE_ROWS);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + EmitterManagerTerminalLayout.LIST_PANEL_X &&
                mouseX < this.leftPos + EmitterManagerTerminalLayout.LIST_PANEL_X +
                        EmitterManagerTerminalLayout.LIST_PANEL_WIDTH &&
                mouseY >= this.topPos + EmitterManagerTerminalLayout.LIST_PANEL_Y &&
                mouseY < this.topPos + EmitterManagerTerminalLayout.LIST_PANEL_Y +
                        EmitterManagerTerminalLayout.LIST_PANEL_HEIGHT;
    }

    private void renderEmitterTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        EmitterManagerTerminalMenu.Entry entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(entry.name());
        tooltip.add(Component.translatable("tooltip.gtlcore.emitter_manager_terminal.function", functionText(entry)));
        if (entry.configuredKey() != null) {
            tooltip.add(Component.translatable(
                    "tooltip.gtlcore.emitter_manager_terminal.monitored_key",
                    entry.configuredKey().getDisplayName()));
        }
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.emitter_manager_terminal.current_value",
                formatAmount(entry, entry.monitoredValue())));
        tooltip.add(Component.translatable(
                "tooltip.gtlcore.emitter_manager_terminal.position",
                entry.address().pos().getX(),
                entry.address().pos().getY(),
                entry.address().pos().getZ(),
                entry.address().side().getName()));
        tooltip.add(Component.translatable(
                entry.online() ?
                        "tooltip.gtlcore.emitter_manager_terminal.online" :
                        "tooltip.gtlcore.emitter_manager_terminal.offline"));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void syncControlValues(@Nullable EmitterManagerTerminalMenu.Entry entry) {
        updateValueFieldWidths(entry);
        if (entry == null) {
            this.primaryValue.setValue("");
            this.secondaryValue.setValue("");
            return;
        }
        boolean threshold = entry.function() == EmitterManagerTerminalMenu.Function.THRESHOLD;
        this.primaryValue.setValue(formatEditableValue(
                entry,
                threshold ? entry.upperValue() : entry.reportingValue()));
        this.secondaryValue.setValue(formatEditableValue(entry, entry.lowerValue()));
        setSettingButton(this.redstoneMode, entry.settingValue(Settings.REDSTONE_EMITTER.getName()), RedstoneMode.class);
        setSettingButton(this.craftingMode, entry.settingValue(Settings.CRAFT_VIA_REDSTONE.getName()), YesNo.class);
        setSettingButton(this.fuzzyMode, entry.settingValue(Settings.FUZZY_MODE.getName()), FuzzyMode.class);
    }

    private void updateValueFieldWidths(@Nullable EmitterManagerTerminalMenu.Entry entry) {
        String unit = getValueUnit(entry);
        int width = WirelessAeStyle.ae2TextFieldTextWidth(
                this.font,
                EmitterManagerTerminalLayout.VALUE_PANEL_WIDTH);
        if (unit != null) {
            width -= this.font.width(unit) + EmitterManagerTerminalLayout.TEXT_FIELD_PADDING;
        }
        this.primaryValue.setWidth(width);
        this.secondaryValue.setWidth(width);
    }

    private void setControlsEnabled(@Nullable EmitterManagerTerminalMenu.Entry entry) {
        boolean selected = entry != null;
        boolean threshold = selected && entry.function() == EmitterManagerTerminalMenu.Function.THRESHOLD;
        boolean valueMode = selected && !entry.craftingCard();
        setFieldEnabled(this.primaryValue, valueMode);
        setFieldEnabled(this.secondaryValue, valueMode && threshold);
        this.locateButton.active = selected;
        this.redstoneMode.setVisibility(valueMode && entry.settingValue(Settings.REDSTONE_EMITTER.getName()) != null);
        this.craftingMode.setVisibility(
                selected && entry.craftingCard() &&
                        entry.settingValue(Settings.CRAFT_VIA_REDSTONE.getName()) != null);
        this.fuzzyMode.setVisibility(
                selected && entry.fuzzyCard() && entry.settingValue(Settings.FUZZY_MODE.getName()) != null);
        layoutButtonRow();
    }

    /** Packs the visible buttons left-to-right so hidden toggles do not leave gaps in the row. */
    private void layoutButtonRow() {
        int x = EmitterManagerTerminalLayout.BUTTON_ROW_X;
        for (SettingToggleButton<?> button : List.of(this.redstoneMode, this.craftingMode, this.fuzzyMode)) {
            if (button.visible) {
                button.setPosition(this.leftPos + x, this.topPos + EmitterManagerTerminalLayout.SETTINGS_ROW_Y);
                x += EmitterManagerTerminalLayout.BUTTON_SPACING;
            }
        }
        this.locateButton.setPosition(
                this.leftPos + x,
                this.topPos + EmitterManagerTerminalLayout.SETTINGS_ROW_Y);
    }

    private static void setFieldEnabled(EditBox field, boolean enabled) {
        field.active = enabled;
        field.setEditable(enabled);
        field.visible = enabled;
    }

    private <T extends Enum<T>> void setSetting(SettingToggleButton<T> button, T value) {
        EmitterManagerTerminalMenu.Entry entry = findSelectedEntry();
        if (entry == null) {
            return;
        }
        button.set(value);
        WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SetEmitterSettingPacket(
                this.menu.containerId,
                entry.address(),
                button.getSetting().getName(),
                value.name()));
    }

    private static <T extends Enum<T>> void setSettingButton(SettingToggleButton<T> button,
                                                             @Nullable String valueName, Class<T> enumClass) {
        if (valueName == null) {
            return;
        }
        try {
            button.set(Enum.valueOf(enumClass, valueName));
        } catch (IllegalArgumentException ignored) {
            // Add-ons may expose enum values unknown to this client version.
        }
    }

    private void applyValues() {
        EmitterManagerTerminalMenu.Entry entry = findSelectedEntry();
        if (entry == null || entry.craftingCard()) {
            return;
        }
        if (entry.function() == EmitterManagerTerminalMenu.Function.THRESHOLD) {
            parseValue(this.primaryValue, entry).ifPresent(value -> sendValue(
                    entry,
                    EmitterManagerTerminalMenu.ValueKind.UPPER_THRESHOLD,
                    value));
            parseValue(this.secondaryValue, entry).ifPresent(value -> sendValue(
                    entry,
                    EmitterManagerTerminalMenu.ValueKind.LOWER_THRESHOLD,
                    value));
        } else {
            parseValue(this.primaryValue, entry).ifPresent(value -> sendValue(
                    entry,
                    EmitterManagerTerminalMenu.ValueKind.REPORTING,
                    value));
        }
    }

    private static OptionalLong parseValue(EditBox field, EmitterManagerTerminalMenu.Entry entry) {
        try {
            String text = field.getValue().trim();
            long value;
            if (entry.configuredKey() instanceof AEFluidKey fluidKey) {
                BigDecimal displayValue = new BigDecimal(text);
                if (displayValue.signum() < 0) {
                    return OptionalLong.empty();
                }
                value = displayValue
                        .multiply(BigDecimal.valueOf(fluidKey.getAmountPerUnit()))
                        .setScale(0, RoundingMode.UP)
                        .longValueExact();
            } else {
                value = Long.parseLong(text);
            }
            return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
        } catch (ArithmeticException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private void sendValue(EmitterManagerTerminalMenu.Entry entry, EmitterManagerTerminalMenu.ValueKind kind,
                           long value) {
        WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SetEmitterValuePacket(
                this.menu.containerId,
                entry.address(),
                kind,
                value));
    }

    private void locateSelected() {
        EmitterManagerTerminalMenu.Entry entry = findSelectedEntry();
        if (entry == null) {
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.address().dimension());
        BlockHighlightHandler.highlight(
                entry.address().pos(),
                entry.address().side(),
                dimension,
                System.currentTimeMillis() + HIGHLIGHT_DURATION_MILLIS,
                new AABB(entry.address().pos()));
    }

    private @Nullable EmitterManagerTerminalMenu.Entry entryAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        if (localX < EmitterManagerTerminalLayout.LIST_X ||
                localX >= EmitterManagerTerminalLayout.LIST_X + EmitterManagerTerminalLayout.LIST_CONTENT_WIDTH ||
                localY < EmitterManagerTerminalLayout.LIST_Y ||
                localY >= EmitterManagerTerminalLayout.LIST_Y +
                        EmitterManagerTerminalLayout.VISIBLE_ROWS * EmitterManagerTerminalLayout.LIST_ROW_HEIGHT) {
            return null;
        }
        List<EmitterManagerTerminalMenu.Entry> entries = getVisibleEntries();
        int index = this.scrollOffset +
                (localY - EmitterManagerTerminalLayout.LIST_Y) / EmitterManagerTerminalLayout.LIST_ROW_HEIGHT;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private @Nullable EmitterManagerTerminalMenu.Entry findSelectedEntry() {
        if (this.selectedAddress == null) {
            return null;
        }
        for (EmitterManagerTerminalMenu.Entry entry : this.menu.getEntries()) {
            if (entry.address().equals(this.selectedAddress)) {
                return entry;
            }
        }
        return null;
    }

    private List<EmitterManagerTerminalMenu.Entry> getVisibleEntries() {
        if (this.searchField == null) {
            return this.menu.getEntries();
        }
        String query = this.searchField.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return this.menu.getEntries();
        }
        return this.menu.getEntries().stream().filter(entry -> matches(entry, query)).toList();
    }

    private static boolean matches(EmitterManagerTerminalMenu.Entry entry, String query) {
        if (UniversalSearch.contains(entry.name().getString(), query) ||
                UniversalSearch.contains(entry.function().name(), query) ||
                UniversalSearch.contains(entry.address().dimension().toString(), query)) {
            return true;
        }
        if (entry.configuredKey() != null &&
                (UniversalSearch.contains(entry.configuredKey().getDisplayName().getString(), query) ||
                        UniversalSearch.contains(entry.configuredKey().getId().toString(), query))) {
            return true;
        }
        String position = entry.address().pos().getX() + " " + entry.address().pos().getY() + " " +
                entry.address().pos().getZ();
        return position.contains(query);
    }

    private static boolean containsAddress(List<EmitterManagerTerminalMenu.Entry> entries,
                                           EmitterManagerTerminalMenu.Address address) {
        for (EmitterManagerTerminalMenu.Entry entry : entries) {
            if (entry.address().equals(address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isThresholdSelected() {
        EmitterManagerTerminalMenu.Entry entry = findSelectedEntry();
        return entry != null && entry.function() == EmitterManagerTerminalMenu.Function.THRESHOLD;
    }

    private Component functionText(EmitterManagerTerminalMenu.Entry entry) {
        String suffix = entry.craftingCard() ? "crafting" : entry.function().name().toLowerCase(Locale.ROOT);
        return Component.translatable("label.gtlcore.emitter_manager_terminal.function." + suffix);
    }

    private static Component formatAmount(EmitterManagerTerminalMenu.Entry entry, long amount) {
        AEKey key = entry.configuredKey();
        if (key instanceof AEFluidKey fluidKey) {
            return Component.literal(fluidKey.formatAmount(amount, AmountFormat.FULL));
        }
        if (key != null) {
            return Component.literal(key.formatAmount(amount, AmountFormat.SLOT));
        }
        if (entry.function() == EmitterManagerTerminalMenu.Function.ENERGY) {
            return Component.translatable("gui.gtlcore.emitter_manager_terminal.energy_amount",
                    formatCompact(amount));
        }
        return Component.literal(formatCompact(amount));
    }

    private static String formatEditableValue(EmitterManagerTerminalMenu.Entry entry, long amount) {
        if (entry.configuredKey() instanceof AEFluidKey fluidKey) {
            return BigDecimal.valueOf(amount)
                    .divide(BigDecimal.valueOf(fluidKey.getAmountPerUnit()))
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return Long.toString(amount);
    }

    private static @Nullable String getValueUnit(@Nullable EmitterManagerTerminalMenu.Entry entry) {
        return entry != null && entry.configuredKey() instanceof AEFluidKey fluidKey ?
                fluidKey.getUnitSymbol() :
                null;
    }

    private static String formatCompact(long amount) {
        if (amount >= 1_000_000_000_000L) return String.format("%.2fT", amount / 1_000_000_000_000.0);
        if (amount >= 1_000_000_000L) return String.format("%.2fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000L) return String.format("%.2fM", amount / 1_000_000.0);
        if (amount >= 1_000L) return String.format("%.2fK", amount / 1_000.0);
        return Long.toString(amount);
    }

    private static final class HighlightEmitterButton extends IconButton {

        private final Component tooltip;

        private HighlightEmitterButton(Component tooltip, Button.OnPress onPress) {
            super(onPress);
            this.tooltip = tooltip;
        }

        @Override
        protected Icon getIcon() {
            return Icon.TOOLBAR_BUTTON_BACKGROUND;
        }

        @Override
        protected Item getItemOverlay() {
            return AEItems.WIRELESS_RECEIVER.asItem();
        }

        @Override
        public List<Component> getTooltipMessage() {
            return List.of(this.tooltip);
        }
    }

    private record ControlState(EmitterManagerTerminalMenu.Address address,
                                EmitterManagerTerminalMenu.Function function,
                                @Nullable AEKey configuredKey, long reportingValue, long lowerValue, long upperValue,
                                boolean craftingCard, boolean fuzzyCard,
                                List<EmitterManagerTerminalMenu.SettingValue> settings) {

        private static @Nullable ControlState of(@Nullable EmitterManagerTerminalMenu.Entry entry) {
            return entry == null ? null : new ControlState(
                    entry.address(),
                    entry.function(),
                    entry.configuredKey(),
                    entry.reportingValue(),
                    entry.lowerValue(),
                    entry.upperValue(),
                    entry.craftingCard(),
                    entry.fuzzyCard(),
                    entry.settings());
        }
    }
}
