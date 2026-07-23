package org.gtlcore.gtlcore.integration.ae2.throughput;

public final class ThroughputMonitorTerminalLayout {

    public static final int IMAGE_WIDTH = 209;
    public static final int IMAGE_HEIGHT = 255;
    public static final int INVENTORY_COLUMNS = 9;
    public static final int INVENTORY_ROWS = 3;
    public static final int SLOT_SIZE = 18;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 8;
    public static final int SEARCH_PANEL_X = 8;
    public static final int SEARCH_PANEL_Y = 21;
    public static final int SEARCH_PANEL_WIDTH = 193;
    public static final int SEARCH_X = SEARCH_PANEL_X + 8;
    public static final int SEARCH_Y = SEARCH_PANEL_Y + 5;
    public static final int SEARCH_WIDTH = SEARCH_PANEL_WIDTH - 16;
    public static final int SEARCH_HEIGHT = 10;
    public static final int LIST_PANEL_X = 8;
    public static final int LIST_PANEL_Y = 44;
    public static final int LIST_PANEL_WIDTH = 193;
    public static final int LIST_PANEL_HEIGHT = 114;
    public static final int LIST_X = 10;
    public static final int LIST_Y = 46;
    public static final int LIST_ROW_HEIGHT = 22;
    public static final int VISIBLE_ROWS = 5;
    public static final int LIST_HEIGHT = VISIBLE_ROWS * LIST_ROW_HEIGHT;
    public static final int LIST_CONTENT_WIDTH = 181;
    public static final int SCROLLBAR_X = 194;
    public static final int SCROLLBAR_Y = 45;
    public static final int SCROLLBAR_HEIGHT = 112;
    public static final int EXPAND_BUTTON_X = 10;
    public static final int EXPAND_BUTTON_SIZE = 16;
    public static final int PLAYER_INVENTORY_LABEL_X = 22;
    public static final int PLAYER_INVENTORY_LABEL_Y = 162;
    public static final int PLAYER_INVENTORY_X = 23;
    public static final int PLAYER_INVENTORY_Y = 175;
    public static final int PLAYER_HOTBAR_Y = 234;
    public static final int PLAYER_INVENTORY_WIDTH = INVENTORY_COLUMNS * SLOT_SIZE;
    public static final int PLAYER_INVENTORY_HEIGHT = INVENTORY_ROWS * SLOT_SIZE;
    public static final int PLAYER_HOTBAR_HEIGHT = SLOT_SIZE;
    public static final int UNIVERSAL_TERMINAL_BUTTON_GAP = 2;
    public static final int LEFT_TOOLBAR_GAP = UNIVERSAL_TERMINAL_BUTTON_GAP;
    public static final int LEFT_TOOLBAR_BUTTON_STEP = 18;
    public static final int SORT_BY_BUTTON_Y = TITLE_Y;
    public static final int SORT_DIRECTION_BUTTON_Y = SORT_BY_BUTTON_Y + LEFT_TOOLBAR_BUTTON_STEP;
    public static final int UNIVERSAL_TERMINAL_BUTTON_Y = SORT_DIRECTION_BUTTON_Y + LEFT_TOOLBAR_BUTTON_STEP;
    public static final int UPDATE_INTERVAL_BUTTON_WIDTH = 32;
    public static final int UPDATE_INTERVAL_BUTTON_HEIGHT = 16;
    public static final int UPDATE_INTERVAL_BUTTON_X = IMAGE_WIDTH - TITLE_X - UPDATE_INTERVAL_BUTTON_WIDTH;
    public static final int UPDATE_INTERVAL_BUTTON_Y = 4;

    private ThroughputMonitorTerminalLayout() {}
}
