package org.gtlcore.gtlcore.integration.ae2.wireless;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WirelessAeNetworkRuntime {

    private static final int CONNECT_INTERVAL_TICKS = 20;

    private static final Map<UUID, Map<WirelessAeSavedData.MemberKey, IGridConnection>> CONNECTIONS = new HashMap<>();
    private static final Set<UUID> REQUESTED_RECONNECTS = new HashSet<>();
    private static int tickCounter;
    private static final String GTCEU_ME_PART_PACKAGE = "com.gregtechceu.gtceu.integration.ae2.";
    private static final String GTMTHINGS_ME_PART_PACKAGE = "com.hepdd.gtmthings.common.block.machine.multiblock.part.appeng.";
    private static final String GTL_ME_PART_PACKAGE = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.";
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

    public enum ConnectionResult {
        CONNECTED,
        ALREADY_CONNECTED,
        CORE_MISSING,
        CORE_NOT_CONNECTED,
        TARGET_MISSING,
        FAILED
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
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
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

        BlockEntity coreBlockEntity = getLoadedBlockEntity(server, corePos);
        if (!(coreBlockEntity instanceof WirelessNetworkCoreBlockEntity core)) {
            disconnectFrequency(frequency);
            return ConnectionResult.CORE_MISSING;
        }

        IGridNode bridgeNode = findBridgeNode(core);
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

        for (IGridConnection connection : frequencyConnections.values()) {
            destroyQuietly(connection);
        }
    }

    public static void disconnectMember(UUID frequency, GlobalPos member) {
        disconnectMember(frequency, new WirelessAeSavedData.MemberKey(member, null));
    }

    public static void disconnectMember(UUID frequency, WirelessAeSavedData.MemberKey member) {
        Map<WirelessAeSavedData.MemberKey, IGridConnection> frequencyConnections = CONNECTIONS.get(frequency);
        if (frequencyConnections == null) {
            return;
        }

        IGridConnection connection = frequencyConnections.remove(member);
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

        if (isWirelessMeTargetObject(blockEntity)) {
            return true;
        }

        Object metaMachine = invokeObject(blockEntity, "getMetaMachine");
        if (isWirelessMeTargetObject(metaMachine)) {
            return true;
        }

        return isWirelessMeTargetId(ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()));
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

    private static void clearMemberBinding(ServerLevel level, BlockPos pos) {
        GlobalPos member = GlobalPos.of(level.dimension(), pos);
        WirelessAeSavedData data = WirelessAeSavedData.get(level.getServer());
        for (UUID frequency : data.removeMembersAt(member)) {
            disconnectMembersAt(frequency, member);
        }
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

        BlockEntity coreBlockEntity = getLoadedBlockEntity(server, corePos);
        if (!(coreBlockEntity instanceof WirelessNetworkCoreBlockEntity core)) {
            disconnectFrequency(frequency);
            return;
        }

        IGridNode bridgeNode = findBridgeNode(core);
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
        ServerLevel targetLevel = bridgeNode.getLevel().getServer().getLevel(member.pos().dimension());
        if (targetLevel == null || !isWirelessMeTarget(targetLevel, member.blockPos())) {
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.TARGET_MISSING;
        }

        IGridNode targetNode = findTargetNode(bridgeNode.getLevel().getServer(), member);
        if (targetNode == null) {
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.TARGET_MISSING;
        }

        if (targetNode == bridgeNode) {
            destroyQuietly(frequencyConnections.remove(member));
            return ConnectionResult.ALREADY_CONNECTED;
        }

        IGridConnection currentConnection = frequencyConnections.get(member);
        if (connectionMatches(currentConnection, bridgeNode, targetNode)) {
            return ConnectionResult.ALREADY_CONNECTED;
        }
        destroyQuietly(currentConnection);
        frequencyConnections.remove(member);

        if (areConnected(bridgeNode, targetNode) || isSameGrid(bridgeNode, targetNode)) {
            return ConnectionResult.ALREADY_CONNECTED;
        }

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

    private static BlockEntity getLoadedBlockEntity(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.hasChunkAt(pos.pos())) {
            return null;
        }
        return level.getBlockEntity(pos.pos());
    }

    private static IGridNode findTargetNode(MinecraftServer server, WirelessAeSavedData.MemberKey member) {
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
            Method getCluster = target.getClass().getMethod("getCluster");
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
            Method method = target.getClass().getMethod(methodName);
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
            Method method = target.getClass().getMethod("getMainNode");
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
            Method method = target.getClass().getMethod(methodName);
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
            Method method = target.getClass().getMethod(methodName);
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

    private static boolean isWirelessMeTargetObject(Object target) {
        if (target == null) {
            return false;
        }

        String className = target.getClass().getName();
        return className.startsWith(GTCEU_ME_PART_PACKAGE) || className.startsWith(GTMTHINGS_ME_PART_PACKAGE) || className.startsWith(GTL_ME_PART_PACKAGE) || className.startsWith("appeng.") || className.startsWith("com.glodblock.github.extendedae.") || GTL_ME_TARGET_CLASSES.contains(className);
    }

    private static boolean isWirelessMeTargetId(ResourceLocation blockId) {
        if (blockId == null) {
            return false;
        }

        String namespace = blockId.getNamespace();
        String path = blockId.getPath();
        return "ae2".equals(namespace) || "appeng".equals(namespace) || "expatternprovider".equals(namespace) || "extendedae".equals(namespace) || "megacells".equals(namespace) || WIRELESS_ME_TARGET_IDS.contains(path) || ("merequester".equals(namespace) && isMeLikePath(path)) || ("gtmthings".equals(namespace) && isMeLikePath(path)) || (namespace.contains("ae") && isMeLikePath(path));
    }

    private static boolean isMeLikePath(String path) {
        return path.startsWith("me_") || path.contains("_me_") || path.contains("appeng") || path.contains("ae2") || path.contains("interface") || path.contains("requester") || path.contains("provider");
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
