package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.client.ae2.FastCellDisplayText;
import org.gtlcore.gtlcore.client.ae2.PreciseRepoAmounts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.ButtonToolTips;
import appeng.menu.me.common.MEStorageMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.*;

@Mixin(MEStorageScreen.class)
public abstract class FastCellMEStorageScreenMixin<C extends MEStorageMenu> extends AEBaseScreen<C> {

    @Unique
    private final Map<AEKey, FastCellDisplayText> gtlcore$amounts = new HashMap<>();
    @Unique
    private Map<AEKey, BigInteger> gtlcore$lastSnapshot;
    @Shadow(remap = false)
    @Final
    protected Repo repo;

    protected FastCellMEStorageScreenMixin(C menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void gtlcore$refreshDisplay(CallbackInfo ci) {
        Map<AEKey, BigInteger> snapshot = ((org.gtlcore.gtlcore.integration.ae2.storage.PreciseDisplayMenu) menu).gtlcore$displayAmounts();
        if (snapshot == gtlcore$lastSnapshot) return;
        gtlcore$lastSnapshot = snapshot;
        gtlcore$amounts.keySet().retainAll(snapshot.keySet());
        snapshot.forEach((key, amount) -> {
            FastCellDisplayText old = gtlcore$amounts.get(key);
            if (old == null || !old.amount().equals(amount)) {
                gtlcore$amounts.put(key, FastCellDisplayText.create(amount, key.getAmountPerUnit(),
                        key instanceof AEFluidKey ? "B" : ""));
            }
        });
        ((PreciseRepoAmounts) repo).gtlcore$setPreciseAmounts(snapshot);
    }

    @Unique
    private FastCellDisplayText gtlcore$getDisplay(AEKey key) {
        return gtlcore$amounts.get(key);
    }

    @WrapOperation(method = "m_280092_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
                   remap = false,
                   at = @At(value = "INVOKE",
                            target = "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;",
                            remap = false))
    private String gtlcore$renderFastAmount(AEKey key, long amount, AmountFormat format, Operation<String> original) {
        FastCellDisplayText text = gtlcore$getDisplay(key);
        return text == null ? original.call(key, amount, format) :
                format == AmountFormat.SLOT_LARGE_FONT ? text.large() : text.small();
    }

    @WrapOperation(method = "renderGridInventoryEntryTooltip",
                   at = @At(value = "INVOKE",
                            target = "Lappeng/core/localization/Tooltips;getAmountTooltip(Lappeng/core/localization/ButtonToolTips;Lappeng/api/stacks/AEKey;J)Lnet/minecraft/network/chat/Component;"),
                   remap = false)
    private Component gtlcore$renderFastTooltip(ButtonToolTips label, AEKey key, long amount, Operation<Component> original) {
        FastCellDisplayText text = gtlcore$getDisplay(key);
        return text == null ? original.call(label, key, amount) : label.text(text.full()).withStyle(appeng.core.localization.Tooltips.MUTED_COLOR);
    }
}
