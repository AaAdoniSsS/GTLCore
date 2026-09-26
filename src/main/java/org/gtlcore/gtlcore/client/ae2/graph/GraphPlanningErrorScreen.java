package org.gtlcore.gtlcore.client.ae2.graph;

import org.gtlcore.gtlcore.integration.ae2.graph.GraphPlanMenu;

import net.minecraft.network.chat.Component;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;

/** A failed calculation has no old plan to submit; retry must calculate again. */
public final class GraphPlanningErrorScreen extends AESubScreen<CraftConfirmMenu, CraftConfirmScreen> {

    public GraphPlanningErrorScreen(CraftConfirmScreen parent, String messageKey) {
        super(parent, "/screens/craft_error.json");
        setTextContent("errorText", Component.translatable(messageKey));
        widgets.addButton("replan", Component.translatable("gui.back"), () -> {
            returnToParent();
            menu.goBack();
        });
        widgets.addButton("retry", GuiText.CraftErrorRetry.text(), () -> {
            returnToParent();
            ((GraphPlanMenu) menu).gtlcore$retryPlanning();
        });
        widgets.addButton("cancel", GuiText.Cancel.text(), this::onClose);
    }

    @Override
    protected void onReturnToParent() {
        menu.clearError();
    }
}
