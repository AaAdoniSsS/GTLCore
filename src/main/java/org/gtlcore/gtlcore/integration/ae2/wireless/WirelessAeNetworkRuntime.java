package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WirelessAeNetworkRuntime {

    private static final int CONNECT_INTERVAL_TICKS = 20;
    private static final int FAVORITE_BIND_RETRY_TICKS = 80;
    private static final int WIRED_RECHECK_INTERVAL_TICKS = 100;

    private static final Map<UUID, Map<WirelessAeSavedData.MemberKey, IGridConnection>> CONNECTIONS = new HashMap<>();
    private static final Map<GlobalPos, PendingFavoriteBind> PENDING_FAVORITE_BINDS = new HashMap<>();
    private static final Map<Class<?>, WirelessTargetClassInfo> WIRELESS_TARGET_CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<RuntimeReflectionKey, Optional<Method>> RUNTIME_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<WirelessAeSavedData.MemberKey, Integer> NEXT_WIRED_RECHECK_TICKS = new HashMap<>();
    private static final Map<WirelessAeSavedData.MemberKey, IGridNode> TICK_TARGET_NODE_CACHE = new HashMap<>();
    private static final Map<UUID, WirelessNetworkCoreBlockEntity> TICK_CORE_CACHE = new HashMap<>();
    private static final Map<UUID, IGridNode> TICK_BRIDGE_NODE_CACHE = new HashMap<>();
    private static final Map<GridNodePair, Boolean> TICK_IN_WORLD_CONNECTION_CACHE = new HashMap<>();
    private static final Set<UUID> REQUESTED_RECONNECTS = new HashSet<>();
    private static int tickCounter;
    private static boolean tickCacheActive;
    private static final String GTCEU_ME_PART_PACKAGE = "com.gregtechceu.gtceu.integration.ae2.";
    private static final String GTMTHINGS_ME_PART_PACKAGE = "com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.";
    private static final String GTL_ME_PART_PACKAGE = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.";
    private static final String GTCEU_META_MACHINE_CLASS = "com.gregtechceu.gtceu.api.machine.MetaMachine";
    private static final String GTCEU_MULTIBLOCK_PART_CLASS = "com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine";
    private static final String GTCEU_GRID_CONNECTED_MACHINE_INTERFACE = "com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine";
    private static final String GTL_ME_IO_PART_MACHINE_INTERFACE = "org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOPartMachine";
    private static final Set<String> GTL_ME_TARGET_CLASSES = Set.of(
            "org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine",
            "org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine",
            "org.gtlcore.gtlcore.integration.wildcard.MEWildcardPatternBufferPartMachine");
    private static final Set<String> WIRELESS_ME_TARGET_IDS = Set.of(
            "me_input_bus",
            "me_stocking_input_bus",
            "me_output_bus",
            "me_input_hatch",
            "me_stocking_input_hatch",
            "me_output_hatch",
            "tag_filter_me_stock_bus_part_machine",
            "me_dual_hatch_stock_part_machine",
            "me_mini_pattern_buffer",
            "me_extend_pattern_buffer",
            "me_stocking_pattern_buffer",
            "me_final_pattern_buffer",
            "me_pattern_buffer_proxy",
            "me_extended_export_buffer",
            "me_extended_async_export_buffer",
            "me_molecular_assembler_io",
            "me_wildcard_pattern_buffer");
    private static final Set<String> QUICK_CONNECT_TOOLTIP_TARGET_IDS = Set.of(
            "me_input_bus",
            "me_stocking_input_bus",
            "me_output_bus",
            "me_input_hatch",
            "me_stocking_input_hatch",
            "me_output_hatch",
            "tag_filter_me_stock_bus_part_machine",
            "me_dual_hatch_stock_part_machine");

    public enum ConnectionResult {
        CONNECTED,
        ALREADY_CONNECTED,
        CORE_MISSING,
        CORE_NOT_CONNECTED,
        TARGET_MISSING,
        FAILED
    }

    private enum FavoriteBindResult {
        BOUND,
        TARGET_NOT_READY,
        CORE_UNAVAILABLE,
        INVALID_TARGET
    }

    private record PendingFavoriteBind(UUID frequency, UUID playerId, int age) {

        private PendingFavoriteBind nextTick() {
            return new PendingFavoriteBind(this.frequency, this.playerId, this.age + 1);
        }
    }

    private record WirelessTargetClassInfo(boolean knownTarget, boolean compatibleGridTarget,
                                           boolean meLikeName) {}

    private record RuntimeReflectionKey(Class<?> type, String methodName) {}

    private static final class GridNodePair {

        private final IGridNode first;
        private final IGridNode second;

        private GridNodePair(IGridNode first, IGridNode second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof GridNodePair other && this.first == other.first && this.second == other.second;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.first) + System.identityHashCode(this.second);
        }
    }

    private WirelessAeNetworkRuntime() {}

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(WirelessAeNetworkRuntime::onBlockBreak);
        forgeBus.addListener(WirelessAeNetworkRuntime::onBlockPlace);
        forgeBus.addListener(WirelessAeNetworkRuntime::onServerTick);
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            clearMemberBinding(serverLevel, event.getPos());
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            clearMemberBinding(serverLevel, event.getPos());
            requestFavoriteNetworkBindOnSneakPlace(serverLevel, event);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        beginTickCaches();
        try {
            processPendingFavoriteBinds(event.getServer());
            if (tickCounter % CONNECT_INTERVAL_TICKS == 0) {
                connectAll(event.getServer());
                return;
            }

            if (!REQUESTED_RECONNECTS.isEmpty()) {
                Set<UUID> requested = new HashSet<>(REQUESTED_RECONNECTS);
                REQUESTED_RECONNECTS.clear();
                for (UUID frequency : requested) {
                    connectFrequency(event.getServer(), frequency);
                }
            }
        } finally {
            endTickCaches();
        }
    }

    public static void requestReconnect(UUID frequency) {
        if (frequency != null) {
            REQUESTED_RECONNECTS.add(frequency);
        }
    }

    public static void connectNow(MinecraftServer server, UUID frequency) {
        connectFrequency(server, frequency);
    }

    public static ConnectionResult connectMemberNow(MinecraftServer server, UUID frequency, GlobalPos member) {
        return connectMemberNow(server, frequency, new WirelessAeSavedData.MemberKey(member, null));
    }

    public static ConnectionResult connectMemberNow(MinecraftServer server, UUID frequency,
                                                    WirelessAeSavedData.MemberKey member) {
        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        GlobalPos corePos = data.getCore(frequency);
        if (corePos == null) {
            disconnectFrequency(frequency);
            return ConnectionResult.CORE_MISSING;
        }

        WirelessNetworkCoreBlockEntity core = getLoadedCoreCached(server, frequency);
        if (core == null) {
            disconnectFrequency(frequency);
            return ConnectionResult.CORE_MISSING;
        }

        IGridNode bridgeNode = findBridgeNodeCached(frequency, core);
        if (bridgeNode == null) {
            disconnectFrequency(frequency);
            return ConnectionResult.CORE_NOT_CONNECTED;
        }

        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.computeIfAbsent(frequency, ignored -> new HashMap<>());
        ConnectionResult result = connectMember(frequency, corePos, member, bridgeNode, frequencyConnections);
        removeEmptyConnectionSet(frequency, frequencyConnections);
        return result;
    }

    public static void disconnectFrequency(UUID frequency) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.remove(frequency);
        if (frequencyConnections == null) {
            return;
        }

        for (Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection> entry : frequencyConnections.entrySet()) {
            forgetWiredConnectionCheck(entry.getKey());
            destroyQuietly(entry.getValue());
        }
    }

    public static void disconnectMember(UUID frequency, GlobalPos member) {
        disconnectMember(frequency, new WirelessAeSavedData.MemberKey(member, null));
    }

    public static void disconnectMember(UUID frequency, WirelessAeSavedData.MemberKey member) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.get(frequency);
        if (frequencyConnections == null) {
            forgetWiredConnectionCheck(member);
            return;
        }

        IGridConnection connection = frequencyConnections.remove(member);
        forgetWiredConnectionCheck(member);
        destroyQuietly(connection);
        if (frequencyConnections.isEmpty()) {
            CONNECTIONS.remove(frequency);
        }
    }

    public static WirelessNetworkCoreBlockEntity getLoadedCore(MinecraftServer server, UUID frequency) {
        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        GlobalPos corePos = data.getCore(frequency);
        if (corePos == null) {
            return null;
        }

        BlockEntity coreBlockEntity = getLoadedBlockEntity(server, corePos);
        if (coreBlockEntity instanceof WirelessNetworkCoreBlockEntity core) {
            return core;
        }
        return null;
    }

    public static boolean canUseAsWirelessTarget(ServerLevel level, BlockPos pos) {
        return isWirelessMeTarget(level, pos) && findTargetNode(level, pos) != null;
    }

    public static boolean canBindAsWirelessTarget(ServerLevel level, BlockPos pos) {
        return isWirelessMeTarget(level, pos) && findTargetNode(level, pos) != null;
    }

    public static boolean canBindAsWirelessTarget(ServerLevel level, BlockPos pos, Direction side) {
        if (!isWirelessMeTarget(level, pos)) {
            return false;
        }
        if (side != null && findTargetNode(level, pos, side) != null) {
            return true;
        }
        return findTargetNode(level, pos) != null;
    }

    public static boolean canOpenWirelessTargetMenu(Level level, BlockPos pos) {
        pos = resolveWirelessTargetPos(level, pos);
        return isWirelessMeTarget(level, pos);
    }

    public static boolean isWirelessMeTarget(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof WirelessNetworkCoreBlockEntity) {
            return false;
        }

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        if (isWirelessMeTargetObject(blockEntity, blockId)) {
            return true;
        }

        Object metaMachine = invokeObject(blockEntity, "getMetaMachine");
        if (isWirelessMeTargetObject(metaMachine, blockId)) {
            return true;
        }

        return isWirelessMeTargetId(blockId);
    }

    public static BlockPos resolveWirelessTargetPos(Level level, BlockPos pos) {
        if (level == null) {
            return pos;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockPos resolved = findReferencedTargetPos(level, blockEntity);
        if (resolved != null) {
            return resolved;
        }

        Object metaMachine = invokeObject(blockEntity, "getMetaMachine");
        resolved = findReferencedTargetPos(level, metaMachine);
        return resolved == null ? pos : resolved;
    }

    public static WirelessAeSavedData.MemberKey resolveWirelessTarget(ServerLevel level, BlockPos pos, Direction side) {
        return resolveWirelessTarget(level, pos, side, null);
    }

    public static WirelessAeSavedData.MemberKey resolveWirelessTarget(ServerLevel level, BlockPos pos, Direction side,
                                                                      Vec3 hitLocation) {
        BlockPos targetPos = resolveWirelessTargetPos(level, pos);
        Direction targetSide = targetPos.equals(pos) ? resolvePartSide(level, targetPos, side, hitLocation) : null;
        return WirelessAeSavedData.MemberKey.of(level.dimension(), targetPos, targetSide);
    }

    public static IGridNode findBridgeNode(WirelessNetworkCoreBlockEntity core) {
        IGridNode coreNode = core.getActionableNode();
        if (coreNode != null) {
            try {
                Direction connectionSide = core.getConnectionSide();
                IGridConnection connection = coreNode.getInWorldConnections().get(connectionSide);
                if (connection != null) {
                    IGridNode bridgeNode = connection.getOtherSide(coreNode);
                    if (isUsableBridgeNode(coreNode, bridgeNode)) {
                        return bridgeNode;
                    }
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        if (!(core.getLevel() instanceof ServerLevel level)) {
            return null;
        }

        Direction connectionSide = core.getConnectionSide();
        try {
            BlockPos neighborPos = core.getBlockPos().relative(connectionSide);
            IGridNode bridgeNode = GridHelper.getExposedNode(level, neighborPos, connectionSide.getOpposite());
            if (isUsableBridgeNode(coreNode, bridgeNode)) {
                return bridgeNode;
            }
        } catch (RuntimeException ignored) {
            // Some third-party block entities throw while their multiblock is rebuilding.
        }
        return null;
    }

    public static String shortFrequency(UUID frequency) {
        return frequency.toString().substring(0, 8);
    }

    public static boolean isMemberConnected(MinecraftServer server, UUID frequency, WirelessAeSavedData.MemberKey member) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.get(frequency);
        if (hasStoredConnection(frequencyConnections, member)) {
            return true;
        }

        WirelessNetworkCoreBlockEntity core = getLoadedCore(server, frequency);
        if (core == null) {
            return false;
        }

        IGridNode bridgeNode = findBridgeNode(core);
        IGridNode targetNode = findTargetNode(server, member);
        return bridgeNode != null && targetNode != null && (areConnected(bridgeNode, targetNode) || isSameGrid(bridgeNode, targetNode));
    }

    public static boolean hasWirelessConnection(UUID frequency, WirelessAeSavedData.MemberKey member) {
        return hasStoredConnection(CONNECTIONS.get(frequency), member);
    }

    public static UUID findConnectedNetworkFrequency(MinecraftServer server, WirelessAeSavedData.MemberKey member) {
        UUID wiredFrequency = findWiredNetworkFrequency(server, member);
        if (wiredFrequency != null) {
            return wiredFrequency;
        }

        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        UUID frequency = data.getMemberNetwork(member);
        if (frequency != null && isMemberConnected(server, frequency, member)) {
            return frequency;
        }

        return null;
    }

    public static UUID findWiredNetworkFrequency(MinecraftServer server, WirelessAeSavedData.MemberKey member) {
        return findWiredNetworkFrequency(server, member, true);
    }

    private static UUID findWiredNetworkFrequency(MinecraftServer server, WirelessAeSavedData.MemberKey member,
                                                  boolean includeWirelessBackedCable) {
        IGridNode targetNode = findTargetNode(server, member);
        if (targetNode == null) {
            return null;
        }

        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        for (WirelessAeSavedData.NetworkInfo network : data.getNetworkInfo()) {
            WirelessNetworkCoreBlockEntity core = getLoadedCoreCached(server, network.frequency());
            if (core == null) {
                continue;
            }

            IGridNode bridgeNode = findBridgeNodeCached(network.frequency(), core);
            if (areConnectedInWorld(bridgeNode, targetNode)) {
                return network.frequency();
            }
            if (includeWirelessBackedCable && isConnectedThroughWirelessBackedCable(server, network.frequency(), bridgeNode, member, targetNode)) {
                return network.frequency();
            }
        }
        return null;
    }

    private static boolean isConnectedThroughWirelessBackedCable(MinecraftServer server, UUID frequency,
                                                                 IGridNode bridgeNode,
                                                                 WirelessAeSavedData.MemberKey member,
                                                                 IGridNode targetNode) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.get(frequency);
        if (frequencyConnections == null || frequencyConnections.isEmpty()) {
            return false;
        }

        for (Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection> entry : frequencyConnections.entrySet()) {
            WirelessAeSavedData.MemberKey connectedMember = entry.getKey();
            if (connectedMember.pos().equals(member.pos())) {
                continue;
            }

            IGridNode connectedNode = findTargetNode(server, connectedMember);
            if (connectedNode == null || !connectionMatches(entry.getValue(), bridgeNode, connectedNode)) {
                continue;
            }

            if (areConnectedInWorld(connectedNode, targetNode)) {
                return true;
            }
        }
        return false;
    }

    private static void clearMemberBinding(ServerLevel level, BlockPos pos) {
        GlobalPos member = GlobalPos.of(level.dimension(), pos);
        forgetWiredConnectionChecksAt(member);
        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        for (UUID frequency : data.removeMembersAt(member)) {
            disconnectMembersAt(frequency, member);
        }
    }

    private static void requestFavoriteNetworkBindOnSneakPlace(ServerLevel level, BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isShiftKeyDown()) {
            return;
        }

        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        UUID favoriteNetwork = data.getFavoriteNetwork();
        if (favoriteNetwork == null) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!isWirelessMeTarget(level, pos) && !isWirelessMeTargetId(ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock()))) {
            return;
        }

        FavoriteBindResult result = bindFavoriteNetwork(level, pos, favoriteNetwork, player, true);
        if (result == FavoriteBindResult.TARGET_NOT_READY) {
            queuePendingFavoriteBind(level, pos, favoriteNetwork, player);
        }
    }

    private static void processPendingFavoriteBinds(MinecraftServer server) {
        Iterator<Map.Entry<GlobalPos, PendingFavoriteBind>> iterator = PENDING_FAVORITE_BINDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GlobalPos, PendingFavoriteBind> entry = iterator.next();
            GlobalPos pos = entry.getKey();
            PendingFavoriteBind pending = entry.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null || !level.hasChunkAt(pos.pos())) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            FavoriteBindResult result = bindFavoriteNetwork(level, pos.pos(), pending.frequency(), player, true);
            if (result == FavoriteBindResult.BOUND || result == FavoriteBindResult.INVALID_TARGET || result == FavoriteBindResult.CORE_UNAVAILABLE) {
                iterator.remove();
                continue;
            }

            if (pending.age() >= FAVORITE_BIND_RETRY_TICKS) {
                iterator.remove();
            } else {
                entry.setValue(pending.nextTick());
            }
        }
    }

    private static void queuePendingFavoriteBind(ServerLevel level, BlockPos pos, UUID favoriteNetwork, Player player) {
        PENDING_FAVORITE_BINDS.put(
                GlobalPos.of(level.dimension(), pos),
                new PendingFavoriteBind(favoriteNetwork, player.getUUID(), 0));
    }

    private static FavoriteBindResult bindFavoriteNetwork(ServerLevel level, BlockPos pos, UUID favoriteNetwork,
                                                          Player player, boolean notify) {
        WirelessAeSavedData.MemberKey target = resolveWirelessTarget(level, pos, null);
        if (!isWirelessMeTarget(level, target.blockPos())) {
            return FavoriteBindResult.INVALID_TARGET;
        }
        if (findTargetNode(level.getServer(), target) == null) {
            return FavoriteBindResult.TARGET_NOT_READY;
        }

        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        WirelessNetworkCoreBlockEntity core = getLoadedCore(level.getServer(), favoriteNetwork);
        if (core == null || !core.isLinkedToAeNetwork()) {
            if (notify && player != null) {
                player.displayClientMessage(
                        Component.translatable("message.gtlcore.wireless_bookmark.favorite_unavailable"),
                        true);
            }
            return FavoriteBindResult.CORE_UNAVAILABLE;
        }

        for (UUID removedNetwork : data.removeMembersAt(target.pos())) {
            disconnectMembersAt(removedNetwork, target.pos());
        }

        ConnectionResult result = connectMemberNow(level.getServer(), favoriteNetwork, target);
        data.addMember(favoriteNetwork, target);
        requestReconnect(favoriteNetwork);

        boolean pending = result == ConnectionResult.TARGET_MISSING || result == ConnectionResult.FAILED;
        if (notify && player != null) {
            player.displayClientMessage(
                    Component.translatable(
                            pending ? "message.gtlcore.wireless_target.linked_target_pending" : "message.gtlcore.wireless_target.linked_target",
                            data.getNetworkName(favoriteNetwork)),
                    true);
        }
        return pending ? FavoriteBindResult.TARGET_NOT_READY : FavoriteBindResult.BOUND;
    }

    public static void disconnectMembersAt(UUID frequency, GlobalPos member) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.get(frequency);
        if (frequencyConnections == null) {
            return;
        }

        Iterator<Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection>> iterator = frequencyConnections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection> entry = iterator.next();
            if (entry.getKey().pos().equals(member)) {
                forgetWiredConnectionCheck(entry.getKey());
                destroyQuietly(entry.getValue());
                iterator.remove();
            }
        }
        if (frequencyConnections.isEmpty()) {
            CONNECTIONS.remove(frequency);
        }
    }

    private static void connectAll(MinecraftServer server) {
        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        for (UUID frequency : data.getFrequencies()) {
            connectFrequency(server, frequency);
        }
    }

    private static void connectFrequency(MinecraftServer server, UUID frequency) {
        WirelessAeSavedData data = WirelessAeSavedData.get(server);
        GlobalPos corePos = data.getCore(frequency);
        if (corePos == null) {
            disconnectFrequency(frequency);
            return;
        }

        WirelessNetworkCoreBlockEntity core = getLoadedCoreCached(server, frequency);
        if (core == null) {
            disconnectFrequency(frequency);
            return;
        }

        IGridNode bridgeNode = findBridgeNodeCached(frequency, core);
        if (bridgeNode == null) {
            disconnectFrequency(frequency);
            return;
        }

        Set<WirelessAeSavedData.MemberKey> members = data.getMembers(frequency);
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.computeIfAbsent(frequency, ignored -> new HashMap<>());

        Iterator<Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection>> existing = frequencyConnections.entrySet().iterator();
        while (existing.hasNext()) {
            Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection> entry = existing.next();
            if (!members.contains(entry.getKey())) {
                forgetWiredConnectionCheck(entry.getKey());
                destroyQuietly(entry.getValue());
                existing.remove();
            }
        }

        for (WirelessAeSavedData.MemberKey member : members) {
            if (member.pos().equals(corePos)) {
                continue;
            }

            connectMember(frequency, corePos, member, bridgeNode, frequencyConnections);
        }

        removeEmptyConnectionSet(frequency, frequencyConnections);
    }

    private static ConnectionResult connectMember(UUID frequency, GlobalPos corePos,
                                                  WirelessAeSavedData.MemberKey member,
                                                  IGridNode bridgeNode,
                                                  Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections) {
        MinecraftServer server = bridgeNode.getLevel().getServer();
        ServerLevel targetLevel = server.getLevel(member.pos().dimension());
        if (targetLevel == null || !isWirelessMeTarget(targetLevel, member.blockPos())) {
            forgetWiredConnectionCheck(member);
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.TARGET_MISSING;
        }

        IGridNode targetNode = findTargetNode(server, member);
        if (targetNode == null) {
            forgetWiredConnectionCheck(member);
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.TARGET_MISSING;
        }

        IGridConnection currentConnection = frequencyConnections.get(member);
        boolean currentConnectionMatches = connectionMatches(currentConnection, bridgeNode, targetNode);
        if (currentConnectionMatches && !shouldRecheckWiredConnection(member)) {
            return ConnectionResult.ALREADY_CONNECTED;
        }

        if (currentConnectionMatches) {
            if (findWiredNetworkFrequency(server, member, false) != null) {
                forgetWiredConnectionCheck(member);
                destroyQuietly(currentConnection);
                frequencyConnections.remove(member);
                return ConnectionResult.ALREADY_CONNECTED;
            }
            markWiredConnectionChecked(member);
            return ConnectionResult.ALREADY_CONNECTED;
        }

        if (targetNode == bridgeNode) {
            forgetWiredConnectionCheck(member);
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.ALREADY_CONNECTED;
        }

        if (areConnected(bridgeNode, targetNode) || isSameGrid(bridgeNode, targetNode)) {
            forgetWiredConnectionCheck(member);
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.ALREADY_CONNECTED;
        }

        if (findWiredNetworkFrequency(server, member, true) != null) {
            forgetWiredConnectionCheck(member);
            destroyQuietly(currentConnection);
            frequencyConnections.remove(member);
            return ConnectionResult.ALREADY_CONNECTED;
        }
        markWiredConnectionChecked(member);

        destroyQuietly(currentConnection);
        frequencyConnections.remove(member);

        try {
            frequencyConnections.put(member, GridHelper.createConnection(bridgeNode, targetNode));
            return ConnectionResult.CONNECTED;
        } catch (RuntimeException error) {
            GTLCore.LOGGER.debug(
                    "Failed to create wireless ME connection {} -> {} for frequency {}",
                    corePos,
                    member,
                    shortFrequency(frequency),
                    error);
            return ConnectionResult.FAILED;
        }
    }

    private static boolean hasStoredConnection(Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections,
                                               WirelessAeSavedData.MemberKey member) {
        if (frequencyConnections == null) {
            return false;
        }
        if (frequencyConnections.get(member) != null) {
            return true;
        }
        if (member.side() != null) {
            return frequencyConnections.get(new WirelessAeSavedData.MemberKey(member.pos(), null)) != null;
        }
        for (Map.Entry<WirelessAeSavedData.MemberKey, IGridConnection> entry : frequencyConnections.entrySet()) {
            if (entry.getValue() != null && entry.getKey().pos().equals(member.pos())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsableBridgeNode(IGridNode coreNode, IGridNode bridgeNode) {
        if (bridgeNode == null || bridgeNode == coreNode) {
            return false;
        }

        try {
            if (bridgeNode.getOwner() instanceof WirelessNetworkCoreBlockEntity) {
                return false;
            }
            return bridgeNode.getGrid() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void removeEmptyConnectionSet(UUID frequency,
                                                 Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections) {
        if (frequencyConnections.isEmpty()) {
            CONNECTIONS.remove(frequency);
        }
    }

    private static void beginTickCaches() {
        tickCacheActive = true;
        TICK_TARGET_NODE_CACHE.clear();
        TICK_CORE_CACHE.clear();
        TICK_BRIDGE_NODE_CACHE.clear();
        TICK_IN_WORLD_CONNECTION_CACHE.clear();
    }

    private static void endTickCaches() {
        tickCacheActive = false;
        TICK_TARGET_NODE_CACHE.clear();
        TICK_CORE_CACHE.clear();
        TICK_BRIDGE_NODE_CACHE.clear();
        TICK_IN_WORLD_CONNECTION_CACHE.clear();
    }

    private static boolean shouldRecheckWiredConnection(WirelessAeSavedData.MemberKey member) {
        Integer nextCheckTick = NEXT_WIRED_RECHECK_TICKS.get(member);
        return nextCheckTick == null || tickCounter >= nextCheckTick;
    }

    private static void markWiredConnectionChecked(WirelessAeSavedData.MemberKey member) {
        NEXT_WIRED_RECHECK_TICKS.put(member, tickCounter + WIRED_RECHECK_INTERVAL_TICKS);
    }

    private static void forgetWiredConnectionCheck(WirelessAeSavedData.MemberKey member) {
        NEXT_WIRED_RECHECK_TICKS.remove(member);
    }

    private static void forgetWiredConnectionChecksAt(GlobalPos pos) {
        NEXT_WIRED_RECHECK_TICKS.keySet().removeIf(member -> member.pos().equals(pos));
    }

    private static WirelessNetworkCoreBlockEntity getLoadedCoreCached(MinecraftServer server, UUID frequency) {
        if (!tickCacheActive) {
            return getLoadedCore(server, frequency);
        }
        if (TICK_CORE_CACHE.containsKey(frequency)) {
            return TICK_CORE_CACHE.get(frequency);
        }

        WirelessNetworkCoreBlockEntity core = getLoadedCore(server, frequency);
        TICK_CORE_CACHE.put(frequency, core);
        return core;
    }

    private static IGridNode findBridgeNodeCached(UUID frequency, WirelessNetworkCoreBlockEntity core) {
        if (!tickCacheActive) {
            return findBridgeNode(core);
        }
        if (TICK_BRIDGE_NODE_CACHE.containsKey(frequency)) {
            return TICK_BRIDGE_NODE_CACHE.get(frequency);
        }

        IGridNode bridgeNode = findBridgeNode(core);
        TICK_BRIDGE_NODE_CACHE.put(frequency, bridgeNode);
        return bridgeNode;
    }

    private static BlockEntity getLoadedBlockEntity(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.hasChunkAt(pos.pos())) {
            return null;
        }
        return level.getBlockEntity(pos.pos());
    }

    private static IGridNode findTargetNode(MinecraftServer server, WirelessAeSavedData.MemberKey member) {
        if (tickCacheActive && TICK_TARGET_NODE_CACHE.containsKey(member)) {
            return TICK_TARGET_NODE_CACHE.get(member);
        }

        IGridNode node = findTargetNodeUncached(server, member);
        if (tickCacheActive) {
            TICK_TARGET_NODE_CACHE.put(member, node);
        }
        return node;
    }

    private static IGridNode findTargetNodeUncached(MinecraftServer server, WirelessAeSavedData.MemberKey member) {
        GlobalPos pos = member.pos();
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.hasChunkAt(pos.pos())) {
            return null;
        }
        return findTargetNode(level, pos.pos(), member.side());
    }

    private static IGridNode findTargetNode(ServerLevel level, BlockPos pos, Direction side) {
        if (side != null) {
            IGridNode partNode = findPartNode(level, pos, side);
            if (partNode != null) {
                return partNode;
            }

            if (level.getBlockEntity(pos) instanceof IPartHost) {
                return findTargetNode(level, pos);
            }

            try {
                IGridNode exposedNode = GridHelper.getExposedNode(level, pos, side);
                if (exposedNode != null) {
                    return exposedNode;
                }
            } catch (RuntimeException ignored) {
                // Some third-party block entities throw while their multiblock is rebuilding.
            }
        }
        return findTargetNode(level, pos);
    }

    private static IGridNode findTargetNode(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof IPartHost) {
            for (Direction direction : Direction.values()) {
                IGridNode node = findPartNode(level, pos, direction);
                if (node != null) {
                    return node;
                }
            }
        }

        IGridNode node = findNode(blockEntity);
        if (node != null) {
            return node;
        }

        for (Direction direction : Direction.values()) {
            try {
                node = GridHelper.getExposedNode(level, pos, direction);
                if (node != null) {
                    return node;
                }
            } catch (RuntimeException ignored) {
                // Some third-party block entities throw while their multiblock is rebuilding.
            }
        }
        return null;
    }

    private static IGridNode findPartNode(ServerLevel level, BlockPos pos, Direction side) {
        if (side == null) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IPartHost host)) {
            return null;
        }

        try {
            IPart part = host.getPart(side);
            if (part == null) {
                return null;
            }
            IGridNode node = part.getGridNode();
            if (node != null) {
                return node;
            }
            node = part.getExternalFacingNode();
            if (node != null) {
                return node;
            }
            return findNode(part, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Direction resolvePartSide(ServerLevel level, BlockPos pos, Direction clickedSide, Vec3 hitLocation) {
        Direction selectedSide = findSelectedPartSide(level, pos, hitLocation);
        if (selectedSide != null) {
            return selectedSide;
        }
        if (findPartNode(level, pos, clickedSide) != null) {
            return clickedSide;
        }
        return findFirstPartSide(level, pos);
    }

    private static Direction findSelectedPartSide(ServerLevel level, BlockPos pos, Vec3 hitLocation) {
        if (hitLocation == null) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IPartHost host)) {
            return null;
        }

        try {
            appeng.api.parts.SelectedPart selectedPart = host.selectPartWorld(hitLocation);
            if (selectedPart == null || selectedPart.part == null) {
                return null;
            }
            if (selectedPart.side != null && findPartNode(level, pos, selectedPart.side) != null) {
                return selectedPart.side;
            }
            for (Direction direction : Direction.values()) {
                if (host.getPart(direction) == selectedPart.part && findPartNode(level, pos, direction) != null) {
                    return direction;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Direction findFirstPartSide(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IPartHost host)) {
            return null;
        }

        for (Direction direction : Direction.values()) {
            try {
                if (host.getPart(direction) != null && findPartNode(level, pos, direction) != null) {
                    return direction;
                }
            } catch (RuntimeException ignored) {
                // Keep scanning other sides.
            }
        }
        return null;
    }

    private static IGridNode findNode(BlockEntity blockEntity) {
        return findNode(blockEntity, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static IGridNode findNode(Object target, Set<Object> visited, int depth) {
        if (target == null || depth > 5 || !visited.add(target)) {
            return null;
        }

        try {
            if (target instanceof IManagedGridNode managedGridNode) {
                IGridNode node = managedGridNode.getNode();
                if (node != null) {
                    return node;
                }
            }

            IGridNode clusterNode = findClusterNode(target);
            if (clusterNode != null) {
                return clusterNode;
            }

            if (target instanceof IActionHost actionHost) {
                IGridNode node = actionHost.getActionableNode();
                if (node != null) {
                    return node;
                }
            }

            if (target instanceof IInWorldGridNodeHost host) {
                for (Direction direction : Direction.values()) {
                    IGridNode node = host.getGridNode(direction);
                    if (node != null) {
                        return node;
                    }
                }
            }

            IGridNode noSideNode = invokeGridNode(target, "getGridNode");
            if (noSideNode != null) {
                return noSideNode;
            }

            IGridNode mainNode = invokeManagedGridNode(target);
            if (mainNode != null) {
                return mainNode;
            }

            IGridNode directNode = invokeGridNode(target, "getNode");
            if (directNode != null) {
                return directNode;
            }

            for (String nestedMethod : new String[] { "getMetaMachine", "getMachine", "getNodeHolder", "getMETrait" }) {
                IGridNode nestedNode = findNode(invokeObject(target, nestedMethod), visited, depth + 1);
                if (nestedNode != null) {
                    return nestedNode;
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static IGridNode findClusterNode(Object target) {
        try {
            Method getCluster = findPublicNoArgMethod(target.getClass(), "getCluster");
            if (getCluster == null) {
                return null;
            }
            Object cluster = getCluster.invoke(target);
            if (cluster == null) {
                return null;
            }
            return invokeGridNode(cluster, "getNode");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static IGridNode invokeGridNode(Object target, String methodName) {
        try {
            Method method = findPublicNoArgMethod(target.getClass(), methodName);
            if (method == null) {
                return null;
            }
            Object result = method.invoke(target);
            if (result instanceof IGridNode node) {
                return node;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static IGridNode invokeManagedGridNode(Object target) {
        try {
            Method method = findPublicNoArgMethod(target.getClass(), "getMainNode");
            if (method == null) {
                return null;
            }
            Object result = method.invoke(target);
            if (result instanceof IManagedGridNode managedGridNode) {
                return managedGridNode.getNode();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Object invokeObject(Object target, String methodName) {
        try {
            Method method = findPublicNoArgMethod(target.getClass(), methodName);
            if (method == null) {
                return null;
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static BlockPos findReferencedTargetPos(Level level, Object target) {
        if (target == null) {
            return null;
        }

        for (String methodName : new String[] { "getBufferPos", "getMainPos" }) {
            BlockPos pos = invokeBlockPos(target, methodName);
            if (isResolvableTargetPos(level, pos)) {
                return pos;
            }
        }

        for (String methodName : new String[] { "getBuffer" }) {
            Object referencedTarget = invokeObject(target, methodName);
            BlockPos pos = invokeBlockPos(referencedTarget, "getPos");
            if (isResolvableTargetPos(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos invokeBlockPos(Object target, String methodName) {
        try {
            Method method = findPublicNoArgMethod(target.getClass(), methodName);
            if (method == null) {
                return null;
            }
            Object result = method.invoke(target);
            if (result instanceof BlockPos pos) {
                return pos;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static boolean isResolvableTargetPos(Level level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos) && !(level.getBlockEntity(pos) instanceof WirelessNetworkCoreBlockEntity);
    }

    private static boolean isWirelessMeTargetObject(Object target, ResourceLocation blockId) {
        if (target == null) {
            return false;
        }

        WirelessTargetClassInfo classInfo = WIRELESS_TARGET_CLASS_CACHE.computeIfAbsent(
                target.getClass(),
                WirelessAeNetworkRuntime::inspectWirelessTargetClass);
        return classInfo.knownTarget() ||
                (classInfo.compatibleGridTarget() &&
                        (classInfo.meLikeName() || isCompatibleExternalMeLikeId(blockId)));
    }

    private static WirelessTargetClassInfo inspectWirelessTargetClass(Class<?> type) {
        String className = type.getName();
        boolean knownTarget = className.startsWith(GTCEU_ME_PART_PACKAGE) ||
                className.startsWith(GTMTHINGS_ME_PART_PACKAGE) ||
                className.startsWith(GTL_ME_PART_PACKAGE) ||
                GTL_ME_TARGET_CLASSES.contains(className);
        boolean compatibleGridTarget = hasMachinePartMarker(type) && hasGridNodeAccess(type);
        return new WirelessTargetClassInfo(knownTarget, compatibleGridTarget, isMeLikeClassName(className));
    }

    private static boolean hasMachinePartMarker(Class<?> type) {
        return hasTypeName(type, GTCEU_META_MACHINE_CLASS) ||
                hasTypeName(type, GTCEU_MULTIBLOCK_PART_CLASS) ||
                hasTypeName(type, GTCEU_GRID_CONNECTED_MACHINE_INTERFACE);
    }

    private static boolean hasGridNodeAccess(Class<?> type) {
        return IManagedGridNode.class.isAssignableFrom(type) ||
                IActionHost.class.isAssignableFrom(type) ||
                IInWorldGridNodeHost.class.isAssignableFrom(type) ||
                hasTypeName(type, GTCEU_GRID_CONNECTED_MACHINE_INTERFACE) ||
                hasTypeName(type, GTL_ME_IO_PART_MACHINE_INTERFACE) ||
                hasNoArgMethod(type, "getMainNode") ||
                hasNoArgMethod(type, "getNodeHolder") ||
                hasNoArgMethod(type, "getMETrait");
    }

    private static boolean hasTypeName(Class<?> type, String typeName) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(typeName)) {
            return true;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (hasTypeName(interfaceType, typeName)) {
                return true;
            }
        }
        return hasTypeName(type.getSuperclass(), typeName);
    }

    private static boolean hasNoArgMethod(Class<?> type, String methodName) {
        return findPublicNoArgMethod(type, methodName) != null;
    }

    private static Method findPublicNoArgMethod(Class<?> type, String methodName) {
        if (type == null) {
            return null;
        }
        return RUNTIME_METHOD_CACHE.computeIfAbsent(
                new RuntimeReflectionKey(type, methodName),
                WirelessAeNetworkRuntime::findPublicNoArgMethodUncached)
                .orElse(null);
    }

    private static Optional<Method> findPublicNoArgMethodUncached(RuntimeReflectionKey key) {
        try {
            Method method = key.type().getMethod(key.methodName());
            method.setAccessible(true);
            return Optional.of(method);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isMeLikeClassName(String className) {
        int simpleNameStart = className.lastIndexOf('.') + 1;
        String simpleName = className.substring(simpleNameStart);
        String lowerClassName = className.toLowerCase(Locale.ROOT);
        String lowerSimpleName = simpleName.toLowerCase(Locale.ROOT);
        return simpleName.startsWith("ME") ||
                simpleName.startsWith("AE") ||
                lowerClassName.contains(".appeng.") ||
                lowerSimpleName.contains("appeng") ||
                lowerSimpleName.contains("ae2") ||
                lowerSimpleName.contains("patternbuffer");
    }

    private static boolean isCompatibleExternalMeLikeId(ResourceLocation blockId) {
        if (blockId == null || "minecraft".equals(blockId.getNamespace())) {
            return false;
        }
        return isMeLikePath(blockId.getPath());
    }

    public static boolean isWirelessMeTargetId(ResourceLocation blockId) {
        if (blockId == null) {
            return false;
        }

        String namespace = blockId.getNamespace();
        String path = blockId.getPath();
        return WIRELESS_ME_TARGET_IDS.contains(path) ||
                isCompatibleExternalMeLikeId(blockId) ||
                ("gtmthings".equals(namespace) && isGtmthingsMeLikePath(path));
    }

    public static boolean shouldShowQuickConnectTooltip(ResourceLocation itemId) {
        return itemId != null && QUICK_CONNECT_TOOLTIP_TARGET_IDS.contains(itemId.getPath());
    }

    private static boolean isGtmthingsMeLikePath(String path) {
        return isMeLikePath(path);
    }

    private static boolean isMeLikePath(String path) {
        return path.startsWith("me_") ||
                path.contains("_me_") ||
                path.startsWith("ae_") ||
                path.contains("_ae_") ||
                path.contains("appeng") ||
                path.contains("ae2");
    }

    private static boolean connectionMatches(IGridConnection connection, IGridNode coreNode, IGridNode targetNode) {
        if (connection == null) {
            return false;
        }
        try {
            return connection.getOtherSide(coreNode) == targetNode;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean areConnected(IGridNode coreNode, IGridNode targetNode) {
        try {
            for (IGridConnection connection : coreNode.getConnections()) {
                if (connection.getOtherSide(coreNode) == targetNode) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static boolean areConnectedInWorld(IGridNode coreNode, IGridNode targetNode) {
        if (coreNode == null || targetNode == null) {
            return false;
        }
        if (tickCacheActive) {
            GridNodePair pair = new GridNodePair(coreNode, targetNode);
            Boolean cached = TICK_IN_WORLD_CONNECTION_CACHE.get(pair);
            if (cached != null) {
                return cached;
            }

            boolean connected = areConnectedInWorldUncached(coreNode, targetNode);
            TICK_IN_WORLD_CONNECTION_CACHE.put(pair, connected);
            return connected;
        }

        return areConnectedInWorldUncached(coreNode, targetNode);
    }

    private static boolean areConnectedInWorldUncached(IGridNode coreNode, IGridNode targetNode) {
        if (coreNode == targetNode) {
            return true;
        }

        Set<IGridNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<IGridNode> pending = new ArrayDeque<>();
        visited.add(coreNode);
        pending.add(coreNode);

        while (!pending.isEmpty()) {
            IGridNode node = pending.removeFirst();
            Iterable<IGridConnection> connections;
            try {
                connections = node.getConnections();
            } catch (RuntimeException ignored) {
                continue;
            }

            for (IGridConnection connection : connections) {
                if (!isInWorldConnection(connection)) {
                    continue;
                }

                IGridNode otherNode;
                try {
                    otherNode = connection.getOtherSide(node);
                } catch (RuntimeException ignored) {
                    continue;
                }

                if (otherNode == targetNode) {
                    return true;
                }
                if (otherNode != null && visited.add(otherNode)) {
                    pending.add(otherNode);
                }
            }
        }
        return false;
    }

    private static boolean isInWorldConnection(IGridConnection connection) {
        if (connection == null) {
            return false;
        }
        try {
            return connection.isInWorld();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSameGrid(IGridNode coreNode, IGridNode targetNode) {
        try {
            return coreNode.getGrid() != null && coreNode.getGrid() == targetNode.getGrid();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void destroyQuietly(IGridConnection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.destroy();
        } catch (RuntimeException ignored) {
            // AE2 may already have destroyed the connection while unloading a node.
        }
    }
}
