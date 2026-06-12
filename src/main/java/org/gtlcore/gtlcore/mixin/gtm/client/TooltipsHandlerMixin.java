package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.common.data.source_tooltip.SourceTooltip;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;

import com.gregtechceu.gtceu.client.TooltipsHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(TooltipsHandler.class)
public abstract class TooltipsHandlerMixin {

    @Inject(method = "appendTooltips",
            at = @At("HEAD"),
            remap = false)
    private static void appendCustomItemTooltips(ItemStack stack, TooltipFlag flag, List<Component> tooltips, CallbackInfo ci) {
        SourceTooltip.append(stack.getItem(), tooltips::add);
        if (WirelessAeNetworkRuntime.shouldShowQuickConnectTooltip(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
            tooltips.add(Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Inject(method = "appendFluidTooltips",
            at = @At("RETURN"),
            remap = false)
    private static void appendCustomFluidTooltips(Fluid fluid, long amount, Consumer<Component> tooltips, TooltipFlag flag, CallbackInfo ci) {
        SourceTooltip.append(fluid, tooltips);
    }
}
