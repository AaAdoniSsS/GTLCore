package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyDisplayTextLimiter;
import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyLocator;
import org.gtlcore.gtlcore.integration.gtmt.WirelessEnergyMonitorSnapshot;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.common.block.machine.electric.WirelessEnergyMonitor;
import com.hepdd.gtmthings.utils.TeamUtil;
import com.mojang.datafixers.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;

@Mixin(WirelessEnergyMonitor.class)
public abstract class WirelessEnergyMonitorMixin extends MetaMachine {

    @Unique
    private static final long GTLCORE$DISPLAY_TEXT_REFRESH_INTERVAL = 40L;

    @Unique
    private static final String GTLCORE$STATISTICS_SCOPE_BUTTON = "all";

    @Unique
    private static final String GTLCORE$HIDDEN_ENTRY_TRANSLATION = "gtlcore.machine.wireless_energy_monitor.tooltip.hidden_entries";

    @Unique
    private static final long GTLCORE$UNSAMPLED_DISPLAY_TEXT_TICK = Long.MIN_VALUE;

    @Shadow(remap = false)
    private UUID userid;

    @Shadow(remap = false)
    private BigInteger beforeEnergy;

    @Shadow(remap = false)
    private ArrayList<BigInteger> longArrayList;

    @Shadow(remap = false)
    private boolean all;

    @Unique
    private List<Component> gtlcore$displayTextCache;

    @Unique
    private UUID gtlcore$displayTextCacheUser;

    @Unique
    private boolean gtlcore$displayTextCacheAll;

    @Unique
    private long gtlcore$displayTextCacheTick = GTLCORE$UNSAMPLED_DISPLAY_TEXT_TICK;

    @Unique
    private long gtlcore$lastUsageSampleTick = GTLCORE$UNSAMPLED_DISPLAY_TEXT_TICK;

    @Unique
    private int gtlcore$hiddenEnergyEntryCount;

    public WirelessEnergyMonitorMixin(IMachineBlockEntity holder) {
        super(holder);
    }

    @Inject(method = "addDisplayText", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$addBoundedDisplayText(List<Component> textList, CallbackInfo ci) {
        long timer = getOffsetTimer();
        if (gtlcore$canUseDisplayTextCache(timer)) {
            gtlcore$sampleUsageForCachedDisplayText(timer);
            textList.addAll(gtlcore$displayTextCache);
            ci.cancel();
            return;
        }

        List<Component> displayText = gtlcore$buildDisplayText();
        textList.addAll(displayText);
        gtlcore$storeDisplayTextCache(displayText, timer);
        ci.cancel();
    }

    @Inject(method = "handleDisplayClick", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$handleDisplayClick(String componentData, ClickData clickData, CallbackInfo ci) {
        if (WirelessEnergyLocator.isLocatorData(componentData)) {
            ci.cancel();
            if (clickData.isRemote) {
                WirelessEnergyLocator.highlightOnClient(componentData);
            }
            return;
        }
        if (!clickData.isRemote && GTLCORE$STATISTICS_SCOPE_BUTTON.equals(componentData)) {
            gtlcore$clearDisplayTextCache();
        }
    }

    @Unique
    private List<Component> gtlcore$buildDisplayText() {
        if (userid == null) {
            return List.of();
        }

        BigInteger energy = WirelessEnergyManager.getUserEU(userid);
        gtlcore$ensureUsageTrackingInitialized(energy);

        List<Component> displayText = new ArrayList<>();
        displayText.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                TeamUtil.GetName(getLevel(), userid)).withStyle(ChatFormatting.AQUA));
        displayText.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.1",
                FormattingUtil.formatNumbers(energy)).withStyle(ChatFormatting.GRAY));

        BigDecimal averageUsage = gtlcore$invokeGetAvgUsage(energy);
        gtlcore$appendAverageUsageText(displayText, energy, averageUsage);
        gtlcore$appendStatisticsScopeText(displayText);
        gtlcore$appendVisibleEnergyEntries(displayText);
        return displayText;
    }

    @Unique
    private void gtlcore$appendAverageUsageText(List<Component> displayText, BigInteger energy,
                                                BigDecimal averageUsage) {
        BigDecimal absoluteUsage = averageUsage.abs();
        int voltageTier = GTUtil.getFloorTierByVoltage(absoluteUsage.longValue());
        Component voltageName = Component.literal(GTValues.VNF[voltageTier]);
        BigDecimal amperage = absoluteUsage.divide(BigDecimal.valueOf(GTValues.V[voltageTier]), 1,
                RoundingMode.FLOOR);

        if (averageUsage.compareTo(BigDecimal.ZERO) >= 0) {
            displayText.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.input",
                    FormattingUtil.formatNumbers(absoluteUsage), amperage, voltageName).withStyle(ChatFormatting.GRAY));
            displayText.add(Component.translatable("gtceu.multiblock.power_substation.time_to_fill",
                    Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.time_to_fill"))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        displayText.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.output",
                FormattingUtil.formatNumbers(absoluteUsage), amperage, voltageName).withStyle(ChatFormatting.GRAY));
        displayText.add(Component.translatable("gtceu.multiblock.power_substation.time_to_drain",
                gtlcore$invokeGetTimeToFillDrainText(energy.divide(absoluteUsage.toBigInteger()
                        .multiply(BigInteger.valueOf(20L)))))
                .withStyle(ChatFormatting.GRAY));
    }

    @Unique
    private void gtlcore$appendStatisticsScopeText(List<Component> displayText) {
        Component scopeText = all ?
                Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.all") :
                Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.team");
        displayText.add(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.statistics")
                .append(ComponentPanelWidget.withButton(scopeText, GTLCORE$STATISTICS_SCOPE_BUTTON)));
    }

    @Unique
    private void gtlcore$appendVisibleEnergyEntries(List<Component> displayText) {
        List<Map.Entry<Pair<UUID, MetaMachine>, Long>> visibleEntries = gtlcore$getVisibleEnergyEntries();
        for (Map.Entry<Pair<UUID, MetaMachine>, Long> entry : visibleEntries) {
            displayText.add(gtlcore$formatEnergyEntry(entry));
        }
        if (gtlcore$hiddenEnergyEntryCount > 0) {
            displayText.add(Component.translatable(GTLCORE$HIDDEN_ENTRY_TRANSLATION, gtlcore$hiddenEnergyEntryCount)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Unique
    private List<Map.Entry<Pair<UUID, MetaMachine>, Long>> gtlcore$getVisibleEnergyEntries() {
        int maxVisibleEntries = WirelessEnergyDisplayTextLimiter.DEFAULT_MAX_VISIBLE_ENTRIES;
        WirelessEnergyMonitorSnapshot.Snapshot snapshot = WirelessEnergyMonitorSnapshot.drain();
        Comparator<Map.Entry<Pair<UUID, MetaMachine>, Long>> byValue = Map.Entry.comparingByValue();
        PriorityQueue<Map.Entry<Pair<UUID, MetaMachine>, Long>> visibleEntries = new PriorityQueue<>(
                Math.max(1, maxVisibleEntries), byValue.reversed());

        int matchingEntryCount = 0;
        for (Map.Entry<Pair<UUID, MetaMachine>, Long> entry : snapshot.entries()) {
            if (!gtlcore$shouldShowEnergyEntry(entry.getKey().getFirst())) {
                continue;
            }

            matchingEntryCount++;
            if (maxVisibleEntries <= 0) {
                continue;
            }

            Map.Entry<Pair<UUID, MetaMachine>, Long> entryCopy = Map.entry(entry.getKey(), entry.getValue());
            if (visibleEntries.size() < maxVisibleEntries) {
                visibleEntries.offer(entryCopy);
            } else if (byValue.compare(entryCopy, visibleEntries.peek()) < 0) {
                visibleEntries.poll();
                visibleEntries.offer(entryCopy);
            }
        }

        List<Map.Entry<Pair<UUID, MetaMachine>, Long>> orderedEntries = new ArrayList<>(visibleEntries);
        orderedEntries.sort(byValue);
        List<Map.Entry<Pair<UUID, MetaMachine>, Long>> limitedEntries = WirelessEnergyDisplayTextLimiter
                .limit(orderedEntries, maxVisibleEntries);
        int trackedEntryCount = matchingEntryCount + (all ? snapshot.overflowEntryCount() : 0);
        gtlcore$hiddenEnergyEntryCount = WirelessEnergyDisplayTextLimiter.hiddenEntryCount(trackedEntryCount,
                limitedEntries.size());
        return List.copyOf(limitedEntries);
    }

    @Unique
    private boolean gtlcore$shouldShowEnergyEntry(UUID entryUserId) {
        return all || Objects.equals(TeamUtil.getTeamUUID(entryUserId), TeamUtil.getTeamUUID(userid));
    }

    @Unique
    private Component gtlcore$formatEnergyEntry(Map.Entry<Pair<UUID, MetaMachine>, Long> entry) {
        UUID entryUserId = entry.getKey().getFirst();
        MetaMachine machine = entry.getKey().getSecond();
        long energyPerTick = entry.getValue();
        long absoluteEnergyPerTick = gtlcore$safeAbs(energyPerTick);
        String positionText = machine.getPos().toShortString();

        MutableComponent hoverText = Component.translatable("recipe.condition.dimension.tooltip",
                machine.getLevel().dimension().location())
                .append(" [")
                .append(positionText)
                .append("]")
                .append(Component.translatable("gtmthings.machine.wireless_energy_monitor.tooltip.0",
                        TeamUtil.GetName(getLevel(), entryUserId)));
        Style hoverStyle = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return Component.translatable(machine.getBlockState().getBlock().getDescriptionId())
                .withStyle(hoverStyle)
                .append(energyPerTick > 0 ? " +" : " -")
                .append(FormattingUtil.formatNumbers(absoluteEnergyPerTick))
                .append(" EU/t (")
                .append(GTValues.VNF[GTUtil.getFloorTierByVoltage(absoluteEnergyPerTick)])
                .append(")")
                .append(ComponentPanelWidget.withButton(
                        Component.literal(" [ ]"),
                        WirelessEnergyLocator.encode(machine.getLevel().dimension(), machine.getPos())));
    }

    @Unique
    private long gtlcore$safeAbs(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
    }

    @Unique
    private boolean gtlcore$canUseDisplayTextCache(long timer) {
        return gtlcore$displayTextCache != null &&
                Objects.equals(gtlcore$displayTextCacheUser, userid) &&
                gtlcore$displayTextCacheAll == all &&
                timer >= gtlcore$displayTextCacheTick &&
                timer - gtlcore$displayTextCacheTick < GTLCORE$DISPLAY_TEXT_REFRESH_INTERVAL;
    }

    @Unique
    private void gtlcore$sampleUsageForCachedDisplayText(long timer) {
        if (userid != null && timer != gtlcore$lastUsageSampleTick) {
            BigInteger energy = WirelessEnergyManager.getUserEU(userid);
            gtlcore$ensureUsageTrackingInitialized(energy);
            gtlcore$invokeGetAvgUsage(energy);
            gtlcore$lastUsageSampleTick = timer;
        }
    }

    @Unique
    private void gtlcore$storeDisplayTextCache(List<Component> displayText, long timer) {
        gtlcore$displayTextCache = List.copyOf(displayText);
        gtlcore$displayTextCacheUser = userid;
        gtlcore$displayTextCacheAll = all;
        gtlcore$displayTextCacheTick = timer;
        gtlcore$lastUsageSampleTick = timer;
    }

    @Unique
    private void gtlcore$ensureUsageTrackingInitialized(BigInteger energy) {
        if (beforeEnergy == null) {
            beforeEnergy = energy;
        }
        if (longArrayList == null) {
            longArrayList = new ArrayList<>();
        }
    }

    @Unique
    private void gtlcore$clearDisplayTextCache() {
        gtlcore$displayTextCache = null;
        gtlcore$displayTextCacheUser = null;
        gtlcore$displayTextCacheAll = false;
        gtlcore$displayTextCacheTick = GTLCORE$UNSAMPLED_DISPLAY_TEXT_TICK;
        gtlcore$lastUsageSampleTick = GTLCORE$UNSAMPLED_DISPLAY_TEXT_TICK;
    }

    @Invoker(value = "getAvgUsage", remap = false)
    protected abstract BigDecimal gtlcore$invokeGetAvgUsage(BigInteger energy);

    @Invoker(value = "getTimeToFillDrainText", remap = false)
    private static Component gtlcore$invokeGetTimeToFillDrainText(BigInteger seconds) {
        throw new AssertionError();
    }
}
