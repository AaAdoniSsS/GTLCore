package org.gtlcore.gtlcore.api.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 通用的模块化机器模块接口
 * 模块负责连接到主机，并在主机提供的上下文中工作
 *
 * @param <H> 主机类型，必须实现 IModularMachineHost
 * @param <M> 模块自身类型（自引用）
 */
public interface IModularMachineModule<H extends IModularMachineHost<H>, M extends IModularMachineModule<H, M>> {

    // ==================== Must Have Implementation ====================

    @Nullable
    BlockPos getHostPosition();

    void setHostPosition(@Nullable BlockPos pos);

    @Nullable
    H getHost();

    void setHost(@Nullable H host);

    // For Validation
    @NotNull
    Class<H> getHostType();

    BlockPos[] getHostScanPositions();

    // ==================== Implementation From WorkableMachine ====================

    Level getLevel();

    boolean isFormed();

    // ==================== Default Implementation ====================

    default void connectToHost(@NotNull H host) {
        H previousHost = getHost();
        if (previousHost != null && previousHost != host) {
            removeFromHost(previousHost);
        }
        setHost(host);
        setHostPosition(host.getPos());
        @SuppressWarnings("unchecked")
        M self = (M) this;
        host.addModule(self);
        onConnected(host);
    }

    default void removeFromHost(@Nullable H host) {
        setHostPosition(null);
        setHost(null);
        if (host != null) {
            @SuppressWarnings("unchecked")
            M self = (M) this;
            host.removeModule(self);
        }
        onDisconnected();
    }

    default boolean findAndConnectToHost() {
        final Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return false;

        // Persisted Position First
        final BlockPos savedPos = getHostPosition();
        if (savedPos != null) {
            if (serverLevel.getBlockEntity(savedPos) instanceof IMachineBlockEntity imbe) {
                MetaMachine machine = imbe.getMetaMachine();
                if (isValidHost(machine)) {
                    @SuppressWarnings("unchecked")
                    H host = (H) machine;
                    connectToHost(host);
                    return true;
                }
            }
        }

        // Scan Others
        final BlockPos[] positions = getHostScanPositions();
        for (BlockPos pos : positions) {
            if (serverLevel.getBlockEntity(pos) instanceof IMachineBlockEntity imbe) {
                MetaMachine machine = imbe.getMetaMachine();
                if (isValidHost(machine)) {
                    @SuppressWarnings("unchecked")
                    H host = (H) machine;
                    connectToHost(host);
                    return true;
                }
            }
        }

        return false;
    }

    default boolean isValidHost(MetaMachine machine) {
        if (machine == null) return false;
        if (!getHostType().isInstance(machine)) return false;
        if (!(machine instanceof IModularMachineHost<?> host)) return false;
        return host.isFormed();
    }

    default boolean isConnectedToHost() {
        return getHost() != null;
    }

    // ==================== CallBack ====================

    default void onConnected(@NotNull H host) {}

    default void onDisconnected() {}
}
