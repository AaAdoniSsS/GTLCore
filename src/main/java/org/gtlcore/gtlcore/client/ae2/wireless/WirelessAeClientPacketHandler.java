package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

public final class WirelessAeClientPacketHandler {

    private WirelessAeClientPacketHandler() {}

    public static void handleTargetNetworks(WirelessAePackets.SyncTargetNetworksPacket packet) {
        WirelessAeScreenHooks.receiveTargetNetworks(packet.targetPos(), packet.entries());
    }
}
