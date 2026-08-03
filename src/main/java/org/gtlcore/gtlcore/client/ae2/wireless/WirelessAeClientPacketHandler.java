package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.client.ae2.MeInventoryAmountClient;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.MeInventoryAmountPackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.Minecraft;

public final class WirelessAeClientPacketHandler {

    private WirelessAeClientPacketHandler() {}

    public static void handleTargetNetworks(WirelessAePackets.SyncTargetNetworksPacket packet) {
        WirelessAeScreenHooks.receiveTargetNetworks(packet.targetPos(), packet.entries());
    }

    public static void handlePatternQuickUploadSelection(WirelessAePackets.OpenPatternQuickUploadSelectionPacket packet) {
        PatternQuickUploadSelectionOverlay.open(packet.patternStack(), packet.entries());
    }

    public static void handleMeInventoryAmount(MeInventoryAmountPackets.Response packet) {
        MeInventoryAmountClient.receive(packet);
    }

    public static void handleThroughputMonitorTerminal(
                                                       WirelessAePackets.SyncThroughputMonitorTerminalPacket packet) {
        if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.containerMenu instanceof ThroughputMonitorTerminalMenu menu &&
                menu.containerId == packet.containerId()) {
            menu.setEntries(packet.entries());
        }
    }

    public static void handleThroughputMonitorSources(
                                                      WirelessAePackets.SyncThroughputMonitorSourcesPacket packet) {
        if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.containerMenu instanceof ThroughputMonitorTerminalMenu menu &&
                menu.containerId == packet.containerId()) {
            menu.setSourceEntries(packet.key(), packet.sources());
        }
    }

    public static void handleEmitterManagerTerminal(
                                                    WirelessAePackets.SyncEmitterManagerTerminalPacket packet) {
        if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.containerMenu instanceof EmitterManagerTerminalMenu menu &&
                menu.containerId == packet.containerId()) {
            menu.setEntries(packet.entries());
        }
    }
}
