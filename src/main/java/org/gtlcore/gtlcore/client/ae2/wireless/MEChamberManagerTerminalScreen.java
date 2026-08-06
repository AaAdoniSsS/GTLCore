package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.api.gui.TagFilterConfigurator;
import org.gtlcore.gtlcore.client.renderer.BlockHighlightHandler;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberConfigurator;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalLayout;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.jei.JeiMissingIngredientBookmarks;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MEChamberManagerTerminalScreen extends AEBaseScreen<MEChamberManagerTerminalMenu> {

    private static final int SEARCH_MAX_LENGTH = 80;
    private static final int SLOT_BACKGROUND_OFFSET = 1;
    private static final int ICON_Y_OFFSET = 3;
    private static final int TITLE_Y_OFFSET = 3;
    private static final int CONTROLLER_Y_OFFSET = 12;
    private static final int NAME_X = MEChamberManagerTerminalLayout.LIST_X + 20;
    private static final int TEXT_WIDTH = MEChamberManagerTerminalLayout.LIST_CONTENT_WIDTH - 20;
    private static final int HIGHLIGHT_DURATION_MILLIS = 15_000;
    private static final TextTexture TAG_SORT_NAME_TEXTURE = new TextTexture("A-Z");
    private static final TextTexture TAG_SORT_AMOUNT_TEXTURE = new TextTexture("数量▼");
    private static final Comparator<MEChamberManagerTerminalMenu.Entry> ENTRY_ORDER = Comparator
            .comparing((MEChamberManagerTerminalMenu.Entry entry) -> entry.name().getString(),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.address().dimension().toString())
            .thenComparingInt(entry -> entry.address().pos().getX())
            .thenComparingInt(entry -> entry.address().pos().getY())
            .thenComparingInt(entry -> entry.address().pos().getZ());

    private EditBox searchField;
    private EditBox amountField;
    private EditBox priorityField;
    private Button previousPageButton;
    private Button nextPageButton;
    private ChamberControlButton workingButton;
    private ChamberControlButton distinctButton;
    private ChamberControlButton autoPullButton;
    private ChamberControlButton circuitButton;
    private ChamberControlButton countSortButton;
    private ChamberControlButton tagFilterButton;
    private ChamberControlButton syncOffsetButton;
    private ChamberControlButton itemBlacklistButton;
    private ChamberControlButton itemNbtButton;
    private ChamberControlButton fluidBlacklistButton;
    private ChamberControlButton fluidNbtButton;
    private @Nullable MEChamberManagerTerminalMenu.StorageKind selectedStorage;
    private int selectedSlot = -1;
    private int listScrollOffset;
    private int contentPageIndex;
    private boolean draggingListScrollbar;
    private final MEChamberConfiguratorOverlay configuratorOverlay;

    public MEChamberManagerTerminalScreen(MEChamberManagerTerminalMenu menu, Inventory inventory, Component title,
                                          ScreenStyle style) {
        super(menu, inventory, title, style);
        this.configuratorOverlay = new MEChamberConfiguratorOverlay(menu);
    }

    @Override
    protected void init() {
        configuratorOverlay.close();
        super.init();
        searchField = new EditBox(
                font,
                leftPos + MEChamberManagerTerminalLayout.SEARCH_X,
                topPos + MEChamberManagerTerminalLayout.SEARCH_Y,
                MEChamberManagerTerminalLayout.SEARCH_WIDTH,
                MEChamberManagerTerminalLayout.SEARCH_HEIGHT,
                Component.translatable("field.gtlcore.me_chamber_manager_terminal.search"));
        searchField.setMaxLength(SEARCH_MAX_LENGTH);
        searchField.setBordered(false);
        searchField.setTextColor(WirelessAeStyle.TEXT);
        searchField.setTextColorUneditable(WirelessAeStyle.MUTED_TEXT);
        searchField.setHint(Component.translatable("field.gtlcore.me_chamber_manager_terminal.search_hint"));
        searchField.setResponder(ignored -> listScrollOffset = 0);
        addRenderableWidget(searchField);
        amountField = new EditBox(
                font,
                leftPos + MEChamberManagerTerminalLayout.AMOUNT_INPUT_X,
                topPos + MEChamberManagerTerminalLayout.AMOUNT_INPUT_Y,
                MEChamberManagerTerminalLayout.AMOUNT_INPUT_WIDTH,
                MEChamberManagerTerminalLayout.AMOUNT_INPUT_HEIGHT,
                Component.translatable("field.gtlcore.me_chamber_manager_terminal.amount"));
        amountField.setMaxLength(Long.toString(Long.MAX_VALUE).length());
        amountField.setBordered(false);
        amountField.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        amountField.setTooltip(Tooltip.create(
                Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.confirm_amount")));
        addRenderableWidget(amountField);
        previousPageButton = WirelessAeStyle.button(
                leftPos + MEChamberManagerTerminalLayout.PREVIOUS_PAGE_X,
                topPos + MEChamberManagerTerminalLayout.PAGE_BUTTON_Y,
                MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE,
                MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE,
                Component.literal("<"),
                ignored -> changeContentPage(-1));
        addRenderableWidget(previousPageButton);
        nextPageButton = WirelessAeStyle.button(
                leftPos + MEChamberManagerTerminalLayout.NEXT_PAGE_X,
                topPos + MEChamberManagerTerminalLayout.PAGE_BUTTON_Y,
                MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE,
                MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE,
                Component.literal(">"),
                ignored -> changeContentPage(1));
        addRenderableWidget(nextPageButton);
        workingButton = addControlButton(
                MEChamberManagerTerminalLayout.WORKING_BUTTON_X,
                GuiTextures.BUTTON_POWER,
                true,
                false,
                "tooltip.gtlcore.me_chamber_manager_terminal.working",
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.WORKING));
        distinctButton = addControlButton(
                MEChamberManagerTerminalLayout.DISTINCT_BUTTON_X,
                GuiTextures.BUTTON_DISTINCT_BUSES,
                true,
                true,
                "tooltip.gtlcore.me_chamber_manager_terminal.distinct",
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.DISTINCT));
        autoPullButton = addControlButton(
                MEChamberManagerTerminalLayout.AUTO_PULL_BUTTON_X,
                GuiTextures.BUTTON_AUTO_PULL,
                true,
                false,
                "tooltip.gtlcore.me_chamber_manager_terminal.auto_pull",
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.AUTO_PULL));
        circuitButton = addControlButton(
                MEChamberManagerTerminalLayout.CIRCUIT_BUTTON_X,
                circuitConfigurator(menu.getSelectedDetails()).getIcon(),
                false,
                false,
                null,
                this::openCircuitConfigurator);
        countSortButton = addControlButton(
                MEChamberManagerTerminalLayout.COUNT_SORT_BUTTON_X,
                TAG_SORT_NAME_TEXTURE,
                false,
                false,
                null,
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.COUNT_SORT));
        tagFilterButton = addControlButton(
                MEChamberManagerTerminalLayout.TAG_FILTER_BUTTON_X,
                TagFilterConfigurator.ICON,
                false,
                false,
                null,
                this::openTagFilterConfigurator);
        syncOffsetButton = addControlButton(
                MEChamberManagerTerminalLayout.OFFSET_BUTTON_X,
                advancedMEConfigurator(menu.getSelectedDetails().syncOffset()).getIcon(),
                false,
                false,
                null,
                this::openAdvancedMEConfigurator);
        itemBlacklistButton = addControlButton(
                MEChamberManagerTerminalLayout.WORKING_BUTTON_X,
                GuiTextures.BUTTON_BLACKLIST,
                true,
                false,
                null,
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.ITEM_BLACKLIST));
        itemNbtButton = addControlButton(
                MEChamberManagerTerminalLayout.DISTINCT_BUTTON_X,
                GuiTextures.BUTTON_FILTER_NBT,
                true,
                false,
                null,
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.ITEM_NBT));
        fluidBlacklistButton = addControlButton(
                MEChamberManagerTerminalLayout.WORKING_BUTTON_X,
                GuiTextures.BUTTON_BLACKLIST,
                true,
                false,
                null,
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.FLUID_BLACKLIST));
        fluidNbtButton = addControlButton(
                MEChamberManagerTerminalLayout.DISTINCT_BUTTON_X,
                GuiTextures.BUTTON_FILTER_NBT,
                true,
                false,
                null,
                () -> toggleControl(MEChamberManagerTerminalMenu.ControlKind.FLUID_NBT));
        priorityField = new EditBox(
                font,
                leftPos + MEChamberManagerTerminalLayout.OFFSET_INPUT_X,
                topPos + MEChamberManagerTerminalLayout.OFFSET_INPUT_Y,
                MEChamberManagerTerminalLayout.OFFSET_INPUT_WIDTH,
                MEChamberManagerTerminalLayout.OFFSET_INPUT_HEIGHT,
                Component.translatable("field.gtlcore.me_chamber_manager_terminal.priority"));
        priorityField.setMaxLength(Integer.toString(Integer.MAX_VALUE).length());
        priorityField.setBordered(false);
        priorityField.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        priorityField.setTooltip(Tooltip.create(
                Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.confirm_priority")));
        addRenderableWidget(priorityField);
        updateAmountControls();
        updateDynamicControls();
    }

    @Override
    public void drawBG(@NotNull GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTick) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTick);
        WirelessAeStyle.drawTextField(
                graphics,
                leftPos + MEChamberManagerTerminalLayout.SEARCH_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.SEARCH_PANEL_Y,
                MEChamberManagerTerminalLayout.SEARCH_PANEL_WIDTH);
        WirelessAeStyle.drawInsetPanel(
                graphics,
                leftPos + MEChamberManagerTerminalLayout.LIST_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.LIST_PANEL_Y,
                MEChamberManagerTerminalLayout.LIST_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.LIST_PANEL_HEIGHT);
        drawContentPanel(graphics);
        if (hasContentPaging()) {
            BackgroundGenerator.draw(
                    MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                    MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE,
                    graphics,
                    leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                    topPos + MEChamberManagerTerminalLayout.PAGE_BUTTON_Y);
        }
        if (hasControlPanel()) {
            drawControlPanel(graphics, mouseX, mouseY);
        }
        if (isAmountEditable()) {
            drawAmountPanel(graphics);
        }
        drawPlayerInventorySlotBackgrounds(graphics);

        List<MEChamberManagerTerminalMenu.Entry> entries = visibleEntries();
        listScrollOffset = WirelessAeStyle.clampScrollOffset(
                listScrollOffset,
                entries.size(),
                MEChamberManagerTerminalLayout.VISIBLE_ROWS);
        WirelessAeStyle.drawAe2Scrollbar(
                graphics,
                leftPos + MEChamberManagerTerminalLayout.SCROLLBAR_X,
                topPos + MEChamberManagerTerminalLayout.SCROLLBAR_Y,
                MEChamberManagerTerminalLayout.SCROLLBAR_HEIGHT,
                entries.size(),
                MEChamberManagerTerminalLayout.VISIBLE_ROWS,
                listScrollOffset);
    }

    @Override
    public void drawFG(@NotNull GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, title, MEChamberManagerTerminalLayout.TITLE_X, MEChamberManagerTerminalLayout.TITLE_Y,
                WirelessAeStyle.TEXT, false);
        graphics.drawString(
                font,
                playerInventoryTitle,
                MEChamberManagerTerminalLayout.PLAYER_INVENTORY_LABEL_X,
                MEChamberManagerTerminalLayout.PLAYER_INVENTORY_LABEL_Y,
                WirelessAeStyle.TEXT,
                false);

        List<MEChamberManagerTerminalMenu.Entry> entries = visibleEntries();
        int rows = Math.min(MEChamberManagerTerminalLayout.VISIBLE_ROWS, Math.max(0, entries.size() - listScrollOffset));
        for (int row = 0; row < rows; row++) {
            drawEntry(graphics, entries.get(listScrollOffset + row),
                    MEChamberManagerTerminalLayout.LIST_Y + row * MEChamberManagerTerminalLayout.LIST_ROW_HEIGHT);
        }
        if (entries.isEmpty()) {
            WirelessAeStyle.drawTrimmedString(
                    graphics,
                    font,
                    Component.translatable("label.gtlcore.me_chamber_manager_terminal.empty"),
                    MEChamberManagerTerminalLayout.LIST_X + 2,
                    MEChamberManagerTerminalLayout.LIST_Y + TITLE_Y_OFFSET,
                    MEChamberManagerTerminalLayout.LIST_CONTENT_WIDTH - 4,
                    WirelessAeStyle.MUTED_TEXT);
        }
        drawContents(graphics);
        drawContentPageLabel(graphics);
        drawControlLabels(graphics);
        if (isAmountEditable()) {
            drawAmountLabel(graphics);
        }
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        List<Rect2i> zones = new ArrayList<>(super.getExclusionZones());
        int height = hasContentPaging() ?
                MEChamberManagerTerminalLayout.PAGE_BUTTON_Y +
                        MEChamberManagerTerminalLayout.PAGE_BUTTON_SIZE -
                        MEChamberManagerTerminalLayout.DETAIL_PANEL_Y :
                MEChamberManagerTerminalLayout.DETAIL_PANEL_HEIGHT;
        if (hasControlPanel()) {
            height = controlPanelY() +
                    MEChamberManagerTerminalLayout.CONTROL_PANEL_HEIGHT -
                    MEChamberManagerTerminalLayout.DETAIL_PANEL_Y;
        }
        if (isAmountEditable()) {
            height = amountPanelY() +
                    MEChamberManagerTerminalLayout.AMOUNT_PANEL_HEIGHT -
                    MEChamberManagerTerminalLayout.DETAIL_PANEL_Y;
        }
        zones.add(new Rect2i(
                leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_Y,
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                height));
        return zones;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateAmountControls();
        updateDynamicControls();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (configuratorOverlay.isOpen()) {
            configuratorOverlay.render(graphics, width, height, mouseX, mouseY, partialTick);
        } else {
            renderContentTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        configuratorOverlay.tick();
    }

    @Override
    public void removed() {
        configuratorOverlay.close();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (configuratorOverlay.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleListClick(mouseX, mouseY, button) || handleContentClick(mouseX, mouseY, button) ||
                handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == minecraft.options.keyInventory.getKey().getValue()) {
            return true;
        }
        if (configuratorOverlay.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                configuratorOverlay.close();
                return true;
            }
            return configuratorOverlay.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (priorityField.isFocused() &&
                (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitPriority();
            return true;
        }
        if (amountField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitAmount();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (configuratorOverlay.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (configuratorOverlay.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (configuratorOverlay.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (draggingListScrollbar) {
            updateListScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (configuratorOverlay.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        boolean wasDragging = draggingListScrollbar;
        draggingListScrollbar = false;
        return wasDragging || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (configuratorOverlay.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_Y,
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.DETAIL_PANEL_HEIGHT)) {
            changeContentPage(-(int) Math.signum(delta));
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + MEChamberManagerTerminalLayout.LIST_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.LIST_PANEL_Y,
                MEChamberManagerTerminalLayout.LIST_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.LIST_PANEL_HEIGHT)) {
            listScrollOffset = WirelessAeStyle.clampScrollOffset(
                    listScrollOffset - (int) Math.signum(delta),
                    visibleEntries().size(),
                    MEChamberManagerTerminalLayout.VISIBLE_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawEntry(GuiGraphics graphics, MEChamberManagerTerminalMenu.Entry entry, int rowY) {
        graphics.renderItem(entry.icon(), MEChamberManagerTerminalLayout.LIST_X, rowY + ICON_Y_OFFSET);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                entry.name(),
                NAME_X,
                rowY + TITLE_Y_OFFSET,
                TEXT_WIDTH,
                WirelessAeStyle.TEXT);
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                controllerText(entry),
                NAME_X,
                rowY + CONTROLLER_Y_OFFSET,
                TEXT_WIDTH,
                WirelessAeStyle.MUTED_TEXT);
    }

    private void drawContentPanel(GuiGraphics graphics) {
        ContentPage page = currentContentPage();
        BackgroundGenerator.draw(
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.DETAIL_PANEL_HEIGHT,
                graphics,
                leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_Y);
        IGuiTexture slotTexture = page.storage() == MEChamberManagerTerminalMenu.StorageKind.FLUID ?
                GuiTextures.FLUID_SLOT :
                GuiTextures.SLOT;
        for (int row = 0; row < page.rows(); row++) {
            for (int column = 0; column < page.columns(); column++) {
                slotTexture.draw(
                        graphics,
                        0,
                        0,
                        leftPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_X +
                                column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                        topPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_Y +
                                row * MEChamberManagerTerminalLayout.SLOT_SIZE,
                        MEChamberManagerTerminalLayout.SLOT_SIZE,
                        MEChamberManagerTerminalLayout.SLOT_SIZE);
            }
        }
    }

    private void drawControlPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int verticalOffset = detailVerticalOffset();
        BackgroundGenerator.draw(
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.CONTROL_PANEL_HEIGHT,
                graphics,
                leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                topPos + controlPanelY());
        if (menu.getSelectedDetails().prioritySupported()) {
            WirelessAeStyle.drawTextField(
                    graphics,
                    leftPos + MEChamberManagerTerminalLayout.OFFSET_INPUT_X - 2,
                    topPos + MEChamberManagerTerminalLayout.OFFSET_INPUT_FRAME_Y + verticalOffset,
                    MEChamberManagerTerminalLayout.OFFSET_INPUT_WIDTH + 4);
        }
    }

    private void drawContentPageLabel(GuiGraphics graphics) {
        ContentPage page = currentContentPage();
        Component label = Component.translatable(pageLabelKey(page), page.storagePage() + 1, page.storagePages());
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                label,
                MEChamberManagerTerminalLayout.DETAIL_TITLE_X,
                MEChamberManagerTerminalLayout.DETAIL_TITLE_Y,
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH -
                        MEChamberManagerTerminalLayout.DETAIL_PANEL_PADDING * 2,
                WirelessAeStyle.TEXT);
        Component pageNumber = Component.translatable(
                "label.gtlcore.me_chamber_manager_terminal.page",
                contentPageIndex + 1,
                contentPages().size());
        if (!hasContentPaging()) {
            return;
        }
        int pageX = MEChamberManagerTerminalLayout.PAGE_LABEL_X +
                Math.max(0, (MEChamberManagerTerminalLayout.PAGE_LABEL_WIDTH - font.width(pageNumber)) / 2);
        graphics.drawString(
                font,
                pageNumber,
                pageX,
                MEChamberManagerTerminalLayout.PAGE_BUTTON_Y + 5,
                WirelessAeStyle.TEXT,
                false);
    }

    private void drawControlLabels(GuiGraphics graphics) {
        if (!hasControlPanel()) {
            return;
        }
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        int verticalOffset = detailVerticalOffset();
        Component status = Component.translatable(details.online() ?
                "label.gtlcore.me_chamber_manager_terminal.online" :
                "label.gtlcore.me_chamber_manager_terminal.offline");
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                status,
                MEChamberManagerTerminalLayout.STATUS_LABEL_X,
                MEChamberManagerTerminalLayout.STATUS_LABEL_Y + verticalOffset,
                details.prioritySupported() ?
                        MEChamberManagerTerminalLayout.STATUS_LABEL_WIDTH :
                        MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH -
                                MEChamberManagerTerminalLayout.DETAIL_PANEL_PADDING * 2,
                details.online() ? WirelessAeStyle.TEXT : WirelessAeStyle.MUTED_TEXT);
        if (details.prioritySupported()) {
            graphics.drawString(
                    font,
                    Component.translatable("field.gtlcore.me_chamber_manager_terminal.priority_short"),
                    MEChamberManagerTerminalLayout.OFFSET_LABEL_X,
                    MEChamberManagerTerminalLayout.STATUS_LABEL_Y + verticalOffset,
                    WirelessAeStyle.TEXT,
                    false);
        }
    }

    private void drawAmountPanel(GuiGraphics graphics) {
        BackgroundGenerator.draw(
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH,
                MEChamberManagerTerminalLayout.AMOUNT_PANEL_HEIGHT,
                graphics,
                leftPos + MEChamberManagerTerminalLayout.DETAIL_PANEL_X,
                topPos + amountPanelY());
        WirelessAeStyle.drawTextField(
                graphics,
                leftPos + MEChamberManagerTerminalLayout.AMOUNT_INPUT_X - 2,
                topPos + MEChamberManagerTerminalLayout.AMOUNT_INPUT_FRAME_Y + detailVerticalOffset(),
                MEChamberManagerTerminalLayout.AMOUNT_INPUT_WIDTH + 4);
    }

    private void drawAmountLabel(GuiGraphics graphics) {
        MEChamberManagerTerminalMenu.SlotContent content = selectedContent();
        Component label;
        if (content == null) {
            label = Component.translatable("label.gtlcore.me_chamber_manager_terminal.select_slot");
        } else if (content.mode() == MEChamberManagerTerminalMenu.SlotMode.STOCKING) {
            label = Component.translatable("label.gtlcore.me_chamber_manager_terminal.stocking_mark");
        } else if (content.mode() == MEChamberManagerTerminalMenu.SlotMode.BUFFER) {
            label = Component.translatable("label.gtlcore.me_chamber_manager_terminal.output_buffer");
        } else if (content.storage() == MEChamberManagerTerminalMenu.StorageKind.FLUID) {
            label = Component.translatable("label.gtlcore.me_chamber_manager_terminal.amount_fluid");
        } else {
            label = Component.translatable("label.gtlcore.me_chamber_manager_terminal.amount_item");
        }
        WirelessAeStyle.drawTrimmedString(
                graphics,
                font,
                label,
                MEChamberManagerTerminalLayout.AMOUNT_LABEL_X,
                MEChamberManagerTerminalLayout.AMOUNT_LABEL_Y + detailVerticalOffset(),
                MEChamberManagerTerminalLayout.DETAIL_PANEL_WIDTH -
                        MEChamberManagerTerminalLayout.DETAIL_PANEL_PADDING * 2,
                WirelessAeStyle.TEXT);
    }

    private void drawPlayerInventorySlotBackgrounds(GuiGraphics graphics) {
        for (int row = 0; row < MEChamberManagerTerminalLayout.INVENTORY_ROWS; row++) {
            for (int column = 0; column < MEChamberManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
                drawSlotBackground(
                        graphics,
                        MEChamberManagerTerminalLayout.PLAYER_INVENTORY_X +
                                column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                        MEChamberManagerTerminalLayout.PLAYER_INVENTORY_Y +
                                row * MEChamberManagerTerminalLayout.SLOT_SIZE);
            }
        }
        for (int column = 0; column < MEChamberManagerTerminalLayout.INVENTORY_COLUMNS; column++) {
            drawSlotBackground(
                    graphics,
                    MEChamberManagerTerminalLayout.PLAYER_INVENTORY_X +
                            column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                    MEChamberManagerTerminalLayout.PLAYER_HOTBAR_Y);
        }
    }

    private void drawSlotBackground(GuiGraphics graphics, int slotX, int slotY) {
        Icon.SLOT_BACKGROUND.getBlitter().copy()
                .dest(leftPos + slotX - SLOT_BACKGROUND_OFFSET, topPos + slotY - SLOT_BACKGROUND_OFFSET)
                .blit(graphics);
    }

    private void drawContents(GuiGraphics graphics) {
        ContentPage page = currentContentPage();
        List<MEChamberManagerTerminalMenu.SlotContent> contents = page.contents();
        for (int index = 0; index < contents.size(); index++) {
            MEChamberManagerTerminalMenu.SlotContent content = contents.get(index);
            if (content.key() == null) {
                continue;
            }
            int column = index % page.columns();
            int row = index / page.columns();
            int x = MEChamberManagerTerminalLayout.DETAIL_SLOT_X + column * MEChamberManagerTerminalLayout.SLOT_SIZE;
            int y = MEChamberManagerTerminalLayout.DETAIL_SLOT_Y + row * MEChamberManagerTerminalLayout.SLOT_SIZE;
            x += MEChamberManagerTerminalLayout.DETAIL_CONTENT_INSET;
            y += MEChamberManagerTerminalLayout.DETAIL_CONTENT_INSET;
            graphics.renderItem(content.key().wrapForDisplayOrFilter(), x, y);
            if (content.mode() != MEChamberManagerTerminalMenu.SlotMode.STOCKING && content.amount() > 0L) {
                graphics.renderItemDecorations(
                        font,
                        content.key().wrapForDisplayOrFilter(),
                        x,
                        y,
                        content.key().formatAmount(content.amount(), AmountFormat.SLOT));
            }
        }
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        MEChamberManagerTerminalMenu.Entry entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return false;
        }
        if (button == 1) {
            highlight(entry);
            return true;
        }
        if (button == 0) {
            contentPageIndex = 0;
            selectedStorage = null;
            selectedSlot = -1;
            menu.setSelectedContents(
                    entry.address(), List.of(), MEChamberManagerTerminalMenu.ChamberDetails.EMPTY);
            WirelessAePackets.CHANNEL.sendToServer(
                    new WirelessAePackets.SelectMEChamberPacket(menu.containerId, entry.address()));
            return true;
        }
        return false;
    }

    private boolean handleContentClick(double mouseX, double mouseY, int button) {
        ContentPage page = currentContentPage();
        if ((button != 0 && button != 1) || !isInside(mouseX, mouseY,
                leftPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_Y,
                page.columns() * MEChamberManagerTerminalLayout.SLOT_SIZE,
                page.rows() * MEChamberManagerTerminalLayout.SLOT_SIZE)) {
            return false;
        }
        int column = (int) (mouseX - leftPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_X) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        int row = (int) (mouseY - topPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_Y) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        int index = row * page.columns() + column;
        List<MEChamberManagerTerminalMenu.SlotContent> contents = page.contents();
        if (index >= 0 && index < contents.size()) {
            MEChamberManagerTerminalMenu.SlotContent content = contents.get(index);
            selectedStorage = content.storage();
            selectedSlot = content.slot();
            amountField.setFocused(false);
            if (content.mode() == MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE ||
                    content.mode() == MEChamberManagerTerminalMenu.SlotMode.STOCKING ||
                    content.mode() == MEChamberManagerTerminalMenu.SlotMode.FILTER) {
                if (button == 1) {
                    sendSlotConfig(content, null);
                } else {
                    AEKey carriedKey = carriedKey(content.storage());
                    if (carriedKey != null) {
                        sendSlotConfig(content, carriedKey);
                    }
                }
            }
            updateAmountControls();
        }
        return true;
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY) {
        if (isInside(mouseX, mouseY, leftPos + MEChamberManagerTerminalLayout.SCROLLBAR_X,
                topPos + MEChamberManagerTerminalLayout.SCROLLBAR_Y, WirelessAeStyle.AE2_SCROLLBAR_WIDTH,
                MEChamberManagerTerminalLayout.SCROLLBAR_HEIGHT)) {
            draggingListScrollbar = true;
            updateListScrollFromMouse(mouseY);
            return true;
        }
        return false;
    }

    private void updateListScrollFromMouse(double mouseY) {
        listScrollOffset = WirelessAeStyle.ae2ScrollbarOffsetFromMouse(
                mouseY,
                topPos + MEChamberManagerTerminalLayout.SCROLLBAR_Y,
                MEChamberManagerTerminalLayout.SCROLLBAR_HEIGHT,
                visibleEntries().size(),
                MEChamberManagerTerminalLayout.VISIBLE_ROWS);
    }

    private List<MEChamberManagerTerminalMenu.Entry> visibleEntries() {
        String search = searchField == null ? "" : searchField.getValue().trim();
        List<MEChamberManagerTerminalMenu.Entry> entries = new ArrayList<>();
        for (MEChamberManagerTerminalMenu.Entry entry : menu.getEntries()) {
            if (search.isEmpty() || matches(entry, search)) {
                entries.add(entry);
            }
        }
        entries.sort(ENTRY_ORDER);
        return entries;
    }

    private List<ContentPage> contentPages() {
        List<ContentPage> pages = new ArrayList<>();
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        if (details.view() == MEChamberManagerTerminalMenu.ChamberView.EXTENDED_OUTPUT) {
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.ITEM,
                    MEChamberManagerTerminalMenu.SlotMode.BUFFER, details.itemStorage());
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.FLUID,
                    MEChamberManagerTerminalMenu.SlotMode.BUFFER, details.fluidStorage());
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.ITEM,
                    MEChamberManagerTerminalMenu.SlotMode.FILTER, details.outputFilterSupported());
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.FLUID,
                    MEChamberManagerTerminalMenu.SlotMode.FILTER, details.outputFilterSupported());
        } else {
            MEChamberManagerTerminalMenu.SlotMode mode = defaultSlotMode(details.view());
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.ITEM, mode, details.itemStorage());
            addContentPages(pages, MEChamberManagerTerminalMenu.StorageKind.FLUID, mode, details.fluidStorage());
        }
        if (pages.isEmpty()) {
            MEChamberManagerTerminalMenu.SlotMode mode = defaultSlotMode(details.view());
            pages.add(new ContentPage(
                    MEChamberManagerTerminalMenu.StorageKind.ITEM,
                    mode,
                    0,
                    1,
                    columnsFor(mode),
                    rowsFor(mode),
                    List.of()));
        }
        return pages;
    }

    private void addContentPages(List<ContentPage> pages, MEChamberManagerTerminalMenu.StorageKind storage,
                                 MEChamberManagerTerminalMenu.SlotMode mode, boolean supported) {
        if (!supported) {
            return;
        }
        List<MEChamberManagerTerminalMenu.SlotContent> storageContents = menu.getSelectedContents().stream()
                .filter(content -> content.storage() == storage && content.mode() == mode)
                .toList();
        int columns = columnsFor(mode);
        int rows = rowsFor(mode);
        int pageSize = columns * rows;
        int pageCount = Math.max(1, (storageContents.size() + pageSize - 1) / pageSize);
        for (int page = 0; page < pageCount; page++) {
            int from = page * pageSize;
            int to = Math.min(storageContents.size(), from + pageSize);
            pages.add(new ContentPage(
                    storage,
                    mode,
                    page,
                    pageCount,
                    columns,
                    rows,
                    from < to ? List.copyOf(storageContents.subList(from, to)) : List.of()));
        }
    }

    private ContentPage currentContentPage() {
        List<ContentPage> pages = contentPages();
        contentPageIndex = Math.max(0, Math.min(contentPageIndex, pages.size() - 1));
        return pages.get(contentPageIndex);
    }

    private void changeContentPage(int delta) {
        List<ContentPage> pages = contentPages();
        contentPageIndex = Math.max(0, Math.min(contentPageIndex + delta, pages.size() - 1));
        selectedStorage = null;
        selectedSlot = -1;
        amountField.setFocused(false);
    }

    private static MEChamberManagerTerminalMenu.SlotMode defaultSlotMode(
                                                                         MEChamberManagerTerminalMenu.ChamberView view) {
        return switch (view) {
            case INPUT -> MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE;
            case STOCKING -> MEChamberManagerTerminalMenu.SlotMode.STOCKING;
            case OUTPUT, OUTPUT_ASSEMBLY, EXTENDED_OUTPUT -> MEChamberManagerTerminalMenu.SlotMode.BUFFER;
        };
    }

    private static int columnsFor(MEChamberManagerTerminalMenu.SlotMode mode) {
        return mode == MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE ||
                mode == MEChamberManagerTerminalMenu.SlotMode.STOCKING ?
                        MEChamberManagerTerminalLayout.CONFIG_COLUMNS :
                        MEChamberManagerTerminalLayout.OUTPUT_COLUMNS;
    }

    private static int rowsFor(MEChamberManagerTerminalMenu.SlotMode mode) {
        return mode == MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE ||
                mode == MEChamberManagerTerminalMenu.SlotMode.STOCKING ?
                        MEChamberManagerTerminalLayout.CONFIG_ROWS :
                        MEChamberManagerTerminalLayout.OUTPUT_ROWS;
    }

    private static String pageLabelKey(ContentPage page) {
        String mode = switch (page.mode()) {
            case CONFIGURABLE -> "input";
            case STOCKING -> "stocking";
            case BUFFER -> "output";
            case FILTER -> "filter";
        };
        String storage = page.storage() == MEChamberManagerTerminalMenu.StorageKind.ITEM ? "item" : "fluid";
        return "label.gtlcore.me_chamber_manager_terminal.page." + mode + "." + storage;
    }

    private static boolean matches(MEChamberManagerTerminalMenu.Entry entry, String search) {
        return UniversalSearch.contains(entry.name().getString(), search) ||
                UniversalSearch.contains(entry.address().pos().toShortString(), search) ||
                (entry.controllerName() != null &&
                        UniversalSearch.contains(entry.controllerName().getString(), search));
    }

    private @Nullable MEChamberManagerTerminalMenu.Entry entryAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY, leftPos + MEChamberManagerTerminalLayout.LIST_X,
                topPos + MEChamberManagerTerminalLayout.LIST_Y, MEChamberManagerTerminalLayout.LIST_CONTENT_WIDTH,
                MEChamberManagerTerminalLayout.VISIBLE_ROWS * MEChamberManagerTerminalLayout.LIST_ROW_HEIGHT)) {
            return null;
        }
        int row = (int) (mouseY - topPos - MEChamberManagerTerminalLayout.LIST_Y) /
                MEChamberManagerTerminalLayout.LIST_ROW_HEIGHT;
        List<MEChamberManagerTerminalMenu.Entry> entries = visibleEntries();
        int index = listScrollOffset + row;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ContentPage page = currentContentPage();
        if (!isInside(mouseX, mouseY, leftPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_Y,
                page.columns() * MEChamberManagerTerminalLayout.SLOT_SIZE,
                page.rows() * MEChamberManagerTerminalLayout.SLOT_SIZE)) {
            return;
        }
        int column = (mouseX - leftPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_X) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        int row = (mouseY - topPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_Y) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        int index = row * page.columns() + column;
        List<MEChamberManagerTerminalMenu.SlotContent> contents = page.contents();
        if (index < 0 || index >= contents.size()) {
            return;
        }
        MEChamberManagerTerminalMenu.SlotContent content = contents.get(index);
        if (content.key() != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(content.key().getDisplayName());
            if (content.mode() == MEChamberManagerTerminalMenu.SlotMode.STOCKING) {
                lines.add(Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.stocking_mark"));
            } else if (content.mode() != MEChamberManagerTerminalMenu.SlotMode.FILTER) {
                lines.add(Component.translatable("tooltip.gtlcore.me_chamber_manager_terminal.amount",
                        content.key().formatAmount(content.amount(), AmountFormat.SLOT)));
            }
            graphics.renderTooltip(
                    font,
                    lines.stream().map(Component::getVisualOrderText).toList(),
                    mouseX,
                    mouseY);
        }
    }

    private void updateDynamicControls() {
        List<ContentPage> pages = contentPages();
        contentPageIndex = Math.max(0, Math.min(contentPageIndex, pages.size() - 1));
        previousPageButton.visible = contentPageIndex > 0;
        previousPageButton.active = previousPageButton.visible;
        nextPageButton.visible = contentPageIndex + 1 < pages.size();
        nextPageButton.active = nextPageButton.visible;

        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        workingButton.visible = details.workingSupported();
        workingButton.setSelected(details.workingEnabled());
        workingButton.setTooltipText(Component.translatable(details.workingEnabled() ?
                "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled"));
        distinctButton.visible = details.distinctSupported();
        distinctButton.setSelected(details.distinct());
        distinctButton.setTooltipText(Component.translatable("gtceu.multiblock.universal.distinct")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                .append(Component.translatable(details.distinct() ?
                        "gtceu.multiblock.universal.distinct.yes" :
                        "gtceu.multiblock.universal.distinct.no")));
        autoPullButton.visible = details.autoPullSupported();
        autoPullButton.setSelected(details.autoPullMode() > 0);
        autoPullButton.setTooltipText(details.autoPullModeCount() == 4 ?
                Component.translatable("gtlcore.machine.me_dual_hatch_stock.turns." + details.autoPullMode()) :
                Component.translatable("gtceu.gui.me_bus.auto_pull_button"));
        circuitButton.visible = details.circuitSupported();
        circuitButton.setSelected(details.circuitConfiguration() > 0);
        CircuitFancyConfigurator circuitConfigurator = circuitConfigurator(details);
        circuitButton.setIcon(circuitConfigurator.getIcon());
        circuitButton.setTooltipText(circuitConfigurator.getTooltips());
        countSortButton.visible = details.tagFilterSupported();
        countSortButton.setSelected(details.countSort());
        countSortButton.setIcon(details.countSort() ? TAG_SORT_AMOUNT_TEXTURE : TAG_SORT_NAME_TEXTURE);
        countSortButton.setTooltipText(Component.translatable("tooltip.gtlcore.auto_pull_sort_mode"));
        tagFilterButton.visible = details.tagFilterSupported();
        tagFilterButton.setTooltipText(tagFilterConfigurator(details).getTooltips());
        syncOffsetButton.visible = details.syncOffsetSupported();
        syncOffsetButton.setTooltipText(advancedMEConfigurator(details.syncOffset()).getTooltips());
        ContentPage page = currentContentPage();
        boolean itemFilterPage = details.outputFilterSupported() &&
                page.mode() == MEChamberManagerTerminalMenu.SlotMode.FILTER &&
                page.storage() == MEChamberManagerTerminalMenu.StorageKind.ITEM;
        boolean fluidFilterPage = details.outputFilterSupported() &&
                page.mode() == MEChamberManagerTerminalMenu.SlotMode.FILTER &&
                page.storage() == MEChamberManagerTerminalMenu.StorageKind.FLUID;
        itemBlacklistButton.visible = itemFilterPage;
        itemBlacklistButton.setSelected(details.itemBlackList());
        itemNbtButton.visible = itemFilterPage;
        itemNbtButton.setSelected(details.ignoreItemNbt());
        fluidBlacklistButton.visible = fluidFilterPage;
        fluidBlacklistButton.setSelected(details.fluidBlackList());
        fluidNbtButton.visible = fluidFilterPage;
        fluidNbtButton.setSelected(details.ignoreFluidNbt());
        priorityField.visible = details.prioritySupported();
        if (!details.prioritySupported()) {
            priorityField.setFocused(false);
        }
        updateTagFilterValue(priorityField, Integer.toString(details.priority()));
        updateDetailControlLayout();
    }

    private void updateDetailControlLayout() {
        int verticalOffset = detailVerticalOffset();
        int nextButtonX = leftPos + MEChamberManagerTerminalLayout.WORKING_BUTTON_X;
        for (ChamberControlButton button : List.of(
                workingButton,
                distinctButton,
                autoPullButton,
                circuitButton,
                countSortButton,
                tagFilterButton,
                syncOffsetButton,
                itemBlacklistButton,
                itemNbtButton,
                fluidBlacklistButton,
                fluidNbtButton)) {
            button.setY(topPos + MEChamberManagerTerminalLayout.CONTROL_BUTTON_Y + verticalOffset);
            if (button.visible) {
                button.setX(nextButtonX);
                nextButtonX += MEChamberManagerTerminalLayout.CONTROL_BUTTON_SIZE +
                        MEChamberManagerTerminalLayout.CONTROL_BUTTON_GAP;
            }
        }
        priorityField.setY(topPos + MEChamberManagerTerminalLayout.OFFSET_INPUT_Y + verticalOffset);
        amountField.setY(topPos + MEChamberManagerTerminalLayout.AMOUNT_INPUT_Y + verticalOffset);
    }

    private boolean hasContentPaging() {
        return contentPages().size() > 1;
    }

    private int detailVerticalOffset() {
        return controlPanelY() - MEChamberManagerTerminalLayout.CONTROL_PANEL_Y;
    }

    private int controlPanelY() {
        return hasContentPaging() ?
                MEChamberManagerTerminalLayout.CONTROL_PANEL_Y :
                MEChamberManagerTerminalLayout.PAGE_BUTTON_Y;
    }

    private int amountPanelY() {
        return MEChamberManagerTerminalLayout.AMOUNT_PANEL_Y + detailVerticalOffset();
    }

    private ChamberControlButton addControlButton(int x, IGuiTexture icon, boolean stateTexture,
                                                  boolean selectedTop, @Nullable String tooltipKey, Runnable action) {
        Component tooltip = tooltipKey == null ? Component.empty() : Component.translatable(tooltipKey);
        ChamberControlButton button = new ChamberControlButton(
                leftPos + x,
                topPos + MEChamberManagerTerminalLayout.CONTROL_BUTTON_Y,
                icon,
                stateTexture,
                selectedTop,
                tooltip,
                ignored -> action.run());
        if (tooltipKey == null) {
            button.setTooltip(null);
        }
        addRenderableWidget(button);
        return button;
    }

    private static void updateTagFilterValue(EditBox field, String value) {
        if (!field.isFocused() && !field.getValue().equals(value)) {
            field.setValue(value);
        }
    }

    private static CircuitFancyConfigurator circuitConfigurator(
                                                                MEChamberManagerTerminalMenu.ChamberDetails details) {
        ItemStackTransfer circuit = new ItemStackTransfer(1);
        if (details.circuitSet()) {
            circuit.setStackInSlot(0, IntCircuitBehaviour.stack(details.circuitConfiguration()));
        }
        return new CircuitFancyConfigurator(circuit);
    }

    private static AdvancedMEConfigurator advancedMEConfigurator(int initialOffset) {
        int[] offset = { initialOffset };
        return new AdvancedMEConfigurator(value -> offset[0] = value, () -> offset[0]);
    }

    private static TagFilterConfigurator tagFilterConfigurator(
                                                               MEChamberManagerTerminalMenu.ChamberDetails details) {
        String[] whitelist = { details.tagWhite() };
        String[] blacklist = { details.tagBlack() };
        return new TagFilterConfigurator(
                () -> whitelist[0], value -> whitelist[0] = value,
                () -> blacklist[0], value -> blacklist[0] = value);
    }

    private void openCircuitConfigurator() {
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (!details.circuitSupported() || address == null) {
            return;
        }
        openConfigurator(address, MEChamberConfigurator.Kind.CIRCUIT);
    }

    private void openAdvancedMEConfigurator() {
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (!details.syncOffsetSupported() || address == null) {
            return;
        }
        openConfigurator(address, MEChamberConfigurator.Kind.SYNC_OFFSET);
    }

    private void openTagFilterConfigurator() {
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (!details.tagFilterSupported() || address == null) {
            return;
        }
        openConfigurator(address, MEChamberConfigurator.Kind.TAG_FILTER);
    }

    private void openConfigurator(MEChamberManagerTerminalMenu.Address address,
                                  MEChamberConfigurator.Kind kind) {
        configuratorOverlay.open(address, kind, menu.getSelectedDetails(), width, height);
    }

    private void submitPriority() {
        if (!menu.getSelectedDetails().prioritySupported()) {
            return;
        }
        try {
            sendControl(MEChamberManagerTerminalMenu.ControlKind.PRIORITY,
                    Integer.parseInt(priorityField.getValue()));
            priorityField.setFocused(false);
        } catch (NumberFormatException ignored) {
            // EditBox can briefly contain an incomplete value.
        }
    }

    private boolean hasControlPanel() {
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        return menu.getSelectedAddress() != null && (details.workingSupported() || details.distinctSupported() ||
                details.autoPullSupported() || details.circuitSupported() || details.tagFilterSupported() ||
                details.syncOffsetSupported() || details.outputFilterSupported() || details.prioritySupported());
    }

    private void toggleControl(MEChamberManagerTerminalMenu.ControlKind control) {
        MEChamberManagerTerminalMenu.ChamberDetails details = menu.getSelectedDetails();
        int value = switch (control) {
            case WORKING -> details.workingEnabled() ? 0 : 1;
            case DISTINCT -> details.distinct() ? 0 : 1;
            case AUTO_PULL -> (details.autoPullMode() + 1) % Math.max(1, details.autoPullModeCount());
            case COUNT_SORT -> details.countSort() ? 0 : 1;
            case PRIORITY -> details.priority();
            case ITEM_BLACKLIST -> details.itemBlackList() ? 0 : 1;
            case ITEM_NBT -> details.ignoreItemNbt() ? 0 : 1;
            case FLUID_BLACKLIST -> details.fluidBlackList() ? 0 : 1;
            case FLUID_NBT -> details.ignoreFluidNbt() ? 0 : 1;
        };
        sendControl(control, value);
    }

    private void sendControl(MEChamberManagerTerminalMenu.ControlKind control, int value) {
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (address != null) {
            sendControl(address, control, value);
        }
    }

    private void sendControl(MEChamberManagerTerminalMenu.Address address,
                             MEChamberManagerTerminalMenu.ControlKind control, int value) {
        WirelessAePackets.CHANNEL.sendToServer(
                new WirelessAePackets.SetMEChamberControlPacket(menu.containerId, address, control, value));
    }

    private @Nullable AEKey carriedKey(MEChamberManagerTerminalMenu.StorageKind storage) {
        var carried = menu.getCarried();
        if (carried.isEmpty()) {
            return null;
        }
        if (storage == MEChamberManagerTerminalMenu.StorageKind.ITEM) {
            return AEItemKey.of(carried);
        }
        return FluidUtil.getFluidContained(carried)
                .map(fluid -> (AEKey) AEFluidKey.of(fluid.getFluid()))
                .orElse(null);
    }

    private void sendSlotConfig(MEChamberManagerTerminalMenu.SlotContent content, @Nullable AEKey key) {
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (address != null) {
            WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SetMEChamberSlotConfigPacket(
                    menu.containerId,
                    address,
                    content.storage(),
                    content.slot(),
                    key));
        }
    }

    public Optional<IClickableIngredient<?>> getJeiClickableIngredientUnderMouse(double mouseX, double mouseY) {
        ContentPage page = currentContentPage();
        int index = contentIndexAt(page, mouseX, mouseY);
        if (index < 0 || index >= page.contents().size()) {
            return Optional.empty();
        }
        MEChamberManagerTerminalMenu.SlotContent content = page.contents().get(index);
        if (!isMarkable(content) || content.key() == null) {
            return Optional.empty();
        }
        return JeiMissingIngredientBookmarks.createClickableIngredient(
                content.key(), contentArea(page, index));
    }

    public <I> List<IGhostIngredientHandler.Target<I>> getJeiGhostIngredientTargets(
                                                                                    ITypedIngredient<I> ingredient) {
        Object value = ingredient.getIngredient();
        boolean item = value instanceof ItemStack;
        boolean fluid = value instanceof FluidStack;
        if (!item && !fluid) {
            return List.of();
        }

        ContentPage page = currentContentPage();
        List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
        for (int index = 0; index < page.contents().size(); index++) {
            MEChamberManagerTerminalMenu.SlotContent content = page.contents().get(index);
            if (!isMarkable(content) || item != (content.storage() == MEChamberManagerTerminalMenu.StorageKind.ITEM)) {
                continue;
            }
            Rect2i area = contentArea(page, index);
            targets.add(new IGhostIngredientHandler.Target<>() {

                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I accepted) {
                    if (accepted instanceof ItemStack stack && item) {
                        sendSlotConfig(content, AEItemKey.of(stack));
                    } else if (accepted instanceof FluidStack stack && fluid) {
                        sendSlotConfig(content, AEFluidKey.of(stack.getFluid()));
                    }
                }
            });
        }
        return targets;
    }

    private static boolean isMarkable(MEChamberManagerTerminalMenu.SlotContent content) {
        return content.mode() == MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE ||
                content.mode() == MEChamberManagerTerminalMenu.SlotMode.STOCKING ||
                content.mode() == MEChamberManagerTerminalMenu.SlotMode.FILTER;
    }

    private Rect2i contentArea(ContentPage page, int index) {
        int column = index % page.columns();
        int row = index / page.columns();
        return new Rect2i(
                leftPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_X +
                        column * MEChamberManagerTerminalLayout.SLOT_SIZE,
                topPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_Y +
                        row * MEChamberManagerTerminalLayout.SLOT_SIZE,
                MEChamberManagerTerminalLayout.SLOT_SIZE,
                MEChamberManagerTerminalLayout.SLOT_SIZE);
    }

    private int contentIndexAt(ContentPage page, double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY,
                leftPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_X,
                topPos + MEChamberManagerTerminalLayout.DETAIL_SLOT_Y,
                page.columns() * MEChamberManagerTerminalLayout.SLOT_SIZE,
                page.rows() * MEChamberManagerTerminalLayout.SLOT_SIZE)) {
            return -1;
        }
        int column = (int) (mouseX - leftPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_X) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        int row = (int) (mouseY - topPos - MEChamberManagerTerminalLayout.DETAIL_SLOT_Y) /
                MEChamberManagerTerminalLayout.SLOT_SIZE;
        return row * page.columns() + column;
    }

    private void updateAmountControls() {
        MEChamberManagerTerminalMenu.SlotContent content = selectedContent();
        boolean editable = isAmountEditable(content);
        amountField.visible = editable;
        amountField.setEditable(editable);
        if (!editable) {
            amountField.setFocused(false);
        }
        if (!amountField.isFocused()) {
            String amount = editable ? Long.toString(content.amount()) : "";
            if (!amountField.getValue().equals(amount)) {
                amountField.setValue(amount);
            }
        }
    }

    private boolean isAmountEditable() {
        return isAmountEditable(selectedContent());
    }

    private static boolean isAmountEditable(@Nullable MEChamberManagerTerminalMenu.SlotContent content) {
        return content != null && content.key() != null &&
                content.mode() == MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE;
    }

    private @Nullable MEChamberManagerTerminalMenu.SlotContent selectedContent() {
        if (selectedStorage == null || selectedSlot < 0) {
            return null;
        }
        for (MEChamberManagerTerminalMenu.SlotContent content : menu.getSelectedContents()) {
            if (content.storage() == selectedStorage && content.slot() == selectedSlot) {
                return content;
            }
        }
        return null;
    }

    private void submitAmount() {
        MEChamberManagerTerminalMenu.SlotContent content = selectedContent();
        MEChamberManagerTerminalMenu.Address address = menu.getSelectedAddress();
        if (content == null || address == null || content.mode() != MEChamberManagerTerminalMenu.SlotMode.CONFIGURABLE) {
            return;
        }
        try {
            long amount = Long.parseLong(amountField.getValue());
            if (amount > 0L) {
                WirelessAePackets.CHANNEL.sendToServer(new WirelessAePackets.SetMEChamberSlotAmountPacket(
                        menu.containerId,
                        address,
                        content.storage(),
                        content.slot(),
                        amount));
            }
        } catch (NumberFormatException ignored) {
            // EditBox can briefly contain an incomplete value.
        }
    }

    private static Component controllerText(MEChamberManagerTerminalMenu.Entry entry) {
        if (entry.controllerName() == null || entry.controllerPos() == null) {
            return Component.translatable("label.gtlcore.me_chamber_manager_terminal.unformed");
        }
        BlockPosLike pos = new BlockPosLike(entry.controllerPos());
        return Component.translatable("label.gtlcore.me_chamber_manager_terminal.controller", entry.controllerName(),
                pos.x(), pos.y(), pos.z());
    }

    private void highlight(MEChamberManagerTerminalMenu.Entry entry) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.address().dimension());
        BlockHighlightHandler.highlight(
                entry.address().pos(),
                entry.address().side(),
                dimension,
                System.currentTimeMillis() + HIGHLIGHT_DURATION_MILLIS,
                new AABB(entry.address().pos()));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final class ChamberControlButton extends Button {

        private IGuiTexture icon;
        private final boolean stateTexture;
        private final boolean selectedTop;
        private boolean selected;

        private ChamberControlButton(int x, int y, IGuiTexture icon, boolean stateTexture, boolean selectedTop,
                                     Component tooltip,
                                     OnPress onPress) {
            super(
                    x,
                    y,
                    MEChamberManagerTerminalLayout.CONTROL_BUTTON_SIZE,
                    MEChamberManagerTerminalLayout.CONTROL_BUTTON_SIZE,
                    tooltip,
                    onPress,
                    DEFAULT_NARRATION);
            this.icon = icon;
            this.stateTexture = stateTexture;
            this.selectedTop = selectedTop;
            if (!tooltip.getString().isEmpty()) {
                setTooltip(Tooltip.create(tooltip));
            }
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        private void setIcon(IGuiTexture icon) {
            this.icon = icon;
        }

        private void setTooltipText(Component tooltip) {
            setMessage(tooltip);
            setTooltip(Tooltip.create(tooltip));
        }

        private void setTooltipText(List<Component> lines) {
            Component tooltip = joinTooltipLines(lines);
            setMessage(tooltip);
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            IGuiTexture renderedIcon = stateTexture && icon instanceof ResourceTexture resource ?
                    resource.getSubTexture(0, selected == selectedTop ? 0 : 0.5, 1, 0.5) :
                    icon;
            if (stateTexture) {
                renderedIcon.draw(graphics, mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
            } else {
                GuiTextures.BUTTON.draw(
                        graphics,
                        mouseX,
                        mouseY,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight());
                renderedIcon.draw(
                        graphics,
                        mouseX,
                        mouseY,
                        getX() + 2,
                        getY() + 2,
                        getWidth() - 4,
                        getHeight() - 4);
            }
        }
    }

    private static Component joinTooltipLines(List<Component> lines) {
        var tooltip = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                tooltip.append("\n");
            }
            tooltip.append(lines.get(index));
        }
        return tooltip;
    }

    private record ContentPage(MEChamberManagerTerminalMenu.StorageKind storage,
                               MEChamberManagerTerminalMenu.SlotMode mode, int storagePage, int storagePages,
                               int columns, int rows,
                               List<MEChamberManagerTerminalMenu.SlotContent> contents) {}

    private record BlockPosLike(int x, int y, int z) {

        private BlockPosLike(net.minecraft.core.BlockPos pos) {
            this(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
