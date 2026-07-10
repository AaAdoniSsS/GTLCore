package org.gtlcore.gtlcore.mixin.ae2.crafting;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingDispatchReasonProvider;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingStatusReasons;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingStatus;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(CraftingStatus.class)
public abstract class CraftingStatusMixin implements ICraftingStatusReasons {

    @Unique
    private Map<Long, Integer> gtlcore$reasonMasks = Map.of();

    @WrapMethod(method = "create", remap = false)
    private static CraftingStatus gtlcore$attachDispatchReasons(
                                                                IncrementalUpdateHelper changes,
                                                                CraftingCpuLogic logic,
                                                                Operation<CraftingStatus> original) {
        List<AEKey> changedKeys = new ArrayList<>();
        changes.forEach(changedKeys::add);

        CraftingStatus status = original.call(changes, logic);
        Map<Long, Integer> reasonMasks = new LinkedHashMap<>();
        var entries = status.getEntries();
        ICraftingDispatchReasonProvider reasonProvider = (ICraftingDispatchReasonProvider) logic;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            int reasonMask = 0;
            if (!entry.isDeleted() && i < changedKeys.size()) {
                reasonMask = reasonProvider.gtlcore$getDispatchReasonMask(changedKeys.get(i));
            }
            reasonMasks.put(entry.getSerial(), reasonMask);
        }
        ((ICraftingStatusReasons) status).gtlcore$setReasonMasks(reasonMasks);
        return status;
    }

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void gtlcore$writeDispatchReasons(FriendlyByteBuf data, CallbackInfo ci) {
        data.writeVarInt(this.gtlcore$reasonMasks.size());
        for (var entry : this.gtlcore$reasonMasks.entrySet()) {
            data.writeVarLong(entry.getKey());
            data.writeVarInt(entry.getValue());
        }
    }

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private static void gtlcore$readDispatchReasons(
                                                    FriendlyByteBuf data,
                                                    CallbackInfoReturnable<CraftingStatus> cir) {
        CraftingStatus status = cir.getReturnValue();
        int count = data.readVarInt();
        int maxCount = status.getEntries().size();
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException("Invalid crafting dispatch reason count " + count);
        }
        Map<Long, Integer> reasonMasks = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            reasonMasks.put(data.readVarLong(), data.readVarInt());
        }
        ((ICraftingStatusReasons) status).gtlcore$setReasonMasks(reasonMasks);
    }

    @Override
    @Unique
    public Map<Long, Integer> gtlcore$getReasonMasks() {
        return this.gtlcore$reasonMasks;
    }

    @Override
    @Unique
    public void gtlcore$setReasonMasks(Map<Long, Integer> reasonMasks) {
        this.gtlcore$reasonMasks = Map.copyOf(reasonMasks);
    }
}
