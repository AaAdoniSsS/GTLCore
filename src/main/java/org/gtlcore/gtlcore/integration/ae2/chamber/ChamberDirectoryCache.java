package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.networking.IGrid;
import appeng.hooks.ticking.TickHandler;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Server-thread directory snapshots shared by viewers; values do not retain their grid. */
@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChamberDirectoryCache {

    private static final Map<IGrid, Snapshot> SNAPSHOTS = new WeakHashMap<>();

    private ChamberDirectoryCache() {}

    public static List<MEChamberManagerTerminalMenu.Entry> get(
                                                               IGrid grid,
                                                               Supplier<List<MEChamberManagerTerminalMenu.Entry>> collect) {
        long tick = TickHandler.instance().getCurrentTick();
        Snapshot snapshot = SNAPSHOTS.get(grid);
        if (snapshot == null || snapshot.tick() > tick || tick - snapshot.tick() >= 20) {
            snapshot = new Snapshot(tick, collect.get());
            SNAPSHOTS.put(grid, snapshot);
        }
        return snapshot.entries();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SNAPSHOTS.clear();
    }

    private record Snapshot(long tick, List<MEChamberManagerTerminalMenu.Entry> entries) {}
}
