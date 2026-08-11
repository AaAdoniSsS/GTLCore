package org.gtlcore.gtlcore.common.player;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.network.GTLNetworkHandler;
import org.gtlcore.gtlcore.network.packet.SSetNoClip;
import org.gtlcore.gtlcore.utils.MachineUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoClipManager {

    private static final String PERSISTENT_KEY = GTLCore.MOD_ID + ":no_clip_enabled";
    private static final Map<Player, AbilityState> ENABLED_PLAYERS = new ConcurrentHashMap<>();

    private NoClipManager() {}

    public static boolean isEnabled(Player player) {
        AbilityState originalState = ENABLED_PLAYERS.get(player);
        if (originalState == null) {
            return false;
        }
        if (!MachineUtil.hasNoClipArmorSet(player)) {
            disable(player, originalState);
            setPersistedEnabled(player, false);
            return false;
        }
        applyNoClipAbilities(player);
        return true;
    }

    public static void setEnabled(Player player, boolean enabled) {
        boolean active = enabled && MachineUtil.hasNoClipArmorSet(player);
        if (active) {
            AbilityState originalState = ENABLED_PLAYERS.putIfAbsent(player, AbilityState.capture(player));
            if (originalState == null) {
                player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
            }
            applyNoClipAbilities(player);
        } else {
            AbilityState originalState = ENABLED_PLAYERS.get(player);
            if (originalState != null) {
                disable(player, originalState);
            }
        }
        player.noPhysics = player.isSpectator() || active;
        setPersistedEnabled(player, active);
    }

    private static void applyNoClipAbilities(Player player) {
        Abilities abilities = player.getAbilities();
        boolean abilitiesChanged = !abilities.mayfly || !abilities.flying;
        abilities.mayfly = true;
        abilities.flying = true;
        player.setNoGravity(true);
        player.setOnGround(false);
        player.resetFallDistance();
        syncAbilities(player, abilitiesChanged);
    }

    private static void disable(Player player, AbilityState originalState) {
        ENABLED_PLAYERS.remove(player);
        Abilities abilities = player.getAbilities();
        boolean abilitiesChanged = abilities.mayfly != originalState.mayfly || abilities.flying != originalState.flying;
        abilities.mayfly = originalState.mayfly;
        abilities.flying = originalState.flying;
        player.setNoGravity(originalState.noGravity);
        player.noPhysics = player.isSpectator();
        player.resetFallDistance();
        syncAbilities(player, abilitiesChanged);
    }

    private static void syncAbilities(Player player, boolean abilitiesChanged) {
        if (abilitiesChanged && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }

    private static void setPersistedEnabled(Player player, boolean enabled) {
        if (!player.level().isClientSide) {
            player.getPersistentData().putBoolean(PERSISTENT_KEY, enabled);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            setEnabled(serverPlayer, serverPlayer.getPersistentData().getBoolean(PERSISTENT_KEY));
            GTLNetworkHandler.INSTANCE.sendTo(new SSetNoClip(isEnabled(serverPlayer)), serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            AbilityState originalState = ENABLED_PLAYERS.get(event.getEntity());
            if (originalState != null) {
                disable(event.getEntity(), originalState);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ENABLED_PLAYERS.clear();
    }

    private record AbilityState(boolean mayfly, boolean flying, boolean noGravity) {

        private static AbilityState capture(Player player) {
            Abilities abilities = player.getAbilities();
            return new AbilityState(abilities.mayfly, abilities.flying, player.isNoGravity());
        }
    }
}
