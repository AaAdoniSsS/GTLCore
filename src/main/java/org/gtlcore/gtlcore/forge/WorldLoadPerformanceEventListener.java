package org.gtlcore.gtlcore.forge;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.world.WorldLoadPerformanceLogger;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldLoadPerformanceEventListener {

    private WorldLoadPerformanceEventListener() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        WorldLoadPerformanceLogger.onServerAboutToStart(event);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        WorldLoadPerformanceLogger.onServerStarting(event);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        WorldLoadPerformanceLogger.onServerStarted(event);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        WorldLoadPerformanceLogger.onLevelLoad(event);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        WorldLoadPerformanceLogger.onPlayerLoggedIn(event);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WorldLoadPerformanceLogger.onServerStopping(event);
    }
}
