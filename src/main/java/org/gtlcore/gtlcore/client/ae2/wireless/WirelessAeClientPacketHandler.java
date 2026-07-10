package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.client.ae2.MeInventoryAmountClient;
import org.gtlcore.gtlcore.integration.ae2.wireless.MeInventoryAmountPackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

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
}
