package org.gtlcore.gtlcore.mixin.ae2wtlib;

import org.gtlcore.gtlcore.integration.ae2.emitter.WirelessEmitterManagerTerminalItem;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.mari_023.ae2wtlib.wut.ItemWUT;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemWUT.class, remap = false)
public abstract class ItemWUTMixin {

    @ModifyExpressionValue(
                           method = "getUpgrades",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lde/mari_023/ae2wtlib/wut/ItemWUT;countInstalledTerminals(Lnet/minecraft/world/item/ItemStack;)I",
                                    remap = false))
    private int gtlcore$countUpgradeSlotTerminals(int terminalCount, ItemStack stack) {
        if (WUTHandler.hasTerminal(stack, WirelessEmitterManagerTerminalItem.TERMINAL_NAME)) {
            terminalCount--;
        }
        return Math.max(0, terminalCount);
    }
}
