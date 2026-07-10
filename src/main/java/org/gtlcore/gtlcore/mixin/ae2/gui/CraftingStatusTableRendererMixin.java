package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingDispatchReason;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingDispatchReasonView;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(CraftingStatusTableRenderer.class)
public abstract class CraftingStatusTableRendererMixin extends AbstractTableRenderer<CraftingStatusEntry> {

    protected CraftingStatusTableRendererMixin(AEBaseScreen<?> screen, int x, int y, int rows) {
        super(screen, x, y, rows);
    }

    @Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void gtlcore$appendDispatchReasons(
                                               CraftingStatusEntry entry,
                                               CallbackInfoReturnable<List<Component>> cir) {
        if (entry.getPendingAmount() <= 0 || !(this.screen instanceof ICraftingDispatchReasonView reasonView)) {
            return;
        }

        List<Component> lines = new ArrayList<>(cir.getReturnValue());
        lines.add(Component.translatable(CraftingDispatchReason.HEADING_TRANSLATION_KEY)
                .withStyle(ChatFormatting.YELLOW));

        var reasons = CraftingDispatchReason.decode(
                reasonView.gtlcore$getDispatchReasonMask(entry.getSerial()));
        if (reasons.isEmpty()) {
            lines.add(Component.translatable(CraftingDispatchReason.NOT_CHECKED_TRANSLATION_KEY)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (CraftingDispatchReason reason : reasons) {
                lines.add(Component.translatable(reason.translationKey()).withStyle(ChatFormatting.GRAY));
            }
        }
        cir.setReturnValue(lines);
    }
}
