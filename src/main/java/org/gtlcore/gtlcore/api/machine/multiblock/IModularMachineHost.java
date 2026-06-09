package org.gtlcore.gtlcore.api.machine.multiblock;

import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 通用的模块化机器主机接口
 * 主机负责管理连接的模块，并提供模块扫描和验证功能
 * 支持一个主机连接多种不同类型的模块
 *
 * @param <H> 主机自身类型（自引用）
 */
public interface IModularMachineHost<H extends IModularMachineHost<H>> {

    // ==================== Must Have Implementation ====================

    @NotNull
    Set<IModularMachineModule<H, ?>> getModuleSet();

    BlockPos[] getModuleScanPositions();

    // ==================== Implementation From WorkableMachine ====================

    BlockPos getPos();

    Level getLevel();

    boolean isFormed();

    // ==================== Default Implementation ====================

    default <M extends IModularMachineModule<H, M>> void addModule(@NotNull M module) {
        getModuleSet().add(module);
    }

    default <M extends IModularMachineModule<H, M>> void removeModule(@NotNull M module) {
        getModuleSet().remove(module);
    }

    @NotNull
    default Set<IModularMachineModule<H, ?>> getModules() {
        return Collections.unmodifiableSet(getModuleSet());
    }

    default void safeClearModules() {
        @SuppressWarnings("unchecked")
        H self = (H) this;
        // removeFromHost mutates the backing set through removeModule, so iterate over a snapshot.
        for (IModularMachineModule<H, ?> module : new ArrayList<>(getModuleSet())) {
            module.removeFromHost(self);
        }
        getModuleSet().clear();
    }

    default void scanAndConnectModules() {
        final Level level = getLevel();
        if (level == null) return;

        final BlockPos[] positions = getModuleScanPositions();

        @SuppressWarnings("unchecked")
        H self = (H) this;

        for (BlockPos pos : positions) {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (isValidModule(machine)) {
                @SuppressWarnings("unchecked")
                IModularMachineModule<H, ?> module = (IModularMachineModule<H, ?>) machine;
                assert module != null;
                module.connectToHost(self);
            }
        }
    }

    default boolean isValidModule(MetaMachine machine) {
        if (!(machine instanceof IModularMachineModule<?, ?> module)) {
            return false;
        }
        if (!module.isFormed()) {
            return false;
        }
        try {
            return module.getHostType().isInstance(this);
        } catch (Exception e) {
            return false;
        }
    }

    default int getFormedModuleCount() {
        return Ints.saturatedCast(getModules().stream()
                .filter(IModularMachineModule::isFormed)
                .count());
    }

    default <M extends IModularMachineModule<H, M>> int getFormedModuleCount(Class<M> moduleClass) {
        return Ints.saturatedCast(getModules().stream()
                .filter(module -> moduleClass.isInstance(module) && module.isFormed())
                .count());
    }

    @NotNull
    default <M extends IModularMachineModule<H, M>> Set<M> getModulesOfType(Class<M> moduleClass) {
        return getModules().stream()
                .filter(moduleClass::isInstance)
                .map(moduleClass::cast)
                .collect(java.util.stream.Collectors.toSet());
    }

    default boolean exceedsModuleLimit() {
        return getFormedModuleCount() > getMaxModuleCount();
    }

    default int getMaxModuleCount() {
        return Integer.MAX_VALUE;
    }

    // ==================== Rendering Support ====================

    @NotNull
    @OnlyIn(Dist.CLIENT)
    default List<ModuleRenderInfo> getModulesForRendering() {
        return Collections.emptyList();
    }
}
