package org.gtlcore.gtlcore.mixin.ae2.gui;

import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCpuListEntry;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;

@Mixin(CraftingStatusMenu.class)
public class CraftingStatusMenuMixin {

    @Shadow(remap = false)
    private ImmutableSet<ICraftingCPU> lastCpuSet;

    @Inject(method = "createCpuList",
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;sort(Ljava/util/Comparator;)V"),
            remap = false)
    private void gtlcore$attachLongCoProcessors(
                                                CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuList> cir,
                                                @Local(name = "entries") ArrayList<CraftingStatusMenu.CraftingCpuListEntry> entries) {
        Iterator<ICraftingCPU> cpus = this.lastCpuSet.iterator();
        for (CraftingStatusMenu.CraftingCpuListEntry entry : entries) {
            if (!cpus.hasNext()) {
                break;
            }
            ICraftingCPU cpu = cpus.next();
            long coProcessors = cpu instanceof TransfiniteCraftingCPU transfiniteCpu ?
                    transfiniteCpu.getLongCoProcessors() : cpu.getCoProcessors();
            ((ICraftingCpuListEntry) (Object) entry).gtlcore$setCoProcessors(coProcessors);
        }
    }
}
