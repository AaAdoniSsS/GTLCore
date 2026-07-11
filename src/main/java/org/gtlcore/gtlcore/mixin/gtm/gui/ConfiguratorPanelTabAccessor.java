package org.gtlcore.gtlcore.mixin.gtm.gui;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ConfiguratorPanel.Tab.class, remap = false)
public interface ConfiguratorPanelTabAccessor {

    @Invoker(value = "collapseTo", remap = false)
    void gtlcore$collapseTo(int x, int y);
}
