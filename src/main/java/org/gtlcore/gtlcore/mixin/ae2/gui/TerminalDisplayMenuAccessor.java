package org.gtlcore.gtlcore.mixin.ae2.gui;

import appeng.api.stacks.KeyCounter;
import appeng.menu.me.common.MEStorageMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MEStorageMenu.class)
public interface TerminalDisplayMenuAccessor {

    @Invoker(value = "getPreviousAvailableStacks", remap = false)
    KeyCounter gtlcore$displayStacks();
}
