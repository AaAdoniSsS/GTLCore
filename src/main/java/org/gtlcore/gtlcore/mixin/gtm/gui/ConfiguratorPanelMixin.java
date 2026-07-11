package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfiguratorPanel.class, remap = false)
public abstract class ConfiguratorPanelMixin {

    @Inject(method = "attachConfigurators", at = @At("TAIL"))
    private void gtlcore$arrangeMEStorageConfiguratorTabs(IFancyConfigurator[] configurators, CallbackInfo ci) {
        MEStorageConfiguratorTabLayout.arrange((ConfiguratorPanel) (Object) this);
    }

    @Redirect(method = { "collapseTab", "expandTab" },
              at = @At(value = "INVOKE",
                       target = "Lcom/gregtechceu/gtceu/api/gui/fancy/ConfiguratorPanel$Tab;collapseTo(II)V"))
    private void gtlcore$collapseMEStorageConfiguratorTab(ConfiguratorPanel.Tab tab, int x, int y) {
        ConfiguratorPanel panel = (ConfiguratorPanel) (Object) this;
        int tabIndex = panel.getTabs().indexOf(tab);
        if (MEStorageConfiguratorTabLayout.isEnabled(panel) && tabIndex >= 0) {
            var position = MEStorageConfiguratorTabLayout.positionFor(panel, tabIndex);
            ((ConfiguratorPanelTabAccessor) tab).gtlcore$collapseTo(position.x, position.y);
        } else {
            ((ConfiguratorPanelTabAccessor) tab).gtlcore$collapseTo(x, y);
        }
    }
}
