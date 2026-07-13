package org.gtlcore.gtlcore.api.crafting;

public interface IAutoExpandMenu {

    String ACTION_TOGGLE_AUTO_EXPAND = "gtlcore:toggle_auto_expand";
    int GUI_SYNC_AUTO_EXPAND = 8;

    boolean gtlcore$isAutoExpand();

    void gtlcore$toggleAutoExpand();
}
