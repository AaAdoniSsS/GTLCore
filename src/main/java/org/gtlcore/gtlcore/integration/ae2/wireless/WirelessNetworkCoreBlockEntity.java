package org.gtlcore.gtlcore.integration.ae2.wireless;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.managed.MultiManagedStorage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public class WirelessNetworkCoreBlockEntity extends BlockEntity
        implements IActionHost, IInWorldGridNodeHost, IMachineBlockEntity {
    private static final String TAG_FREQUENCY = "frequency";
    private static final String NODE_TAG = "wireless_core";
    private static final MachineDefinition DEFINITION = createDefinition();

    private static final IGridNodeListener<WirelessNetworkCoreBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(WirelessNetworkCoreBlockEntity host, IGridNode node) {
                    host.setChanged();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.DENSE_CAPACITY)
            .setIdlePowerUsage(0.0D)
            .setInWorldNode(true)
            .setExposedOnSides(getExposedSides())
            .setTagName(NODE_TAG);

    private final MultiManagedStorage rootStorage = new MultiManagedStorage();
    private final WirelessNetworkCoreMachine metaMachine = new WirelessNetworkCoreMachine(this);
    private UUID frequency;

    public WirelessNetworkCoreBlockEntity(BlockPos pos, BlockState state) {
        super(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_BE.get(), pos, state);
        this.mainNode.setVisualRepresentation(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_ITEM.get());
        refreshExposedSides();
    }

    public static MachineDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            ensureFrequency();
            refreshExposedSides();
            createMainNode(serverLevel);
            WirelessAeSavedData.get(serverLevel.getServer())
                    .setCore(this.frequency, GlobalPos.of(serverLevel.dimension(), this.worldPosition));
            WirelessAeNetworkRuntime.requestReconnect(this.frequency);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
        }
        this.mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
        }
        this.mainNode.destroy();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID(TAG_FREQUENCY)) {
            this.frequency = tag.getUUID(TAG_FREQUENCY);
        }
        this.mainNode.loadFromNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID(TAG_FREQUENCY, ensureFrequency());
        this.mainNode.saveToNBT(tag);
    }

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        refreshExposedSides();
        return isConnectionSide(direction) ? this.mainNode.getNode() : null;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        refreshExposedSides();
        return isConnectionSide(direction) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public MetaMachine getMetaMachine() {
        return this.metaMachine;
    }

    @Override
    public long getOffset() {
        return this.worldPosition.asLong();
    }

    @Override
    public MultiManagedStorage getRootStorage() {
        return this.rootStorage;
    }

    @Override
    public MachineDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack heldStack,
                                    Set<GTToolType> toolTypes) {
        return this.metaMachine.shouldRenderGrid(player, pos, state, heldStack, toolTypes);
    }

    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state,
                                    Set<GTToolType> toolTypes, Direction side) {
        return this.metaMachine.sideTips(player, pos, state, toolTypes, side);
    }

    public UUID getFrequency() {
        return ensureFrequency();
    }

    public String getShortFrequency() {
        return WirelessAeNetworkRuntime.shortFrequency(getFrequency());
    }

    public void setOwner(Player player) {
        this.mainNode.setOwningPlayer(player);
    }

    public boolean isLinkedToAeNetwork() {
        return WirelessAeNetworkRuntime.findBridgeNode(this) != null;
    }

    public Direction getConnectionSide() {
        BlockState state = getBlockState();
        if (state.hasProperty(WirelessNetworkCoreBlock.FACING)) {
            return state.getValue(WirelessNetworkCoreBlock.FACING);
        }
        return Direction.NORTH;
    }

    public void removeNetwork(MinecraftServer server) {
        if (this.frequency == null) {
            return;
        }
        WirelessAeSavedData.get(server).removeNetwork(this.frequency);
        WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
    }

    public void refreshConnectionSide() {
        refreshConnectionSide(null, getConnectionSide());
    }

    public void refreshConnectionSide(Direction oldSide, Direction newSide) {
        Direction connectionSide = newSide == null ? getConnectionSide() : newSide;
        rebuildExposedSide(connectionSide);
        setChanged();
        if (this.frequency != null) {
            WirelessAeNetworkRuntime.disconnectFrequency(this.frequency);
            WirelessAeNetworkRuntime.requestReconnect(this.frequency);
        }
        BlockState state = getBlockState();
        LevelUpdate.send(this, state);
        notifyConnectionNeighbors(oldSide, connectionSide, state);
    }

    private static MachineDefinition createDefinition() {
        MachineDefinition definition = MachineDefinition.createDefinition(
                new ResourceLocation(GTLCore.MOD_ID, "wireless_network_core")
        );
        definition.setBlockSupplier(() -> GTLWirelessAeContent.WIRELESS_NETWORK_CORE.get());
        definition.setBlockEntityTypeSupplier(() -> GTLWirelessAeContent.WIRELESS_NETWORK_CORE_BE.get());
        definition.setMachineSupplier(WirelessNetworkCoreMachine::new);
        definition.setShape(Shapes.block());
        definition.setAppearance(() -> GTLWirelessAeContent.WIRELESS_NETWORK_CORE.get().defaultBlockState());
        definition.setTooltipBuilder((stack, tooltip) -> {
        });
        return definition;
    }

    private UUID ensureFrequency() {
        if (this.frequency == null) {
            this.frequency = UUID.randomUUID();
            setChanged();
        }
        return this.frequency;
    }

    private void createMainNode(ServerLevel serverLevel) {
        if (!this.mainNode.isReady()) {
            this.mainNode.create(serverLevel, this.worldPosition);
        }
    }

    private boolean isConnectionSide(Direction direction) {
        return direction != null && direction == getConnectionSide();
    }

    private EnumSet<Direction> getExposedSides() {
        return EnumSet.of(getConnectionSide());
    }

    private void refreshExposedSides() {
        Direction connectionSide = getConnectionSide();
        this.mainNode.setExposedOnSides(EnumSet.of(connectionSide));

        IGridNode node = this.mainNode.getNode();
        if (node == null) {
            return;
        }

        try {
            for (Map.Entry<Direction, IGridConnection> entry
                    : new ArrayList<>(node.getInWorldConnections().entrySet())) {
                if (entry.getKey() != connectionSide) {
                    entry.getValue().destroy();
                }
            }
        } catch (RuntimeException ignored) {
            // AE2 may update the connection map while neighboring nodes are changing.
        }
    }

    private void rebuildExposedSide(Direction connectionSide) {
        this.mainNode.setExposedOnSides(EnumSet.noneOf(Direction.class));
        this.mainNode.setExposedOnSides(EnumSet.of(connectionSide));
    }

    private void notifyConnectionNeighbors(Direction oldSide, Direction newSide, BlockState state) {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }

        level.updateNeighborsAt(this.worldPosition, state.getBlock());
        notifyConnectionNeighbor(level, oldSide, state);
        if (newSide != oldSide) {
            notifyConnectionNeighbor(level, newSide, state);
        }
    }

    private void notifyConnectionNeighbor(Level level, Direction side, BlockState state) {
        if (side == null) {
            return;
        }

        BlockPos neighborPos = this.worldPosition.relative(side);
        level.neighborChanged(neighborPos, state.getBlock(), this.worldPosition);
        level.updateNeighborsAt(neighborPos, state.getBlock());
    }

    private static final class WirelessNetworkCoreMachine extends MetaMachine {
        private final WirelessNetworkCoreBlockEntity core;

        private WirelessNetworkCoreMachine(IMachineBlockEntity holder) {
            super(holder);
            this.core = holder instanceof WirelessNetworkCoreBlockEntity core ? core : null;
        }

        @Override
        public void onRotated(Direction oldFacing, Direction newFacing) {
            super.onRotated(oldFacing, newFacing);
            if (this.core != null) {
                this.core.refreshConnectionSide(oldFacing, newFacing);
            }
        }
    }

    private static final class LevelUpdate {
        private static void send(WirelessNetworkCoreBlockEntity core, BlockState state) {
            if (core.level != null) {
                core.level.sendBlockUpdated(core.worldPosition, state, state, 3);
            }
        }
    }
}
