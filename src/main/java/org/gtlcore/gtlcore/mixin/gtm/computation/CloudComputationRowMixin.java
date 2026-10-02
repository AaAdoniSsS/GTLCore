package org.gtlcore.gtlcore.mixin.gtm.computation;

import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationUsage;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Preserve Additions' widget protocol, and carry long usage in a separate update. */
@Pseudo
@Mixin(targets = "com.gtladd.gtladditions.common.machine.CloudOpticalComputationMonitorMachine$RowWidgets", remap = false)
public abstract class CloudComputationRowMixin extends WidgetGroup {

    @Shadow @Final private boolean provider;
    @Shadow @Final private MetaMachine machine;
    @Shadow @Final private ComponentPanelWidget label;
    @Shadow private long current;
    @Shadow private long max;
    @Shadow private int cwu;
    @Unique private long gtlcore$usage;
    @Unique private long gtlcore$sentUsage;
    @Unique private static final int GTLCORE_USAGE_UPDATE = 0x435755;

    private CloudComputationRowMixin() {}

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtlcore$longUsageLabel(CallbackInfo ci) {
        if (!provider && machine != null) label.textSupplier(lines -> lines.add(Component.translatable(
                "gui.gtladditions.cloud_monitor.requester_info", FormattingUtil.formatNumbers(gtlcore$usage))));
    }

    @Inject(method = "refreshValues", at = @At("HEAD"), cancellable = true)
    private void gtlcore$longValues(CallbackInfo ci) {
        if (provider && machine instanceof IOpticalComputationProvider source) {
            max = ComputationNetwork.capacity(List.of(source));
            current = ComputationNetwork.available(this, List.of(source));
        } else {
            gtlcore$usage = machine instanceof IRecipeLogicMachine owner && owner.getRecipeLogic().isWorking() ?
                    ((ComputationUsage) owner.getRecipeLogic()).gtlcore$usedComputation() : 0;
            cwu = ComputationMath.toInt(gtlcore$usage);
        }
        ci.cancel();
    }

    @Inject(method = "writeInitialData", at = @At("TAIL"))
    private void gtlcore$writeUsage(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarLong(gtlcore$usage);
        gtlcore$sentUsage = gtlcore$usage;
    }

    @Inject(method = "readInitialData", at = @At("TAIL"))
    private void gtlcore$readUsage(FriendlyByteBuf buffer, CallbackInfo ci) {
        gtlcore$usage = buffer.readVarLong();
    }

    @Inject(method = "detectAndSendChanges", at = @At("TAIL"))
    private void gtlcore$updateUsage(CallbackInfo ci) {
        if (gtlcore$sentUsage == gtlcore$usage) return;
        gtlcore$sentUsage = gtlcore$usage;
        writeUpdateInfo(GTLCORE_USAGE_UPDATE, buffer -> buffer.writeVarLong(gtlcore$usage));
    }

    @Inject(method = "readUpdateInfo", at = @At("HEAD"), cancellable = true)
    private void gtlcore$receiveUsage(int id, FriendlyByteBuf buffer, CallbackInfo ci) {
        if (id != GTLCORE_USAGE_UPDATE) return;
        gtlcore$usage = buffer.readVarLong();
        ci.cancel();
    }
}
