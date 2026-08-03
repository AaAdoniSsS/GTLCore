package org.gtlcore.gtlcore.integration.ae2.emitter;

public final class EmitterManagerTerminalLayout {

    public static final int IMAGE_WIDTH = 286;
    public static final int IMAGE_HEIGHT = 244;
    public static final int INVENTORY_COLUMNS = 9;
    public static final int INVENTORY_ROWS = 3;
    public static final int SLOT_SIZE = 18;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 8;
    /** Inset of the editable area inside an AE2 text field frame. Mirrors {@code AETextField}'s padding. */
    public static final int TEXT_FIELD_PADDING = 2;
    /** Height of an AE2 text field frame; the editable area is 4px shorter. */
    public static final int TEXT_FIELD_HEIGHT = 12;
    public static final int SEARCH_PANEL_X = 8;
    public static final int SEARCH_PANEL_Y = 20;
    public static final int SEARCH_PANEL_WIDTH = 154;
    public static final int SEARCH_X = SEARCH_PANEL_X + TEXT_FIELD_PADDING;
    public static final int SEARCH_Y = SEARCH_PANEL_Y + TEXT_FIELD_PADDING;
    public static final int LIST_PANEL_X = 8;
    public static final int LIST_PANEL_Y = 36;
    public static final int LIST_PANEL_WIDTH = 154;
    public static final int LIST_PANEL_HEIGHT = 107;
    public static final int LIST_X = LIST_PANEL_X + 2;
    public static final int LIST_Y = LIST_PANEL_Y + 1;
    public static final int LIST_ROW_HEIGHT = 21;
    public static final int VISIBLE_ROWS = 5;
    public static final int LIST_CONTENT_WIDTH = 145;
    public static final int SCROLLBAR_X = LIST_PANEL_X + LIST_PANEL_WIDTH - 8;
    public static final int SCROLLBAR_Y = LIST_PANEL_Y + 1;
    public static final int SCROLLBAR_HEIGHT = LIST_PANEL_HEIGHT - 2;
    public static final int VALUE_PANEL_X = 170;
    public static final int VALUE_PANEL_WIDTH = 108;
    public static final int DETAIL_PANEL_X = VALUE_PANEL_X;
    public static final int DETAIL_PANEL_Y = 17;
    public static final int DETAIL_PANEL_WIDTH = VALUE_PANEL_WIDTH;
    public static final int DETAIL_PANEL_HEIGHT = 36;
    public static final int DETAIL_X = DETAIL_PANEL_X + 4;
    public static final int DETAIL_Y = DETAIL_PANEL_Y + 3;
    public static final int DETAIL_WIDTH = DETAIL_PANEL_WIDTH - 8;
    public static final int DETAIL_LINE_HEIGHT = 10;
    public static final int PRIMARY_LABEL_Y = 60;
    public static final int PRIMARY_INPUT_X = VALUE_PANEL_X;
    public static final int PRIMARY_INPUT_Y = 72;
    public static final int SECONDARY_INPUT_X = VALUE_PANEL_X;
    public static final int SECONDARY_INPUT_Y = 104;
    /** Vertical distance from an input frame's top to its label's top. */
    public static final int LABEL_GAP = 12;
    public static final int VALUE_INPUT_X_OFFSET = TEXT_FIELD_PADDING;
    public static final int VALUE_INPUT_Y_OFFSET = TEXT_FIELD_PADDING;
    public static final int VALUE_INPUT_HEIGHT = TEXT_FIELD_HEIGHT - TEXT_FIELD_PADDING * 2;
    /**
     * Upgrade-card slots on the right panel. Vanilla AE2 level emitters expose at most one
     * ({@code StorageLevelEmitterPart}; the energy emitter has none), but addons may raise that, so the
     * layout reserves {@link #MAX_UPGRADE_SLOTS} positions and only as many as the part reports are shown.
     */
    public static final int MAX_UPGRADE_SLOTS = 2;
    /** Y position of the settings-button row, the locate button, and the bottom-row slots. */
    public static final int SETTINGS_ROW_Y = 124;
    /**
     * The filter slot holding the item or fluid the emitter watches, pinned to the panel's right edge.
     * Shown for every emitter that has a config inventory (storage and threshold emitters do; the energy
     * emitter does not).
     */
    public static final int CONFIG_SLOT_X = VALUE_PANEL_X + VALUE_PANEL_WIDTH - SLOT_SIZE;
    public static final int CONFIG_SLOT_Y = SETTINGS_ROW_Y;
    /**
     * Cards share the bottom row with the buttons and sit left of the filter slot. The redstone and crafting
     * toggles are mutually exclusive, so at most three buttons render and the row never reaches
     * {@link #CARD_SLOT_X}.
     */
    public static final int CARD_SLOT_X = CONFIG_SLOT_X - MAX_UPGRADE_SLOTS * SLOT_SIZE;
    public static final int CARD_SLOT_Y = SETTINGS_ROW_Y;
    /** Buttons are packed left-to-right from here; hidden ones collapse so the row stays contiguous. */
    public static final int BUTTON_ROW_X = VALUE_PANEL_X;
    public static final int BUTTON_SIZE = 16;
    public static final int BUTTON_SPACING = 18;
    public static final int UNIVERSAL_TERMINAL_BUTTON_GAP = 2;
    public static final int UNIVERSAL_TERMINAL_BUTTON_Y = TITLE_Y;
    public static final int PLAYER_INVENTORY_LABEL_X = 62;
    public static final int PLAYER_INVENTORY_LABEL_Y = 150;
    public static final int PLAYER_INVENTORY_X = 62;
    public static final int PLAYER_INVENTORY_Y = 162;
    public static final int PLAYER_HOTBAR_Y = PLAYER_INVENTORY_Y + INVENTORY_ROWS * SLOT_SIZE + 4;

    private EmitterManagerTerminalLayout() {}
}
