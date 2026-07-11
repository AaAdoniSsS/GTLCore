package org.gtlcore.gtlcore.client.gui;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

import com.lowdragmc.lowdraglib.utils.Position;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class MEStorageConfiguratorTabLayout {

    public static final int TABS_PER_COLUMN = 4;

    private static final int TAB_GAP = 2;
    private static final Set<ConfiguratorPanel> ENABLED_PANELS = Collections.newSetFromMap(new WeakHashMap<>());

    private MEStorageConfiguratorTabLayout() {}

    public static void setEnabled(ConfiguratorPanel panel, boolean enabled) {
        if (enabled) {
            ENABLED_PANELS.add(panel);
        } else {
            ENABLED_PANELS.remove(panel);
        }
    }

    public static boolean isEnabled(ConfiguratorPanel panel) {
        return ENABLED_PANELS.contains(panel);
    }

    public static void arrange(ConfiguratorPanel panel) {
        if (!isEnabled(panel)) {
            return;
        }

        List<ConfiguratorPanel.Tab> tabs = panel.getTabs();
        ConfiguratorPanel.Tab expandedTab = panel.getExpanded();
        for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
            ConfiguratorPanel.Tab tab = tabs.get(tabIndex);
            if (tab != expandedTab) {
                tab.setSelfPosition(positionFor(panel, tabIndex));
            }
        }
    }

    public static Position positionFor(ConfiguratorPanel panel, int tabIndex) {
        int stride = panel.getTabSize() + TAB_GAP;
        int column = column(tabIndex);
        int tabCount = panel.getTabs().size();
        int tabsInColumn = Math.min(TABS_PER_COLUMN, tabCount - column * TABS_PER_COLUMN);
        int columnHeight = tabsInColumn * stride - TAB_GAP;
        int y = panel.getSize().height - columnHeight + row(tabIndex) * stride;
        return new Position(-column * stride, y);
    }

    public static boolean isMouseOverTab(ConfiguratorPanel panel, double mouseX, double mouseY) {
        return isEnabled(panel) && panel.getTabs().stream().anyMatch(tab -> tab.isMouseOverElement(mouseX, mouseY));
    }

    private static int column(int tabIndex) {
        return tabIndex / TABS_PER_COLUMN;
    }

    private static int row(int tabIndex) {
        return tabIndex % TABS_PER_COLUMN;
    }
}
