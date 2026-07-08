package org.gtlcore.gtlcore.integration.ae2.pattern;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

public final class PatternQuickUploadService {

    private static final String LOG_PREFIX = "[PatternQuickUpload]";
    private static final String MULTI_RECIPE_TYPE_METHOD = "getMultiRecipeType";
    private static final String COMPOSITE_RECIPE_TYPES_METHOD = "getTypeList";

    private PatternQuickUploadService() {}

    public static SearchResult findTargets(ServerPlayer player, @Nullable IGridNode node, ItemStack patternStack) {
        GTLCore.LOGGER.debug("{} findTargets stack={}", LOG_PREFIX, patternStack.getHoverName().getString());
        if (patternStack.isEmpty()) {
            GTLCore.LOGGER.debug("{} rejected: empty pattern stack", LOG_PREFIX);
            return SearchResult.fail(Component.translatable("message.gtlcore.pattern_quick_upload_invalid_pattern"));
        }
        IPatternDetails patternDetails = PatternDetailsHelper.decodePattern(patternStack, player.level());
        if (!isQuickUploadPattern(patternDetails)) {
            GTLCore.LOGGER.debug("{} rejected: unsupported pattern", LOG_PREFIX);
            return SearchResult.fail(Component.translatable("message.gtlcore.pattern_quick_upload_processing_only"));
        }
        if (node == null || node.getGrid() == null) {
            GTLCore.LOGGER.debug("{} rejected: missing ME network node", LOG_PREFIX);
            return SearchResult.fail(Component.translatable("message.gtlcore.pattern_quick_upload_no_network"));
        }

        Set<ResourceLocation> recipeTypeIds = new LinkedHashSet<>(PatternQuickUploadMetadata.readRecipeTypeIds(patternStack));
        if (recipeTypeIds.isEmpty()) {
            recipeTypeIds.addAll(PatternQuickUploadRecipeTypeResolver.findRecipeTypeIds(player, patternStack));
        }
        GTLCore.LOGGER.debug("{} pattern recipe types={}", LOG_PREFIX, recipeTypeIds);
        if (recipeTypeIds.isEmpty()) {
            return SearchResult.fail(Component.translatable("message.gtlcore.pattern_quick_upload_no_recipe_type"));
        }

        List<Target> targets = findTargets(player, node.getGrid(), patternStack, patternDetails, recipeTypeIds);
        GTLCore.LOGGER.debug("{} target count={}", LOG_PREFIX, targets.size());
        if (targets.isEmpty()) {
            return SearchResult.fail(Component.translatable("message.gtlcore.pattern_quick_upload_no_target"));
        }
        return SearchResult.success(selectTargets(targets));
    }

    public static boolean insertIntoTarget(ServerPlayer player, ItemStack patternStack, BlockPos targetPos) {
        return insertIntoTargetSlot(player, patternStack, player.level().dimension(), targetPos) >= 0;
    }

    public static boolean insertIntoTarget(ServerPlayer player, ItemStack patternStack, Target target) {
        return insertIntoTargetSlot(player, patternStack, target) >= 0;
    }

    public static int insertIntoTargetSlot(ServerPlayer player, ItemStack patternStack, Target target) {
        UploadResult result = insertIntoTargetSlotResult(player, patternStack, target);
        return result == null ? -1 : result.slot();
    }

    @Nullable
    public static UploadResult insertIntoTargetSlotResult(ServerPlayer player, ItemStack patternStack, Target target) {
        for (BlockPos bufferPos : target.bufferPositions()) {
            int slot = insertIntoTargetSlot(player, patternStack, target.levelKey(), bufferPos);
            if (slot >= 0) {
                return new UploadResult(target.withSingleBufferPos(bufferPos), slot);
            }
        }
        return null;
    }

    public static boolean removeFromTarget(ServerPlayer player, ItemStack patternStack, Target target) {
        for (BlockPos bufferPos : target.bufferPositions()) {
            if (removeFromTarget(player, patternStack, target.levelKey(), bufferPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeFromTarget(ServerPlayer player, ItemStack patternStack, Target target, int slot) {
        for (BlockPos bufferPos : target.bufferPositions()) {
            if (removeFromTarget(player, patternStack, target.levelKey(), bufferPos, slot)) {
                return true;
            }
        }
        return false;
    }

    private static int insertIntoTargetSlot(ServerPlayer player, ItemStack patternStack,
                                            ResourceKey<Level> targetLevelKey, BlockPos targetPos) {
        if (patternStack.isEmpty()) {
            return -1;
        }
        ServerLevel targetLevel = player.server.getLevel(targetLevelKey);
        MetaMachine machine = targetLevel == null ? null : MetaMachine.getMachine(targetLevel, targetPos);
        if (!(machine instanceof PatternContainer container)) {
            GTLCore.LOGGER.debug("{} insert rejected: no pattern container at {} {}",
                    LOG_PREFIX,
                    targetLevelKey.location(),
                    targetPos);
            return -1;
        }
        if (!container.isVisibleInTerminal()) {
            GTLCore.LOGGER.debug("{} insert rejected: target hidden at {} {}", LOG_PREFIX, targetLevelKey.location(), targetPos);
            return -1;
        }
        int slot = insertPattern(container, patternStack.copy());
        GTLCore.LOGGER.debug("{} insert slot={} target={} {}",
                LOG_PREFIX,
                slot,
                targetLevelKey.location(),
                targetPos);
        return slot;
    }

    private static boolean removeFromTarget(ServerPlayer player, ItemStack patternStack,
                                            ResourceKey<Level> targetLevelKey, BlockPos targetPos) {
        if (patternStack.isEmpty()) {
            return false;
        }
        ServerLevel targetLevel = player.server.getLevel(targetLevelKey);
        MetaMachine machine = targetLevel == null ? null : MetaMachine.getMachine(targetLevel, targetPos);
        if (!(machine instanceof PatternContainer container)) {
            GTLCore.LOGGER.debug("{} undo rejected: no pattern container at {} {}",
                    LOG_PREFIX,
                    targetLevelKey.location(),
                    targetPos);
            return false;
        }
        boolean removed = removePattern(container, patternStack.copy());
        GTLCore.LOGGER.debug("{} undo result={} target={} {}",
                LOG_PREFIX,
                removed,
                targetLevelKey.location(),
                targetPos);
        return removed;
    }

    private static boolean removeFromTarget(ServerPlayer player, ItemStack patternStack,
                                            ResourceKey<Level> targetLevelKey, BlockPos targetPos, int slot) {
        if (patternStack.isEmpty() || slot < 0) {
            return false;
        }
        ServerLevel targetLevel = player.server.getLevel(targetLevelKey);
        MetaMachine machine = targetLevel == null ? null : MetaMachine.getMachine(targetLevel, targetPos);
        if (!(machine instanceof PatternContainer container)) {
            GTLCore.LOGGER.debug("{} undo rejected: no pattern container at {} {}",
                    LOG_PREFIX,
                    targetLevelKey.location(),
                    targetPos);
            return false;
        }
        boolean removed = removePattern(container, patternStack.copy(), slot);
        GTLCore.LOGGER.debug("{} undo result={} target={} {} slot={}",
                LOG_PREFIX,
                removed,
                targetLevelKey.location(),
                targetPos,
                slot);
        return removed;
    }

    private static List<Target> findTargets(ServerPlayer player, IGrid grid, ItemStack patternStack,
                                            IPatternDetails patternDetails, Set<ResourceLocation> recipeTypeIds) {
        List<Target> targets = new ArrayList<>();
        Set<TargetKey> seen = new HashSet<>();
        GTLCore.LOGGER.debug("{} scanning grid machine classes", LOG_PREFIX);
        collectTargetsFromGrid(grid, patternStack, patternDetails, recipeTypeIds, targets, seen);
        GTLCore.LOGGER.debug("{} scanning loaded multiblocks in {}", LOG_PREFIX, player.serverLevel().dimension().location());
        collectTargetsFromLoadedMultiblocks(player.serverLevel(), grid, patternStack, patternDetails, recipeTypeIds,
                targets, seen);
        return targets;
    }

    private static PatternQuickUploadMatch<Target> selectTargets(List<Target> targets) {
        return PatternQuickUploadMatch.select(collapseEquivalentAssemblyTargetsByGroup(targets));
    }

    private static List<Target> collapseEquivalentAssemblyTargetsByGroup(List<Target> targets) {
        if (targets.size() <= 1) {
            return targets;
        }

        Map<TargetGroupKey, List<Target>> targetsByGroup = new LinkedHashMap<>();
        List<List<Target>> orderedGroups = new ArrayList<>();
        for (Target target : targets) {
            TargetGroupKey groupKey = groupKey(target);
            if (groupKey == null) {
                orderedGroups.add(new ArrayList<>(List.of(target)));
                continue;
            }
            List<Target> groupTargets = targetsByGroup.get(groupKey);
            if (groupTargets == null) {
                groupTargets = new ArrayList<>();
                targetsByGroup.put(groupKey, groupTargets);
                orderedGroups.add(groupTargets);
            }
            groupTargets.add(target);
        }

        List<Target> collapsedTargets = new ArrayList<>(orderedGroups.size());
        for (List<Target> groupTargets : orderedGroups) {
            Target collapsedTarget = groupTargets.size() == 1 ? groupTargets.get(0) : mergeTargets(groupTargets);
            if (groupTargets.size() > 1) {
                GTLCore.LOGGER.debug("{} collapsed {} equivalent targets into {} buffer positions for recipeType={} name={}",
                        LOG_PREFIX,
                        groupTargets.size(),
                        collapsedTarget.bufferPositions().size(),
                        collapsedTarget.recipeTypeId(),
                        collapsedTarget.targetName().getString());
            }
            collapsedTargets.add(collapsedTarget);
        }
        return collapsedTargets;
    }

    @Nullable
    private static TargetGroupKey groupKey(Target target) {
        if (target.targetMachineId() == null) {
            return null;
        }
        return new TargetGroupKey(
                target.levelKey(),
                target.recipeTypeId(),
                target.targetName().getString(),
                target.targetMachineId());
    }

    private static Target mergeTargets(List<Target> targets) {
        Target firstTarget = targets.get(0);
        LinkedHashSet<BlockPos> bufferPositions = new LinkedHashSet<>();
        for (Target target : targets) {
            bufferPositions.addAll(target.bufferPositions());
        }
        return new Target(
                firstTarget.levelKey(),
                firstTarget.bufferPos(),
                firstTarget.targetName(),
                firstTarget.recipeTypeId(),
                firstTarget.recipeTypeName(),
                firstTarget.targetIcon(),
                firstTarget.targetMachineId(),
                List.copyOf(bufferPositions));
    }

    private static void collectTargetsFromGrid(IGrid grid, ItemStack patternStack, IPatternDetails patternDetails,
                                               Set<ResourceLocation> recipeTypeIds,
                                               List<Target> targets, Set<TargetKey> seen) {
        for (Class<?> machineClass : grid.getMachineClasses()) {
            Class<? extends PatternContainer> containerClass = asPatternContainerClass(machineClass);
            if (containerClass == null) {
                continue;
            }
            collectTargetsFromPatternContainerClass(grid, containerClass, patternStack, patternDetails, recipeTypeIds,
                    targets, seen);
        }
    }

    @Nullable
    private static Class<? extends PatternContainer> asPatternContainerClass(Class<?> machineClass) {
        return PatternContainer.class.isAssignableFrom(machineClass) ?
                machineClass.asSubclass(PatternContainer.class) :
                null;
    }

    private static <T extends PatternContainer> void collectTargetsFromPatternContainerClass(
                                                                                             IGrid grid, Class<T> containerClass, ItemStack patternStack, IPatternDetails patternDetails,
                                                                                             Set<ResourceLocation> recipeTypeIds,
                                                                                             List<Target> targets, Set<TargetKey> seen) {
        for (T container : grid.getActiveMachines(containerClass)) {
            collectTargetsFromPatternContainer(grid, container, patternStack, patternDetails, recipeTypeIds, targets, seen);
        }
    }

    private static void collectTargetsFromPatternContainer(IGrid grid, PatternContainer container, ItemStack patternStack,
                                                           IPatternDetails patternDetails,
                                                           Set<ResourceLocation> recipeTypeIds, List<Target> targets,
                                                           Set<TargetKey> seen) {
        if (patternDetails instanceof AEProcessingPattern && container instanceof MEPatternBufferPartMachineBase buffer) {
            Level level = buffer.getLevel();
            if (level == null) {
                GTLCore.LOGGER.debug("{} skipped grid buffer with no level at {}", LOG_PREFIX, buffer.getPos());
                return;
            }
            GTLCore.LOGGER.debug("{} found grid buffer at {} {}", LOG_PREFIX, level.dimension().location(), buffer.getPos());
            collectTargetsFromBuffer(grid, level.dimension(), buffer, buffer.getControllers(),
                    patternStack, recipeTypeIds, targets, seen);
            return;
        }
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern) ||
                !(container instanceof MEMolecularAssemblerIOPartMachine molecularAssembler)) {
            return;
        }
        Level level = molecularAssembler.getLevel();
        if (level == null) {
            GTLCore.LOGGER.debug("{} skipped grid molecular assembler with no level at {}",
                    LOG_PREFIX,
                    molecularAssembler.getPos());
            return;
        }
        GTLCore.LOGGER.debug("{} found grid molecular assembler at {} {}",
                LOG_PREFIX,
                level.dimension().location(),
                molecularAssembler.getPos());
        collectTargetsFromMolecularAssembler(grid, level.dimension(), molecularAssembler,
                patternStack, recipeTypeIds, targets, seen);
    }

    private static void collectTargetsFromLoadedMultiblocks(ServerLevel level, IGrid grid, ItemStack patternStack,
                                                            IPatternDetails patternDetails,
                                                            Set<ResourceLocation> recipeTypeIds, List<Target> targets,
                                                            Set<TargetKey> seen) {
        MultiblockWorldSavedData savedData = MultiblockWorldSavedData.getOrCreate(level);
        for (MultiblockState state : List.copyOf(savedData.mapping.values())) {
            IMultiController controller = state.getController();
            if (controller == null || !controller.isFormed()) {
                continue;
            }
            GTLCore.LOGGER.debug("{} found formed controller {} in {}",
                    LOG_PREFIX,
                    controller.self().getPos(),
                    level.dimension().location());
            collectTargetsFromController(level.dimension(), grid, controller, patternStack, patternDetails, recipeTypeIds,
                    targets, seen);
        }
    }

    private static void collectTargetsFromController(ResourceKey<Level> levelKey, IGrid grid, IMultiController controller,
                                                     ItemStack patternStack, IPatternDetails patternDetails,
                                                     Set<ResourceLocation> recipeTypeIds,
                                                     List<Target> targets, Set<TargetKey> seen) {
        for (IMultiPart part : controller.getParts()) {
            collectTargetsFromPart(levelKey, grid, controller, part, patternStack, patternDetails, recipeTypeIds, targets,
                    seen);
        }
        collectTargetsFromRecipeCapabilityCache(levelKey, grid, controller, patternStack, patternDetails, recipeTypeIds,
                targets, seen);
    }

    private static void collectTargetsFromRecipeCapabilityCache(ResourceKey<Level> levelKey, IGrid grid,
                                                                IMultiController controller,
                                                                ItemStack patternStack,
                                                                IPatternDetails patternDetails,
                                                                Set<ResourceLocation> recipeTypeIds,
                                                                List<Target> targets, Set<TargetKey> seen) {
        if (!(controller instanceof IRecipeCapabilityMachine recipeMachine)) {
            return;
        }
        for (MEPatternRecipeHandlePart patternPart : recipeMachine.getMEPatternRecipeHandleParts()) {
            for (var handler : patternPart.getMERecipeHandlers()) {
                if (handler instanceof MachineTrait trait) {
                    collectTargetsFromMachine(levelKey, grid, controller, trait.getMachine(),
                            patternStack, patternDetails, recipeTypeIds, targets, seen);
                    break;
                }
            }
        }
    }

    private static void collectTargetsFromPart(ResourceKey<Level> levelKey, IGrid grid, IMultiController controller,
                                               IMultiPart part, ItemStack patternStack,
                                               IPatternDetails patternDetails,
                                               Set<ResourceLocation> recipeTypeIds, List<Target> targets,
                                               Set<TargetKey> seen) {
        if (part == null || !part.isFormed()) {
            return;
        }
        collectTargetsFromMachine(levelKey, grid, controller, part.self(), patternStack, patternDetails, recipeTypeIds,
                targets, seen);
    }

    private static void collectTargetsFromMachine(ResourceKey<Level> levelKey, IGrid grid, IMultiController controller,
                                                  MetaMachine machine, ItemStack patternStack,
                                                  IPatternDetails patternDetails,
                                                  Set<ResourceLocation> recipeTypeIds, List<Target> targets,
                                                  Set<TargetKey> seen) {
        if (machine == null) {
            return;
        }
        if (patternDetails instanceof AEProcessingPattern && machine instanceof MEPatternBufferPartMachineBase buffer) {
            collectTargetsFromBuffer(grid, levelKey, buffer, List.of(controller), patternStack, recipeTypeIds, targets, seen);
        } else if (patternDetails instanceof AEProcessingPattern && machine instanceof MEPatternBufferProxyPartMachine proxy) {
            MEPatternBufferPartMachineBase buffer = proxy.getBuffer();
            if (buffer != null) {
                collectTargetsFromBuffer(grid, levelKey, buffer, List.of(controller), patternStack, recipeTypeIds, targets, seen);
            }
        } else if (patternDetails instanceof IMolecularAssemblerSupportedPattern &&
                machine instanceof MEMolecularAssemblerIOPartMachine molecularAssembler) {
                    collectTargetsFromMolecularAssembler(grid, levelKey, molecularAssembler,
                            patternStack, recipeTypeIds, targets, seen);
                }
    }

    private static void collectTargetsFromBuffer(IGrid grid, ResourceKey<Level> levelKey, MEPatternBufferPartMachineBase buffer,
                                                 Iterable<IMultiController> controllers, ItemStack patternStack,
                                                 Set<ResourceLocation> recipeTypeIds, List<Target> targets,
                                                 Set<TargetKey> seen) {
        if (!isOnCurrentGrid(grid, levelKey, buffer)) {
            return;
        }
        if (!buffer.isVisibleInTerminal()) {
            GTLCore.LOGGER.debug("{} skipped buffer {} {}: hidden in terminal",
                    LOG_PREFIX,
                    levelKey.location(),
                    buffer.getPos());
            return;
        }
        if (!hasFormedController(controllers)) {
            GTLCore.LOGGER.debug("{} skipped buffer {} {}: no formed controller",
                    LOG_PREFIX,
                    levelKey.location(),
                    buffer.getPos());
            return;
        }
        if (!canInsert(buffer, patternStack)) {
            GTLCore.LOGGER.debug("{} skipped buffer {} {}: no insertable pattern slot",
                    LOG_PREFIX,
                    levelKey.location(),
                    buffer.getPos());
            return;
        }
        Set<GTRecipeType> supportedRecipeTypes = getSupportedRecipeTypes(controllers);
        GTLCore.LOGGER.debug("{} buffer {} {} supports recipe types {}",
                LOG_PREFIX,
                levelKey.location(),
                buffer.getPos(),
                supportedRecipeTypes.stream()
                        .map(recipeType -> recipeType.registryName)
                        .filter(Objects::nonNull)
                        .toList());
        for (GTRecipeType recipeType : supportedRecipeTypes) {
            if (recipeType.registryName == null) {
                continue;
            }
            if (!recipeTypeIds.contains(recipeType.registryName)) {
                continue;
            }
            TargetKey key = new TargetKey(levelKey, buffer.getPos(), recipeType.registryName);
            if (seen.add(key)) {
                var terminalGroup = buffer.getTerminalGroup();
                ResourceLocation targetMachineId = targetMachineId(controllers, terminalGroup.icon());
                targets.add(new Target(
                        levelKey,
                        buffer.getPos(),
                        terminalGroup.name(),
                        recipeType.registryName,
                        recipeTypeName(recipeType),
                        terminalGroup.icon(),
                        targetMachineId));
                GTLCore.LOGGER.debug("{} accepted target {} {} recipeType={} name={}",
                        LOG_PREFIX,
                        levelKey.location(),
                        buffer.getPos(),
                        recipeType.registryName,
                        terminalGroup.name().getString());
            }
        }
    }

    private static void collectTargetsFromMolecularAssembler(IGrid grid, ResourceKey<Level> levelKey,
                                                             MEMolecularAssemblerIOPartMachine molecularAssembler,
                                                             ItemStack patternStack,
                                                             Set<ResourceLocation> recipeTypeIds,
                                                             List<Target> targets,
                                                             Set<TargetKey> seen) {
        if (!isOnCurrentGrid(grid, levelKey, molecularAssembler)) {
            return;
        }
        if (!molecularAssembler.isVisibleInTerminal()) {
            GTLCore.LOGGER.debug("{} skipped molecular assembler {} {}: hidden in terminal",
                    LOG_PREFIX,
                    levelKey.location(),
                    molecularAssembler.getPos());
            return;
        }
        List<IMultiController> controllers = molecularAssembler.getControllers();
        if (!hasFormedController(controllers)) {
            GTLCore.LOGGER.debug("{} skipped molecular assembler {} {}: no formed controller",
                    LOG_PREFIX,
                    levelKey.location(),
                    molecularAssembler.getPos());
            return;
        }
        if (!canInsert(molecularAssembler, patternStack)) {
            GTLCore.LOGGER.debug("{} skipped molecular assembler {} {}: no insertable pattern slot",
                    LOG_PREFIX,
                    levelKey.location(),
                    molecularAssembler.getPos());
            return;
        }
        for (ResourceLocation recipeTypeId : recipeTypeIds) {
            if (!PatternQuickUploadRecipeTypeResolver.isMolecularRecipeTypeId(recipeTypeId)) {
                continue;
            }
            TargetKey key = new TargetKey(levelKey, molecularAssembler.getPos(), recipeTypeId);
            if (seen.add(key)) {
                var terminalGroup = molecularAssembler.getTerminalGroup();
                ResourceLocation targetMachineId = targetMachineId(controllers, terminalGroup.icon());
                targets.add(new Target(
                        levelKey,
                        molecularAssembler.getPos(),
                        terminalGroup.name(),
                        recipeTypeId,
                        PatternQuickUploadMetadata.recipeTypeName(recipeTypeId),
                        terminalGroup.icon(),
                        targetMachineId));
                GTLCore.LOGGER.debug("{} accepted molecular assembler target {} {} recipeType={} name={}",
                        LOG_PREFIX,
                        levelKey.location(),
                        molecularAssembler.getPos(),
                        recipeTypeId,
                        terminalGroup.name().getString());
            }
        }
    }

    private static boolean hasFormedController(Iterable<IMultiController> controllers) {
        for (IMultiController controller : controllers) {
            if (controller != null && controller.isFormed()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ResourceLocation targetMachineId(Iterable<IMultiController> controllers,
                                                    @Nullable AEItemKey fallbackIcon) {
        for (IMultiController controller : controllers) {
            if (controller != null && controller.isFormed()) {
                return controller.self().getDefinition().getId();
            }
        }
        return fallbackIcon == null ? null : fallbackIcon.getId();
    }

    private static boolean isOnCurrentGrid(IGrid grid, ResourceKey<Level> levelKey, MEIOPartMachine machine) {
        var mainNode = machine.getMainNode();
        if (!mainNode.isActive()) {
            GTLCore.LOGGER.debug("{} skipped target {} {}: ME node inactive",
                    LOG_PREFIX,
                    levelKey.location(),
                    machine.getPos());
            return false;
        }
        if (mainNode.getGrid() != grid) {
            GTLCore.LOGGER.debug("{} skipped target {} {}: not on current ME network",
                    LOG_PREFIX,
                    levelKey.location(),
                    machine.getPos());
            return false;
        }
        return true;
    }

    private static Set<GTRecipeType> getSupportedRecipeTypes(Iterable<IMultiController> controllers) {
        Set<GTRecipeType> recipeTypes = new LinkedHashSet<>();
        for (IMultiController controller : controllers) {
            if (controller == null || !controller.isFormed()) {
                continue;
            }
            if (addRuntimeRecipeTypes(recipeTypes, controller)) {
                continue;
            }
            addRecipeTypes(recipeTypes, controller.self().getDefinition().getRecipeTypes());
        }
        return recipeTypes;
    }

    private static boolean addRuntimeRecipeTypes(Set<GTRecipeType> recipeTypes, IMultiController controller) {
        if (!(controller.self() instanceof IRecipeLogicMachine recipeLogicMachine)) {
            return false;
        }
        GTRecipeType multiRecipeType = getReflectedRecipeType(recipeLogicMachine, MULTI_RECIPE_TYPE_METHOD);
        if (multiRecipeType != null) {
            addExpandedRecipeType(recipeTypes, multiRecipeType);
            return true;
        }
        GTRecipeType currentRecipeType = recipeLogicMachine.getRecipeType();
        if (currentRecipeType != null) {
            addExpandedRecipeType(recipeTypes, currentRecipeType);
            return true;
        }
        return false;
    }

    private static void addExpandedRecipeType(Set<GTRecipeType> target, GTRecipeType recipeType) {
        ArrayDeque<GTRecipeType> pendingRecipeTypes = new ArrayDeque<>();
        Set<GTRecipeType> visitedRecipeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        pendingRecipeTypes.add(recipeType);

        while (!pendingRecipeTypes.isEmpty()) {
            GTRecipeType currentRecipeType = pendingRecipeTypes.removeFirst();
            if (!visitedRecipeTypes.add(currentRecipeType)) {
                continue;
            }

            GTRecipeType[] childRecipeTypes = getReflectedRecipeTypes(currentRecipeType, COMPOSITE_RECIPE_TYPES_METHOD);
            boolean expanded = false;
            if (childRecipeTypes != null) {
                for (GTRecipeType childRecipeType : childRecipeTypes) {
                    if (childRecipeType != null && childRecipeType != currentRecipeType) {
                        pendingRecipeTypes.addLast(childRecipeType);
                        expanded = true;
                    }
                }
            }

            if (!expanded) {
                target.add(currentRecipeType);
            }
        }
    }

    private static void addRecipeTypes(Set<GTRecipeType> target, @Nullable GTRecipeType[] recipeTypes) {
        if (recipeTypes != null) {
            Arrays.stream(recipeTypes)
                    .filter(Objects::nonNull)
                    .forEach(recipeType -> addExpandedRecipeType(target, recipeType));
        }
    }

    private static @Nullable GTRecipeType getReflectedRecipeType(Object owner, String methodName) {
        try {
            Method method = owner.getClass().getMethod(methodName);
            Object value = method.invoke(owner);
            return value instanceof GTRecipeType recipeType ? recipeType : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable GTRecipeType[] getReflectedRecipeTypes(Object owner, String methodName) {
        try {
            Method method = owner.getClass().getMethod(methodName);
            Object value = method.invoke(owner);
            return value instanceof GTRecipeType[] recipeTypes ? recipeTypes : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean canInsert(PatternContainer container, ItemStack patternStack) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.isItemValid(slot, patternStack) && inventory.insertItem(slot, patternStack.copy(), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int insertPattern(PatternContainer container, ItemStack patternStack) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.isItemValid(slot, patternStack)) {
                continue;
            }
            ItemStack remainder = inventory.insertItem(slot, patternStack.copy(), true);
            if (!remainder.isEmpty()) {
                continue;
            }
            inventory.insertItem(slot, patternStack.copy(), false);
            return slot;
        }
        return -1;
    }

    private static boolean removePattern(PatternContainer container, ItemStack patternStack) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (removePattern(container, patternStack, slot)) return true;
        }
        return false;
    }

    private static boolean removePattern(PatternContainer container, ItemStack patternStack, int slot) {
        InternalInventory inventory = container.getTerminalPatternInventory();
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemStack storedStack = inventory.getStackInSlot(slot);
        if (!ItemStack.isSameItemSameTags(storedStack, patternStack) ||
                storedStack.getCount() < patternStack.getCount()) {
            return false;
        }
        ItemStack extracted = inventory.extractItem(slot, patternStack.getCount(), true);
        if (!ItemStack.matches(extracted, patternStack)) {
            return false;
        }
        inventory.extractItem(slot, patternStack.getCount(), false);
        return true;
    }

    private static Component recipeTypeName(GTRecipeType recipeType) {
        return PatternQuickUploadMetadata.recipeTypeName(recipeType.registryName);
    }

    private static boolean isQuickUploadPattern(@Nullable IPatternDetails patternDetails) {
        return patternDetails instanceof AEProcessingPattern ||
                patternDetails instanceof IMolecularAssemblerSupportedPattern;
    }

    public record SearchResult(@Nullable Component failureMessage, PatternQuickUploadMatch<Target> match) {

        static SearchResult fail(Component message) {
            return new SearchResult(message, PatternQuickUploadMatch.select(List.of()));
        }

        static SearchResult success(PatternQuickUploadMatch<Target> match) {
            return new SearchResult(null, match);
        }
    }

    public record UploadResult(Target target, int slot) {}

    public record Target(ResourceKey<Level> levelKey, BlockPos bufferPos, Component targetName, ResourceLocation recipeTypeId,
                         Component recipeTypeName, @Nullable AEItemKey targetIcon,
                         @Nullable ResourceLocation targetMachineId, List<BlockPos> bufferPositions) {

        public Target {
            Objects.requireNonNull(levelKey, "levelKey");
            Objects.requireNonNull(bufferPos, "bufferPos");
            Objects.requireNonNull(targetName, "targetName");
            Objects.requireNonNull(recipeTypeId, "recipeTypeId");
            Objects.requireNonNull(recipeTypeName, "recipeTypeName");
            List<BlockPos> safeBufferPositions = bufferPositions == null || bufferPositions.isEmpty() ?
                    List.of(bufferPos) :
                    bufferPositions;
            bufferPositions = List.copyOf(safeBufferPositions);
        }

        public Target(ResourceKey<Level> levelKey, BlockPos bufferPos, Component targetName,
                      ResourceLocation recipeTypeId, Component recipeTypeName) {
            this(levelKey, bufferPos, targetName, recipeTypeId, recipeTypeName, null, null, List.of(bufferPos));
        }

        public Target(ResourceKey<Level> levelKey, BlockPos bufferPos, Component targetName,
                      ResourceLocation recipeTypeId, Component recipeTypeName, AEItemKey targetIcon) {
            this(levelKey, bufferPos, targetName, recipeTypeId, recipeTypeName, targetIcon,
                    targetIcon == null ? null : targetIcon.getId(), List.of(bufferPos));
        }

        public Target(ResourceKey<Level> levelKey, BlockPos bufferPos, Component targetName,
                      ResourceLocation recipeTypeId, Component recipeTypeName, AEItemKey targetIcon,
                      @Nullable ResourceLocation targetMachineId) {
            this(levelKey, bufferPos, targetName, recipeTypeId, recipeTypeName, targetIcon, targetMachineId,
                    List.of(bufferPos));
        }

        private Target withSingleBufferPos(BlockPos bufferPos) {
            return new Target(levelKey, bufferPos, targetName, recipeTypeId, recipeTypeName, targetIcon, targetMachineId,
                    List.of(bufferPos));
        }

        public boolean showsSinglePosition() {
            return bufferPositions.size() == 1;
        }
    }

    private record TargetKey(ResourceKey<Level> levelKey, BlockPos bufferPos, ResourceLocation recipeTypeId) {}

    private record TargetGroupKey(ResourceKey<Level> levelKey, ResourceLocation recipeTypeId, String targetName,
                                  ResourceLocation targetMachineId) {}
}
