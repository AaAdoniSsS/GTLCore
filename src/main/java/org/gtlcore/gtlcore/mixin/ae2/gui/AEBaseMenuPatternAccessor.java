package org.gtlcore.gtlcore.mixin.ae2.gui;

import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AEBaseMenu.class, remap = false)
public interface AEBaseMenuPatternAccessor {

    @Invoker("getActionHost")
    IActionHost gtlcore$getPatternActionHost();
}
