package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.client.ae2.CraftingCpuSearchTarget;
import org.gtlcore.gtlcore.client.ae2.wireless.UniversalSearch;
import org.gtlcore.gtlcore.integration.ae2.crafting.ICraftingCpuListEntry;
import org.gtlcore.gtlcore.utils.NumberUtils;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CPUSelectionList.class)
public abstract class CPUSelectionListMixin implements ICompositeWidget, CraftingCpuSearchTarget {

    @Shadow(remap = false)
    @Final
    private Scrollbar scrollbar;

    @Unique
    private String gtlcore$cpuSearchQuery = "";

    @Unique
    private List<CraftingStatusMenu.CraftingCpuListEntry> gtlcore$filteredSource = List.of();

    @Unique
    private String gtlcore$cachedCpuSearchQuery = "";

    @Unique
    private List<CraftingStatusMenu.CraftingCpuListEntry> gtlcore$filteredCpus = List.of();

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlcore$initializeCpuSearch(CraftingStatusMenu menu, Scrollbar scrollbar,
                                             ScreenStyle style, CallbackInfo ci) {
        this.gtlcore$cpuSearchQuery = "";
        this.gtlcore$filteredSource = List.of();
        this.gtlcore$cachedCpuSearchQuery = "";
        this.gtlcore$filteredCpus = List.of();
    }

    @Inject(method = "formatStorage", at = @At("HEAD"), remap = false, cancellable = true)
    private void formatStorage(CraftingStatusMenu.CraftingCpuListEntry cpu, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(NumberUtils.numberText(cpu.storage()).getString());
    }

    @ModifyArg(method = "getTooltip",
               at = @At(value = "INVOKE",
                        target = "Lappeng/core/localization/Tooltips;ofNumber(J)Lnet/minecraft/network/chat/MutableComponent;"),
               index = 0,
               remap = false)
    private long gtlcore$useLongCoProcessorsInTooltip(long coProcessors,
                                                      @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        return ((ICraftingCpuListEntry) (Object) cpu).gtlcore$getCoProcessors();
    }

    @Redirect(method = "drawBackgroundLayer",
              at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"),
              remap = false)
    private String gtlcore$formatLongCoProcessors(int coProcessors,
                                                  @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        long longCoProcessors = ((ICraftingCpuListEntry) (Object) cpu).gtlcore$getCoProcessors();
        return NumberUtils.numberText(longCoProcessors).getString();
    }

    @Override
    public void gtlcore$setCpuSearchQuery(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (!normalizedQuery.equals(this.gtlcore$cpuSearchQuery)) {
            this.gtlcore$cpuSearchQuery = normalizedQuery;
            this.scrollbar.setCurrentScroll(0);
        }
    }

    @Redirect(
              method = { "hitTestCpu", "updateBeforeRender", "drawBackgroundLayer" },
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuList;cpus()Ljava/util/List;"),
              remap = false)
    private List<CraftingStatusMenu.CraftingCpuListEntry> gtlcore$getVisibleCpus(
                                                                                 CraftingStatusMenu.CraftingCpuList cpuList) {
        List<CraftingStatusMenu.CraftingCpuListEntry> source = cpuList.cpus();
        String searchQuery = this.gtlcore$cpuSearchQuery == null ? "" : this.gtlcore$cpuSearchQuery;
        if (searchQuery.isEmpty()) {
            return source;
        }
        if (source != this.gtlcore$filteredSource ||
                !searchQuery.equals(this.gtlcore$cachedCpuSearchQuery)) {
            this.gtlcore$filteredSource = source;
            this.gtlcore$cachedCpuSearchQuery = searchQuery;
            this.gtlcore$filteredCpus = source.stream()
                    .filter(cpu -> gtlcore$matchesCpuSearch(cpu, searchQuery))
                    .toList();
        }
        return this.gtlcore$filteredCpus;
    }

    @Unique
    private boolean gtlcore$matchesCpuSearch(CraftingStatusMenu.CraftingCpuListEntry cpu, String searchQuery) {
        var cpuName = cpu.name() == null ?
                GuiText.CPUs.text().append(" #" + cpu.serial()) :
                cpu.name();
        if (UniversalSearch.contains(cpuName.getString(), searchQuery)) {
            return true;
        }
        var currentJob = cpu.currentJob();
        return currentJob != null &&
                UniversalSearch.contains(
                        currentJob.what().getDisplayName().getString(),
                        searchQuery);
    }
}
