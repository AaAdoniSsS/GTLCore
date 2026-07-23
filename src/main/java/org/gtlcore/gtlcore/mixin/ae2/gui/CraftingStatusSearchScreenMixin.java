package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.CraftingCpuSearchTarget;
import org.gtlcore.gtlcore.client.ae2.CraftingStatusBulkActionControls;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStatusBulkActions;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingStatusScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CraftingStatusScreen.class)
public abstract class CraftingStatusSearchScreenMixin extends AEBaseScreen<CraftingStatusMenu>
                                                      implements CraftingStatusBulkActionControls {

    @Unique
    private static final String GTLCORE$CPU_LIST_WIDGET_ID = "selectCpuList";

    @Unique
    private static final String GTLCORE$CPU_SEARCH_WIDGET_ID = "cpuSearch";

    @Unique
    private static final String GTLCORE$SUSPEND_ALL_WIDGET_ID = "suspendAll";

    @Unique
    private static final String GTLCORE$CANCEL_ALL_WIDGET_ID = "cancelAll";

    @Unique
    private static final int GTLCORE$CPU_SEARCH_MAX_LENGTH = 80;

    @Unique
    private Button gtlcore$suspendAllButton;

    @Unique
    private Button gtlcore$cancelAllButton;

    private CraftingStatusSearchScreenMixin(CraftingStatusMenu menu, Inventory playerInventory,
                                            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gtlcore$addCpuSearchField(CraftingStatusMenu menu, Inventory playerInventory,
                                           Component title, ScreenStyle style, CallbackInfo ci) {
        var cpuList = ((WidgetContainerAccessor) this.widgets)
                .gtlcore$getCompositeWidgets()
                .get(GTLCORE$CPU_LIST_WIDGET_ID);
        if (cpuList instanceof CraftingCpuSearchTarget searchTarget) {
            AETextField searchField = this.widgets.addTextField(GTLCORE$CPU_SEARCH_WIDGET_ID);
            searchField.setMaxLength(GTLCORE$CPU_SEARCH_MAX_LENGTH);
            searchField.setPlaceholder(Component.translatable("field.gtlcore.crafting_cpu.search_hint"));
            searchField.setTooltipMessage(List.of(
                    Component.translatable("tooltip.gtlcore.crafting_cpu.search_title")
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                    Component.translatable("tooltip.gtlcore.crafting_cpu.search_name")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("tooltip.gtlcore.crafting_cpu.search_output")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("tooltip.gtlcore.crafting_cpu.search_pinyin")
                            .withStyle(ChatFormatting.GRAY)));
            searchField.setResponder(searchTarget::gtlcore$setCpuSearchQuery);
        }

        if (menu instanceof ICraftingStatusBulkActions bulkActions) {
            this.gtlcore$suspendAllButton = this.widgets.addButton(
                    GTLCORE$SUSPEND_ALL_WIDGET_ID,
                    Component.translatable("gui.gtlcore.crafting_suspend_all"),
                    bulkActions::gtlcore$suspendAllCrafting);
            this.gtlcore$cancelAllButton = this.widgets.addButton(
                    GTLCORE$CANCEL_ALL_WIDGET_ID,
                    Component.translatable("gui.gtlcore.crafting_cancel_all"),
                    bulkActions::gtlcore$cancelAllCrafting);
        }
    }

    @Override
    public void gtlcore$refreshBulkActionButtons() {
        boolean hasActiveCrafting = this.menu.cpuList.cpus().stream()
                .anyMatch(cpu -> cpu.currentJob() != null);
        if (this.gtlcore$suspendAllButton != null) {
            this.gtlcore$suspendAllButton.active = hasActiveCrafting;
        }
        if (this.gtlcore$cancelAllButton != null) {
            this.gtlcore$cancelAllButton.active = hasActiveCrafting;
        }
    }
}
