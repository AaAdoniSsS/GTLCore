package org.gtlcore.gtlcore.common.player;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.network.GTLNetworkHandler;
import org.gtlcore.gtlcore.network.packet.SSetNoClip;
import org.gtlcore.gtlcore.utils.MachineUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoClipManager {

    private static final String PERSISTENT_KEY = GTLCore.MOD_ID + ":no_clip_enabled";
    private static final String STATE_VERSION_KEY = GTLCore.MOD_ID + ":no_clip_state_version";
    private static final int STATE_VERSION = 2;
    private static final Set<Player> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private NoClipManager() {}

    public static boolean isEnabled(Player player) {
        return ENABLED_PLAYERS.contains(player);
    }

    public static void setEnabled(Player player, boolean enabled) {
        boolean active = enabled && MachineUtil.hasNoClipArmorSet(player);
        if (active) {
            ENABLED_PLAYERS.add(player);
        } else {
            ENABLED_PLAYERS.remove(player);
            player.noPhysics = player.isSpectator();
        }
        setPersistedEnabled(player, active);
    }

    private static void setPersistedEnabled(Player player, boolean enabled) {
        if (!player.level().isClientSide) {
            player.getPersistentData().putBoolean(PERSISTENT_KEY, enabled);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            migrateLeakedAbilities(serverPlayer);
            setEnabled(serverPlayer, serverPlayer.getPersistentData().getBoolean(PERSISTENT_KEY));
            GTLNetworkHandler.INSTANCE.sendTo(new SSetNoClip(isEnabled(serverPlayer)), serverPlayer);
        }
    }

    private static void migrateLeakedAbilities(ServerPlayer player) {
        if (player.getPersistentData().getInt(STATE_VERSION_KEY) >= STATE_VERSION) {
            return;
        }

        if (player.getPersistentData().contains(PERSISTENT_KEY) && !player.isCreative() && !player.isSpectator()) {
            Abilities abilities = player.getAbilities();
            abilities.flying = false;
            if (!MachineUtil.hasNoClipArmorSet(player)) {
                abilities.mayfly = false;
            }
            player.setNoGravity(false);
            player.noPhysics = false;
            player.onUpdateAbilities();
        }
        player.getPersistentData().putInt(STATE_VERSION_KEY, STATE_VERSION);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ENABLED_PLAYERS.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ENABLED_PLAYERS.contains(event.player)) {
            return;
        }
        if (!MachineUtil.hasNoClipArmorSet(event.player)) {
            setEnabled(event.player, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ENABLED_PLAYERS.clear();
    }
}
