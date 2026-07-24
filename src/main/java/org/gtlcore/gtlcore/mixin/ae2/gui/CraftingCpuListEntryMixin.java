package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCpuListEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftingStatusMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingStatusMenu.CraftingCpuListEntry.class)
public class CraftingCpuListEntryMixin implements ICraftingCpuListEntry {

    @Unique
    private long gtlcore$coProcessors;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$initializeCoProcessors(int serial, long storage, int coProcessors, Component name,
                                                CpuSelectionMode mode, GenericStack currentJob, float progress,
                                                long elapsedTimeNanos, CallbackInfo ci) {
        this.gtlcore$coProcessors = coProcessors;
    }

    @Override
    public long gtlcore$getCoProcessors() {
        return this.gtlcore$coProcessors;
    }

    @Override
    public void gtlcore$setCoProcessors(long coProcessors) {
        this.gtlcore$coProcessors = Math.max(0L, coProcessors);
    }

    @Inject(method = "writeToPacket", at = @At("TAIL"), remap = false)
    private void gtlcore$writeCoProcessors(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarLong(this.gtlcore$coProcessors);
    }

    @Inject(method = "readFromPacket", at = @At("TAIL"), cancellable = true, remap = false)
    private static void gtlcore$readCoProcessors(FriendlyByteBuf buffer,
                                                 CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuListEntry> cir) {
        CraftingStatusMenu.CraftingCpuListEntry entry = cir.getReturnValue();
        ((ICraftingCpuListEntry) (Object) entry).gtlcore$setCoProcessors(buffer.readVarLong());
        cir.setReturnValue(entry);
    }
}
