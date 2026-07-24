package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellItem;
import org.gtlcore.gtlcore.integration.ae2.tag.TagViewCellMenu;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import org.lwjgl.glfw.GLFW;

public final class TagViewCellScreen extends AEBaseScreen<TagViewCellMenu> {

    private static final String WHITELIST_WIDGET = "whitelist";
    private static final String BLACKLIST_WIDGET = "blacklist";
    private static final String DONE_WIDGET = "done";
    private static final String CANCEL_WIDGET = "cancel";

    private final AETextField whitelistField;
    private final AETextField blacklistField;

    public TagViewCellScreen(TagViewCellMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        this.whitelistField = this.widgets.addTextField(WHITELIST_WIDGET);
        this.whitelistField.setMaxLength(TagViewCellItem.MAX_EXPRESSION_LENGTH);
        this.whitelistField.setValue(menu.getInitialWhitelist());
        this.blacklistField = this.widgets.addTextField(BLACKLIST_WIDGET);
        this.blacklistField.setMaxLength(TagViewCellItem.MAX_EXPRESSION_LENGTH);
        this.blacklistField.setValue(menu.getInitialBlacklist());
        this.widgets.addButton(DONE_WIDGET, CommonComponents.GUI_DONE, ignored -> saveAndClose());
        this.widgets.addButton(CANCEL_WIDGET, CommonComponents.GUI_CANCEL, this::onClose);
    }

    @Override
    protected void init() {
        super.init();
        setFocused(this.whitelistField);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        if ((this.whitelistField.isFocused() || this.blacklistField.isFocused()) &&
                keyCode == this.minecraft.options.keyInventory.getKey().getValue()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveAndClose() {
        this.menu.saveFilter(this.whitelistField.getValue(), this.blacklistField.getValue());
        onClose();
    }
}
