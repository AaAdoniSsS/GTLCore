package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.client.ae2.MeInventoryAmountClient;
import org.gtlcore.gtlcore.integration.ae2.chamber.MEChamberManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.emitter.EmitterManagerTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorTerminalMenu;
import org.gtlcore.gtlcore.integration.ae2.wireless.MeInventoryAmountPackets;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Consumer;

public final class WirelessAeClientPacketHandler {

    private WirelessAeClientPacketHandler() {}

    public static void handleTargetNetworks(WirelessAePackets.SyncTargetNetworksPacket packet) {
        WirelessAeScreenHooks.receiveTargetNetworks(packet.targetPos(), packet.entries());
    }

    public static void handlePatternQuickUploadSelection(WirelessAePackets.OpenPatternQuickUploadSelectionPacket packet) {
        PatternQuickUploadSelectionOverlay.open(packet.patternStack(), packet.entries());
    }

    public static void handlePatternQuickUploadDuplicate(WirelessAePackets.PatternQuickUploadDuplicatePacket packet) {
        PatternQuickUploadSelectionOverlay.showDuplicateNotice(packet.targetName());
    }

    public static void handleMeInventoryAmount(MeInventoryAmountPackets.Response packet) {
        MeInventoryAmountClient.receive(packet);
    }

    public static void handleThroughputMonitorTerminal(
                                                       WirelessAePackets.SyncThroughputMonitorTerminalPacket packet) {
        withOpenMenu(packet.containerId(), ThroughputMonitorTerminalMenu.class,
                menu -> menu.setEntries(packet.entries()));
    }

    public static void handleThroughputMonitorSources(
                                                      WirelessAePackets.SyncThroughputMonitorSourcesPacket packet) {
        withOpenMenu(packet.containerId(), ThroughputMonitorTerminalMenu.class,
                menu -> menu.setSourceEntries(packet.key(), packet.sources()));
    }

    public static void handleEmitterManagerTerminal(
                                                    WirelessAePackets.SyncEmitterManagerTerminalPacket packet) {
        withOpenMenu(packet.containerId(), EmitterManagerTerminalMenu.class,
                menu -> menu.setEntries(packet.entries()));
    }

    public static void handleMEChamberManagerEntries(
                                                     WirelessAePackets.SyncMEChamberManagerEntriesPacket packet) {
        withOpenMenu(packet.containerId(), MEChamberManagerTerminalMenu.class,
                menu -> menu.setEntries(packet.entries()));
    }

    public static void handleMEChamberManagerContents(
                                                      WirelessAePackets.SyncMEChamberManagerContentsPacket packet) {
        withOpenMenu(packet.containerId(), MEChamberManagerTerminalMenu.class,
                menu -> menu.setSelectedContents(packet.address(), packet.contents(), packet.details()));
    }

    private static <M extends AbstractContainerMenu> void withOpenMenu(
                                                                       int containerId, Class<M> menuType, Consumer<M> action) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        if (menu.containerId == containerId && menuType.isInstance(menu)) {
            action.accept(menuType.cast(menu));
        }
    }
}
