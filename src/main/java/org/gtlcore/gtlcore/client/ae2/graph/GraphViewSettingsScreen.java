package org.gtlcore.gtlcore.client.ae2.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.widgets.AECheckbox;
import appeng.menu.me.crafting.CraftConfirmMenu;

public final class GraphViewSettingsScreen extends AESubScreen<CraftConfirmMenu, CraftingRingScreen> {

    private final AECheckbox compact, amounts, screenshot, missing;

    public GraphViewSettingsScreen(CraftingRingScreen parent) {
        super(parent, "/screens/gtl_crafting_view_settings.json");
        compact = widgets.addCheckbox("compact", text("compact_layout"), this::save);
        amounts = widgets.addCheckbox("amounts", text("show_amounts"), this::save);
        screenshot = widgets.addCheckbox("screenshot", text("screenshot_amounts"), this::save);
        missing = widgets.addCheckbox("missing", text("missing_default"), this::save);
        widgets.addButton("back", Component.translatable("gui.back"), this::returnToParent);
        var settings = GraphViewSettings.get();
        compact.setSelected(settings.compact);
        amounts.setSelected(settings.showAmounts);
        screenshot.setSelected(settings.screenshotAmounts);
        missing.setSelected(settings.missingOnlyByDefault);
    }

    private static Component text(String key) {
        return Component.translatable("gtlcore.ae.ring." + key);
    }

    private void save() {
        var settings = GraphViewSettings.get();
        settings.compact = compact.isSelected();
        settings.showAmounts = amounts.isSelected();
        settings.screenshotAmounts = screenshot.isSelected();
        settings.missingOnlyByDefault = missing.isSelected();
        settings.save();
        getParent().refreshTree();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        graphics.drawString(font, text("settings"), 12, 12, 0xFF40404C, false);
    }
}
